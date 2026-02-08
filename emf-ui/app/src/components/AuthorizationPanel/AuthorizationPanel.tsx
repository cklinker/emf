/**
 * AuthorizationPanel Component
 *
 * Displays and manages route-level and field-level authorization configuration
 * for a collection. Allows selecting policies for each operation.
 *
 * Requirements:
 * - 5.9: Display route-level authorization configuration per operation (create, read, update, delete, list)
 * - 5.10: Allow selecting policies for each operation
 * - 5.11: Display field-level authorization configuration per field
 * - 5.12: Allow selecting policies for each field and operation
 *
 * Features:
 * - Route-level authorization with policy selection per operation
 * - Field-level authorization with policy selection per field and operation
 * - Authorization hints display
 * - Loading state
 * - Accessible with keyboard navigation and ARIA attributes
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import styles from './AuthorizationPanel.module.css'

/**
 * Operation types for route-level authorization
 */
export type RouteOperation = 'create' | 'read' | 'update' | 'delete' | 'list'

/**
 * Operation types for field-level authorization
 */
export type FieldOperation = 'read' | 'write'

/**
 * Policy summary for dropdown selection
 */
export interface PolicySummary {
  id: string
  name: string
  description?: string
}

/**
 * Route policy configuration
 */
export interface RoutePolicyConfig {
  operation: RouteOperation
  policyId: string | null
}

/**
 * Field policy configuration
 */
export interface FieldPolicyConfig {
  fieldName: string
  operation: FieldOperation
  policyId: string | null
}

/**
 * Field definition for field-level authorization
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
}

/**
 * Collection authorization configuration
 */
export interface CollectionAuthz {
  routePolicies: RoutePolicyConfig[]
  fieldPolicies: FieldPolicyConfig[]
}

/**
 * Props for the AuthorizationPanel component
 */
export interface AuthorizationPanelProps {
  /** Collection ID for context */
  collectionId: string
  /** Collection name for display */
  collectionName: string
  /** Fields in the collection for field-level authorization */
  fields: FieldDefinition[]
  /** Available policies for selection */
  policies: PolicySummary[]
  /** Current authorization configuration */
  authz?: CollectionAuthz
  /** Callback when route policy changes */
  onRouteAuthzChange: (routePolicies: RoutePolicyConfig[]) => void
  /** Callback when field policy changes */
  onFieldAuthzChange: (fieldPolicies: FieldPolicyConfig[]) => void
  /** Whether the panel is in a loading state */
  isLoading?: boolean
  /** Whether the panel is in a saving state */
  isSaving?: boolean
  /** Test ID for the component */
  testId?: string
}

/**
 * All route operations
 */
export const ROUTE_OPERATIONS: RouteOperation[] = ['create', 'read', 'update', 'delete', 'list']

/**
 * All field operations
 */
export const FIELD_OPERATIONS: FieldOperation[] = ['read', 'write']

/**
 * Get the icon for an operation
 */
function getOperationIcon(operation: RouteOperation | FieldOperation): string {
  const icons: Record<string, string> = {
    create: '➕',
    read: '👁️',
    update: '✏️',
    delete: '🗑️',
    list: '📋',
    write: '✏️',
  }
  return icons[operation] || '•'
}

/**
 * AuthorizationPanel Component
 *
 * Displays route-level and field-level authorization configuration
 * with policy selection for each operation.
 *
 * @example
 * ```tsx
 * <AuthorizationPanel
 *   collectionId="123"
 *   collectionName="users"
 *   fields={fields}
 *   policies={policies}
 *   authz={authz}
 *   onRouteAuthzChange={(routePolicies) => updateRouteAuthz(routePolicies)}
 *   onFieldAuthzChange={(fieldPolicies) => updateFieldAuthz(fieldPolicies)}
 *   isLoading={isLoading}
 * />
 * ```
 */
export function AuthorizationPanel({
  collectionId,
  collectionName,
  fields,
  policies,
  authz,
  onRouteAuthzChange,
  onFieldAuthzChange,
  isLoading = false,
  isSaving = false,
  testId = 'authorization-panel',
}: AuthorizationPanelProps): React.ReactElement {
  const { t } = useI18n()

  // State for expanded field sections
  const [expandedFields, setExpandedFields] = useState<Set<string>>(new Set())

  // Get current route policy for an operation
  const getRoutePolicy = useCallback(
    (operation: RouteOperation): string | null => {
      const config = authz?.routePolicies?.find((p) => p.operation === operation)
      return config?.policyId ?? null
    },
    [authz]
  )

  // Get current field policy for a field and operation
  const getFieldPolicy = useCallback(
    (fieldName: string, operation: FieldOperation): string | null => {
      const config = authz?.fieldPolicies?.find(
        (p) => p.fieldName === fieldName && p.operation === operation
      )
      return config?.policyId ?? null
    },
    [authz]
  )

  // Handle route policy change
  const handleRouteAuthzChange = useCallback(
    (operation: RouteOperation, policyId: string | null) => {
      const currentPolicies = authz?.routePolicies ?? []
      const existingIndex = currentPolicies.findIndex((p) => p.operation === operation)

      let newPolicies: RoutePolicyConfig[]
      if (existingIndex >= 0) {
        // Update existing
        newPolicies = [...currentPolicies]
        newPolicies[existingIndex] = { operation, policyId }
      } else {
        // Add new
        newPolicies = [...currentPolicies, { operation, policyId }]
      }

      // Remove entries with null policyId
      newPolicies = newPolicies.filter((p) => p.policyId !== null)

      onRouteAuthzChange(newPolicies)
    },
    [authz, onRouteAuthzChange]
  )

  // Handle field policy change
  const handleFieldAuthzChange = useCallback(
    (fieldName: string, operation: FieldOperation, policyId: string | null) => {
      const currentPolicies = authz?.fieldPolicies ?? []
      const existingIndex = currentPolicies.findIndex(
        (p) => p.fieldName === fieldName && p.operation === operation
      )

      let newPolicies: FieldPolicyConfig[]
      if (existingIndex >= 0) {
        // Update existing
        newPolicies = [...currentPolicies]
        newPolicies[existingIndex] = { fieldName, operation, policyId }
      } else {
        // Add new
        newPolicies = [...currentPolicies, { fieldName, operation, policyId }]
      }

      // Remove entries with null policyId
      newPolicies = newPolicies.filter((p) => p.policyId !== null)

      onFieldAuthzChange(newPolicies)
    },
    [authz, onFieldAuthzChange]
  )

  // Toggle field expansion
  const toggleFieldExpansion = useCallback((fieldId: string) => {
    setExpandedFields((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(fieldId)) {
        newSet.delete(fieldId)
      } else {
        newSet.add(fieldId)
      }
      return newSet
    })
  }, [])

  // Check if a field has any policies configured
  const hasFieldPolicies = useCallback(
    (fieldName: string): boolean => {
      return authz?.fieldPolicies?.some((p) => p.fieldName === fieldName && p.policyId) ?? false
    },
    [authz]
  )

  // Count configured route policies
  const configuredRoutePoliciesCount = useMemo(() => {
    return authz?.routePolicies?.filter((p) => p.policyId).length ?? 0
  }, [authz])

  // Count configured field policies
  const configuredFieldPoliciesCount = useMemo(() => {
    return authz?.fieldPolicies?.filter((p) => p.policyId).length ?? 0
  }, [authz])

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.header}>
          <h3 className={styles.title}>{t('authorizationPanel.title')}</h3>
        </div>
        <div className={styles.loadingContainer} data-testid={`${testId}-loading`}>
          <LoadingSpinner size="medium" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Header */}
      <div className={styles.header}>
        <h3 className={styles.title}>{t('authorizationPanel.title')}</h3>
        {isSaving && (
          <span className={styles.savingIndicator} data-testid={`${testId}-saving`}>
            <LoadingSpinner size="small" />
            <span>{t('common.saving')}</span>
          </span>
        )}
      </div>

      {/* Authorization Hint */}
      <div className={styles.hint} data-testid={`${testId}-hint`}>
        <span className={styles.hintIcon} aria-hidden="true">
          💡
        </span>
        <p className={styles.hintText}>{t('authorizationPanel.hint')}</p>
      </div>

      {/* Route-Level Authorization Section */}
      <section
        className={styles.section}
        aria-labelledby="route-authz-heading"
        data-testid={`${testId}-route-section`}
      >
        <div className={styles.sectionHeader}>
          <h4 id="route-authz-heading" className={styles.sectionTitle}>
            {t('authorization.routeAuthorization')}
          </h4>
          <span className={styles.badge} data-testid={`${testId}-route-count`}>
            {t('authorizationPanel.configuredCount', {
              count: String(configuredRoutePoliciesCount),
            })}
          </span>
        </div>

        <p className={styles.sectionDescription}>{t('authorizationPanel.routeDescription')}</p>

        <div
          className={styles.operationsList}
          role="list"
          aria-label={t('authorizationPanel.routeOperationsLabel')}
        >
          {ROUTE_OPERATIONS.map((operation) => (
            <div
              key={operation}
              className={styles.operationItem}
              role="listitem"
              data-testid={`${testId}-route-${operation}`}
            >
              <div className={styles.operationInfo}>
                <span className={styles.operationIcon} aria-hidden="true">
                  {getOperationIcon(operation)}
                </span>
                <label htmlFor={`route-policy-${operation}`} className={styles.operationLabel}>
                  {t(`authorization.operations.${operation}`)}
                </label>
              </div>
              <select
                id={`route-policy-${operation}`}
                className={styles.policySelect}
                value={getRoutePolicy(operation) ?? ''}
                onChange={(e) => handleRouteAuthzChange(operation, e.target.value || null)}
                disabled={isSaving}
                aria-describedby={`route-policy-${operation}-hint`}
                data-testid={`${testId}-route-${operation}-select`}
              >
                <option value="">{t('authorizationPanel.noPolicy')}</option>
                {policies.map((policy) => (
                  <option key={policy.id} value={policy.id}>
                    {policy.name}
                  </option>
                ))}
              </select>
              <span id={`route-policy-${operation}-hint`} className={styles.visuallyHidden}>
                {t('authorizationPanel.selectPolicyHint', {
                  operation: t(`authorization.operations.${operation}`),
                })}
              </span>
            </div>
          ))}
        </div>
      </section>

      {/* Field-Level Authorization Section */}
      <section
        className={styles.section}
        aria-labelledby="field-authz-heading"
        data-testid={`${testId}-field-section`}
      >
        <div className={styles.sectionHeader}>
          <h4 id="field-authz-heading" className={styles.sectionTitle}>
            {t('authorization.fieldAuthorization')}
          </h4>
          <span className={styles.badge} data-testid={`${testId}-field-count`}>
            {t('authorizationPanel.configuredCount', {
              count: String(configuredFieldPoliciesCount),
            })}
          </span>
        </div>

        <p className={styles.sectionDescription}>{t('authorizationPanel.fieldDescription')}</p>

        {fields.length === 0 ? (
          <div className={styles.emptyState} data-testid={`${testId}-no-fields`}>
            <p>{t('authorizationPanel.noFields')}</p>
          </div>
        ) : (
          <div
            className={styles.fieldsList}
            role="list"
            aria-label={t('authorizationPanel.fieldListLabel')}
          >
            {fields.map((field) => {
              const isExpanded = expandedFields.has(field.id)
              const hasPolicies = hasFieldPolicies(field.name)

              return (
                <div
                  key={field.id}
                  className={`${styles.fieldItem} ${isExpanded ? styles.expanded : ''}`}
                  role="listitem"
                  data-testid={`${testId}-field-${field.id}`}
                >
                  <button
                    type="button"
                    className={styles.fieldHeader}
                    onClick={() => toggleFieldExpansion(field.id)}
                    aria-expanded={isExpanded}
                    aria-controls={`field-authz-${field.id}`}
                    data-testid={`${testId}-field-${field.id}-toggle`}
                  >
                    <span
                      className={`${styles.expandIcon} ${isExpanded ? styles.rotated : ''}`}
                      aria-hidden="true"
                    >
                      ▶
                    </span>
                    <span className={styles.fieldName}>{field.displayName || field.name}</span>
                    {field.displayName && field.displayName !== field.name && (
                      <span className={styles.fieldTechnicalName}>({field.name})</span>
                    )}
                    {hasPolicies && (
                      <span
                        className={styles.configuredBadge}
                        title={t('authorizationPanel.hasConfiguredPolicies')}
                        aria-label={t('authorizationPanel.hasConfiguredPolicies')}
                      >
                        🔒
                      </span>
                    )}
                  </button>

                  {isExpanded && (
                    <div
                      id={`field-authz-${field.id}`}
                      className={styles.fieldOperations}
                      data-testid={`${testId}-field-${field.id}-operations`}
                    >
                      {FIELD_OPERATIONS.map((operation) => (
                        <div
                          key={operation}
                          className={styles.fieldOperationItem}
                          data-testid={`${testId}-field-${field.id}-${operation}`}
                        >
                          <div className={styles.operationInfo}>
                            <span className={styles.operationIcon} aria-hidden="true">
                              {getOperationIcon(operation)}
                            </span>
                            <label
                              htmlFor={`field-policy-${field.id}-${operation}`}
                              className={styles.operationLabel}
                            >
                              {t(`authorizationPanel.fieldOperations.${operation}`)}
                            </label>
                          </div>
                          <select
                            id={`field-policy-${field.id}-${operation}`}
                            className={styles.policySelect}
                            value={getFieldPolicy(field.name, operation) ?? ''}
                            onChange={(e) =>
                              handleFieldAuthzChange(field.name, operation, e.target.value || null)
                            }
                            disabled={isSaving}
                            data-testid={`${testId}-field-${field.id}-${operation}-select`}
                          >
                            <option value="">{t('authorizationPanel.noPolicy')}</option>
                            {policies.map((policy) => (
                              <option key={policy.id} value={policy.id}>
                                {policy.name}
                              </option>
                            ))}
                          </select>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </section>

      {/* No Policies Warning */}
      {policies.length === 0 && (
        <div className={styles.warningBanner} data-testid={`${testId}-no-policies-warning`}>
          <span className={styles.warningIcon} aria-hidden="true">
            ⚠️
          </span>
          <p className={styles.warningText}>{t('authorizationPanel.noPoliciesWarning')}</p>
        </div>
      )}
    </div>
  )
}

export default AuthorizationPanel
