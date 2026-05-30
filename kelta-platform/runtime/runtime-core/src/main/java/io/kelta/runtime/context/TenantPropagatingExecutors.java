package io.kelta.runtime.context;

import org.springframework.core.task.TaskDecorator;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utilities for propagating the current {@link TenantContext} across thread boundaries.
 *
 * <p>Virtual threads, {@link java.util.concurrent.ExecutorService} submissions, Spring
 * {@code @Async} tasks, and event handlers all execute on threads that do not inherit
 * the caller's {@code ScopedValue} binding unless propagation is explicit. Using this
 * class ensures that any DB query issued from the wrapped task observes the same
 * tenant as the submitter, so the {@code current_tenant_id} session variable — and the
 * RLS policy it drives — cannot silently fall back to the platform sentinel.
 *
 * <p>Callers should prefer the {@link #decorate(ExecutorService)} wrapper for long-lived
 * executors and {@link #taskDecorator()} for Spring {@code ThreadPoolTaskExecutor}
 * beans. Ad-hoc submissions can use {@link #wrap(Runnable)} or {@link #wrap(Callable)}.
 */
public final class TenantPropagatingExecutors {

    private TenantPropagatingExecutors() {}

    public static ExecutorService decorate(ExecutorService delegate) {
        return new TenantPropagatingExecutorService(delegate);
    }

    public static TaskDecorator taskDecorator() {
        return TenantPropagatingExecutors::wrap;
    }

    public static Runnable wrap(Runnable task) {
        Snapshot snapshot = Snapshot.capture();
        return () -> snapshot.run(task);
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Snapshot snapshot = Snapshot.capture();
        return () -> snapshot.call(task);
    }

    /**
     * Immutable capture of the submitter's tenant binding plus OpenTelemetry
     * context. Replayed on the worker thread so DB queries see the right
     * tenant AND tracer spans on the worker attach to the submitter's parent
     * span.
     */
    private record Snapshot(String tenantId, String tenantSlug, Object otelContext) {

        static Snapshot capture() {
            return new Snapshot(TenantContext.get(), TenantContext.getSlug(), OtelBridge.currentContext());
        }

        void run(Runnable task) {
            Runnable wrapped = OtelBridge.wrap(otelContext, task);
            if (!hasBinding()) {
                wrapped.run();
                return;
            }
            carrier().run(wrapped);
        }

        <T> T call(Callable<T> task) throws Exception {
            Callable<T> wrapped = OtelBridge.wrap(otelContext, task);
            if (!hasBinding()) {
                return wrapped.call();
            }
            AtomicReference<T> result = new AtomicReference<>();
            AtomicReference<Exception> failure = new AtomicReference<>();
            carrier().run(() -> {
                try {
                    result.set(wrapped.call());
                } catch (Exception e) {
                    failure.set(e);
                }
            });
            Exception err = failure.get();
            if (err != null) {
                throw err;
            }
            return result.get();
        }

        private boolean hasBinding() {
            return notBlank(tenantId) || notBlank(tenantSlug);
        }

        private ScopedValue.Carrier carrier() {
            ScopedValue.Carrier carrier = null;
            if (notBlank(tenantId)) {
                carrier = ScopedValue.where(TenantContext.CURRENT_TENANT, tenantId);
            }
            if (notBlank(tenantSlug)) {
                carrier = (carrier == null)
                        ? ScopedValue.where(TenantContext.CURRENT_TENANT_SLUG, tenantSlug)
                        : carrier.where(TenantContext.CURRENT_TENANT_SLUG, tenantSlug);
            }
            return carrier;
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /**
     * Reflection-friendly bridge to {@code io.opentelemetry.context.Context}.
     * Uses direct types when the OTel API is on the classpath; gracefully
     * degrades to a no-op when it is not (the dep is declared
     * {@code <optional>true</optional>} in {@code runtime-core}). The
     * methods are short enough that JIT erases the indirection.
     */
    private static final class OtelBridge {

        private static final boolean OTEL_AVAILABLE = isOtelAvailable();

        private OtelBridge() {}

        private static boolean isOtelAvailable() {
            try {
                Class.forName("io.opentelemetry.context.Context");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        static Object currentContext() {
            if (!OTEL_AVAILABLE) {
                return null;
            }
            return io.opentelemetry.context.Context.current();
        }

        static Runnable wrap(Object ctx, Runnable task) {
            if (!OTEL_AVAILABLE || ctx == null) {
                return task;
            }
            return ((io.opentelemetry.context.Context) ctx).wrap(task);
        }

        static <T> Callable<T> wrap(Object ctx, Callable<T> task) {
            if (!OTEL_AVAILABLE || ctx == null) {
                return task;
            }
            return ((io.opentelemetry.context.Context) ctx).wrap(task);
        }
    }

    /** {@link ExecutorService} that wraps every submission in the caller's tenant scope. */
    private static final class TenantPropagatingExecutorService implements ExecutorService {

        private final ExecutorService delegate;

        TenantPropagatingExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override public void execute(Runnable command) { delegate.execute(wrap(command)); }
        @Override public Future<?> submit(Runnable task) { return delegate.submit(wrap(task)); }
        @Override public <T> Future<T> submit(Runnable task, T result) { return delegate.submit(wrap(task), result); }
        @Override public <T> Future<T> submit(Callable<T> task) { return delegate.submit(wrap(task)); }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(TenantPropagatingExecutors::wrap).toList());
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                             long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(TenantPropagatingExecutors::wrap).toList(),
                    timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, java.util.concurrent.ExecutionException {
            return delegate.invokeAny(tasks.stream().map(TenantPropagatingExecutors::wrap).toList());
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks.stream().map(TenantPropagatingExecutors::wrap).toList(),
                    timeout, unit);
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override public void close() { delegate.close(); }
    }
}
