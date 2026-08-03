package io.kelta.auth.controller;

import io.kelta.auth.service.botchallenge.BotChallengeVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BotChallengeController Tests")
class BotChallengeControllerTest {

    private final BotChallengeVerifier verifier = mock(BotChallengeVerifier.class);
    private final BotChallengeController controller = new BotChallengeController(verifier);

    @Test
    @DisplayName("serves a challenge when the control is enabled")
    void servesChallenge() {
        Map<String, Object> challenge = Map.of("algorithm", "SHA-256", "challenge", "abc",
                "maxnumber", 100000, "salt", "s?expires=1", "signature", "sig");
        when(verifier.enabled()).thenReturn(true);
        when(verifier.issue()).thenReturn(challenge);

        var response = controller.challenge();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(challenge);
    }

    @Test
    @DisplayName("404s when disabled instead of issuing an unusable challenge")
    void notFoundWhenDisabled() {
        // A frontend needs to distinguish "this deployment requires no challenge"
        // from a misconfiguration; issue() would also throw here.
        when(verifier.enabled()).thenReturn(false);

        assertThat(controller.challenge().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(verifier, never()).issue();
    }
}
