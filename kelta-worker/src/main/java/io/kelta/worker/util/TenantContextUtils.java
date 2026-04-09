package io.kelta.worker.util;

import io.kelta.runtime.context.TenantContext;

import java.util.concurrent.Callable;

/**
 * Helpers for managing {@link TenantContext} lifecycle around background task execution.
 *
 * <p>Use {@link #withTenant} when running scheduled or async work that requires a tenant context
 * to be set for the duration of the operation and reliably cleared afterward.
 */
public final class TenantContextUtils {

    private TenantContextUtils() {}

    /**
     * Executes {@code task} with the given tenant set in {@link TenantContext}, clearing it in a
     * {@code finally} block regardless of outcome.
     *
     * @param tenantId the tenant ID to set before running the task
     * @param task     the task to run within tenant context
     * @throws Exception if the task throws
     */
    public static void withTenant(String tenantId, ThrowingRunnable task) throws Exception {
        TenantContext.set(tenantId);
        try {
            task.run();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Executes {@code task} with the given tenant set in {@link TenantContext}, clearing it in a
     * {@code finally} block regardless of outcome.
     *
     * @param tenantId the tenant ID to set before running the task
     * @param task     the task to run within tenant context
     * @param <T>      the return type of the task
     * @return the value returned by the task
     * @throws Exception if the task throws
     */
    public static <T> T withTenant(String tenantId, Callable<T> task) throws Exception {
        TenantContext.set(tenantId);
        try {
            return task.call();
        } finally {
            TenantContext.clear();
        }
    }

    /** A {@link Runnable}-like interface that allows checked exceptions. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
