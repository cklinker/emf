package io.kelta.runtime.event;

import java.util.function.Consumer;

/**
 * Describes a subscription to platform events on a messaging subject.
 *
 * <p>Subscriptions can be either queue-based (load-balanced across instances)
 * or broadcast (every instance receives every message). Queue subscriptions
 * use a shared durable consumer; broadcast subscriptions use ephemeral
 * per-instance consumers.
 *
 * @since 1.0.0
 */
public record EventSubscription(
        String name,
        String subject,
        DeliveryMode deliveryMode,
        String queueGroup,
        Consumer<String> handler
) {

    /**
     * Determines how messages are delivered to subscribers.
     */
    public enum DeliveryMode {
        /**
         * Messages are load-balanced across instances sharing the same queue group.
         * Only one instance processes each message.
         */
        QUEUE_GROUP,

        /**
         * Every instance receives every message independently.
         * Used for cache invalidation and per-pod state refresh.
         */
        BROADCAST
    }

    /**
     * Creates a queue group subscription where messages are load-balanced across instances.
     *
     * @param name       subscription name (used for durable consumer naming)
     * @param subject    the messaging subject pattern (e.g., "kelta.record.changed.>")
     * @param queueGroup the queue group name for load balancing
     * @param handler    message handler receiving the raw JSON string
     * @return the subscription descriptor
     */
    public static EventSubscription queueGroup(String name, String subject,
                                                String queueGroup, Consumer<String> handler) {
        return new EventSubscription(name, subject, DeliveryMode.QUEUE_GROUP, queueGroup, handler);
    }

    /**
     * Creates a broadcast subscription where every instance receives every message.
     *
     * @param name    subscription name
     * @param subject the messaging subject pattern
     * @param handler message handler receiving the raw JSON string
     * @return the subscription descriptor
     */
    public static EventSubscription broadcast(String name, String subject, Consumer<String> handler) {
        return new EventSubscription(name, subject, DeliveryMode.BROADCAST, null, handler);
    }
}
