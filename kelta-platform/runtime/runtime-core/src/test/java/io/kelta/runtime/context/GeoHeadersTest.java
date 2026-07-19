package io.kelta.runtime.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GeoHeaders Tests")
class GeoHeadersTest {

    @Test
    @DisplayName("parses a full set of gateway geo headers")
    void parsesFullHeaderSet() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GeoHeaders.HEADER_COUNTRY, "PT");
        request.addHeader(GeoHeaders.HEADER_REGION, "Lisbon");
        request.addHeader(GeoHeaders.HEADER_CITY, "M%C3%BCnchen");
        request.addHeader(GeoHeaders.HEADER_LAT, "38.6979");
        request.addHeader(GeoHeaders.HEADER_LON, "-9.4207");
        request.addHeader(GeoHeaders.HEADER_ACCURACY, "20");

        Optional<GeoStamp> stamp = GeoHeaders.parse(request);

        assertThat(stamp).isPresent();
        assertThat(stamp.get().country()).isEqualTo("PT");
        assertThat(stamp.get().region()).isEqualTo("Lisbon");
        assertThat(stamp.get().city()).isEqualTo("München");
        assertThat(stamp.get().latitude()).isEqualTo(38.6979);
        assertThat(stamp.get().longitude()).isEqualTo(-9.4207);
        assertThat(stamp.get().accuracyKm()).isEqualTo(20);
    }

    @Test
    @DisplayName("returns empty without a valid country header")
    void emptyWithoutCountry() {
        assertThat(GeoHeaders.parse(new MockHttpServletRequest())).isEmpty();

        MockHttpServletRequest bad = new MockHttpServletRequest();
        bad.addHeader(GeoHeaders.HEADER_COUNTRY, "NOT-A-COUNTRY");
        assertThat(GeoHeaders.parse(bad)).isEmpty();
    }

    @Test
    @DisplayName("drops malformed optional fields instead of failing")
    void dropsMalformedOptionalFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GeoHeaders.HEADER_COUNTRY, "de");
        request.addHeader(GeoHeaders.HEADER_LAT, "not-a-number");
        request.addHeader(GeoHeaders.HEADER_ACCURACY, "");

        Optional<GeoStamp> stamp = GeoHeaders.parse(request);

        assertThat(stamp).isPresent();
        assertThat(stamp.get().country()).isEqualTo("DE"); // normalized to upper case
        assertThat(stamp.get().latitude()).isNull();
        assertThat(stamp.get().accuracyKm()).isNull();
    }

    @Test
    @DisplayName("toMap omits null fields and keeps the country")
    void toMapOmitsNulls() {
        GeoStamp stamp = new GeoStamp("PT", null, "Cascais", null, null, null);
        Map<String, Object> map = stamp.toMap();

        assertThat(map).containsEntry("country", "PT")
                .containsEntry("city", "Cascais")
                .doesNotContainKeys("region", "lat", "lon", "accuracyKm");
    }
}
