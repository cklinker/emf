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
        assertThat(contract.version()).isEqualTo("2.0");
        assertThat(contract.slug()).isEqualTo("home");
        assertThat(contract.title()).isEqualTo("Home");
        assertThat(contract.tree()).containsKey("components");
        assertThat(contract.variables()).isEmpty();
        assertThat(contract.dataSources()).isEmpty();

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
    @DisplayName("surfaces a v2 page's variables and dataSources alongside the whole config as tree")
    void rendersV2Page() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        List<Object> components = List.of(Map.of("id", "h1", "type", "heading"));
        Map<String, Object> config = Map.of(
                "schemaVersion", 2,
                "components", components,
                "variables", List.of(Map.of("name", "statusFilter", "type", "string")),
                "dataSources", List.of(Map.of("name", "tickets", "collection", "tickets")));
        Map<String, Object> page = Map.of("slug", "dashboard", "title", "Dashboard",
                "path", "/dashboard", "config", config);
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        Optional<PageRenderContract> result = service().render("dashboard");

        assertThat(result).isPresent();
        PageRenderContract contract = result.get();
        assertThat(contract.version()).isEqualTo("2.0");
        assertThat(contract.tree()).containsKeys("components", "variables", "dataSources");
        assertThat(contract.tree().get("components")).isEqualTo(components);
        assertThat(contract.variables()).hasSize(1);
        assertThat(contract.dataSources()).hasSize(1);
    }

    @Test
    @DisplayName("a v1 Map config (no variables/dataSources) becomes the tree with empty sibling lists")
    void v1WholeConfigToTree() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of("slug", "welcome", "title", "Welcome", "path", "/welcome",
                "config", Map.of("components", List.of(Map.of("type", "heading")),
                        "layout", Map.of("kind", "stack")));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        PageRenderContract contract = service().render("welcome").orElseThrow();

        assertThat(contract.version()).isEqualTo("2.0");
        assertThat(contract.tree()).containsKeys("components", "layout");
        assertThat(contract.variables()).isEmpty();
        assertThat(contract.dataSources()).isEmpty();
    }

    @Test
    @DisplayName("parses a config stored as a JSON string, defaulting the sibling lists to empty")
    void parsesStringConfig() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of("slug", "home", "title", "Home", "path", "/home",
                "config", "{\"components\":[{\"type\":\"heading\"}]}");
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        Optional<PageRenderContract> result = service().render("home");

        assertThat(result).isPresent();
        assertThat(result.get().tree()).containsKey("components");
        assertThat(result.get().variables()).isEmpty();
        assertThat(result.get().dataSources()).isEmpty();
    }

    @Test
    @DisplayName("a config String with no siblings parses once: whole config to tree, empty lists")
    void v1StringConfigToTree() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of("slug", "home", "title", "Home", "path", "/home",
                "config", "{\"components\":[{\"type\":\"heading\"}],\"layout\":{\"kind\":\"stack\"}}");
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        PageRenderContract contract = service().render("home").orElseThrow();

        assertThat(contract.tree()).containsKeys("components", "layout");
        assertThat(contract.variables()).isEmpty();
        assertThat(contract.dataSources()).isEmpty();
    }

    @Test
    @DisplayName("a null config yields an empty tree and empty sibling lists without an NPE")
    void nullConfig() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = new java.util.HashMap<>();
        page.put("slug", "home");
        page.put("title", "Home");
        page.put("path", "/home");
        page.put("config", null);
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        PageRenderContract contract = service().render("home").orElseThrow();

        assertThat(contract.version()).isEqualTo("2.0");
        assertThat(contract.tree()).isEmpty();
        assertThat(contract.variables()).isEmpty();
        assertThat(contract.dataSources()).isEmpty();
    }

    @Test
    @DisplayName("an unparseable config String warns and yields an empty tree, no throw")
    void garbageStringConfig() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(collectionRegistry.get("ui-pages")).thenReturn(def);
        Map<String, Object> page = Map.of("slug", "home", "title", "Home", "path", "/home",
                "config", "not json");
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(oneRow(page));

        PageRenderContract contract = service().render("home").orElseThrow();

        assertThat(contract.tree()).isEmpty();
        assertThat(contract.variables()).isEmpty();
        assertThat(contract.dataSources()).isEmpty();
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
