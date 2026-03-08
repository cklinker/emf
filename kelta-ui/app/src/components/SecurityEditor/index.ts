/**
 * SecurityEditor Components
 *
 * Reusable components for managing security profiles and permission sets.
 * Used in Profile detail/edit pages, Permission Set pages, and user administration.
 */

// System Permission Checklist
export { SystemPermissionChecklist } from './SystemPermissionChecklist'
export type { SystemPermissionChecklistProps } from './SystemPermissionChecklist'

// Object Permission Matrix
export { ObjectPermissionMatrix } from './ObjectPermissionMatrix'
export type { ObjectPermissionMatrixProps, ObjectPermission } from './ObjectPermissionMatrix'

// Field Permission Editor
export { FieldPermissionEditor } from './FieldPermissionEditor'
export type {
  FieldPermissionEditorProps,
  FieldVisibility,
  CollectionRef,
  FieldRef,
  FieldPermission,
} from './FieldPermissionEditor'

// Effective Permissions Panel
export { EffectivePermissionsPanel } from './EffectivePermissionsPanel'
export type { EffectivePermissionsPanelProps } from './EffectivePermissionsPanel'
