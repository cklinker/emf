import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { FieldDefinition, ResourceMetadata } from '@emf/sdk';
import { useEMFClient, useCurrentUser } from '../context/EMFContext';
import type { ResourceDetailProps, FieldRenderer, FieldRendererComponent } from './types';

/**
 * Optional ComponentRegistry interface for custom field renderers
 * This allows the component to work with or without the plugin-sdk
 */
interface ComponentRegistryInterface {
  getFieldRenderer: (fieldType: string) => FieldRendererComponent | undefined;
  hasFieldRenderer: (fieldType: string) => boolean;
}

// Global reference to ComponentRegistry if available
let componentRegistry: ComponentRegistryInterface | undefined;

/**
 * Set the ComponentRegistry for custom field renderers
 * This should be called by the application if plugin-sdk is available
 */
export function setComponentRegistry(registry: ComponentRegistryInterface): void {
  componentRegistry = registry;
}

/**
 * Get the current ComponentRegistry
 */
export function getComponentRegistry(): ComponentRegistryInterface | undefined {
  return componentRegistry;
}

/**
 * Default field renderers by type
 */
const defaultRenderers: Record<string, FieldRenderer> = {
  string: (value) => String(value ?? ''),
  number: (value) => String(value ?? ''),
  boolean: (value) => (
    <span aria-label={value ? 'Yes' : 'No'}>{value ? 'Yes' : 'No'}</span>
  ),
  date: (value) => {
    if (!value) return '';
    const date = new Date(value as string);
    return (
      <time dateTime={date.toISOString()}>
        {date.toLocaleDateString()}
      </time>
    );
  },
  datetime: (value) => {
    if (!value) return '';
    const date = new Date(value as string);
    return (
      <time dateTime={date.toISOString()}>
        {date.toLocaleString()}
      </time>
    );
  },
  json: (value) => (
    <pre className="emf-resource-detail__json">
      <code>{JSON.stringify(value, null, 2)}</code>
    </pre>
  ),
  reference: (value) => String(value ?? ''),
};

/**
 * ResourceDetail component for displaying resource details in read-only mode.
 * 
 * Features:
 * - Fetches resource data and schema on mount
 * - Renders fields based on the Collection_Schema
 * - Supports custom field renderers from plugin registry
 * - Uses default renderers for standard types
 * - Handles field-level authorization (hides restricted fields)
 * - Provides loading and error states with proper accessibility
 * 
 * @example
 * ```tsx
 * <ResourceDetail
 *   resourceName="users"
 *   recordId="123"
 *   layout="horizontal"
 *   customRenderers={{
 *     avatar: (value) => <img src={value} alt="Avatar" />
 *   }}
 * />
 * ```
 */
export function ResourceDetail({
  resourceName,
  recordId,
  layout = 'vertical',
  customRenderers = {},
  className = '',
  testId = 'emf-resource-detail',
}: ResourceDetailProps): JSX.Element {
  const client = useEMFClient();
  const user = useCurrentUser();

  // Fetch schema
  const { 
    data: schema, 
    isLoading: schemaLoading,
    error: schemaError,
  } = useQuery({
    queryKey: ['schema', resourceName],
    queryFn: async () => {
      const resources = await client.discover();
      const found = resources.find((r) => r.name === resourceName);
      // Return null instead of undefined to satisfy TanStack Query
      return found ?? null;
    },
  });

  // Fetch resource data
  const { 
    data, 
    isLoading: dataLoading, 
    error: dataError,
    refetch,
  } = useQuery({
    queryKey: ['resource', resourceName, recordId],
    queryFn: async () => {
      return client.resource(resourceName).get(recordId);
    },
  });

  // Check if user has access to a field
  const hasFieldAccess = useCallback((field: FieldDefinition, schemaData: ResourceMetadata | null | undefined): boolean => {
    if (!schemaData?.authz?.fieldLevel) return true;
    const requiredRoles = schemaData.authz.fieldLevel[field.name];
    if (!requiredRoles || requiredRoles.length === 0) return true;
    if (!user) return false;
    return requiredRoles.some((role) => user.roles.includes(role));
  }, [user]);

  // Get accessible fields
  const accessibleFields = useMemo((): FieldDefinition[] => {
    if (!schema) return [];
    return schema.fields.filter((field) => hasFieldAccess(field, schema));
  }, [schema, hasFieldAccess]);

  // Get renderer for a field - checks custom renderers, then registry, then defaults
  const getRenderer = useCallback((field: FieldDefinition): FieldRenderer => {
    // First check custom renderers passed as props (by field name)
    if (customRenderers[field.name]) {
      return customRenderers[field.name];
    }

    // Then check ComponentRegistry for type-based renderers
    const registry = getComponentRegistry();
    if (registry?.hasFieldRenderer(field.type)) {
      const RegistryRenderer = registry.getFieldRenderer(field.type);
      if (RegistryRenderer) {
        // Wrap the component-based renderer to match FieldRenderer signature
        return (value: unknown, fieldDef: FieldDefinition) => (
          <RegistryRenderer
            value={value}
            field={fieldDef}
            readOnly={true}
          />
        );
      }
    }

    // Fall back to default renderers
    return defaultRenderers[field.type] || defaultRenderers.string;
  }, [customRenderers]);

  // Combined error
  const error = schemaError || dataError;
  const isLoading = schemaLoading || dataLoading;

  // Render loading state
  if (isLoading) {
    return (
      <div 
        className={`emf-resource-detail emf-resource-detail--loading ${className}`}
        data-testid={testId}
        role="status"
        aria-busy="true"
        aria-label={`Loading ${resourceName} details`}
      >
        <div className="emf-resource-detail__loading" aria-live="polite">
          Loading...
        </div>
      </div>
    );
  }

  // Render error state
  if (error) {
    return (
      <div 
        className={`emf-resource-detail emf-resource-detail--error ${className}`}
        data-testid={testId}
        role="alert"
        aria-live="assertive"
      >
        <div className="emf-resource-detail__error">
          Error loading data: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
        <button 
          className="emf-resource-detail__retry-button"
          onClick={() => void refetch()}
          aria-label="Retry loading data"
        >
          Retry
        </button>
      </div>
    );
  }

  // Render not found state
  if (!schema) {
    return (
      <div 
        className={`emf-resource-detail emf-resource-detail--not-found ${className}`}
        data-testid={testId}
        role="alert"
      >
        <div className="emf-resource-detail__not-found">
          Resource schema not found for "{resourceName}"
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div 
        className={`emf-resource-detail emf-resource-detail--not-found ${className}`}
        data-testid={testId}
        role="alert"
      >
        <div className="emf-resource-detail__not-found">
          Record not found with ID "{recordId}"
        </div>
      </div>
    );
  }

  const record = data as Record<string, unknown>;

  return (
    <dl 
      className={`emf-resource-detail emf-resource-detail--${layout} ${className}`}
      data-testid={testId}
      aria-label={`${schema.displayName || resourceName} details`}
    >
      {accessibleFields.map((field) => (
        <div 
          key={field.name} 
          className="emf-resource-detail__field" 
          data-field={field.name}
        >
          <dt className="emf-resource-detail__label">
            {field.displayName || field.name}
          </dt>
          <dd className="emf-resource-detail__value">
            {getRenderer(field)(record[field.name], field)}
          </dd>
        </div>
      ))}
    </dl>
  );
}
