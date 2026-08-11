/**
 * ResourceDetailPage Module
 *
 * Exports the ResourceDetailPage component and related types.
 */

export { ResourceDetailPage } from './ResourceDetailPage'
export type { ResourceDetailPageProps, Resource } from './ResourceDetailPage'
// The schema types are owned by the shared hook — the page no longer forks them.
export type { FieldDefinition, CollectionSchema } from '../../hooks/useCollectionSchema'
