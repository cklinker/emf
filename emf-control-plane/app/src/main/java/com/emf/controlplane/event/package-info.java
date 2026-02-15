/**
 * Event classes for Kafka event publishing.
 * 
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.emf.controlplane.event.ConfigEvent} - Base event wrapper with correlation ID</li>
 *   <li>{@link com.emf.controlplane.event.CollectionChangedPayload} - Collection change event payload</li>
 *   <li>{@link com.emf.controlplane.event.UiChangedPayload} - UI configuration change event payload</li>
 *   <li>{@link com.emf.controlplane.event.OidcChangedPayload} - OIDC configuration change event payload</li>
 *   <li>{@link com.emf.controlplane.event.ChangeType} - Enum for change types (CREATED, UPDATED, DELETED)</li>
 * </ul>
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>10.1: Publish collection change events to Kafka with full entity payload</li>
 *   <li>10.3: Publish UI configuration change events to Kafka</li>
 *   <li>10.4: Publish OIDC configuration change events to Kafka</li>
 *   <li>10.5: Include correlation ID in all events</li>
 *   <li>10.6: Events should be published asynchronously</li>
 * </ul>
 */
package com.emf.controlplane.event;
