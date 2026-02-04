/**
 * MigrationsPage Component
 *
 * Manages schema migrations with history display, planning, execution, and run details.
 * Provides migration history list with status, duration, and step details.
 * Supports migration planning with schema selection, steps preview, and risk assessment.
 * Supports migration execution with real-time progress tracking and rollback on failure.
 *
 * Requirements:
 * - 10.1: Display migration history showing all migration runs
 * - 10.2: Migration planning allows selecting source and target schemas
 * - 10.3: Migration plan displays steps to be executed
 * - 10.4: Migration plan displays estimated impact and risks
 * - 10.5: Migration execution shows real-time progress
 * - 10.6: Migration execution handles errors gracefully
 * - 10.7: Migration execution offers rollback option on failure
 * - 10.8: Display status, duration, and step details for each run
 */

import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../../context/I18nContext';
import { useApi } from '../../context/ApiContext';
import { LoadingSpinner, ErrorMessage } from '../../components';
import styles from './MigrationsPage.module.css';

/**
 * Migration run status type
 */
export type MigrationStatus = 'pending' | 'running' | 'completed' | 'failed' | 'rolled_back';

/**
 * Migration step result interface
 */
export interface MigrationStepResult {
  stepOrder: number;
  operation: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  details?: Record<string, unknown>;
  startedAt?: string;
  completedAt?: string;
  error?: string;
}

/**
 * Migration run interface matching the API response
 */
export interface MigrationRun {
  id: string;
  planId: string;
  collectionId: string;
  collectionName: string;
  fromVersion: number;
  toVersion: number;
  status: MigrationStatus;
  steps: MigrationStepResult[];
  startedAt?: string;
  completedAt?: string;
  error?: string;
}

/**
 * Migration step interface for planning
 */
export interface MigrationStep {
  order: number;
  operation: 'ADD_FIELD' | 'REMOVE_FIELD' | 'MODIFY_FIELD' | 'ADD_INDEX' | 'REMOVE_INDEX';
  details: Record<string, unknown>;
  reversible: boolean;
}

/**
 * Migration risk interface
 */
export interface MigrationRisk {
  level: 'low' | 'medium' | 'high';
  description: string;
}

/**
 * Migration plan interface
 */
export interface MigrationPlan {
  id: string;
  collectionId: string;
  collectionName: string;
  fromVersion: number;
  toVersion: number;
  steps: MigrationStep[];
  estimatedDuration: number;
  estimatedRecordsAffected: number;
  risks: MigrationRisk[];
}

/**
 * Collection summary for selection
 */
export interface CollectionSummary {
  id: string;
  name: string;
  displayName: string;
  currentVersion: number;
  availableVersions: number[];
}

/**
 * Props for MigrationsPage component
 */
export interface MigrationsPageProps {
  /** Optional test ID for testing */
  testId?: string;
}

// API functions using apiClient
async function fetchMigrationHistory(apiClient: any): Promise<MigrationRun[]> {
  return apiClient.get('/control/migrations');
}

async function fetchMigrationDetails(apiClient: any, id: string): Promise<MigrationRun> {
  return apiClient.get(`/control/migrations/${id}`);
}

async function fetchCollectionsForMigration(apiClient: any): Promise<CollectionSummary[]> {
  // The API returns a paginated response, so we need to extract the content array
  const response = await apiClient.get('/control/collections?size=1000');
  
  // Handle paginated response structure
  if (response && response.content && Array.isArray(response.content)) {
    // Map the collection DTOs to CollectionSummary format
    return Promise.all(
      response.content.map(async (collection: any) => {
        // Fetch versions for each collection
        const versions = await apiClient.get(`/control/collections/${collection.id}/versions`);
        const versionNumbers = Array.isArray(versions) 
          ? versions.map((v: any) => v.version)
          : [];
        
        return {
          id: collection.id,
          name: collection.name,
          displayName: collection.displayName || collection.name,
          currentVersion: collection.currentVersion || 1,
          availableVersions: versionNumbers.length > 0 ? versionNumbers : [1],
        };
      })
    );
  }
  
  // Fallback to empty array if response structure is unexpected
  return [];
}

interface CreateMigrationPlanRequest {
  collectionId: string;
  targetVersion: number;
}

async function createMigrationPlan(apiClient: any, request: CreateMigrationPlanRequest): Promise<MigrationPlan> {
  return apiClient.post('/control/migrations/plan', request);
}

/**
 * Execute a migration plan
 * Requirement 10.5: Migration execution shows real-time progress
 */
export interface ExecuteMigrationRequest {
  planId: string;
}

export interface ExecuteMigrationResponse {
  runId: string;
  status: MigrationStatus;
}

async function executeMigration(apiClient: any, request: ExecuteMigrationRequest): Promise<ExecuteMigrationResponse> {
  return apiClient.post('/control/migrations/execute', request);
}

/**
 * Rollback a failed migration
 * Requirement 10.7: Migration execution offers rollback option on failure
 */
async function rollbackMigration(apiClient: any, runId: string): Promise<MigrationRun> {
  return apiClient.post(`/control/migrations/${runId}/rollback`, {});
}

/**
 * Get migration run status for polling
 */
async function getMigrationRunStatus(apiClient: any, runId: string): Promise<MigrationRun> {
  return apiClient.get(`/control/migrations/${runId}`);
}

/**
 * Status Badge Component
 */
interface StatusBadgeProps {
  status: MigrationStatus;
}

function StatusBadge({ status }: StatusBadgeProps): React.ReactElement {
  const { t } = useI18n();
  const statusLabels: Record<MigrationStatus, string> = {
    pending: t('migrations.status.pending'),
    running: t('migrations.status.running'),
    completed: t('migrations.status.completed'),
    failed: t('migrations.status.failed'),
    rolled_back: t('migrations.status.rolledBack'),
  };

  const statusClass = status.replace('_', '');
  return (
    <span
      className={`${styles.statusBadge} ${styles[`status${statusClass.charAt(0).toUpperCase() + statusClass.slice(1)}`]}`}
      data-testid="status-badge"
    >
      {statusLabels[status] || status}
    </span>
  );
}

/**
 * Step Status Badge Component
 */
interface StepStatusBadgeProps {
  status: 'pending' | 'running' | 'completed' | 'failed';
}

function StepStatusBadge({ status }: StepStatusBadgeProps): React.ReactElement {
  const { t } = useI18n();
  const statusLabels: Record<string, string> = {
    pending: t('migrations.status.pending'),
    running: t('migrations.status.running'),
    completed: t('migrations.status.completed'),
    failed: t('migrations.status.failed'),
  };

  return (
    <span
      className={`${styles.stepStatusBadge} ${styles[`stepStatus${status.charAt(0).toUpperCase() + status.slice(1)}`]}`}
      data-testid="step-status-badge"
    >
      {statusLabels[status] || status}
    </span>
  );
}

/**
 * Risk Level Badge Component
 * Requirement 10.4: Display estimated impact and risks
 */
interface RiskBadgeProps {
  level: 'low' | 'medium' | 'high';
}

function RiskBadge({ level }: RiskBadgeProps): React.ReactElement {
  const { t } = useI18n();
  const levelLabels: Record<string, string> = {
    low: t('migrations.riskLevels.low'),
    medium: t('migrations.riskLevels.medium'),
    high: t('migrations.riskLevels.high'),
  };

  return (
    <span
      className={`${styles.riskBadge} ${styles[`risk${level.charAt(0).toUpperCase() + level.slice(1)}`]}`}
      data-testid="risk-badge"
    >
      {levelLabels[level] || level}
    </span>
  );
}

/**
 * Migration Plan Display Component
 * Requirements 10.3, 10.4: Display migration steps and estimated impact/risks
 */
interface MigrationPlanDisplayProps {
  plan: MigrationPlan;
  onClose: () => void;
  onExecute: (plan: MigrationPlan) => void;
}

function MigrationPlanDisplay({ plan, onClose, onExecute }: MigrationPlanDisplayProps): React.ReactElement {
  const { t } = useI18n();

  const formatDuration = (seconds: number): string => {
    if (seconds < 60) {
      return `${seconds}s`;
    } else if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      const secs = seconds % 60;
      return secs > 0 ? `${minutes}m ${secs}s` : `${minutes}m`;
    } else {
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
    }
  };

  const handleExecute = () => {
    onExecute(plan);
  };

  return (
    <div
      className={styles.modalOverlay}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="migration-plan-title"
      data-testid="migration-plan-modal"
    >
      <div
        className={styles.modalContent}
        onClick={(e) => e.stopPropagation()}
        role="document"
      >
        <div className={styles.modalHeader}>
          <h2 id="migration-plan-title" className={styles.modalTitle}>
            {t('migrations.planDetails')}
          </h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="close-plan-button"
          >
            ×
          </button>
        </div>

        <div className={styles.modalBody}>
          {/* Overview Section */}
          <div className={styles.detailsSection}>
            <h3 className={styles.detailsSectionTitle}>{t('migrations.overview')}</h3>
            <div className={styles.detailsGrid}>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.collection')}</span>
                <span className={styles.detailValue}>{plan.collectionName}</span>
              </div>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.versionChange')}</span>
                <span className={styles.detailValue}>
                  v{plan.fromVersion} → v{plan.toVersion}
                </span>
              </div>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.estimatedDuration')}</span>
                <span className={styles.detailValue}>
                  {formatDuration(plan.estimatedDuration)}
                </span>
              </div>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.recordsAffected')}</span>
                <span className={styles.detailValue}>
                  {plan.estimatedRecordsAffected.toLocaleString()}
                </span>
              </div>
            </div>
          </div>

          {/* Steps Section - Requirement 10.3 */}
          <div className={styles.detailsSection} data-testid="plan-steps-section">
            <h3 className={styles.detailsSectionTitle}>
              {t('migrations.steps')} ({plan.steps.length})
            </h3>
            {plan.steps.length === 0 ? (
              <p className={styles.noSteps}>{t('migrations.noSteps')}</p>
            ) : (
              <div className={styles.stepsList} data-testid="plan-steps-list">
                {plan.steps.map((step) => (
                  <div
                    key={step.order}
                    className={styles.stepItem}
                    data-testid={`plan-step-${step.order}`}
                  >
                    <div className={styles.stepHeader}>
                      <span className={styles.stepNumber}>{step.order}</span>
                      <span className={styles.stepOperation}>{step.operation}</span>
                      {step.reversible ? (
                        <span className={styles.reversibleBadge} data-testid="reversible-badge">
                          {t('migrations.reversible')}
                        </span>
                      ) : (
                        <span className={styles.irreversibleBadge} data-testid="irreversible-badge">
                          {t('migrations.irreversible')}
                        </span>
                      )}
                    </div>
                    {step.details && Object.keys(step.details).length > 0 && (
                      <div className={styles.stepDetails}>
                        {Object.entries(step.details).map(([key, value]) => (
                          <div key={key} className={styles.stepDetailItem}>
                            <span className={styles.stepDetailKey}>{key}:</span>
                            <span className={styles.stepDetailValue}>
                              {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Risks Section - Requirement 10.4 */}
          <div className={styles.detailsSection} data-testid="plan-risks-section">
            <h3 className={styles.detailsSectionTitle}>
              {t('migrations.risks')} ({plan.risks.length})
            </h3>
            {plan.risks.length === 0 ? (
              <p className={styles.noRisks} data-testid="no-risks">
                {t('migrations.noRisks')}
              </p>
            ) : (
              <div className={styles.risksList} data-testid="plan-risks-list">
                {plan.risks.map((risk, index) => (
                  <div
                    key={index}
                    className={styles.riskItem}
                    data-testid={`plan-risk-${index}`}
                  >
                    <RiskBadge level={risk.level} />
                    <span className={styles.riskDescription}>{risk.description}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={onClose}
            data-testid="close-plan-details-button"
          >
            {t('common.close')}
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={handleExecute}
            data-testid="execute-migration-button"
          >
            {t('migrations.executeMigration')}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Migration Execution Modal Component
 * Requirements 10.5, 10.6, 10.7: Execute migration with progress tracking, error handling, and rollback
 */
interface MigrationExecutionModalProps {
  plan: MigrationPlan;
  onClose: () => void;
  onComplete: () => void;
}

function MigrationExecutionModal({ plan, onClose, onComplete }: MigrationExecutionModalProps): React.ReactElement {
  const { t } = useI18n();
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [runId, setRunId] = useState<string | null>(null);
  const [currentRun, setCurrentRun] = useState<MigrationRun | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Execute migration mutation
  const executeMutation = useMutation({
    mutationFn: (request: ExecuteMigrationRequest) => executeMigration(apiClient, request),
    onSuccess: (response) => {
      setRunId(response.runId);
      setIsPolling(true);
    },
    onError: () => {
      // Error is handled in the UI
    },
  });

  // Rollback mutation - Requirement 10.7
  const rollbackMutation = useMutation({
    mutationFn: (runId: string) => rollbackMigration(apiClient, runId),
    onSuccess: (run) => {
      setCurrentRun(run);
      queryClient.invalidateQueries({ queryKey: ['migration-history'] });
    },
    onError: () => {
      // Error is handled in the UI
    },
  });

  // Poll for migration status - Requirement 10.5
  useEffect(() => {
    if (!isPolling || !runId) return;

    const pollStatus = async () => {
      try {
        const run = await getMigrationRunStatus(apiClient, runId);
        setCurrentRun(run);

        // Stop polling when migration is complete or failed
        if (run.status === 'completed' || run.status === 'failed' || run.status === 'rolled_back') {
          setIsPolling(false);
          queryClient.invalidateQueries({ queryKey: ['migration-history'] });
        }
      } catch {
        // Continue polling on error
      }
    };

    // Initial poll
    pollStatus();

    // Set up polling interval
    pollingIntervalRef.current = setInterval(pollStatus, 1000);

    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, [isPolling, runId, queryClient]);

  // Start execution when modal opens
  useEffect(() => {
    if (!runId && !executeMutation.isPending && !executeMutation.isError) {
      executeMutation.mutate({ planId: plan.id });
    }
  }, [plan.id, runId, executeMutation]);

  const handleRollback = () => {
    if (runId) {
      rollbackMutation.mutate(runId);
    }
  };

  const handleClose = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
    }
    if (currentRun?.status === 'completed') {
      onComplete();
    }
    onClose();
  };

  // Calculate progress percentage
  const progressPercentage = useMemo(() => {
    if (!currentRun || currentRun.steps.length === 0) return 0;
    const completedSteps = currentRun.steps.filter(
      (s) => s.status === 'completed'
    ).length;
    return Math.round((completedSteps / currentRun.steps.length) * 100);
  }, [currentRun]);

  // Determine if rollback is available - Requirement 10.7
  const canRollback = currentRun?.status === 'failed' && !rollbackMutation.isPending;

  // Determine current step
  const currentStep = currentRun?.steps.find((s) => s.status === 'running');

  return (
    <div
      className={styles.modalOverlay}
      role="dialog"
      aria-modal="true"
      aria-labelledby="migration-execution-title"
      data-testid="migration-execution-modal"
    >
      <div
        className={styles.modalContent}
        role="document"
      >
        <div className={styles.modalHeader}>
          <h2 id="migration-execution-title" className={styles.modalTitle}>
            {t('migrations.executingMigration')}
          </h2>
        </div>

        <div className={styles.modalBody}>
          {/* Overview */}
          <div className={styles.detailsSection}>
            <div className={styles.detailsGrid}>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.collection')}</span>
                <span className={styles.detailValue}>{plan.collectionName}</span>
              </div>
              <div className={styles.detailItem}>
                <span className={styles.detailLabel}>{t('migrations.versionChange')}</span>
                <span className={styles.detailValue}>
                  v{plan.fromVersion} → v{plan.toVersion}
                </span>
              </div>
            </div>
          </div>

          {/* Execution Error - Requirement 10.6 */}
          {executeMutation.isError && (
            <div className={styles.errorBox} data-testid="execution-error">
              <span className={styles.errorLabel}>{t('common.error')}:</span>
              <span className={styles.errorText}>
                {(executeMutation.error as Error)?.message || t('migrations.executionFailed')}
              </span>
            </div>
          )}

          {/* Progress Section - Requirement 10.5 */}
          {(isPolling || currentRun) && (
            <div className={styles.detailsSection} data-testid="execution-progress-section">
              <h3 className={styles.detailsSectionTitle}>{t('migrations.progress')}</h3>
              
              {/* Progress Bar */}
              <div className={styles.progressContainer} data-testid="progress-container">
                <div className={styles.progressBar}>
                  <div
                    className={`${styles.progressFill} ${
                      currentRun?.status === 'failed' ? styles.progressFailed : ''
                    } ${currentRun?.status === 'completed' ? styles.progressCompleted : ''}`}
                    style={{ width: `${progressPercentage}%` }}
                    data-testid="progress-fill"
                  />
                </div>
                <span className={styles.progressText} data-testid="progress-text">
                  {progressPercentage}%
                </span>
              </div>

              {/* Status */}
              <div className={styles.executionStatus} data-testid="execution-status">
                {currentRun?.status === 'running' && currentStep && (
                  <div className={styles.currentStepInfo}>
                    <LoadingSpinner size="small" />
                    <span>
                      {t('migrations.executingStep', {
                        step: String(currentStep.stepOrder),
                        total: String(currentRun.steps.length),
                        operation: currentStep.operation,
                      })}
                    </span>
                  </div>
                )}
                {currentRun?.status === 'completed' && (
                  <div className={styles.successMessage} data-testid="success-message">
                    <span className={styles.successIcon}>✓</span>
                    <span>{t('migrations.executionCompleted')}</span>
                  </div>
                )}
                {currentRun?.status === 'failed' && (
                  <div className={styles.failureMessage} data-testid="failure-message">
                    <span className={styles.failureIcon}>✗</span>
                    <span>{t('migrations.executionFailed')}</span>
                  </div>
                )}
                {currentRun?.status === 'rolled_back' && (
                  <div className={styles.rolledBackMessage} data-testid="rolled-back-message">
                    <span className={styles.rolledBackIcon}>↩</span>
                    <span>{t('migrations.rollbackCompleted')}</span>
                  </div>
                )}
              </div>

              {/* Steps Progress */}
              {currentRun && currentRun.steps.length > 0 && (
                <div className={styles.stepsProgress} data-testid="steps-progress">
                  {currentRun.steps.map((step) => (
                    <div
                      key={step.stepOrder}
                      className={`${styles.stepProgressItem} ${
                        step.status === 'completed' ? styles.stepCompleted : ''
                      } ${step.status === 'running' ? styles.stepRunning : ''} ${
                        step.status === 'failed' ? styles.stepFailed : ''
                      }`}
                      data-testid={`step-progress-${step.stepOrder}`}
                    >
                      <div className={styles.stepProgressHeader}>
                        <span className={styles.stepProgressNumber}>{step.stepOrder}</span>
                        <span className={styles.stepProgressOperation}>{step.operation}</span>
                        <StepStatusBadge status={step.status} />
                      </div>
                      {step.error && (
                        <div className={styles.stepProgressError} data-testid={`step-error-${step.stepOrder}`}>
                          {step.error}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* Error Details - Requirement 10.6 */}
              {currentRun?.error && (
                <div className={styles.errorBox} data-testid="migration-run-error">
                  <span className={styles.errorLabel}>{t('common.error')}:</span>
                  <span className={styles.errorText}>{currentRun.error}</span>
                </div>
              )}

              {/* Rollback Error */}
              {rollbackMutation.isError && (
                <div className={styles.errorBox} data-testid="rollback-error">
                  <span className={styles.errorLabel}>{t('migrations.rollbackFailed')}:</span>
                  <span className={styles.errorText}>
                    {(rollbackMutation.error as Error)?.message || t('errors.generic')}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Loading state while starting */}
          {executeMutation.isPending && !currentRun && (
            <div className={styles.loadingContainer} data-testid="starting-execution">
              <LoadingSpinner label={t('migrations.startingExecution')} />
            </div>
          )}
        </div>

        <div className={styles.modalFooter}>
          {/* Rollback Button - Requirement 10.7 */}
          {canRollback && (
            <button
              type="button"
              className={styles.dangerButton}
              onClick={handleRollback}
              disabled={rollbackMutation.isPending}
              data-testid="rollback-button"
            >
              {rollbackMutation.isPending ? t('common.loading') : t('migrations.rollback')}
            </button>
          )}
          
          <button
            type="button"
            className={styles.primaryButton}
            onClick={handleClose}
            disabled={isPolling && currentRun?.status === 'running'}
            data-testid="close-execution-button"
          >
            {currentRun?.status === 'completed' ? t('common.close') : 
             currentRun?.status === 'failed' || currentRun?.status === 'rolled_back' ? t('common.close') :
             isPolling ? t('migrations.executing') : t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Migration Planning Form Component
 * Requirements 10.2, 10.3, 10.4: Plan migration with schema selection
 */
interface MigrationPlanningFormProps {
  onClose: () => void;
  onPlanCreated: (plan: MigrationPlan) => void;
}

function MigrationPlanningForm({ onClose, onPlanCreated }: MigrationPlanningFormProps): React.ReactElement {
  const { t } = useI18n();
  const { apiClient } = useApi();
  const [selectedCollectionId, setSelectedCollectionId] = useState<string>('');
  const [targetVersion, setTargetVersion] = useState<number | ''>('');

  const {
    data: collections = [],
    isLoading: collectionsLoading,
    error: collectionsError,
  } = useQuery({
    queryKey: ['collections-for-migration'],
    queryFn: () => fetchCollectionsForMigration(apiClient),
  });

  const planMutation = useMutation({
    mutationFn: (request: CreateMigrationPlanRequest) => createMigrationPlan(apiClient, request),
    onSuccess: (plan) => {
      onPlanCreated(plan);
    },
  });

  // Ensure collections is always an array
  const collectionsArray = Array.isArray(collections) ? collections : [];

  const selectedCollection = useMemo(() => {
    return collectionsArray.find((c) => c.id === selectedCollectionId);
  }, [collectionsArray, selectedCollectionId]);

  const availableTargetVersions = useMemo(() => {
    if (!selectedCollection) return [];
    // Filter versions that are different from current version
    return selectedCollection.availableVersions.filter(
      (v) => v !== selectedCollection.currentVersion
    );
  }, [selectedCollection]);

  const handleCollectionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedCollectionId(e.target.value);
    setTargetVersion('');
  };

  const handleTargetVersionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    setTargetVersion(value === '' ? '' : parseInt(value, 10));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedCollectionId && targetVersion !== '') {
      planMutation.mutate({
        collectionId: selectedCollectionId,
        targetVersion: targetVersion as number,
      });
    }
  };

  const isFormValid = selectedCollectionId !== '' && targetVersion !== '';

  return (
    <div
      className={styles.modalOverlay}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="plan-migration-title"
      data-testid="plan-migration-modal"
    >
      <div
        className={styles.modalContent}
        onClick={(e) => e.stopPropagation()}
        role="document"
      >
        <div className={styles.modalHeader}>
          <h2 id="plan-migration-title" className={styles.modalTitle}>
            {t('migrations.planMigration')}
          </h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="close-planning-button"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalBody}>
            {collectionsLoading && (
              <div className={styles.loadingContainer}>
                <LoadingSpinner label={t('common.loading')} />
              </div>
            )}

            {collectionsError && (
              <div className={styles.errorContainer}>
                <ErrorMessage error={collectionsError as Error} />
              </div>
            )}

            {!collectionsLoading && !collectionsError && (
              <div className={styles.formFields}>
                {/* Collection Selection - Requirement 10.2 */}
                <div className={styles.formField}>
                  <label htmlFor="collection-select" className={styles.formLabel}>
                    {t('migrations.selectCollection')}
                    <span className={styles.requiredMark}>*</span>
                  </label>
                  <select
                    id="collection-select"
                    className={styles.formSelect}
                    value={selectedCollectionId}
                    onChange={handleCollectionChange}
                    data-testid="collection-select"
                    aria-describedby="collection-hint"
                  >
                    <option value="">{t('migrations.selectCollectionPlaceholder')}</option>
                    {collectionsArray.map((collection) => (
                      <option key={collection.id} value={collection.id}>
                        {collection.displayName || collection.name} (v{collection.currentVersion})
                      </option>
                    ))}
                  </select>
                  <span id="collection-hint" className={styles.formHint}>
                    {t('migrations.selectCollectionHint')}
                  </span>
                </div>

                {/* Target Version Selection - Requirement 10.2 */}
                <div className={styles.formField}>
                  <label htmlFor="target-version-select" className={styles.formLabel}>
                    {t('migrations.targetVersion')}
                    <span className={styles.requiredMark}>*</span>
                  </label>
                  <select
                    id="target-version-select"
                    className={styles.formSelect}
                    value={targetVersion}
                    onChange={handleTargetVersionChange}
                    disabled={!selectedCollectionId || availableTargetVersions.length === 0}
                    data-testid="target-version-select"
                    aria-describedby="target-version-hint"
                  >
                    <option value="">{t('migrations.selectVersionPlaceholder')}</option>
                    {availableTargetVersions.map((version) => (
                      <option key={version} value={version}>
                        v{version}
                      </option>
                    ))}
                  </select>
                  <span id="target-version-hint" className={styles.formHint}>
                    {selectedCollection
                      ? t('migrations.currentVersionHint', {
                          version: String(selectedCollection.currentVersion),
                        })
                      : t('migrations.selectCollectionFirst')}
                  </span>
                  {selectedCollectionId && availableTargetVersions.length === 0 && (
                    <span className={styles.formWarning} data-testid="no-versions-warning">
                      {t('migrations.noOtherVersions')}
                    </span>
                  )}
                </div>

                {/* Current Selection Summary */}
                {selectedCollection && targetVersion !== '' && (
                  <div className={styles.selectionSummary} data-testid="selection-summary">
                    <span className={styles.summaryLabel}>{t('migrations.plannedChange')}:</span>
                    <span className={styles.summaryValue}>
                      {selectedCollection.displayName || selectedCollection.name}:{' '}
                      v{selectedCollection.currentVersion} → v{targetVersion}
                    </span>
                  </div>
                )}

                {/* Error Display */}
                {planMutation.error && (
                  <div className={styles.errorBox} data-testid="plan-error">
                    <ErrorMessage error={planMutation.error as Error} />
                  </div>
                )}
              </div>
            )}
          </div>

          <div className={styles.modalFooter}>
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={onClose}
              data-testid="cancel-planning-button"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              className={styles.primaryButton}
              disabled={!isFormValid || planMutation.isPending}
              data-testid="create-plan-button"
            >
              {planMutation.isPending ? t('common.loading') : t('migrations.createPlan')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/**
 * Calculate duration between two dates
 */
function calculateDuration(startedAt?: string, completedAt?: string): string {
  if (!startedAt) return '-';
  
  const start = new Date(startedAt);
  const end = completedAt ? new Date(completedAt) : new Date();
  const durationMs = end.getTime() - start.getTime();
  
  if (durationMs < 1000) {
    return `${durationMs}ms`;
  } else if (durationMs < 60000) {
    return `${(durationMs / 1000).toFixed(1)}s`;
  } else {
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }
}

/**
 * Migration Run Details Modal Component
 * Requirement 10.8: Display step details for each run
 */
interface MigrationRunDetailsProps {
  migrationId: string;
  onClose: () => void;
}

function MigrationRunDetails({ migrationId, onClose }: MigrationRunDetailsProps): React.ReactElement {
  const { t, formatDate } = useI18n();
  const { apiClient } = useApi();

  const {
    data: migration,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['migration-details', migrationId],
    queryFn: () => fetchMigrationDetails(apiClient, migrationId),
  });

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    },
    [onClose]
  );

  return (
    <div
      className={styles.modalOverlay}
      onClick={onClose}
      onKeyDown={handleKeyDown}
      role="dialog"
      aria-modal="true"
      aria-labelledby="migration-details-title"
      data-testid="migration-details-modal"
    >
      <div
        className={styles.modalContent}
        onClick={(e) => e.stopPropagation()}
        role="document"
      >
        <div className={styles.modalHeader}>
          <h2 id="migration-details-title" className={styles.modalTitle}>
            {t('migrations.runDetails')}
          </h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="close-details-button"
          >
            ×
          </button>
        </div>

        <div className={styles.modalBody}>
          {isLoading && (
            <div className={styles.loadingContainer}>
              <LoadingSpinner label={t('common.loading')} />
            </div>
          )}

          {error && (
            <div className={styles.errorContainer}>
              <ErrorMessage error={error as Error} />
            </div>
          )}

          {migration && (
            <>
              <div className={styles.detailsSection}>
                <h3 className={styles.detailsSectionTitle}>{t('migrations.overview')}</h3>
                <div className={styles.detailsGrid}>
                  <div className={styles.detailItem}>
                    <span className={styles.detailLabel}>{t('migrations.collection')}</span>
                    <span className={styles.detailValue}>{migration.collectionName}</span>
                  </div>
                  <div className={styles.detailItem}>
                    <span className={styles.detailLabel}>{t('migrations.versionChange')}</span>
                    <span className={styles.detailValue}>
                      v{migration.fromVersion} → v{migration.toVersion}
                    </span>
                  </div>
                  <div className={styles.detailItem}>
                    <span className={styles.detailLabel}>{t('packages.status')}</span>
                    <StatusBadge status={migration.status} />
                  </div>
                  <div className={styles.detailItem}>
                    <span className={styles.detailLabel}>{t('migrations.duration')}</span>
                    <span className={styles.detailValue}>
                      {calculateDuration(migration.startedAt, migration.completedAt)}
                    </span>
                  </div>
                  {migration.startedAt && (
                    <div className={styles.detailItem}>
                      <span className={styles.detailLabel}>{t('migrations.startedAt')}</span>
                      <span className={styles.detailValue}>
                        {formatDate(new Date(migration.startedAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </span>
                    </div>
                  )}
                  {migration.completedAt && (
                    <div className={styles.detailItem}>
                      <span className={styles.detailLabel}>{t('migrations.completedAt')}</span>
                      <span className={styles.detailValue}>
                        {formatDate(new Date(migration.completedAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </span>
                    </div>
                  )}
                </div>

                {migration.error && (
                  <div className={styles.errorBox} data-testid="migration-error">
                    <span className={styles.errorLabel}>{t('common.error')}:</span>
                    <span className={styles.errorText}>{migration.error}</span>
                  </div>
                )}
              </div>

              <div className={styles.detailsSection}>
                <h3 className={styles.detailsSectionTitle}>
                  {t('migrations.steps')} ({migration.steps.length})
                </h3>
                {migration.steps.length === 0 ? (
                  <p className={styles.noSteps}>{t('migrations.noSteps')}</p>
                ) : (
                  <div className={styles.stepsList} data-testid="steps-list">
                    {migration.steps.map((step, index) => (
                      <div
                        key={step.stepOrder}
                        className={styles.stepItem}
                        data-testid={`step-${step.stepOrder}`}
                      >
                        <div className={styles.stepHeader}>
                          <span className={styles.stepNumber}>{index + 1}</span>
                          <span className={styles.stepOperation}>{step.operation}</span>
                          <StepStatusBadge status={step.status} />
                        </div>
                        {step.details && Object.keys(step.details).length > 0 && (
                          <div className={styles.stepDetails}>
                            {Object.entries(step.details).map(([key, value]) => (
                              <div key={key} className={styles.stepDetailItem}>
                                <span className={styles.stepDetailKey}>{key}:</span>
                                <span className={styles.stepDetailValue}>
                                  {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                        {step.error && (
                          <div className={styles.stepError}>
                            <span className={styles.errorLabel}>{t('common.error')}:</span>
                            <span className={styles.errorText}>{step.error}</span>
                          </div>
                        )}
                        {step.startedAt && (
                          <div className={styles.stepTiming}>
                            <span className={styles.stepTimingLabel}>{t('migrations.duration')}:</span>
                            <span className={styles.stepTimingValue}>
                              {calculateDuration(step.startedAt, step.completedAt)}
                            </span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={onClose}
            data-testid="close-button"
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Migration History Table Component
 * Requirement 10.1: Display migration history showing all migration runs
 */
interface MigrationHistoryTableProps {
  migrations: MigrationRun[];
  onViewDetails: (id: string) => void;
}

function MigrationHistoryTable({
  migrations,
  onViewDetails,
}: MigrationHistoryTableProps): React.ReactElement {
  const { t, formatDate } = useI18n();

  if (migrations.length === 0) {
    return (
      <div className={styles.emptyState} data-testid="history-empty">
        <p>{t('migrations.noHistory')}</p>
        <p className={styles.emptyStateHint}>{t('migrations.noHistoryHint')}</p>
      </div>
    );
  }

  return (
    <div className={styles.tableContainer} data-testid="history-table">
      <table className={styles.table}>
        <thead>
          <tr>
            <th>{t('migrations.collection')}</th>
            <th>{t('migrations.versionChange')}</th>
            <th>{t('packages.status')}</th>
            <th>{t('migrations.steps')}</th>
            <th>{t('migrations.duration')}</th>
            <th>{t('packages.date')}</th>
            <th>{t('common.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {migrations.map((migration) => (
            <tr
              key={migration.id}
              className={styles.tableRow}
              data-testid={`history-row-${migration.id}`}
            >
              <td className={styles.collectionCell}>{migration.collectionName}</td>
              <td className={styles.versionCell}>
                v{migration.fromVersion} → v{migration.toVersion}
              </td>
              <td>
                <StatusBadge status={migration.status} />
              </td>
              <td className={styles.stepsCell}>{migration.steps.length}</td>
              <td className={styles.durationCell}>
                {calculateDuration(migration.startedAt, migration.completedAt)}
              </td>
              <td className={styles.dateCell}>
                {migration.startedAt
                  ? formatDate(new Date(migration.startedAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })
                  : '-'}
              </td>
              <td className={styles.actionsCell}>
                <button
                  type="button"
                  className={styles.viewButton}
                  onClick={() => onViewDetails(migration.id)}
                  aria-label={t('migrations.viewDetails')}
                  data-testid={`view-details-${migration.id}`}
                >
                  {t('migrations.viewDetails')}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * MigrationsPage Component
 *
 * Main component for migration management with history display, planning, and execution.
 *
 * Requirements:
 * - 10.1: Display migration history showing all migration runs
 * - 10.2: Migration planning allows selecting source and target schemas
 * - 10.3: Migration plan displays steps to be executed
 * - 10.4: Migration plan displays estimated impact and risks
 * - 10.5: Migration execution shows real-time progress
 * - 10.6: Migration execution handles errors gracefully
 * - 10.7: Migration execution offers rollback option on failure
 * - 10.8: Display status, duration, and step details for each run
 */
export function MigrationsPage({ testId }: MigrationsPageProps): React.ReactElement {
  const { t } = useI18n();
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [selectedMigrationId, setSelectedMigrationId] = useState<string | null>(null);
  const [showPlanningForm, setShowPlanningForm] = useState(false);
  const [currentPlan, setCurrentPlan] = useState<MigrationPlan | null>(null);
  const [executingPlan, setExecutingPlan] = useState<MigrationPlan | null>(null);

  const {
    data: migrations = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['migration-history'],
    queryFn: () => fetchMigrationHistory(apiClient),
  });

  const handleViewDetails = useCallback((id: string) => {
    setSelectedMigrationId(id);
  }, []);

  const handleCloseDetails = useCallback(() => {
    setSelectedMigrationId(null);
  }, []);

  const handleOpenPlanningForm = useCallback(() => {
    setShowPlanningForm(true);
  }, []);

  const handleClosePlanningForm = useCallback(() => {
    setShowPlanningForm(false);
  }, []);

  const handlePlanCreated = useCallback((plan: MigrationPlan) => {
    setShowPlanningForm(false);
    setCurrentPlan(plan);
  }, []);

  const handleClosePlanDisplay = useCallback(() => {
    setCurrentPlan(null);
  }, []);

  // Handle execute migration from plan display
  const handleExecuteMigration = useCallback((plan: MigrationPlan) => {
    setCurrentPlan(null);
    setExecutingPlan(plan);
  }, []);

  // Handle close execution modal
  const handleCloseExecution = useCallback(() => {
    setExecutingPlan(null);
  }, []);

  // Handle execution complete
  const handleExecutionComplete = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['migration-history'] });
    setExecutingPlan(null);
  }, [queryClient]);

  return (
    <div className={styles.container} data-testid={testId || 'migrations-page'}>
      <header className={styles.header}>
        <h1 className={styles.title}>{t('migrations.title')}</h1>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={handleOpenPlanningForm}
          data-testid="plan-migration-button"
        >
          {t('migrations.planMigration')}
        </button>
      </header>

      <div className={styles.content}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>{t('migrations.history')}</h2>
        </div>

        {isLoading && (
          <div className={styles.loadingContainer}>
            <LoadingSpinner label={t('common.loading')} />
          </div>
        )}

        {error && (
          <div className={styles.errorContainer}>
            <ErrorMessage error={error as Error} onRetry={() => refetch()} />
          </div>
        )}

        {!isLoading && !error && (
          <MigrationHistoryTable
            migrations={migrations}
            onViewDetails={handleViewDetails}
          />
        )}
      </div>

      {selectedMigrationId && (
        <MigrationRunDetails
          migrationId={selectedMigrationId}
          onClose={handleCloseDetails}
        />
      )}

      {showPlanningForm && (
        <MigrationPlanningForm
          onClose={handleClosePlanningForm}
          onPlanCreated={handlePlanCreated}
        />
      )}

      {currentPlan && (
        <MigrationPlanDisplay
          plan={currentPlan}
          onClose={handleClosePlanDisplay}
          onExecute={handleExecuteMigration}
        />
      )}

      {executingPlan && (
        <MigrationExecutionModal
          plan={executingPlan}
          onClose={handleCloseExecution}
          onComplete={handleExecutionComplete}
        />
      )}
    </div>
  );
}

export default MigrationsPage;
