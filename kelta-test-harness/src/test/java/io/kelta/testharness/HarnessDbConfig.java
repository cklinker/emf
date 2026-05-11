package io.kelta.testharness;

import java.util.function.Function;

/**
 * Datasource credentials passed to {@code kelta-worker} and {@code kelta-auth}
 * by {@link KeltaStack}.
 *
 * <p>When {@code CI_DB_JDBC_URL} is set we use the shared {@code kelta-ci-db}
 * pool (schema-isolated per CI run via {@code scripts/ci/checkout-db.sh}) and
 * Testcontainers PG must NOT be started. Otherwise we fall back to a
 * Testcontainers PG reachable on the docker network alias {@code postgres}.
 *
 * <p>The external JDBC URL is passed through verbatim — it already carries
 * {@code currentSchema=ci_<run-tag>} so Flyway lands in the isolated schema.
 * If the harness ever creates per-test schemas, the URL's schema must remain
 * the default to honour the outer isolation contract.
 */
record HarnessDbConfig(String jdbcUrl, String username, String password, boolean external) {

    static final String LOCAL_JDBC_URL = "jdbc:postgresql://postgres:5432/kelta_control_plane";
    static final String LOCAL_USERNAME = "kelta";
    static final String LOCAL_PASSWORD = "kelta";

    static HarnessDbConfig resolve(Function<String, String> env) {
        String url = env.apply("CI_DB_JDBC_URL");
        if (url == null || url.isBlank()) {
            return new HarnessDbConfig(LOCAL_JDBC_URL, LOCAL_USERNAME, LOCAL_PASSWORD, false);
        }
        String user = env.apply("CI_DB_USER");
        String pwd  = env.apply("CI_DB_PASSWORD");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("CI_DB_JDBC_URL is set but CI_DB_USER is missing");
        }
        if (pwd == null) {
            throw new IllegalStateException("CI_DB_JDBC_URL is set but CI_DB_PASSWORD is missing");
        }
        return new HarnessDbConfig(url, user, pwd, true);
    }
}
