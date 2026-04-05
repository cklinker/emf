package io.kelta.worker.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentPromotionRepository")
class EnvironmentPromotionRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private EnvironmentPromotionRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new EnvironmentPromotionRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("create should insert promotion and return ID")
    void createShouldInsertAndReturnId() {
        String id = repository.create("t1", "env-1", "env-2", "FULL", null, "admin@test.com");

        assertThat(id).isNotNull().hasSize(36);
        verify(jdbcTemplate).update(contains("INSERT INTO environment_promotion"), any(Object[].class));
    }

    @Test
    @DisplayName("findByIdAndTenant should return promotion with env names")
    void findByIdAndTenantShouldReturn() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "promo-1");
        row.put("status", "PENDING");
        row.put("source_env_name", "Sandbox");
        row.put("target_env_name", "Production");
        when(jdbcTemplate.queryForList(contains("FROM environment_promotion"), eq("promo-1"), eq("t1")))
                .thenReturn(List.of(row));

        var result = repository.findByIdAndTenant("promo-1", "t1");

        assertThat(result).isPresent();
        assertThat(result.get().get("source_env_name")).isEqualTo("Sandbox");
    }

    @Test
    @DisplayName("markStarted should set IN_PROGRESS and started_at")
    void markStartedShouldUpdate() {
        repository.markStarted("promo-1");

        verify(jdbcTemplate).update(contains("IN_PROGRESS"), eq("promo-1"));
    }

    @Test
    @DisplayName("markCompleted should set status and counts")
    void markCompletedShouldUpdate() {
        repository.markCompleted("promo-1", 10, 2, 0, "{\"created\":10}");

        verify(jdbcTemplate).update(contains("UPDATE environment_promotion SET status"),
                eq("COMPLETED"), eq(10), eq(2), eq(0), eq("{\"created\":10}"), eq("promo-1"));
    }

    @Test
    @DisplayName("markCompleted should set FAILED status when items failed")
    void markCompletedShouldSetFailedWhenItemsFailed() {
        repository.markCompleted("promo-1", 5, 0, 3, "{}");

        verify(jdbcTemplate).update(contains("UPDATE environment_promotion SET status"),
                eq("FAILED"), eq(5), eq(0), eq(3), eq("{}"), eq("promo-1"));
    }

    @Test
    @DisplayName("approve should set APPROVED status")
    void approveShouldUpdate() {
        repository.approve("promo-1", "manager@test.com");

        verify(jdbcTemplate).update(contains("APPROVED"), eq("manager@test.com"), eq("promo-1"));
    }

    @Test
    @DisplayName("createItem should insert promotion item")
    void createItemShouldInsert() {
        String id = repository.createItem("promo-1", "COLLECTION", "col-1", "Accounts", "CREATE");

        assertThat(id).isNotNull().hasSize(36);
        verify(jdbcTemplate).update(contains("INSERT INTO promotion_item"), any(Object[].class));
    }

    @Test
    @DisplayName("findItemsByPromotion should return items")
    void findItemsByPromotionShouldReturn() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", "item-1", "item_type", "COLLECTION", "action", "CREATE"),
                Map.of("id", "item-2", "item_type", "FIELD", "action", "CREATE")
        );
        when(jdbcTemplate.queryForList(contains("FROM promotion_item"), eq("promo-1")))
                .thenReturn(rows);

        var result = repository.findItemsByPromotion("promo-1");

        assertThat(result).hasSize(2);
    }
}
