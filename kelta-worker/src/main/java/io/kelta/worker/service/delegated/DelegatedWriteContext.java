package io.kelta.worker.service.delegated;

import java.util.concurrent.Callable;

/**
 * Marks the current thread as executing a delegated-admin write that has already been
 * scope-validated by {@code DelegatedUserAdminController}.
 *
 * <p>The {@code IdentityCollectionGuardHook} rejects identity-collection writes from request
 * threads whose profile lacks {@code MANAGE_USERS} — this scoped value is the controlled
 * exception: the delegated controller validates the operation against the caller's effective
 * scope, then binds this context around the {@code QueryEngine} call so the guard admits it.
 *
 * <p>{@link ScopedValue}-based (virtual-thread safe), mirroring {@code TenantContext}.
 */
public final class DelegatedWriteContext {

    private static final ScopedValue<Boolean> AUTHORIZED = ScopedValue.newInstance();

    private DelegatedWriteContext() {
    }

    /** True when the current thread is inside a scope-validated delegated write. */
    public static boolean isAuthorized() {
        return AUTHORIZED.isBound() && Boolean.TRUE.equals(AUTHORIZED.get());
    }

    /** Runs {@code operation} with the delegated-write authorization bound. */
    public static void runAuthorized(Runnable operation) {
        ScopedValue.where(AUTHORIZED, Boolean.TRUE).run(operation);
    }

    /** Calls {@code operation} with the delegated-write authorization bound. */
    public static <T> T callAuthorized(Callable<T> operation) {
        try {
            return ScopedValue.where(AUTHORIZED, Boolean.TRUE).call(operation::call);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Delegated write failed", e);
        }
    }
}
