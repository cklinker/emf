import type { FieldDefinition } from '@emf/sdk';

/**
 * Props for ResourceForm component
 */
export interface ResourceFormProps {
  /**
   * Name of the resource
   */
  resourceName: string;

  /**
   * Record ID for edit mode (optional)
   */
  recordId?: string;

  /**
   * Callback when save succeeds
   */
  onSave: (data: unknown) => void;

  /**
   * Callback when cancel is clicked
   */
  onCancel: () => void;

  /**
   * Initial values for the form
   */
  initialValues?: Record<string, unknown>;

  /**
   * Whether the form is read-only
   */
  readOnly?: boolean;

  /**
   * Custom class name
   */
  className?: string;
}

/**
 * Props passed to field renderer components
 */
export interface FieldRendererProps {
  /**
   * Field value
   */
  value: unknown;

  /**
   * Field definition
   */
  field: FieldDefinition;

  /**
   * Change handler (for edit mode)
   */
  onChange?: (value: unknown) => void;

  /**
   * Whether the field is read-only
   */
  readOnly?: boolean;
}

/**
 * Field renderer component type
 */
export type FieldRendererComponent = (props: FieldRendererProps) => JSX.Element;
