package io.kelta.runtime.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GeoContext Tests")
class GeoContextTest {

    @Test
    @DisplayName("returns the bound stamp inside a scope and empty outside")
    void boundAndUnbound() {
        assertThat(GeoContext.current()).isEmpty();
        assertThat(GeoContext.currentCountry()).isEmpty();

        GeoStamp stamp = new GeoStamp("PT", "Lisbon", "Cascais", 38.7, -9.4, 20);
        ScopedValue.where(GeoContext.CURRENT_GEO, stamp).run(() -> {
            assertThat(GeoContext.current()).contains(stamp);
            assertThat(GeoContext.currentCountry()).isEqualTo("PT");
        });

        assertThat(GeoContext.current()).isEmpty();
    }
}
