package io.kelta.runtime.storage;

/**
 * Marker for storage adapters backed by an external system (a remote REST API, a
 * foreign JDBC database, …) rather than a Kelta-owned physical table.
 *
 * <p>External adapters are collected by {@code DispatchingStorageAdapter} (via
 * {@code List<ExternalStorageAdapter>}) and keyed by {@link #storageType()}. Using a
 * dedicated sub-interface — rather than {@code List<StorageAdapter>} — keeps the
 * dispatcher out of its own dependency list, so there is no circular bean reference.
 *
 * <p>Implementations must still honour the {@link StorageAdapter} contract: return
 * records as {@code Map<String,Object>} keyed by camelCase API field names, and respect
 * {@code QueryRequest} filtering/sort/pagination as far as the backing system allows.
 *
 * @since 1.0.0
 */
public interface ExternalStorageAdapter extends StorageAdapter {
}
