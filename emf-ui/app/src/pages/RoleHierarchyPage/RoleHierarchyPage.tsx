/**
 * RoleHierarchyPage Component
 *
 * Displays a tree visualization of the role hierarchy.
 * Supports selecting roles to view details and editing parent assignments.
 */

import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './RoleHierarchyPage.module.css'

export interface RoleHierarchyPageProps {
  className?: string
}

interface RoleNode {
  id: string
  name: string
  description?: string
  parentRoleId?: string
  hierarchyLevel: number
  children?: RoleNode[]
}

interface Role {
  id: string
  name: string
  description: string
}

export function RoleHierarchyPage({ className }: RoleHierarchyPageProps): React.ReactElement {
  const { t } = useI18n()
  const { adminClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [selectedRole, setSelectedRole] = useState<RoleNode | null>(null)
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set())
  const [showEditModal, setShowEditModal] = useState(false)

  // Fetch hierarchy tree
  const {
    data: hierarchy = [],
    isLoading,
    error,
  } = useQuery({
    queryKey: ['role-hierarchy'],
    queryFn: () => adminClient.roleHierarchy.get(),
  })

  // Fetch flat role list for parent selector
  const { data: allRoles = [] } = useQuery({
    queryKey: ['roles'],
    queryFn: async () => {
      const response = await adminClient.roles.list()
      return response as Role[]
    },
  })

  // Set parent mutation
  const setParentMutation = useMutation({
    mutationFn: ({ roleId, parentRoleId }: { roleId: string; parentRoleId: string | null }) =>
      adminClient.roleHierarchy.setParent(roleId, parentRoleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['role-hierarchy'] })
      showToast(t('roleHierarchy.parentUpdated'), 'success')
      setShowEditModal(false)
    },
  })

  const toggleExpand = useCallback((nodeId: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev)
      if (next.has(nodeId)) {
        next.delete(nodeId)
      } else {
        next.add(nodeId)
      }
      return next
    })
  }, [])

  const handleSelectRole = useCallback((node: RoleNode) => {
    setSelectedRole(node)
  }, [])

  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message={t('roleHierarchy.loadError')} />

  return (
    <div className={`${styles.container} ${className || ''}`}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t('roleHierarchy.title')}</h1>
      </div>

      <div className={styles.tree}>
        {hierarchy.length === 0 ? (
          <div className={styles.empty}>
            <p>{t('roleHierarchy.noRoles')}</p>
          </div>
        ) : (
          hierarchy.map((node) => renderTreeNode(node))
        )}
      </div>

      {selectedRole && (
        <div className={styles.detailPanel}>
          <h2 className={styles.detailTitle}>{selectedRole.name}</h2>
          <div className={styles.detailField}>
            <span className={styles.detailLabel}>{t('roleHierarchy.level')}</span>
            <span className={styles.detailValue}>{selectedRole.hierarchyLevel}</span>
          </div>
          {selectedRole.description && (
            <div className={styles.detailField}>
              <span className={styles.detailLabel}>{t('roleHierarchy.description')}</span>
              <span className={styles.detailValue}>{selectedRole.description}</span>
            </div>
          )}
          <div className={styles.detailField}>
            <span className={styles.detailLabel}>{t('roleHierarchy.parent')}</span>
            <span className={styles.detailValue}>
              {selectedRole.parentRoleId
                ? findRoleName(selectedRole.parentRoleId)
                : t('roleHierarchy.noParent')}
            </span>
          </div>
          <div className={styles.detailField}>
            <span className={styles.detailLabel}>{t('roleHierarchy.directChildren')}</span>
            <span className={styles.detailValue}>{selectedRole.children?.length || 0}</span>
          </div>
          <button
            className={`${styles.btn} ${styles.btnSecondary}`}
            onClick={() => setShowEditModal(true)}
            style={{ marginTop: '1rem' }}
          >
            {t('roleHierarchy.changeParent')}
          </button>
        </div>
      )}

      {showEditModal && selectedRole && renderEditModal()}
    </div>
  )

  function renderTreeNode(node: RoleNode): React.ReactElement {
    const hasChildren = node.children && node.children.length > 0
    const isExpanded = expandedNodes.has(node.id)
    const isSelected = selectedRole?.id === node.id

    return (
      <div key={node.id} className={styles.treeNode}>
        <div
          className={`${styles.treeNodeContent} ${isSelected ? styles.treeNodeSelected : ''}`}
          onClick={() => handleSelectRole(node)}
        >
          {hasChildren ? (
            <button
              className={styles.expandBtn}
              onClick={(e) => {
                e.stopPropagation()
                toggleExpand(node.id)
              }}
              aria-label={isExpanded ? 'Collapse' : 'Expand'}
            >
              {isExpanded ? '\u25BC' : '\u25B6'}
            </button>
          ) : (
            <span style={{ width: '1.25rem', display: 'inline-block' }} />
          )}
          <span className={styles.treeNodeName}>{node.name}</span>
          <span className={styles.treeNodeLevel}>L{node.hierarchyLevel}</span>
        </div>
        {hasChildren && isExpanded && (
          <div className={styles.treeChildren}>
            {node.children!.map((child) => renderTreeNode(child))}
          </div>
        )}
      </div>
    )
  }

  function findRoleName(roleId: string): string {
    const role = allRoles.find((r) => r.id === roleId)
    return role ? role.name : roleId
  }

  function renderEditModal() {
    if (!selectedRole) return null

    return (
      <div className={styles.modal}>
        <div
          className={styles.modalBackdrop}
          onClick={() => setShowEditModal(false)}
          role="presentation"
        />
        <div className={styles.modalContent} role="dialog" aria-modal="true">
          <h3 className={styles.modalTitle}>
            {t('roleHierarchy.changeParentFor', { name: selectedRole.name })}
          </h3>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              const formData = new FormData(e.currentTarget)
              const parentRoleId = (formData.get('parentRoleId') as string) || null
              setParentMutation.mutate({
                roleId: selectedRole.id,
                parentRoleId,
              })
            }}
          >
            <div className={styles.formGroup}>
              <label htmlFor="parent-role">{t('roleHierarchy.parentRole')}</label>
              <select
                id="parent-role"
                name="parentRoleId"
                defaultValue={selectedRole.parentRoleId || ''}
              >
                <option value="">{t('roleHierarchy.noParent')}</option>
                {allRoles
                  .filter((r) => r.id !== selectedRole.id)
                  .map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={`${styles.btn} ${styles.btnSecondary}`}
                onClick={() => setShowEditModal(false)}
              >
                {t('common.cancel')}
              </button>
              <button type="submit" className={`${styles.btn} ${styles.btnPrimary}`}>
                {t('common.save')}
              </button>
            </div>
          </form>
        </div>
      </div>
    )
  }
}

export default RoleHierarchyPage
