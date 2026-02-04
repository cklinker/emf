/**
 * EMF Control Plane Service - Central configuration management for the EMF platform.
 * 
 * <p>This package contains the main application entry point and configuration classes
 * for the Control Plane Service. The service provides REST APIs for managing:
 * <ul>
 *   <li>Collection definitions and field schemas</li>
 *   <li>Authorization policies (roles, policies, route/field policies)</li>
 *   <li>UI configuration (pages, menus)</li>
 *   <li>OIDC provider configuration</li>
 *   <li>Package import/export for environment promotion</li>
 *   <li>Schema migration planning and tracking</li>
 * </ul>
 * 
 * <p>All configuration changes are persisted to PostgreSQL and published to Kafka
 * for consumption by domain services.
 */
package com.emf.controlplane;
