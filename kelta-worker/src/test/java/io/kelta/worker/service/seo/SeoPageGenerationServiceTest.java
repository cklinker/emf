package io.kelta.worker.service.seo;

import io.kelta.worker.repository.SeoPageRepository;
import io.kelta.worker.repository.SeoPageRepository.SeoPageRow;
import io.kelta.worker.repository.SeoPageRepository.TargetAggregate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SeoPageGenerationService")
class SeoPageGenerationServiceTest {

    private SeoPageRepository repository;
    private SeoPageGenerationService service;

    @BeforeEach
    void setUp() {
        repository = mock(SeoPageRepository.class);
        service = new SeoPageGenerationService(
                repository, new ObjectMapper(), new SimpleMeterRegistry(), true, 5);
    }

    private static TargetAggregate agg(String tenant, String targetId, String name,
                                       int watchers, int wins) {
        return new TargetAggregate(tenant, targetId, name, "campsites", watchers, wins,
                wins > 0 ? Instant.now() : null);
    }

    @Test
    @DisplayName("slug is a human-readable slugified name plus a short target-id suffix")
    void slugShape() {
        assertThat(SeoPageGenerationService.slug("Maroon Bells Campground",
                "abcdef12-3456-7890-abcd-ef1234567890"))
                .isEqualTo("maroon-bells-campground-abcdef12");
    }

    @Test
    @DisplayName("slug falls back to 'target' for a blank name and tolerates a null id")
    void slugFallbacks() {
        assertThat(SeoPageGenerationService.slug("   ", "abcdef12-3456")).isEqualTo("target-abcdef12");
        assertThat(SeoPageGenerationService.slug("Angels Landing!!!", null)).isEqualTo("angels-landing");
    }

    @Test
    @DisplayName("guardrail publishes a target with enough watchers or any win, else not")
    void publishGuardrail() {
        assertThat(service.isPublishable(agg("t", "x", "n", 5, 0))).isTrue();  // >= min watchers
        assertThat(service.isPublishable(agg("t", "x", "n", 0, 1))).isTrue();  // has a win
        assertThat(service.isPublishable(agg("t", "x", "n", 4, 0))).isFalse(); // thin
    }

    @Test
    @DisplayName("generate upserts a page per target with the right published flag, then prunes")
    void generateUpsertsAndPrunes() {
        when(repository.computeTargetAggregates()).thenReturn(List.of(
                agg("t1", "target-hot", "Hot Site", 12, 3),   // publishable
                agg("t1", "target-cold", "Cold Site", 1, 0))); // thin

        service.generate();

        ArgumentCaptor<SeoPageRow> captor = ArgumentCaptor.forClass(SeoPageRow.class);
        verify(repository, times(2)).upsert(captor.capture());
        List<SeoPageRow> rows = captor.getAllValues();
        assertThat(rows).extracting(SeoPageRow::title).containsExactly("Hot Site", "Cold Site");
        assertThat(rows).extracting(SeoPageRow::published).containsExactly(true, false);
        assertThat(rows.get(0).statsJson()).contains("\"winCount\":3");

        verify(repository).deleteGeneratedBefore(any());
    }

    @Test
    @DisplayName("does nothing when disabled")
    void disabled() {
        SeoPageGenerationService off = new SeoPageGenerationService(
                repository, new ObjectMapper(), new SimpleMeterRegistry(), false, 5);
        off.generate();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a repository failure is swallowed — the sweep never throws")
    void swallowsFailure() {
        when(repository.computeTargetAggregates()).thenThrow(new RuntimeException("db down"));
        service.generate(); // must not throw
        verify(repository, never()).upsert(any());
    }
}
