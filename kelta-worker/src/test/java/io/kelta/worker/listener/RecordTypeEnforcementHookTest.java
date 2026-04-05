package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RecordTypeEnforcementHook")
class RecordTypeEnforcementHookTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private RecordTypeEnforcementHook hook;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = JsonMapper.builder().build();
        hook = new RecordTypeEnforcementHook(jdbcTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should use wildcard collection name")
    void shouldUseWildcard() {
        assertThat(hook.getCollectionName()).isEqualTo("*");
    }

    @Test
    @DisplayName("Should have order 100")
    void shouldHaveOrder100() {
        assertThat(hook.getOrder()).isEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // beforeCreate tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should skip when no recordTypeId on create")
    void shouldSkipCreateWhenNoRecordTypeId() {
        var record = new HashMap<String, Object>();
        record.put("name", "Test Account");

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Should skip system collections on create")
    void shouldSkipSystemCollectionsOnCreate() {
        var record = new HashMap<String, Object>();
        record.put("__collectionName", "collections");
        record.put("recordTypeId", "rt-1");

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Should reject invalid record type on create")
    void shouldRejectInvalidRecordTypeOnCreate() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-invalid");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().field()).isEqualTo("recordTypeId");
        assertThat(result.getErrors().getFirst().message()).contains("Invalid or inactive");
    }

    @Test
    @DisplayName("Should allow create with valid record type and no restrictions")
    void shouldAllowCreateWithValidTypeNoRestrictions() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-1");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of());

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should reject disallowed picklist value on create")
    void shouldRejectDisallowedPicklistValueOnCreate() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-1");
        record.put("status", "Closed");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        var restriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "In Progress"), null);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(restriction));

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().field()).isEqualTo("status");
        assertThat(result.getErrors().getFirst().message()).contains("Closed");
        assertThat(result.getErrors().getFirst().message()).contains("not allowed");
    }

    @Test
    @DisplayName("Should allow valid picklist value on create")
    void shouldAllowValidPicklistValueOnCreate() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-1");
        record.put("status", "Open");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        var restriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "In Progress"), null);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(restriction));

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should apply default value on create when field is empty")
    void shouldApplyDefaultValueOnCreate() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-1");
        // status not provided

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        var restriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "In Progress"), "Open");
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(restriction));

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isTrue();
        assertThat(result.getFieldUpdates()).containsEntry("status", "Open");
    }

    // -------------------------------------------------------------------------
    // beforeUpdate tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should skip when no recordTypeId on update")
    void shouldSkipUpdateWhenNoRecordTypeId() {
        var record = new HashMap<String, Object>();
        record.put("name", "Updated Name");
        var previous = new HashMap<String, Object>();

        BeforeSaveResult result = hook.beforeUpdate("rec-1", record, previous, "t1");

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Should use previous recordTypeId when not in update data")
    void shouldUsePreviousRecordTypeIdOnUpdate() {
        var record = new HashMap<String, Object>();
        record.put("status", "Open");

        var previous = new HashMap<String, Object>();
        previous.put("recordTypeId", "rt-1");

        // No restrictions for the record type
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of());

        BeforeSaveResult result = hook.beforeUpdate("rec-1", record, previous, "t1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should only validate fields being updated")
    void shouldOnlyValidateUpdatedFields() {
        var record = new HashMap<String, Object>();
        record.put("name", "Updated Name");
        // Not updating 'status'

        var previous = new HashMap<String, Object>();
        previous.put("recordTypeId", "rt-1");

        var restriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "In Progress"), null);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(restriction));

        BeforeSaveResult result = hook.beforeUpdate("rec-1", record, previous, "t1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should reject disallowed picklist value on update")
    void shouldRejectDisallowedPicklistValueOnUpdate() {
        var record = new HashMap<String, Object>();
        record.put("status", "Closed");

        var previous = new HashMap<String, Object>();
        previous.put("recordTypeId", "rt-1");

        var restriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "In Progress"), null);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(restriction));

        BeforeSaveResult result = hook.beforeUpdate("rec-1", record, previous, "t1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().field()).isEqualTo("status");
    }

    @Test
    @DisplayName("Should reject changing to inactive record type on update")
    void shouldRejectInactiveRecordTypeOnUpdate() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-inactive");

        var previous = new HashMap<String, Object>();
        previous.put("recordTypeId", "rt-1");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        BeforeSaveResult result = hook.beforeUpdate("rec-1", record, previous, "t1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().getFirst().field()).isEqualTo("recordTypeId");
    }

    @Test
    @DisplayName("Should validate multiple picklist fields and collect all errors")
    void shouldValidateMultiplePicklistFields() {
        var record = new HashMap<String, Object>();
        record.put("recordTypeId", "rt-1");
        record.put("status", "Invalid");
        record.put("priority", "Wrong");

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        var statusRestriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "status", java.util.Set.of("Open", "Closed"), null);
        var priorityRestriction = new RecordTypeEnforcementHook.PicklistRestriction(
                "priority", java.util.Set.of("High", "Medium", "Low"), null);
        when(jdbcTemplate.query(contains("record_type_picklist"), any(RowMapper.class), eq("rt-1")))
                .thenReturn(List.of(statusRestriction, priorityRestriction));

        BeforeSaveResult result = hook.beforeCreate(record, "t1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
    }
}
