package io.kelta.testharness;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that boots the full Kelta mini-stack once per test JVM run.
 *
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(KeltaStackExtension.class)
 * class MyScenarioTest { ... }
 * }</pre>
 *
 * <p>The stack starts on first use and stops when the JVM exits (via shutdown hook).
 * All scenario tests share the same running stack to avoid redundant startup time.
 */
public class KeltaStackExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static volatile boolean started = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!started) {
            synchronized (KeltaStackExtension.class) {
                if (!started) {
                    KeltaStack.start();
                    // Register shutdown hook to stop containers when JVM exits
                    Runtime.getRuntime().addShutdownHook(new Thread(KeltaStack::stop, "kelta-stack-shutdown"));
                    started = true;
                }
            }
        }
        // Store this instance so JUnit can call close() — we use a no-op here since
        // teardown is handled by the shutdown hook (shared singleton pattern)
        context.getStore(ExtensionContext.Namespace.GLOBAL)
               .put(KeltaStackExtension.class.getName(), this);
    }

    @Override
    public void close() {
        // Intentional no-op: containers are shared across all scenario tests
        // and torn down by the JVM shutdown hook instead.
    }
}
