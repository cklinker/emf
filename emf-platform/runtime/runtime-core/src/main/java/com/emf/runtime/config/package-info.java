/**
 * Spring Boot auto-configuration for EMF runtime components.
 * 
 * <p>This package provides Spring Boot auto-configuration for:
 * <ul>
 *   <li>Runtime registry bean configuration</li>
 *   <li>Storage adapter selection (Mode A vs Mode B via properties)</li>
 *   <li>Query engine configuration</li>
 *   <li>Validation engine configuration</li>
 *   <li>Event publisher configuration (Kafka)</li>
 *   <li>Connection pooling for JdbcTemplate</li>
 *   <li>Spring Actuator endpoints for health checks</li>
 * </ul>
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code emf.storage.mode} - PHYSICAL_TABLES (default) or JSONB_STORE</li>
 *   <li>{@code emf.query.default-page-size} - Default page size (20)</li>
 *   <li>{@code emf.events.enabled} - Enable/disable event publishing</li>
 *   <li>{@code emf.events.topic-prefix} - Kafka topic prefix</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package com.emf.runtime.config;
