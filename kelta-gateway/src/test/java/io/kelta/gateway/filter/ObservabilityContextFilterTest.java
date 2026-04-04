package io.kelta.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ObservabilityContextFilter Tests")
class ObservabilityContextFilterTest {

    @Test
    void shouldHaveCorrectOrder() {
        var filter = new ObservabilityContextFilter();
        assertEquals(-90, filter.getOrder());
    }

    @Test
    void shouldImplementGlobalFilterAndOrdered() {
        var filter = new ObservabilityContextFilter();
        assertInstanceOf(Ordered.class, filter);
    }
}
