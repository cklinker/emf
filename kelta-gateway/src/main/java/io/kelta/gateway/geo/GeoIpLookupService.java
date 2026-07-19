package io.kelta.gateway.geo;

import com.maxmind.db.Reader;
import io.kelta.gateway.geo.model.GeoCityData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Optional;

/**
 * In-memory IP → {@link GeoResult} lookup against the loaded GeoLite2-City database.
 *
 * <p>Lookups are microseconds against a {@code FileMode.MEMORY} reader, so calling this
 * inline from a reactive filter is acceptable. Fail-open throughout: no reader loaded,
 * unparseable IP, non-public IP, or no DB entry → {@code Optional.empty()}.
 */
@Component
public class GeoIpLookupService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpLookupService.class);

    private final GeoIpDatabaseManager databaseManager;

    public GeoIpLookupService(GeoIpDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<GeoResult> lookup(String ip) {
        if (ip == null || ip.isBlank()) {
            return Optional.empty();
        }
        Reader reader = databaseManager.getReader();
        if (reader == null) {
            return Optional.empty();
        }
        InetAddress address;
        try {
            // DNS-free literal parsing — attacker-supplied strings must never resolve.
            address = InetAddress.ofLiteral(ip);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (isNonPublic(address)) {
            return Optional.empty();
        }
        try {
            GeoCityData data = reader.get(address, GeoCityData.class);
            if (data == null || data.country() == null || data.country().isoCode() == null) {
                return Optional.empty();
            }
            String region = data.subdivisions() != null && !data.subdivisions().isEmpty()
                    ? data.subdivisions().getFirst().displayName()
                    : null;
            String city = data.city() != null ? data.city().displayName() : null;
            Double lat = data.location() != null ? data.location().latitude() : null;
            Double lon = data.location() != null ? data.location().longitude() : null;
            Integer accuracy = data.location() != null ? data.location().accuracyRadius() : null;
            return Optional.of(new GeoResult(data.country().isoCode(), region, city, lat, lon, accuracy));
        } catch (Exception e) {
            // Includes lookups racing a reader close during hot-swap — fail open.
            log.debug("GeoIP lookup failed for {}: {}", ip, e.toString());
            return Optional.empty();
        }
    }

    /**
     * True for addresses that can never have a meaningful public geolocation:
     * loopback, unspecified, link-local, multicast, RFC1918, CGNAT 100.64/10 and
     * IPv6 ULA fc00::/7.
     */
    static boolean isNonPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            // CGNAT 100.64.0.0/10
            return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 0x40;
        }
        if (address instanceof Inet6Address) {
            // Unique local addresses fc00::/7
            return (bytes[0] & 0xFE) == (byte) 0xFC;
        }
        return false;
    }
}
