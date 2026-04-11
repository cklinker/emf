package io.kelta.testharness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

/**
 * Singleton stack of Testcontainers that backs all harness scenario tests.
 *
 * <p>Container startup order:
 * <ol>
 *   <li>Infrastructure: Postgres, Redis, NATS, Cerbos (in parallel)
 *   <li>kelta-worker (Flyway runs migrations + seeds)
 *   <li>kelta-auth (needs DB + worker for user lookups)
 *   <li>kelta-gateway (needs auth for JWKS + worker for route bootstrap)
 * </ol>
 *
 * <p>All containers share a single Docker network. Service containers are built
 * from the pre-built fat JARs in each service's {@code target/} directory —
 * run {@code mvn install -DskipTests} for all services before running these tests.
 *
 * <p>A fresh RSA-2048 JWK and AES-256 key are generated per test JVM run so
 * tests are fully self-contained.
 */
public final class KeltaStack {

    private static final Logger log = LoggerFactory.getLogger(KeltaStack.class);

    private static final String SERVICE_VERSION = "1.0.0-SNAPSHOT";
    private static final String ENCRYPTION_KEY;
    private static final String JWK_SET;
    private static final String INTERNAL_TOKEN = "harness-internal-token";

    static {
        try {
            ENCRYPTION_KEY = generateEncryptionKey();
            JWK_SET       = generateJwkSet();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ── Docker network shared by all containers ──────────────────────────────

    public static final Network NETWORK = Network.newNetwork();

    // ── Infrastructure containers ────────────────────────────────────────────

    @SuppressWarnings("resource")
    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withNetworkAliases("postgres")
                    .withNetwork(NETWORK)
                    .withDatabaseName("kelta_control_plane")
                    .withUsername("kelta")
                    .withPassword("kelta");

    @SuppressWarnings({"resource", "unchecked"})
    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withNetworkAliases("redis")
                    .withNetwork(NETWORK)
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @SuppressWarnings({"resource", "unchecked"})
    public static final GenericContainer<?> NATS =
            new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
                    .withNetworkAliases("nats")
                    .withNetwork(NETWORK)
                    .withCommand("--jetstream", "--store_dir", "/data/jetstream")
                    .withExposedPorts(4222)
                    .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));

    @SuppressWarnings({"resource", "unchecked"})
    public static final GenericContainer<?> CERBOS =
            new GenericContainer<>(DockerImageName.parse("ghcr.io/cerbos/cerbos:0.40.0"))
                    .withNetworkAliases("cerbos")
                    .withNetwork(NETWORK)
                    .withCommand("server", "--config=/config/config.yaml")
                    .withFileSystemBind(cerbosConfigPath(), "/config", org.testcontainers.containers.BindMode.READ_ONLY)
                    .withFileSystemBind(cerbosPoliciesPath(), "/policies", org.testcontainers.containers.BindMode.READ_ONLY)
                    .withExposedPorts(3592, 3593)
                    .waitingFor(Wait.forHttp("/_cerbos/health").forPort(3592).withStartupTimeout(Duration.ofSeconds(60)));

    // ── Service containers (built from pre-built JARs) ───────────────────────

    public static final GenericContainer<?> WORKER = serviceContainer("kelta-worker")
            .withNetworkAliases("kelta-worker")
            .withNetwork(NETWORK)
            .withEnv("SPRING_DATASOURCE_URL",     "jdbc:postgresql://postgres:5432/kelta_control_plane")
            .withEnv("SPRING_DATASOURCE_USERNAME", "kelta")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "kelta")
            .withEnv("SPRING_DATA_REDIS_HOST",     "redis")
            .withEnv("SPRING_DATA_REDIS_PORT",     "6379")
            .withEnv("NATS_URL",                   "nats://nats:4222")
            .withEnv("KELTA_AUTH_ISSUER_URI",      "http://kelta-auth:8080")
            .withEnv("EXTERNAL_BASE_URL",          "http://kelta-gateway:8080")
            .withEnv("CERBOS_HOST",                "cerbos")
            .withEnv("CERBOS_GRPC_PORT",           "3593")
            .withEnv("KELTA_ENCRYPTION_KEY",       ENCRYPTION_KEY)
            .withEnv("KELTA_INTERNAL_TOKEN",       INTERNAL_TOKEN)
            .withEnv("EMAIL_ENABLED",              "false")
            .withEnv("SMTP_AUTH",                  "false")
            .withEnv("SMTP_STARTTLS",              "false")
            .withEnv("SCHEDULER_ENABLED",          "false")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").withStartupTimeout(Duration.ofMinutes(3)));

    public static final GenericContainer<?> AUTH = serviceContainer("kelta-auth")
            .withNetworkAliases("kelta-auth")
            .withNetwork(NETWORK)
            .withEnv("DB_URL",                    "jdbc:postgresql://postgres:5432/kelta_control_plane")
            .withEnv("DB_USERNAME",               "kelta")
            .withEnv("DB_PASSWORD",               "kelta")
            .withEnv("SPRING_DATA_REDIS_HOST",    "redis")
            .withEnv("SPRING_DATA_REDIS_PORT",    "6379")
            .withEnv("KELTA_AUTH_ISSUER_URI",     "http://kelta-auth:8080")
            .withEnv("WORKER_SERVICE_URL",        "http://kelta-worker:8080")
            .withEnv("KELTA_ENCRYPTION_KEY",      ENCRYPTION_KEY)
            .withEnv("JWK_SET",                   JWK_SET)
            .withEnv("KELTA_INTERNAL_TOKEN",      INTERNAL_TOKEN)
            .withEnv("COOKIE_DOMAIN",             "localhost")
            .withEnv("UI_BASE_URL",               "http://localhost:5173")
            .withEnv("CORS_ALLOWED_ORIGINS",      "http://localhost:5173")
            .withEnv("DIRECT_LOGIN_ENABLED",      "true")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").withStartupTimeout(Duration.ofMinutes(2)));

    public static final GenericContainer<?> GATEWAY = serviceContainer("kelta-gateway")
            .withNetworkAliases("kelta-gateway")
            .withNetwork(NETWORK)
            .withEnv("REDIS_HOST",              "redis")
            .withEnv("REDIS_PORT",              "6379")
            .withEnv("NATS_URL",               "nats://nats:4222")
            .withEnv("KELTA_AUTH_ISSUER_URI",   "http://kelta-auth:8080")
            .withEnv("WORKER_SERVICE_URL",      "http://kelta-worker:8080")
            .withEnv("AI_SERVICE_URL",          "http://kelta-ai:8080")
            .withEnv("CERBOS_HOST",             "cerbos")
            .withEnv("CERBOS_GRPC_PORT",        "3593")
            .withEnv("SINGLE_ISSUER",           "true")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").withStartupTimeout(Duration.ofMinutes(2)));

    // ── Accessors ────────────────────────────────────────────────────────────

    public static String workerBaseUrl() {
        return "http://" + WORKER.getHost() + ":" + WORKER.getMappedPort(8080);
    }

    public static String authBaseUrl() {
        return "http://" + AUTH.getHost() + ":" + AUTH.getMappedPort(8080);
    }

    public static String gatewayBaseUrl() {
        return "http://" + GATEWAY.getHost() + ":" + GATEWAY.getMappedPort(8080);
    }

    // ── Startup ──────────────────────────────────────────────────────────────

    /**
     * Start infrastructure and services in the correct order.
     * Called once by {@link KeltaStackExtension}.
     */
    public static void start() {
        log.info("Starting Kelta test stack...");

        // Phase 1: infrastructure in parallel
        org.testcontainers.lifecycle.Startables.deepStart(POSTGRES, REDIS, NATS, CERBOS).join();
        log.info("Infrastructure healthy");

        // Phase 2: worker (owns Flyway migrations)
        WORKER.start();
        log.info("kelta-worker healthy (Flyway complete)");

        // Phase 3: auth (needs DB + worker)
        AUTH.start();
        log.info("kelta-auth healthy");

        // Phase 4: gateway (needs auth JWKS + worker routes)
        GATEWAY.start();
        log.info("kelta-gateway healthy — stack ready");
    }

    public static void stop() {
        GATEWAY.stop();
        AUTH.stop();
        WORKER.stop();
        CERBOS.stop();
        NATS.stop();
        REDIS.stop();
        POSTGRES.stop();
        NETWORK.close();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Builds a lightweight JRE container that runs the pre-built fat JAR
     * for the given service. The JAR must already exist at
     * {@code ../<service>/target/<service>-<version>.jar}.
     */
    @SuppressWarnings("unchecked")
    private static GenericContainer<?> serviceContainer(String service) {
        Path jarPath = resolveJar(service);
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException(
                    "Service JAR not found: " + jarPath +
                    " — run 'mvn install -DskipTests' for " + service + " first");
        }

        return new GenericContainer<>(
                new ImageFromDockerfile(service + "-harness", false)
                        .withFileFromPath("app.jar", jarPath)
                        .withDockerfileFromBuilder(b -> b
                                .from("eclipse-temurin:25-jre-alpine")
                                .copy("app.jar", "/app.jar")
                                // Cap heap and metaspace so three concurrent service JVMs don't
                                // exceed the runner pod's memory limit. Metaspace is unbounded by
                                // default and can add 200-400 MB per JVM for a complex Spring Boot app.
                                .entryPoint("java",
                                        "-Xmx512m",
                                        "-XX:MaxMetaspaceSize=256m",
                                        "-jar", "/app.jar")
                        )
        );
    }

    private static Path resolveJar(String service) {
        // harness.basedir is the kelta-test-harness/ directory (injected via Maven filtering)
        String basedir = harnessBasedir();
        return Path.of(basedir)
                   .getParent()
                   .resolve(service)
                   .resolve("target")
                   .resolve(service + "-" + SERVICE_VERSION + ".jar");
    }

    private static String harnessBasedir() {
        try (var in = KeltaStack.class.getResourceAsStream("/harness.properties")) {
            if (in == null) throw new IllegalStateException("harness.properties not found on classpath");
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("harness.basedir");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load harness.properties", e);
        }
    }

    private static String cerbosConfigPath() {
        return Path.of(harnessBasedir()).getParent()
                   .resolve("docker/cerbos").toAbsolutePath().toString();
    }

    private static String cerbosPoliciesPath() {
        return Path.of(harnessBasedir()).getParent()
                   .resolve("docker/cerbos/policies").toAbsolutePath().toString();
    }

    private static String generateEncryptionKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String generateJwkSet() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair pair = gen.generateKeyPair();

        RSAPublicKey pub   = (RSAPublicKey) pair.getPublic();
        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) pair.getPrivate();

        String n  = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        String e  = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());
        String d  = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getPrivateExponent().toByteArray());
        String p  = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getPrimeP().toByteArray());
        String q  = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getPrimeQ().toByteArray());
        String dp = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getPrimeExponentP().toByteArray());
        String dq = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getPrimeExponentQ().toByteArray());
        String qi = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getCrtCoefficient().toByteArray());

        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"harness-key\",\"use\":\"sig\",\"alg\":\"RS256\"" +
               ",\"n\":\"" + n + "\",\"e\":\"" + e + "\",\"d\":\"" + d +
               "\",\"p\":\"" + p + "\",\"q\":\"" + q +
               "\",\"dp\":\"" + dp + "\",\"dq\":\"" + dq + "\",\"qi\":\"" + qi + "\"}]}";
    }

    private KeltaStack() {}
}
