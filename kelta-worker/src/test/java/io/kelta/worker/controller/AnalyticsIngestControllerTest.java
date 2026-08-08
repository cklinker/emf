package io.kelta.worker.controller;

import io.kelta.worker.controller.AnalyticsIngestController.EventDto;
import io.kelta.worker.controller.AnalyticsIngestController.IngestRequest;
import io.kelta.worker.service.analytics.AnalyticsCaptureService;
import io.kelta.worker.service.analytics.AnalyticsCaptureService.IncomingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("AnalyticsIngestController")
class AnalyticsIngestControllerTest {

    private AnalyticsCaptureService captureService;
    private AnalyticsIngestController controller;

    @BeforeEach
    void setUp() {
        captureService = mock(AnalyticsCaptureService.class);
        controller = new AnalyticsIngestController(captureService);
    }

    private EventDto event(String type) {
        return new EventDto(type, "q", null, null, "/p", null, null, "s", null, null);
    }

    @Test
    @DisplayName("delegates a valid batch to the capture service and returns the accepted count")
    void acceptsBatch() {
        when(captureService.capture(anyList(), eq("user-1"))).thenReturn(2);

        ResponseEntity<Map<String, Object>> response = controller.ingest(
                new IngestRequest(List.of(event("PAGE_VIEW"), event("ASSISTANT_QUESTION"))), "user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("accepted", 2);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(captureService).capture(captor.capture(), eq("user-1"));
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0)).isInstanceOf(IncomingEvent.class);
    }

    @Test
    @DisplayName("an empty batch is a no-op accepted:0")
    void emptyBatchNoOp() {
        ResponseEntity<Map<String, Object>> response =
                controller.ingest(new IngestRequest(List.of()), "user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("accepted", 0);
        verifyNoInteractions(captureService);
    }

    @Test
    @DisplayName("a null body is a no-op accepted:0")
    void nullBodyNoOp() {
        ResponseEntity<Map<String, Object>> response = controller.ingest(null, "user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("accepted", 0);
        verifyNoInteractions(captureService);
    }

    @Test
    @DisplayName("a batch over the limit is rejected 400 and never reaches the service")
    void oversizedBatchRejected() {
        List<EventDto> tooMany = IntStream.rangeClosed(0, AnalyticsCaptureService.MAX_BATCH)
                .mapToObj(i -> event("PAGE_VIEW"))
                .toList();

        assertThatThrownBy(() -> controller.ingest(new IngestRequest(tooMany), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(captureService);
    }
}
