/**
 * EMF Runtime Core - Root package for the Enterprise Microservice Framework runtime library.
 * 
 * <p>This library provides the core abstractions and runtime engine for dynamic collection management,
 * including:
 * <ul>
 *   <li>{@code model} - Collection and field definition models</li>
 *   <li>{@code registry} - Thread-safe runtime registry for collection definitions</li>
 *   <li>{@code router} - Dynamic HTTP routing based on runtime collections</li>
 *   <li>{@code query} - Query engine with pagination, sorting, filtering, and field selection</li>
 *   <li>{@code validation} - Field-level validation engine</li>
 *   <li>{@code storage} - Storage adapter interface and implementations (Mode A/B)</li>
 *   <li>{@code events} - Event publishing hooks for Kafka integration</li>
 *   <li>{@code config} - Spring Boot auto-configuration</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package com.emf.runtime;
