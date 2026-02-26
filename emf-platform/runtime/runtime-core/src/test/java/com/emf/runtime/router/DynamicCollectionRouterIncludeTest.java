package com.emf.runtime.router;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.ReferenceConfig;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.PaginationMetadata;
import com.emf.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for JSON:API include parameter handling in DynamicCollectionRouter.
 */
class DynamicCollectionRouterIncludeTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private DynamicCollectionRouter router;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        router = new DynamicCollectionRouter(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(router).build();
        objectMapper = new ObjectMapper();
    }

    private CollectionDefinition buildMenuCollection() {
        return new CollectionDefinitionBuilder()
                .name("ui-menus")
                .displayName("UI Menus")
                .addField(FieldDefinition.requiredString("name", 100))
                .addField(FieldDefinition.string("description", 500))
                .systemCollection(true)
                .tenantScoped(true)
                .readOnly(false)
                .build();
    }

    private CollectionDefinition buildMenuItemCollection() {
        return new CollectionDefinitionBuilder()
                .name("ui-menu-items")
                .displayName("UI Menu Items")
                .addField(new FieldDefinition("menuId", FieldType.MASTER_DETAIL, false, false, false,
                        null, null, null,
                        ReferenceConfig.masterDetail("ui-menus", "Menu"), null))
                .addField(FieldDefinition.requiredString("label", 100))
                .addField(FieldDefinition.requiredString("path", 200))
                .addField(FieldDefinition.string("icon", 100))
                .systemCollection(true)
                .tenantScoped(false)
                .readOnly(false)
                .build();
    }

    private Map<String, Object> menuRecord(String id, String name) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("name", name);
        record.put("description", null);
        return record;
    }

    private Map<String, Object> menuItemRecord(String id, String menuId, String label, String path) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("menuId", menuId);
        record.put("label", label);
        record.put("path", path);
        record.put("icon", null);
        return record;
    }

    @Nested
    @DisplayName("List with include parameter")
    class ListWithInclude {

        @Test
        @DisplayName("Should include child records when include parameter is specified")
        @SuppressWarnings("unchecked")
        void list_withInclude_returnsIncludedResources() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            CollectionDefinition menuItemDef = buildMenuItemCollection();

            when(registry.get("ui-menus")).thenReturn(menuDef);
            when(registry.get("ui-menu-items")).thenReturn(menuItemDef);

            // Primary query returns menus
            Map<String, Object> menu1 = menuRecord("menu-1", "Main Menu");
            Map<String, Object> menu2 = menuRecord("menu-2", "Settings Menu");
            QueryResult menuResult = new QueryResult(
                    List.of(menu1, menu2),
                    new PaginationMetadata(2, 1, 20, 1));

            // Child query returns menu items
            Map<String, Object> item1 = menuItemRecord("item-1", "menu-1", "Home", "/home");
            Map<String, Object> item2 = menuItemRecord("item-2", "menu-1", "Dashboard", "/dashboard");
            Map<String, Object> item3 = menuItemRecord("item-3", "menu-2", "Profile", "/profile");
            QueryResult itemResult = new QueryResult(
                    List.of(item1, item2, item3),
                    new PaginationMetadata(3, 1, 1000, 1));

            // First call = primary query, second call = child include query
            when(queryEngine.executeQuery(eq(menuDef), any(QueryRequest.class)))
                    .thenReturn(menuResult);
            when(queryEngine.executeQuery(eq(menuItemDef), any(QueryRequest.class)))
                    .thenReturn(itemResult);

            MvcResult result = mockMvc.perform(get("/api/collections/ui-menus")
                            .param("include", "ui-menu-items")
                            .header("X-Tenant-ID", "tenant-1"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            // Verify data is present
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            assertNotNull(data);
            assertEquals(2, data.size());

            // Verify included is present with 3 items
            List<Map<String, Object>> included = (List<Map<String, Object>>) response.get("included");
            assertNotNull(included, "Response should have 'included' array");
            assertEquals(3, included.size());

            // Verify included items have correct type
            for (Map<String, Object> inc : included) {
                assertEquals("ui-menu-items", inc.get("type"));
            }
        }

        @Test
        @DisplayName("Should not include array when include collection not found")
        @SuppressWarnings("unchecked")
        void list_withUnknownInclude_noIncludedArray() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            when(registry.get("ui-menus")).thenReturn(menuDef);
            when(registry.get("nonexistent")).thenReturn(null);

            Map<String, Object> menu1 = menuRecord("menu-1", "Main Menu");
            QueryResult menuResult = new QueryResult(
                    List.of(menu1),
                    new PaginationMetadata(1, 1, 20, 1));

            when(queryEngine.executeQuery(eq(menuDef), any(QueryRequest.class)))
                    .thenReturn(menuResult);

            MvcResult result = mockMvc.perform(get("/api/collections/ui-menus")
                            .param("include", "nonexistent")
                            .header("X-Tenant-ID", "tenant-1"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            assertNull(response.get("included"), "Should not have 'included' when collection not found");
        }

        @Test
        @DisplayName("Should work without include parameter")
        @SuppressWarnings("unchecked")
        void list_withoutInclude_noIncludedArray() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            when(registry.get("ui-menus")).thenReturn(menuDef);

            Map<String, Object> menu1 = menuRecord("menu-1", "Main Menu");
            QueryResult menuResult = new QueryResult(
                    List.of(menu1),
                    new PaginationMetadata(1, 1, 20, 1));

            when(queryEngine.executeQuery(eq(menuDef), any(QueryRequest.class)))
                    .thenReturn(menuResult);

            MvcResult result = mockMvc.perform(get("/api/collections/ui-menus")
                            .header("X-Tenant-ID", "tenant-1"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            assertNull(response.get("included"), "Should not have 'included' without include param");
        }

        @Test
        @DisplayName("Should use IN filter with all parent IDs for child query")
        void list_withInclude_usesInFilter() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            CollectionDefinition menuItemDef = buildMenuItemCollection();

            when(registry.get("ui-menus")).thenReturn(menuDef);
            when(registry.get("ui-menu-items")).thenReturn(menuItemDef);

            Map<String, Object> menu1 = menuRecord("menu-1", "Main Menu");
            Map<String, Object> menu2 = menuRecord("menu-2", "Settings Menu");
            QueryResult menuResult = new QueryResult(
                    List.of(menu1, menu2),
                    new PaginationMetadata(2, 1, 20, 1));

            QueryResult emptyItemResult = new QueryResult(
                    List.of(),
                    new PaginationMetadata(0, 1, 1000, 0));

            when(queryEngine.executeQuery(eq(menuDef), any(QueryRequest.class)))
                    .thenReturn(menuResult);
            when(queryEngine.executeQuery(eq(menuItemDef), any(QueryRequest.class)))
                    .thenReturn(emptyItemResult);

            mockMvc.perform(get("/api/collections/ui-menus")
                            .param("include", "ui-menu-items")
                            .header("X-Tenant-ID", "tenant-1"))
                    .andExpect(status().isOk());

            // Capture the child query
            ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(queryEngine).executeQuery(eq(menuItemDef), queryCaptor.capture());

            QueryRequest childQuery = queryCaptor.getValue();
            assertFalse(childQuery.filters().isEmpty(), "Child query should have filters");
            assertEquals("menuId", childQuery.filters().get(0).fieldName());
            assertEquals(com.emf.runtime.query.FilterOperator.IN, childQuery.filters().get(0).operator());

            Object filterValue = childQuery.filters().get(0).value();
            assertTrue(filterValue instanceof List, "IN filter value should be a List");
            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) filterValue;
            assertEquals(2, ids.size());
            assertTrue(ids.contains("menu-1"));
            assertTrue(ids.contains("menu-2"));
        }
    }

    @Nested
    @DisplayName("Get single with include parameter")
    class GetWithInclude {

        @Test
        @DisplayName("Should include child records for single resource")
        @SuppressWarnings("unchecked")
        void get_withInclude_returnsIncludedResources() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            CollectionDefinition menuItemDef = buildMenuItemCollection();

            when(registry.get("ui-menus")).thenReturn(menuDef);
            when(registry.get("ui-menu-items")).thenReturn(menuItemDef);

            Map<String, Object> menu1 = menuRecord("menu-1", "Main Menu");
            when(queryEngine.getById(menuDef, "menu-1")).thenReturn(Optional.of(menu1));

            Map<String, Object> item1 = menuItemRecord("item-1", "menu-1", "Home", "/home");
            Map<String, Object> item2 = menuItemRecord("item-2", "menu-1", "Dashboard", "/dashboard");
            QueryResult itemResult = new QueryResult(
                    List.of(item1, item2),
                    new PaginationMetadata(2, 1, 1000, 1));

            when(queryEngine.executeQuery(eq(menuItemDef), any(QueryRequest.class)))
                    .thenReturn(itemResult);

            MvcResult result = mockMvc.perform(get("/api/collections/ui-menus/menu-1")
                            .param("include", "ui-menu-items"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            // Verify data is present
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertNotNull(data);
            assertEquals("menu-1", data.get("id"));

            // Verify included is present with 2 items
            List<Map<String, Object>> included = (List<Map<String, Object>>) response.get("included");
            assertNotNull(included, "Response should have 'included' array");
            assertEquals(2, included.size());
        }

        @Test
        @DisplayName("Should return 404 when record not found even with include")
        void get_notFound_withInclude_returns404() throws Exception {
            CollectionDefinition menuDef = buildMenuCollection();
            when(registry.get("ui-menus")).thenReturn(menuDef);

            when(queryEngine.getById(menuDef, "nonexistent")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/collections/ui-menus/nonexistent")
                            .param("include", "ui-menu-items"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // Transitive (grandchild) includes
    // =========================================================================

    /**
     * Builds a page-layouts collection (grandparent in layout hierarchy).
     */
    private CollectionDefinition buildPageLayoutsCollection() {
        return new CollectionDefinitionBuilder()
                .name("page-layouts")
                .displayName("Page Layouts")
                .addField(FieldDefinition.requiredString("name", 100))
                .addField(FieldDefinition.string("layoutType", 20))
                .systemCollection(true)
                .tenantScoped(false)
                .readOnly(false)
                .build();
    }

    /**
     * Builds a layout-sections collection (direct child of page-layouts).
     */
    private CollectionDefinition buildLayoutSectionsCollection() {
        return new CollectionDefinitionBuilder()
                .name("layout-sections")
                .displayName("Layout Sections")
                .addField(new FieldDefinition("layoutId", FieldType.MASTER_DETAIL, false, false, false,
                        null, null, null,
                        ReferenceConfig.masterDetail("page-layouts", "Layout"), null))
                .addField(FieldDefinition.string("heading", 200))
                .addField(FieldDefinition.integer("sortOrder"))
                .systemCollection(true)
                .tenantScoped(false)
                .readOnly(false)
                .build();
    }

    /**
     * Builds a layout-fields collection (grandchild: references layout-sections, not page-layouts).
     */
    private CollectionDefinition buildLayoutFieldsCollection() {
        return new CollectionDefinitionBuilder()
                .name("layout-fields")
                .displayName("Layout Fields")
                .addField(new FieldDefinition("sectionId", FieldType.MASTER_DETAIL, false, false, false,
                        null, null, null,
                        ReferenceConfig.masterDetail("layout-sections", "Section"), null))
                .addField(FieldDefinition.string("fieldId", 36))
                .addField(FieldDefinition.integer("sortOrder"))
                .systemCollection(true)
                .tenantScoped(false)
                .readOnly(false)
                .build();
    }

    private Map<String, Object> layoutRecord(String id, String name, String type) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("name", name);
        record.put("layoutType", type);
        return record;
    }

    private Map<String, Object> sectionRecord(String id, String layoutId, String heading, int order) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("layoutId", layoutId);
        record.put("heading", heading);
        record.put("sortOrder", order);
        return record;
    }

    private Map<String, Object> fieldPlacementRecord(String id, String sectionId,
                                                      String fieldId, int order) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("sectionId", sectionId);
        record.put("fieldId", fieldId);
        record.put("sortOrder", order);
        return record;
    }

    @Nested
    @DisplayName("Transitive (grandchild) includes")
    class TransitiveIncludes {

        @Test
        @DisplayName("Should resolve grandchild include transitively via direct child")
        @SuppressWarnings("unchecked")
        void get_withTransitiveInclude_resolvesGrandchildren() throws Exception {
            CollectionDefinition layoutDef = buildPageLayoutsCollection();
            CollectionDefinition sectionDef = buildLayoutSectionsCollection();
            CollectionDefinition fieldDef = buildLayoutFieldsCollection();

            when(registry.get("page-layouts")).thenReturn(layoutDef);
            when(registry.get("layout-sections")).thenReturn(sectionDef);
            when(registry.get("layout-fields")).thenReturn(fieldDef);

            // Primary record: one page layout
            Map<String, Object> layout = layoutRecord("layout-1", "Detail Layout", "DETAIL");
            when(queryEngine.getById(layoutDef, "layout-1")).thenReturn(Optional.of(layout));

            // Direct child: two sections
            Map<String, Object> sec1 = sectionRecord("sec-1", "layout-1", "General", 1);
            Map<String, Object> sec2 = sectionRecord("sec-2", "layout-1", "Details", 2);
            QueryResult sectionResult = new QueryResult(
                    List.of(sec1, sec2),
                    new PaginationMetadata(2, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(sectionDef), any(QueryRequest.class)))
                    .thenReturn(sectionResult);

            // Grandchild: three field placements across the two sections
            Map<String, Object> fp1 = fieldPlacementRecord("fp-1", "sec-1", "field-a", 1);
            Map<String, Object> fp2 = fieldPlacementRecord("fp-2", "sec-1", "field-b", 2);
            Map<String, Object> fp3 = fieldPlacementRecord("fp-3", "sec-2", "field-c", 1);
            QueryResult fieldResult = new QueryResult(
                    List.of(fp1, fp2, fp3),
                    new PaginationMetadata(3, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(fieldDef), any(QueryRequest.class)))
                    .thenReturn(fieldResult);

            MvcResult result = mockMvc.perform(
                            get("/api/collections/page-layouts/layout-1")
                                    .param("include", "layout-sections,layout-fields"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            // Verify primary data
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertNotNull(data);
            assertEquals("layout-1", data.get("id"));

            // Verify included contains both sections and field placements
            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included, "Response should have 'included' array");
            assertEquals(5, included.size(), "Should include 2 sections + 3 field placements");

            long sectionCount = included.stream()
                    .filter(inc -> "layout-sections".equals(inc.get("type")))
                    .count();
            long fieldCount = included.stream()
                    .filter(inc -> "layout-fields".equals(inc.get("type")))
                    .count();
            assertEquals(2, sectionCount, "Should include 2 layout sections");
            assertEquals(3, fieldCount, "Should include 3 layout fields");
        }

        @Test
        @DisplayName("Should use section IDs (not layout IDs) for grandchild query")
        void get_withTransitiveInclude_usesIntermediateIds() throws Exception {
            CollectionDefinition layoutDef = buildPageLayoutsCollection();
            CollectionDefinition sectionDef = buildLayoutSectionsCollection();
            CollectionDefinition fieldDef = buildLayoutFieldsCollection();

            when(registry.get("page-layouts")).thenReturn(layoutDef);
            when(registry.get("layout-sections")).thenReturn(sectionDef);
            when(registry.get("layout-fields")).thenReturn(fieldDef);

            Map<String, Object> layout = layoutRecord("layout-1", "Detail Layout", "DETAIL");
            when(queryEngine.getById(layoutDef, "layout-1")).thenReturn(Optional.of(layout));

            // Direct child: two sections with specific IDs
            Map<String, Object> sec1 = sectionRecord("sec-100", "layout-1", "General", 1);
            Map<String, Object> sec2 = sectionRecord("sec-200", "layout-1", "Details", 2);
            QueryResult sectionResult = new QueryResult(
                    List.of(sec1, sec2),
                    new PaginationMetadata(2, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(sectionDef), any(QueryRequest.class)))
                    .thenReturn(sectionResult);

            // Grandchild: empty result (we just need to verify the query)
            QueryResult emptyFieldResult = new QueryResult(
                    List.of(),
                    new PaginationMetadata(0, 1, 1000, 0));
            when(queryEngine.executeQuery(eq(fieldDef), any(QueryRequest.class)))
                    .thenReturn(emptyFieldResult);

            mockMvc.perform(get("/api/collections/page-layouts/layout-1")
                            .param("include", "layout-sections,layout-fields"))
                    .andExpect(status().isOk());

            // Capture the grandchild query and verify it uses section IDs
            ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(queryEngine).executeQuery(eq(fieldDef), queryCaptor.capture());

            QueryRequest childQuery = queryCaptor.getValue();
            assertFalse(childQuery.filters().isEmpty(), "Grandchild query should have filters");
            assertEquals("sectionId", childQuery.filters().get(0).fieldName(),
                    "Should filter on sectionId, not layoutId");
            assertEquals(com.emf.runtime.query.FilterOperator.IN,
                    childQuery.filters().get(0).operator());

            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) childQuery.filters().get(0).value();
            assertEquals(2, ids.size());
            assertTrue(ids.contains("sec-100"), "Should use section ID sec-100");
            assertTrue(ids.contains("sec-200"), "Should use section ID sec-200");
        }

        @Test
        @DisplayName("Should handle transitive include with no intermediate records")
        @SuppressWarnings("unchecked")
        void get_withTransitiveInclude_noIntermediateRecords() throws Exception {
            CollectionDefinition layoutDef = buildPageLayoutsCollection();
            CollectionDefinition sectionDef = buildLayoutSectionsCollection();
            CollectionDefinition fieldDef = buildLayoutFieldsCollection();

            when(registry.get("page-layouts")).thenReturn(layoutDef);
            when(registry.get("layout-sections")).thenReturn(sectionDef);
            when(registry.get("layout-fields")).thenReturn(fieldDef);

            Map<String, Object> layout = layoutRecord("layout-1", "Empty Layout", "DETAIL");
            when(queryEngine.getById(layoutDef, "layout-1")).thenReturn(Optional.of(layout));

            // Direct child: no sections
            QueryResult emptySectionResult = new QueryResult(
                    List.of(),
                    new PaginationMetadata(0, 1, 1000, 0));
            when(queryEngine.executeQuery(eq(sectionDef), any(QueryRequest.class)))
                    .thenReturn(emptySectionResult);

            MvcResult result = mockMvc.perform(
                            get("/api/collections/page-layouts/layout-1")
                                    .param("include", "layout-sections,layout-fields"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            // Should not query for grandchild records when there are no intermediate records
            verify(queryEngine, never()).executeQuery(eq(fieldDef), any(QueryRequest.class));

            // included should be absent or empty (no sections and no fields)
            Object included = response.get("included");
            assertTrue(included == null || ((List<?>) included).isEmpty(),
                    "Should have no included resources when no sections exist");
        }

        @Test
        @DisplayName("Should work with list endpoint and transitive includes")
        @SuppressWarnings("unchecked")
        void list_withTransitiveInclude_resolvesGrandchildren() throws Exception {
            CollectionDefinition layoutDef = buildPageLayoutsCollection();
            CollectionDefinition sectionDef = buildLayoutSectionsCollection();
            CollectionDefinition fieldDef = buildLayoutFieldsCollection();

            when(registry.get("page-layouts")).thenReturn(layoutDef);
            when(registry.get("layout-sections")).thenReturn(sectionDef);
            when(registry.get("layout-fields")).thenReturn(fieldDef);

            // Two primary layouts
            Map<String, Object> layout1 = layoutRecord("layout-1", "Detail Layout", "DETAIL");
            Map<String, Object> layout2 = layoutRecord("layout-2", "Edit Layout", "EDIT");
            QueryResult layoutResult = new QueryResult(
                    List.of(layout1, layout2),
                    new PaginationMetadata(2, 1, 20, 1));
            when(queryEngine.executeQuery(eq(layoutDef), any(QueryRequest.class)))
                    .thenReturn(layoutResult);

            // Sections for both layouts
            Map<String, Object> sec1 = sectionRecord("sec-1", "layout-1", "General", 1);
            Map<String, Object> sec2 = sectionRecord("sec-2", "layout-2", "Main", 1);
            QueryResult sectionResult = new QueryResult(
                    List.of(sec1, sec2),
                    new PaginationMetadata(2, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(sectionDef), any(QueryRequest.class)))
                    .thenReturn(sectionResult);

            // Fields across sections
            Map<String, Object> fp1 = fieldPlacementRecord("fp-1", "sec-1", "field-a", 1);
            Map<String, Object> fp2 = fieldPlacementRecord("fp-2", "sec-2", "field-b", 1);
            QueryResult fieldResult = new QueryResult(
                    List.of(fp1, fp2),
                    new PaginationMetadata(2, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(fieldDef), any(QueryRequest.class)))
                    .thenReturn(fieldResult);

            MvcResult result = mockMvc.perform(
                            get("/api/collections/page-layouts")
                                    .param("include", "layout-sections,layout-fields"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            assertEquals(2, data.size());

            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included, "Response should have 'included' array");
            assertEquals(4, included.size(), "Should include 2 sections + 2 field placements");
        }

        @Test
        @DisplayName("Should skip unresolvable includes gracefully")
        @SuppressWarnings("unchecked")
        void get_withUnresolvableTransitiveInclude_skipsGracefully() throws Exception {
            CollectionDefinition layoutDef = buildPageLayoutsCollection();
            CollectionDefinition fieldDef = buildLayoutFieldsCollection();

            when(registry.get("page-layouts")).thenReturn(layoutDef);
            // Note: layout-sections is NOT registered
            when(registry.get("layout-sections")).thenReturn(null);
            when(registry.get("layout-fields")).thenReturn(fieldDef);

            Map<String, Object> layout = layoutRecord("layout-1", "Detail Layout", "DETAIL");
            when(queryEngine.getById(layoutDef, "layout-1")).thenReturn(Optional.of(layout));

            MvcResult result = mockMvc.perform(
                            get("/api/collections/page-layouts/layout-1")
                                    .param("include", "layout-sections,layout-fields"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            // layout-fields has no direct or transitive path without layout-sections
            // Should still succeed without errors
            assertNotNull(response.get("data"));
            // No grandchild queries should be made
            verify(queryEngine, never()).executeQuery(eq(fieldDef), any(QueryRequest.class));
        }
    }
}
