/**
 * CollectionFormPage Component
 *
 * Page for creating or editing a collection.
 * Wraps the CollectionForm component with page layout.
 */

import React, { useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { CollectionForm, LoadingSpinner, ErrorMessage } from '../../components'
import type { Collection, CollectionFormData } from '../../components/CollectionForm/CollectionForm'
import styles from './CollectionFormPage.module.css'

/**
 * Props for CollectionFormPage component
 */
export interface CollectionFormPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * CollectionFormPage Component
 *
 * Page for creating or editing collections.
 */
export function CollectionFormPage({
  testId = 'collection-form-page',
}: CollectionFormPageProps): React.ReactElement {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useI18n()
  const { apiClient } = useApi()

  const isEditMode = Boolean(id)

  // Fetch existing collection if in edit mode
  const {
    data: collection,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['collection', id],
    queryFn: () => apiClient.get<Collection>(`/control/collections/${id}`),
    enabled: isEditMode,
  })

  // Handle form submission
  const handleSubmit = useCallback(
    async (data: CollectionFormData) => {
      if (isEditMode && id) {
        // Update existing collection - only name and description can be updated
        const requestData = {
          name: data.name,
          description: data.description || '',
        }
        await apiClient.put(`/control/collections/${id}`, requestData)
        navigate(`/collections/${id}`)
      } else {
        // Create new collection
        const requestData = {
          name: data.name,
          description: data.description || '',
        }

        const created = await apiClient.post<{
          id: string
          name: string
          description: string
          active: boolean
          currentVersion: number
          createdAt: string
          updatedAt: string
        }>('/control/collections', requestData)

        console.log('Created collection:', created)
        navigate(`/collections/${created.id}`)
      }
    },
    [apiClient, navigate, isEditMode, id]
  )

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (isEditMode && id) {
      navigate(`/collections/${id}`)
    } else {
      navigate('/collections')
    }
  }, [navigate, isEditMode, id])

  // Show loading state while fetching collection in edit mode
  if (isEditMode && isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Show error state if fetch failed
  if (isEditMode && error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => navigate('/collections')}
        />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>
          {isEditMode ? t('collections.editCollection') : t('collections.createCollection')}
        </h1>
      </header>

      <div className={styles.content}>
        <CollectionForm collection={collection} onSubmit={handleSubmit} onCancel={handleCancel} />
      </div>
    </div>
  )
}

export default CollectionFormPage
