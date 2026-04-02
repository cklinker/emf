package io.kelta.gateway.filter;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCollectionResponseCacheFilter Tests")
class SystemCollectionResponseCacheFilterTest {

    @Mock
    private GatewayCacheManager cacheManager;

    private SystemCollectionResponseCacheFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SystemCollectionResponseCacheFilter(cacheManager);
    }

    @Nested
    @DisplayName("extractCollectionName Tests")
    class ExtractCollectionNameTests {

        @Test
        void extractsCollectionNameFromListPath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/api/collections"))
                    .isEqualTo("collections");
        }

        @Test
        void extractsCollectionNameFromGetByIdPath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/api/collections/abc-123"))
                    .isEqualTo("collections");
        }

        @Test
        void extractsHyphenatedCollectionName() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/api/ui-pages"))
                    .isEqualTo("ui-pages");
        }

        @Test
        void extractsFromSubResourcePath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/api/collections/abc/fields"))
                    .isEqualTo("collections");
        }

        @Test
        void returnsNullForNonApiPath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/internal/health"))
                    .isNull();
        }

        @Test
        void returnsNullForRootApiPath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName("/api"))
                    .isNull();
        }

        @Test
        void returnsNullForEmptyPath() {
            assertThat(SystemCollectionResponseCacheFilter.extractCollectionName(""))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("CACHEABLE_COLLECTIONS Tests")
    class CacheableCollectionsTests {

        @Test
        void containsHighTrafficCollections() {
            assertThat(SystemCollectionResponseCacheFilter.CACHEABLE_COLLECTIONS)
                    .contains("ui-pages", "ui-menus", "ui-menu-items",
                            "collections", "fields", "tenants", "oidc-providers");
        }

        @Test
        void doesNotContainUserDataCollections() {
            assertThat(SystemCollectionResponseCacheFilter.CACHEABLE_COLLECTIONS)
                    .doesNotContain("users", "notes", "attachments", "bulk-jobs");
        }
    }

    @Test
    void filterOrderIsBeforeRouting() {
        assertThat(filter.getOrder()).isEqualTo(-10);
    }
}
