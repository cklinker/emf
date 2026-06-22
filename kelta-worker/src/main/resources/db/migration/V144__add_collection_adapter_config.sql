-- Rec 4 (external connectors): per-collection storage-adapter configuration.
--
-- Holds the connection/mapping settings consumed by external StorageAdapters
-- (external-rest, external-jdbc) — e.g. {"adapterType":"external-rest","baseUrl":...}.
-- NULL for ordinary physical-table collections, which keep using
-- PhysicalTableStorageAdapter. The DispatchingStorageAdapter reads
-- adapterConfig.adapterType to route each collection to the right adapter.
ALTER TABLE collection ADD COLUMN IF NOT EXISTS adapter_config JSONB;
