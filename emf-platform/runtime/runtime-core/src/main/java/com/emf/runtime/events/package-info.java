/**
 * Event publishing hooks for collection lifecycle events.
 * 
 * <p>This package provides integration points for publishing events to Kafka:
 * <ul>
 *   <li><b>Create events</b> - Published when a record is created</li>
 *   <li><b>Update events</b> - Published when a record is updated</li>
 *   <li><b>Delete events</b> - Published when a record is deleted</li>
 * </ul>
 * 
 * <p>Event payloads include:
 * <ul>
 *   <li>Event ID (UUID)</li>
 *   <li>Event type (CREATED, UPDATED, DELETED)</li>
 *   <li>Collection name</li>
 *   <li>Record ID</li>
 *   <li>Record data</li>
 *   <li>Timestamp</li>
 *   <li>Metadata (including collection version)</li>
 * </ul>
 * 
 * <p>Event hook failures are logged but do not fail the main operation,
 * ensuring system resilience.
 * 
 * @since 1.0.0
 */
package com.emf.runtime.events;
