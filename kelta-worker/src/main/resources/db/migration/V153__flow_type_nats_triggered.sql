-- V153: Replace the never-implemented KAFKA_TRIGGERED flow type with NATS_TRIGGERED.
-- Kafka is fully removed from the platform (messaging is NATS JetStream only);
-- NATS_TRIGGERED flows start when a message is published to the platform
-- trigger subject kelta.trigger.<tenantId>.<topic>.

UPDATE flow SET flow_type = 'NATS_TRIGGERED' WHERE flow_type = 'KAFKA_TRIGGERED';

ALTER TABLE flow DROP CONSTRAINT chk_flow_type;
ALTER TABLE flow ADD CONSTRAINT chk_flow_type CHECK (flow_type IN (
    'RECORD_TRIGGERED', 'NATS_TRIGGERED', 'SCHEDULED', 'AUTOLAUNCHED', 'SCREEN'
));
