package io.kelta.worker.util;

import io.kelta.runtime.context.TenantContext;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helpers for running background or scheduled work inside a bounded {@link TenantContext}
 * scope.
 *
 * <p>All variants bind the tenant via {@link ScopedValue} (virtual-thread safe,
 * automatically cleared on exit). Callers may still use these wrappers as a
 * convenient bridge from code that receives a tenant ID as a parameter rather than
 * inheriting one from the caller's scope — e.g. scheduled jobs loaded from the DB,
 * NATS listeners, and async workers.
 */
public final class TenantContextUtils {

    private TenantContextUtils() {}

    /**
     * Executes {@code task} with the given tenant bound in {@link TenantContext}.
     */
    public static void withTenant(String tenantId, ThrowingRunnable task) throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                task.run();
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /**
     * Callable variant of {@link #withTenant(String, ThrowingRunnable)}.
     */
    public static <T> T withTenant(String tenantId, Callable<T> task) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /** A {@link Runnable}-like interface that allows checked exceptions. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
