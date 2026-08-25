package io.kelta.worker.controller;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldReportConnectivityControllerTest {

    @Mock private QueryEngine queryEngine;
    @Mock private CollectionRegistry collectionRegistry;

    private FieldReportConnectivityController controller;
    private CollectionDefinition definition;

    @BeforeEach
    void setUp() {
        controller = new FieldReportConnectivityController(queryEngine, collectionRegistry);
        definition = mock(CollectionDefinition.class);
        when(collectionRegistry.get("field-reports")).thenReturn(definition);
    }

    private QueryResult resultOf(List<Map<String, Object>> rows) {
        return QueryResult.of(rows, rows.size(), new Pagination(1, Pagination.MAX_PAGE_SIZE));
    }

    @Test
    void collectionMissing_returns404() {
        when(collectionRegistry.get("field-reports")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.connectivity("f1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noReports_returnsEmptyAggregate() {
        when(queryEngine.executeQuery(any(), any())).thenReturn(resultOf(List.of()));

        ResponseEntity<Map<String, Object>> response = controller.connectivity("f1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("totalReports")).isEqualTo(0);
        assertThat((List<?>) body.get("carriers")).isEmpty();
    }

    @Test
    void averagesSignalBarsPerCarrier() {
        when(queryEngine.executeQuery(any(), any())).thenReturn(resultOf(List.of(
                Map.of("carrier", "Verizon", "signalBars", 4),
                Map.of("carrier", "Verizon", "signalBars", 2),
                Map.of("carrier", "AT&T", "signalBars", 1)
        )));

        Map<String, Object> body = controller.connectivity("f1").getBody();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> carriers = (List<Map<String, Object>>) body.get("carriers");
        assertThat(carriers).hasSize(2);
        // Most-reported carrier first.
        assertThat(carriers.get(0).get("carrier")).isEqualTo("Verizon");
        assertThat(carriers.get(0).get("reportCount")).isEqualTo(2L);
        assertThat(carriers.get(0).get("avgSignalBars")).isEqualTo(3.0);
        assertThat(carriers.get(1).get("carrier")).isEqualTo("AT&T");
        assertThat(carriers.get(1).get("reportCount")).isEqualTo(1L);
        assertThat(carriers.get(1).get("avgSignalBars")).isEqualTo(1.0);
    }

    @Test
    void foldsDifferentCasingOfTheSameCarrierTogether() {
        when(queryEngine.executeQuery(any(), any())).thenReturn(resultOf(List.of(
                Map.of("carrier", "Verizon", "signalBars", 5),
                Map.of("carrier", "verizon", "signalBars", 3)
        )));

        Map<String, Object> body = controller.connectivity("f1").getBody();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> carriers = (List<Map<String, Object>>) body.get("carriers");
        assertThat(carriers).hasSize(1);
        assertThat(carriers.get(0).get("carrier")).isEqualTo("Verizon"); // first-seen casing wins
        assertThat(carriers.get(0).get("reportCount")).isEqualTo(2L);
        assertThat(carriers.get(0).get("avgSignalBars")).isEqualTo(4.0);
    }

    @Test
    void skipsRowsMissingCarrierOrSignalBars() {
        when(queryEngine.executeQuery(any(), any())).thenReturn(resultOf(List.of(
                Map.of("carrier", "Verizon", "signalBars", 4),
                Map.of("carrier", "", "signalBars", 3),
                java.util.Collections.singletonMap("carrier", "T-Mobile") // no signalBars key
        )));

        Map<String, Object> body = controller.connectivity("f1").getBody();

        assertThat(body.get("totalReports")).isEqualTo(3); // raw row count, not the filtered aggregate count
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> carriers = (List<Map<String, Object>>) body.get("carriers");
        assertThat(carriers).hasSize(1);
        assertThat(carriers.get(0).get("carrier")).isEqualTo("Verizon");
    }

    @Test
    void queriesOnlyCarrierAndSignalBarsFilteredByFacility() {
        when(queryEngine.executeQuery(any(), any())).thenReturn(resultOf(List.of()));

        controller.connectivity("USFS-1863");

        org.mockito.ArgumentCaptor<QueryRequest> captor = org.mockito.ArgumentCaptor.forClass(QueryRequest.class);
        org.mockito.Mockito.verify(queryEngine).executeQuery(org.mockito.ArgumentMatchers.eq(definition), captor.capture());
        QueryRequest request = captor.getValue();
        assertThat(request.fields()).containsExactlyInAnyOrder("carrier", "signalBars");
        assertThat(request.filters()).hasSize(1);
        assertThat(request.filters().get(0).fieldName()).isEqualTo("facilityId");
        assertThat(request.filters().get(0).value()).isEqualTo("USFS-1863");
    }
}
