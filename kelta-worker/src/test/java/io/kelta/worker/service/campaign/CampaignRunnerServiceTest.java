package io.kelta.worker.service.campaign;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.config.CampaignProperties;
import io.kelta.worker.repository.CampaignRecipientRepository;
import io.kelta.worker.repository.CampaignRepository;
import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.service.TenantQuotaResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignRunnerService")
class CampaignRunnerServiceTest {

    private static final String TENANT = "t1";
    private static final String CAMPAIGN = "c1";

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignRecipientRepository recipientRepository;
    @Mock private EmailSuppressionRepository suppressionRepository;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private QueryEngine queryEngine;
    @Mock private EmailService emailService;
    @Mock private TenantQuotaResolver quotaResolver;
    @Mock private CollectionDefinition definition;

    private CampaignRunnerService runner;

    @BeforeEach
    void setUp() {
        CampaignProperties props = new CampaignProperties(
                true, 15000, 5, 0 /* no pacing in tests */, 50000,
                "https://acme.kelta.io", "unit-secret");
        TrackingTokenService tokenService = new TrackingTokenService(props);
        runner = new CampaignRunnerService(campaignRepository, recipientRepository,
                suppressionRepository, collectionRegistry, queryEngine, emailService,
                quotaResolver, tokenService, props, new ObjectMapper());
    }

    private void stubClaimableCampaign(String bodyHtml) {
        when(campaignRepository.findClaimable(anyInt()))
                .thenReturn(List.of(Map.of("id", CAMPAIGN, "tenant_id", TENANT)));
        when(campaignRepository.claim(CAMPAIGN)).thenReturn(true);
        when(campaignRepository.findById(CAMPAIGN, TENANT)).thenReturn(Optional.of(Map.ofEntries(
                Map.entry("id", CAMPAIGN),
                Map.entry("target_collection", "contacts"),
                Map.entry("recipient_email_field", "email"),
                Map.entry("subject", "Hi ${email}"),
                Map.entry("body_html", bodyHtml),
                Map.entry("status", "SENDING"))));
        when(collectionRegistry.get("contacts")).thenReturn(definition);
    }

    private void stubOnePageOfRecipients() {
        QueryResult page = new QueryResult(
                List.of(Map.of("id", "rec-a", "email", "a@example.com"),
                        Map.of("id", "rec-b", "email", "b@example.com")),
                new PaginationMetadata(2, 1, 500, 1));
        when(queryEngine.executeQuery(eq(definition), any())).thenReturn(page);
    }

    @Test
    @DisplayName("sends to eligible recipients, skips suppressed, and finalizes the campaign")
    void sendsAndSuppresses() {
        stubClaimableCampaign("<a href=\"https://acme.example/deal\">Deal</a>");
        stubOnePageOfRecipients();
        when(quotaResolver.intQuota(eq(TENANT), anyString())).thenReturn(1000);
        when(recipientRepository.countSentToday(TENANT)).thenReturn(0);
        when(recipientRepository.insertPending(eq(TENANT), eq(CAMPAIGN), eq("rec-a"), eq("a@example.com")))
                .thenReturn(Optional.of("recipient-a"));
        when(recipientRepository.insertPending(eq(TENANT), eq(CAMPAIGN), eq("rec-b"), eq("b@example.com")))
                .thenReturn(Optional.of("recipient-b"));
        when(suppressionRepository.isSuppressed(TENANT, "a@example.com")).thenReturn(true);
        when(suppressionRepository.isSuppressed(TENANT, "b@example.com")).thenReturn(false);
        when(emailService.queueEmail(eq(TENANT), eq("b@example.com"), anyString(), anyString(),
                eq("CAMPAIGN_SEND"), eq(CAMPAIGN))).thenReturn("log-b");

        runner.processClaimableCampaigns();

        // Suppressed recipient: marked, never emailed.
        verify(recipientRepository).markStatus("recipient-a", "SUPPRESSED");
        verify(emailService, never()).queueEmail(eq(TENANT), eq("a@example.com"),
                anyString(), anyString(), anyString(), anyString());

        // Eligible recipient: rendered subject + tracked body, sent, counted.
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).queueEmail(eq(TENANT), eq("b@example.com"), subject.capture(),
                body.capture(), eq("CAMPAIGN_SEND"), eq(CAMPAIGN));
        assertThat(subject.getValue()).isEqualTo("Hi b@example.com");
        assertThat(body.getValue())
                .contains("/api/track/open")                 // open pixel injected
                .contains("/api/track/click")                // link rewritten through click tracker
                .contains("/api/track/unsubscribe");         // unsubscribe footer

        verify(recipientRepository).markSent("recipient-b", "log-b");
        verify(campaignRepository).incrementSent(CAMPAIGN);
        verify(campaignRepository).setTotalRecipients(CAMPAIGN, 2);
        verify(campaignRepository).markSent(CAMPAIGN);
    }

    @Test
    @DisplayName("skips all recipients once the daily governor limit is exhausted")
    void respectsDailyGovernor() {
        stubClaimableCampaign("<p>Hello</p>");
        stubOnePageOfRecipients();
        when(quotaResolver.intQuota(eq(TENANT), anyString())).thenReturn(5);
        when(recipientRepository.countSentToday(TENANT)).thenReturn(5); // already at the cap
        when(recipientRepository.insertPending(eq(TENANT), eq(CAMPAIGN), anyString(), anyString()))
                .thenReturn(Optional.of("recipient-a"), Optional.of("recipient-b"));
        when(suppressionRepository.isSuppressed(eq(TENANT), anyString())).thenReturn(false);

        runner.processClaimableCampaigns();

        verify(recipientRepository, times(2)).markStatus(anyString(), eq("SKIPPED"));
        verify(emailService, never()).queueEmail(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(campaignRepository).markSent(CAMPAIGN);
    }

    @Test
    @DisplayName("fails the campaign when the target collection is unknown")
    void failsOnUnknownCollection() {
        when(campaignRepository.findClaimable(anyInt()))
                .thenReturn(List.of(Map.of("id", CAMPAIGN, "tenant_id", TENANT)));
        when(campaignRepository.claim(CAMPAIGN)).thenReturn(true);
        when(campaignRepository.findById(CAMPAIGN, TENANT)).thenReturn(Optional.of(Map.of(
                "id", CAMPAIGN, "target_collection", "ghost",
                "recipient_email_field", "email", "subject", "s")));
        when(collectionRegistry.get("ghost")).thenReturn(null);

        runner.processClaimableCampaigns();

        verify(campaignRepository).markFailed(eq(CAMPAIGN), anyString());
        verify(emailService, never()).queueEmail(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }
}
