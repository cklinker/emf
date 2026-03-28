package io.kelta.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class TokenUsageRepository {

    private final JdbcTemplate jdbc;

    public TokenUsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void incrementUsage(long tenantId, String yearMonth, int inputTokens, int outputTokens) {
        jdbc.update("""
                INSERT INTO ai_token_usage (id, tenant_id, year_month, input_tokens, output_tokens, request_count, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, 1, NOW())
                ON CONFLICT (tenant_id, year_month) DO UPDATE SET
                    input_tokens = ai_token_usage.input_tokens + EXCLUDED.input_tokens,
                    output_tokens = ai_token_usage.output_tokens + EXCLUDED.output_tokens,
                    request_count = ai_token_usage.request_count + 1,
                    updated_at = NOW()
                """, tenantId, yearMonth, inputTokens, outputTokens);
    }

    public long getTotalTokens(long tenantId, String yearMonth) {
        var result = jdbc.queryForObject(
                "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM ai_token_usage WHERE tenant_id = ? AND year_month = ?",
                Long.class, tenantId, yearMonth);
        return result != null ? result : 0L;
    }

    public Map<String, Map<String, Long>> getUsageHistory(long tenantId, int months) {
        String startMonth = YearMonth.now().minusMonths(months - 1).toString();
        Map<String, Map<String, Long>> history = new LinkedHashMap<>();
        jdbc.query("""
                SELECT year_month, input_tokens, output_tokens, request_count
                FROM ai_token_usage WHERE tenant_id = ? AND year_month >= ? ORDER BY year_month ASC
                """, (rs, rowNum) -> {
            Map<String, Long> entry = new LinkedHashMap<>();
            entry.put("inputTokens", rs.getLong("input_tokens"));
            entry.put("outputTokens", rs.getLong("output_tokens"));
            entry.put("requestCount", rs.getLong("request_count"));
            history.put(rs.getString("year_month"), entry);
            return null;
        }, tenantId, startMonth);
        return history;
    }
}
