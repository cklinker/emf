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

    /** Immutable capture of the submitter's tenant binding, replayed on the worker thread. */
    private record Snapshot(String tenantId, String tenantSlug) {

        static Snapshot capture() {
            return new Snapshot(TenantContext.get(), TenantContext.getSlug());
        }

        void run(Runnable task) {
            if (!hasBinding()) {
                task.run();
                return;
            }
            carrier().run(task::run);
        }

        <T> T call(Callable<T> task) throws Exception {
            if (!hasBinding()) {
                return task.call();
            }
            AtomicReference<T> result = new AtomicReference<>();
            AtomicReference<Exception> failure = new AtomicReference<>();
            carrier().run(() -> {
                try {
                    result.set(task.call());
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
