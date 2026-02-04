/**
 * TypeScript Type Definitions
 * 
 * This module exports all shared TypeScript types and interfaces used throughout the application.
 * Types are organized by domain: configuration, collections, authorization, UI builder, etc.
 */

// Re-export types from domain-specific files
export * from './common';
export * from './config';
export * from './collections';
export * from './authorization';
export * from './auth';
export * from './plugin';

// Additional type exports will be added as they are implemented:
// export * from './packages';
// export * from './migrations';
// export * from './uiBuilder';
// export * from './dashboard';
