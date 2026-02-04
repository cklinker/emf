/**
 * ResourceDetailPage Component
 *
 * Displays a single resource record with all field values formatted
 * according to their field types. Provides edit and delete actions.
 *
 * Requirements:
 * - 11.7: Resource browser displays resource detail view
 * - 11.8: Resource browser allows viewing all field values
 * - 11.10: Resource browser allows deleting resources with confirmation
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../../context/I18nContext';
import { useApi } from '../../context/ApiContext';
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components';
import styles from './ResourceDetailPage.module.css';

/**
 * Field definition interface for collection schema
 */
export interface FieldDefinition {
  id: string;
  name: string;
  displayName?: string;
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference';
  required: boolean;
  referenceTarget?: string;
}

/**
 * Collection schema interface
 */
export interface CollectionSchema {
  id: string;
  name: string;
  displayName: string;
  fields: FieldDefinition[];
}

/**
 * Resource record interface
 */
export interface Resource {
  id: string;
  [key: string]: unknown;
}

/**
 * Props for ResourceDetailPage component
 */
export interface ResourceDetailPageProps {
  /** Collection name from route params (optional, can be from useParams) */
  collectionName?: string;
  /** Resource ID from route params (optional, can be from useParams) */
  resourceId?: string;
  /** Optional test ID for testing */
  testId?: string;
}

// API functions using apiClient
async function fetchCollectionSchema(apiClient: any, collectionName: string): Promise<CollectionSchema> {
  return apiClient.get(`/control/collections/${collectionName}`);
}

async function fetchResource(apiClient: any, collectionName: string, resourceId: string): Promise<Resource> {
  return apiClient.get(`/api/${collectionName}/${resourceId}`);
}

async function deleteResource(apiClient: any, collectionName: string, resourceId: string): Promise<void> {
  return apiClient.delete(`/api/${collectionName}/${resourceId}`);
}

/**
 * ResourceDetailPage Component
 *
 * Main page for viewing a single resource record.
 * Displays all field values with appropriate formatting and provides
 * edit and delete actions.
 */
export function ResourceDetailPage({
  collectionName: propCollectionName,
  resourceId: propResourceId,
  testId = 'resource-detail-page',
}: ResourceDetailPageProps): React.ReactElement {
  const params = useParams<{ collectionName: string; resourceId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { t, formatDate, formatNumber } = useI18n();
  const { apiClient } = useApi();
  const { showToast } = useToast();

  // Get collection name and resource ID from props or route params
  const collectionName = propCollectionName || params.collectionName || '';
  const resourceId = propResourceId || params.resourceId || '';

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  // Fetch collection schema
  const {
    data: schema,
    isLoading: schemaLoading,
    error: schemaError,
  } = useQuery({
    queryKey: ['collection-schema', collectionName],
    queryFn: () => fetchCollectionSchema(apiClient, collectionName),
    enabled: !!collectionName,
  });

  // Fetch resource data
  const {
    data: resource,
    isLoading: resourceLoading,
    error: resourceError,
    refetch: refetchResource,
  } = useQuery({
    queryKey: ['resource', collectionName, resourceId],
    queryFn: () => fetchResource(apiClient, collectionName, resourceId),
    enabled: !!collectionName && !!resourceId,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => deleteResource(apiClient, collectionName, resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resources', collectionName] });
      showToast(t('success.deleted', { item: t('resources.record') }), 'success');
      navigate(`/resources/${collectionName}`);
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Sort fields by order
  const sortedFields = useMemo(() => {
    if (!schema?.fields) return [];
    return [...schema.fields].sort((a, b) => {
      // Fields may not have order property, so default to 0
      const orderA = (a as FieldDefinition & { order?: number }).order ?? 0;
      const orderB = (b as FieldDefinition & { order?: number }).order ?? 0;
      return orderA - orderB;
    });
  }, [schema]);

  // Handle back navigation
  const handleBack = useCallback(() => {
    navigate(`/resources/${collectionName}`);
  }, [navigate, collectionName]);

  // Handle edit action
  const handleEdit = useCallback(() => {
    navigate(`/resources/${collectionName}/${resourceId}/edit`);
  }, [navigate, collectionName, resourceId]);

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback(() => {
    setDeleteDialogOpen(true);
  }, []);

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    deleteMutation.mutate();
  }, [deleteMutation]);

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false);
  }, []);

  /**
   * Format field value based on field type
   * Requirement 11.8: Display all field values with appropriate formatting
   */
  const formatFieldValue = useCallback(
    (value: unknown, field: FieldDefinition): React.ReactNode => {
      if (value === null || value === undefined) {
        return <span className={styles.emptyValue}>-</span>;
      }

      switch (field.type) {
        case 'boolean':
          return (
            <span className={value ? styles.booleanTrue : styles.booleanFalse}>
              {value ? t('common.yes') : t('common.no')}
            </span>
          );

        case 'number':
          return (
            <span className={styles.numberValue}>
              {formatNumber(value as number)}
            </span>
          );

        case 'date':
          try {
            return formatDate(new Date(value as string), {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            });
          } catch {
            return String(value);
          }

        case 'datetime':
          try {
            return formatDate(new Date(value as string), {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            });
          } catch {
            return String(value);
          }

        case 'json':
          if (typeof value === 'object') {
            return (
              <pre className={styles.jsonValue}>
                <code>{JSON.stringify(value, null, 2)}</code>
              </pre>
            );
          }
          return String(value);

        case 'reference':
          // For reference fields, display the ID with a link if possible
          return (
            <span className={styles.referenceValue}>
              {field.referenceTarget ? (
                <Link
                  to={`/resources/${field.referenceTarget}/${value}`}
                  className={styles.referenceLink}
                >
                  {String(value)}
                </Link>
              ) : (
                String(value)
              )}
            </span>
          );

        case 'string':
        default:
          // Handle long strings
          const stringValue = String(value);
          if (stringValue.length > 500) {
            return (
              <div className={styles.longTextValue}>
                {stringValue}
              </div>
            );
          }
          return stringValue;
      }
    },
    [t, formatDate, formatNumber]
  );

  /**
   * Get field type display label
   */
  const getFieldTypeLabel = useCallback(
    (type: FieldDefinition['type']): string => {
      return t(`fields.types.${type}`);
    },
    [t]
  );

  // Loading state
  const isLoading = schemaLoading || resourceLoading;

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    );
  }

  // Error state - schema error
  if (schemaError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={schemaError instanceof Error ? schemaError : new Error(t('errors.generic'))}
          onRetry={() => queryClient.invalidateQueries({ queryKey: ['collection-schema', collectionName] })}
        />
      </div>
    );
  }

  // Error state - resource error
  if (resourceError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={resourceError instanceof Error ? resourceError : new Error(t('errors.generic'))}
          onRetry={() => refetchResource()}
        />
      </div>
    );
  }

  // Not found state
  if (!schema || !resource) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage error={new Error(t('errors.notFound'))} />
      </div>
    );
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <nav className={styles.breadcrumb} aria-label="Breadcrumb">
            <Link to="/resources" className={styles.breadcrumbLink}>
              {t('resources.title')}
            </Link>
            <span className={styles.breadcrumbSeparator} aria-hidden="true">/</span>
            <Link to={`/resources/${collectionName}`} className={styles.breadcrumbLink}>
              {schema.displayName}
            </Link>
            <span className={styles.breadcrumbSeparator} aria-hidden="true">/</span>
            <span className={styles.breadcrumbCurrent}>{resource.id}</span>
          </nav>
          <h1 className={styles.title} data-testid="resource-title">
            {t('resources.viewRecord')}
          </h1>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.backButton}
            onClick={handleBack}
            aria-label={t('common.back')}
            data-testid="back-button"
          >
            ← {t('common.back')}
          </button>
          <button
            type="button"
            className={styles.editButton}
            onClick={handleEdit}
            aria-label={t('resources.editRecord')}
            data-testid="edit-button"
          >
            {t('common.edit')}
          </button>
          <button
            type="button"
            className={styles.deleteButton}
            onClick={handleDeleteClick}
            aria-label={t('resources.deleteRecord')}
            data-testid="delete-button"
          >
            {t('common.delete')}
          </button>
        </div>
      </header>

      {/* Resource ID Section */}
      <section className={styles.idSection} aria-labelledby="id-heading">
        <h2 id="id-heading" className={styles.sectionTitle}>
          ID
        </h2>
        <div className={styles.idValue} data-testid="resource-id">
          {resource.id}
        </div>
      </section>

      {/* Field Values Section */}
      <section className={styles.fieldsSection} aria-labelledby="fields-heading">
        <h2 id="fields-heading" className={styles.sectionTitle}>
          {t('collections.fields')}
        </h2>
        
        {sortedFields.length === 0 ? (
          <div className={styles.emptyState} data-testid="no-fields">
            <p>{t('common.noData')}</p>
          </div>
        ) : (
          <div className={styles.fieldsGrid} data-testid="fields-grid">
            {sortedFields.map((field, index) => (
              <div
                key={field.id}
                className={styles.fieldItem}
                data-testid={`field-item-${index}`}
              >
                <div className={styles.fieldHeader}>
                  <span className={styles.fieldName}>
                    {field.displayName || field.name}
                  </span>
                  <span className={styles.fieldType}>
                    {getFieldTypeLabel(field.type)}
                  </span>
                </div>
                <div
                  className={styles.fieldValue}
                  data-testid={`field-value-${field.name}`}
                >
                  {formatFieldValue(resource[field.name], field)}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Metadata Section */}
      <section className={styles.metadataSection} aria-labelledby="metadata-heading">
        <h2 id="metadata-heading" className={styles.sectionTitle}>
          Metadata
        </h2>
        <div className={styles.metadataGrid}>
          {resource.createdAt && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>{t('collections.created')}</span>
              <span className={styles.metadataValue} data-testid="created-at">
                {formatDate(new Date(resource.createdAt as string), {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          )}
          {resource.updatedAt && (
            <div className={styles.metadataItem}>
              <span className={styles.metadataLabel}>{t('collections.updated')}</span>
              <span className={styles.metadataValue} data-testid="updated-at">
                {formatDate(new Date(resource.updatedAt as string), {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          )}
        </div>
      </section>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('resources.deleteRecord')}
        message={t('resources.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  );
}

export default ResourceDetailPage;
