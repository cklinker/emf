package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UIPageSlugHook")
class UIPageSlugHookTest {

    private static final String TENANT = "t1";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private UIPageSlugHook hook;

    @BeforeEach
    void setUp() {
        hook = new UIPageSlugHook(jdbcTemplate);
    }

    private void slugFree() {
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(0);
    }

    private static Map<String, Object> record(String name, String path, String slug) {
        Map<String, Object> r = new HashMap<>();
        if (name != null) r.put("name", name);
        if (path != null) r.put("path", path);
        if (slug != null) r.put("slug", slug);
        return r;
    }

    @Test
    @DisplayName("targets the ui-pages collection")
    void targetsUiPages() {
        assertThat(hook.getCollectionName()).isEqualTo("ui-pages");
    }

    @Test
    @DisplayName("derives a slug from name when none is supplied")
    void derivesFromName() {
        slugFree();
        BeforeSaveResult result = hook.beforeCreate(record("Dashboard", "/home", null), TENANT);
        assertThat(result.getFieldUpdates()).containsEntry("slug", "dashboard");
    }

    @Test
    @DisplayName("slugifies spaces and punctuation")
    void slugifies() {
        assertThat(UIPageSlugHook.slugify("My Page! 2")).isEqualTo("my-page-2");
        assertThat(UIPageSlugHook.slugify("  Trailing--/  ")).isEqualTo("trailing");
        assertThat(UIPageSlugHook.slugify("!!!")).isEqualTo("page");
        assertThat(UIPageSlugHook.slugify(null)).isEqualTo("page");
    }

    @Test
    @DisplayName("falls back to path when name is blank")
    void fallsBackToPath() {
        slugFree();
        BeforeSaveResult result = hook.beforeCreate(record("", "/home/main", null), TENANT);
        assertThat(result.getFieldUpdates()).containsEntry("slug", "home-main");
    }

    @Test
    @DisplayName("appends a numeric suffix when the slug is already taken")
    void disambiguatesCollision() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT), eq("dashboard")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT), eq("dashboard-2")))
                .thenReturn(0);

        BeforeSaveResult result = hook.beforeCreate(record("Dashboard", "/home", null), TENANT);

        assertThat(result.getFieldUpdates()).containsEntry("slug", "dashboard-2");
    }

    @Test
    @DisplayName("leaves an explicitly-supplied slug untouched (no DB lookup)")
    void keepsSuppliedSlug() {
        BeforeSaveResult result = hook.beforeCreate(record("Dashboard", "/home", "custom-slug"), TENANT);
        assertThat(result.hasFieldUpdates()).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("update only fills a slug that was explicitly blanked")
    void updateFillsBlankSlug() {
        // slug absent from the update → untouched
        assertThat(hook.beforeUpdate("p1", record("Dashboard", "/home", null), Map.of(), TENANT)
                .hasFieldUpdates()).isFalse();

        // slug present but blank → derived
        slugFree();
        BeforeSaveResult blanked = hook.beforeUpdate("p1", record("Dashboard", "/home", "  "), Map.of(), TENANT);
        assertThat(blanked.getFieldUpdates()).containsEntry("slug", "dashboard");
    }
}
