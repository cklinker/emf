/**
 * Spring Boot auto-configuration for EMF runtime components.
 * 
 * <p>This package provides Spring Boot auto-configuration for:
 * <ul>
 *   <li>Runtime registry bean configuration</li>
 *   <li>Storage adapter configuration</li>
 *   <li>Query engine configuration</li>
 *   <li>Validation engine configuration</li>
 *   <li>Connection pooling for JdbcTemplate</li>
 *   <li>Spring Actuator endpoints for health checks</li>
 * </ul>
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code emf.query.default-page-size} - Default page size (20)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package com.emf.runtime.config;
