/**
 * Dynamic HTTP routing based on runtime collections.
 * 
 * <p>This package provides dynamic routing capabilities that map HTTP requests to
 * collection operations without requiring code changes. Features include:
 * <ul>
 *   <li>URL pattern matching for collection endpoints</li>
 *   <li>Support for standard CRUD operations (LIST, GET, POST, PUT, PATCH, DELETE)</li>
 *   <li>Automatic collection name extraction from URL paths</li>
 *   <li>Integration with the runtime registry for collection lookup</li>
 * </ul>
 * 
 * <p>URL patterns supported:
 * <ul>
 *   <li>{@code /api/collections/{collectionName}} - List and create operations</li>
 *   <li>{@code /api/collections/{collectionName}/{id}} - Get, update, and delete operations</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package com.emf.runtime.router;
