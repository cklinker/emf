package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PageRenderService")
class PageRenderServiceTest {

    @Mock
    private QueryEngine queryEngine;

    @Mock
    private CollectionRegistry collectionRegistry;

    private PageRenderService service() {
        return new PageRenderService(queryEngine, collectionRegistry, JsonMapper.builder().build());
    }

    private static QueryResult oneRow(Map<String, Object> page) {
        return QueryResult.of(List.of(page), 1, new Pagination(1, 1));
    }

    @Test
    @DisplayName("returns a versioned contract for a published page, filtering by slug+published+active")
    void rendersPublishedPage() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of(
                "slug", "home", "title", "Home", "path", "/home",
                "config", Map.of("components", List.of()));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        Optional<PageRenderContract> result = service().render("home");

        assertThat(result).isPresent();
        PageRenderContract contract = result.get();
        assertThat(contract.version()).isEqualTo("1.0");
        assertThat(contract.slug()).isEqualTo("home");
        assertThat(contract.title()).isEqualTo("Home");
        assertThat(contract.tree()).containsKey("components");

        ArgumentCaptor<QueryRequest> req = ArgumentCaptor.forClass(QueryRequest.class);
        verify(queryEngine).executeQuery(eq(def), req.capture());
        List<FilterCondition> filters = req.getValue().filters();
        assertThat(filters).anySatisfy(f -> {
            assertThat(f.fieldName()).isEqualTo("published");
            assertThat(f.value()).isEqualTo(true);
        });
        assertThat(filters).anySatisfy(f -> assertThat(f.fieldName()).isEqualTo("active"));
        assertThat(filters).anySatisfy(f -> {
            assertThat(f.fieldName()).isEqualTo("slug");
            assertThat(f.value()).isEqualTo("home");
        });
    }

    @Test
    @DisplayName("parses a config stored as a JSON string")
    void parsesStringConfig() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of("slug", "home", "title", "Home", "path", "/home",
                "config", "{\"components\":[{\"type\":\"heading\"}]}");
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        Optional<PageRenderContract> result = service().render("home");

        assertThat(result).isPresent();
        assertThat(result.get().tree()).containsKey("components");
    }

    @Test
    @DisplayName("returns empty when no published page matches the slug")
    void notFoundWhenNoMatch() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class)))
                .thenReturn(QueryResult.of(List.of(), 0, new Pagination(1, 1)));

        assertThat(service().render("missing")).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the ui-pages collection is not registered")
    void emptyWhenNoCollection() {
        when(collectionRegistry.get("ui-pages")).thenReturn(null);
        assertThat(service().render("home")).isEmpty();
        verifyNoInteractions(queryEngine);
    }

    @Test
    @DisplayName("returns empty for a blank slug without touching the registry")
    void blankSlug() {
        assertThat(service().render("  ")).isEmpty();
        verify(collectionRegistry, never()).get(any());
    }
}
