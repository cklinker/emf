/**
 * SharingSettingsPage Component
 *
 * Admin page for managing record-level sharing settings:
 * - Organization-wide defaults per collection
 * - Sharing rules per collection
 * - User groups management
 */

import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner } from '../../components'
import styles from './SharingSettingsPage.module.css'

export interface SharingSettingsPageProps {
  className?: string
}

interface Collection {
  id: string
  name: string
  displayName: string
}

type TabType = 'owd' | 'rules' | 'groups'

export function SharingSettingsPage({ className }: SharingSettingsPageProps): React.ReactElement {
  const { t } = useI18n()
  const { adminClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [activeTab, setActiveTab] = useState<TabType>('owd')
  const [selectedCollectionId, setSelectedCollectionId] = useState<string>('')
  const [showRuleModal, setShowRuleModal] = useState(false)
  const [showGroupModal, setShowGroupModal] = useState(false)

  // Fetch collections for the dropdown
  const { data: collections = [] } = useQuery({
    queryKey: ['collections'],
    queryFn: async () => {
      const response = await adminClient.collections.list()
      return response as Collection[]
    },
  })

  // Fetch OWD for selected collection
  const { data: owd } = useQuery({
    queryKey: ['owd', selectedCollectionId],
    queryFn: () => adminClient.sharing.getOwd(selectedCollectionId),
    enabled: !!selectedCollectionId,
  })

  // Fetch sharing rules for selected collection
  const { data: rules = [] } = useQuery({
    queryKey: ['sharing-rules', selectedCollectionId],
    queryFn: () => adminClient.sharing.listRules(selectedCollectionId),
    enabled: !!selectedCollectionId,
  })

  // Fetch user groups
  const { data: groups = [], isLoading: groupsLoading } = useQuery({
    queryKey: ['user-groups'],
    queryFn: () => adminClient.groups.list(),
  })

  // Set OWD mutation
  const setOwdMutation = useMutation({
    mutationFn: ({
      collectionId,
      internalAccess,
    }: {
      collectionId: string
      internalAccess: string
    }) => adminClient.sharing.setOwd(collectionId, { internalAccess }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['owd', selectedCollectionId] })
      showToast(t('sharing.owdSaved'), 'success')
    },
  })

  // Create sharing rule mutation
  const createRuleMutation = useMutation({
    mutationFn: (data: {
      collectionId: string
      name: string
      ruleType: string
      sharedTo: string
      sharedToType: string
      accessLevel: string
    }) =>
      adminClient.sharing.createRule(data.collectionId, {
        name: data.name,
        ruleType: data.ruleType as 'OWNER_BASED' | 'CRITERIA_BASED',
        sharedTo: data.sharedTo,
        sharedToType: data.sharedToType as 'ROLE' | 'GROUP' | 'QUEUE',
        accessLevel: data.accessLevel as 'READ' | 'READ_WRITE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['sharing-rules', selectedCollectionId],
      })
      showToast(t('sharing.ruleCreated'), 'success')
      setShowRuleModal(false)
    },
  })

  // Delete sharing rule mutation
  const deleteRuleMutation = useMutation({
    mutationFn: (ruleId: string) => adminClient.sharing.deleteRule(ruleId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['sharing-rules', selectedCollectionId],
      })
      showToast(t('sharing.ruleDeleted'), 'success')
    },
  })

  // Create group mutation
  const createGroupMutation = useMutation({
    mutationFn: (data: { name: string; description?: string }) => adminClient.groups.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-groups'] })
      showToast(t('sharing.groupCreated'), 'success')
      setShowGroupModal(false)
    },
  })

  // Delete group mutation
  const deleteGroupMutation = useMutation({
    mutationFn: (groupId: string) => adminClient.groups.delete(groupId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-groups'] })
      showToast(t('sharing.groupDeleted'), 'success')
    },
  })

  const handleOwdChange = useCallback(
    (internalAccess: string) => {
      if (selectedCollectionId) {
        setOwdMutation.mutate({
          collectionId: selectedCollectionId,
          internalAccess,
        })
      }
    },
    [selectedCollectionId, setOwdMutation]
  )

  return (
    <div className={`${styles.container} ${className || ''}`}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t('sharing.title')}</h1>
      </div>

      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'owd' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('owd')}
        >
          {t('sharing.owdTab')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'rules' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('rules')}
        >
          {t('sharing.rulesTab')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'groups' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('groups')}
        >
          {t('sharing.groupsTab')}
        </button>
      </div>

      {(activeTab === 'owd' || activeTab === 'rules') && (
        <div className={styles.collectionSelector}>
          <label htmlFor="collection-select">{t('sharing.selectCollection')}</label>
          <select
            id="collection-select"
            value={selectedCollectionId}
            onChange={(e) => setSelectedCollectionId(e.target.value)}
          >
            <option value="">{t('sharing.chooseCollection')}</option>
            {collections.map((col) => (
              <option key={col.id} value={col.id}>
                {col.displayName || col.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {activeTab === 'owd' && renderOwdTab()}
      {activeTab === 'rules' && renderRulesTab()}
      {activeTab === 'groups' && renderGroupsTab()}

      {showRuleModal && renderRuleModal()}
      {showGroupModal && renderGroupModal()}
    </div>
  )

  function renderOwdTab() {
    if (!selectedCollectionId) {
      return (
        <div className={styles.empty}>
          <p>{t('sharing.selectCollectionFirst')}</p>
        </div>
      )
    }

    return (
      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>{t('sharing.owdSettings')}</h2>
        <div className={styles.owdCard}>
          <div className={styles.owdField}>
            <label htmlFor="internal-access">{t('sharing.internalAccess')}</label>
            <select
              id="internal-access"
              value={owd?.internalAccess || 'PUBLIC_READ_WRITE'}
              onChange={(e) => handleOwdChange(e.target.value)}
            >
              <option value="PUBLIC_READ_WRITE">{t('sharing.publicReadWrite')}</option>
              <option value="PUBLIC_READ">{t('sharing.publicRead')}</option>
              <option value="PRIVATE">{t('sharing.private')}</option>
            </select>
          </div>
          <div className={styles.owdField}>
            <label>{t('sharing.externalAccess')}</label>
            <span>{owd?.externalAccess || 'PRIVATE'}</span>
          </div>
        </div>
      </div>
    )
  }

  function renderRulesTab() {
    if (!selectedCollectionId) {
      return (
        <div className={styles.empty}>
          <p>{t('sharing.selectCollectionFirst')}</p>
        </div>
      )
    }

    return (
      <div className={styles.section}>
        <div className={styles.header}>
          <h2 className={styles.sectionTitle}>{t('sharing.sharingRules')}</h2>
          <button
            className={`${styles.btn} ${styles.btnPrimary}`}
            onClick={() => setShowRuleModal(true)}
          >
            {t('sharing.createRule')}
          </button>
        </div>

        {rules.length === 0 ? (
          <div className={styles.empty}>
            <p>{t('sharing.noRules')}</p>
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{t('sharing.ruleName')}</th>
                <th>{t('sharing.ruleType')}</th>
                <th>{t('sharing.sharedTo')}</th>
                <th>{t('sharing.accessLevel')}</th>
                <th>{t('sharing.status')}</th>
                <th>{t('sharing.ruleActions')}</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td>{rule.name}</td>
                  <td>{rule.ruleType}</td>
                  <td>
                    {rule.sharedToType}: {rule.sharedTo}
                  </td>
                  <td>{rule.accessLevel}</td>
                  <td>
                    <span
                      className={`${styles.badge} ${rule.active ? styles.badgeActive : styles.badgeInactive}`}
                    >
                      {rule.active ? t('sharing.active') : t('sharing.inactive')}
                    </span>
                  </td>
                  <td>
                    <div className={styles.actions}>
                      <button
                        className={`${styles.btn} ${styles.btnDanger} ${styles.btnSmall}`}
                        onClick={() => deleteRuleMutation.mutate(rule.id)}
                      >
                        {t('sharing.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    )
  }

  function renderGroupsTab() {
    return (
      <div className={styles.section}>
        <div className={styles.header}>
          <h2 className={styles.sectionTitle}>{t('sharing.userGroups')}</h2>
          <button
            className={`${styles.btn} ${styles.btnPrimary}`}
            onClick={() => setShowGroupModal(true)}
          >
            {t('sharing.createGroup')}
          </button>
        </div>

        {groupsLoading ? (
          <LoadingSpinner />
        ) : groups.length === 0 ? (
          <div className={styles.empty}>
            <p>{t('sharing.noGroups')}</p>
          </div>
        ) : (
          <div className={styles.groupList}>
            {groups.map((group) => (
              <div key={group.id} className={styles.groupCard}>
                <h4>{group.name}</h4>
                <p>{group.description || t('sharing.noDescription')}</p>
                <div className={styles.groupMeta}>
                  {t('sharing.groupType')}: {group.groupType} | {t('sharing.members')}:{' '}
                  {group.memberIds?.length || 0}
                </div>
                <div className={styles.groupActions}>
                  <button
                    className={`${styles.btn} ${styles.btnDanger} ${styles.btnSmall}`}
                    onClick={() => deleteGroupMutation.mutate(group.id)}
                  >
                    {t('sharing.delete')}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    )
  }

  function renderRuleModal() {
    return (
      <div className={styles.modal}>
        <div
          className={styles.modalBackdrop}
          onClick={() => setShowRuleModal(false)}
          role="presentation"
        />
        <div className={styles.modalContent} role="dialog" aria-modal="true">
          <h3 className={styles.modalTitle}>{t('sharing.createRule')}</h3>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              const formData = new FormData(e.currentTarget)
              createRuleMutation.mutate({
                collectionId: selectedCollectionId,
                name: formData.get('name') as string,
                ruleType: formData.get('ruleType') as string,
                sharedTo: formData.get('sharedTo') as string,
                sharedToType: formData.get('sharedToType') as string,
                accessLevel: formData.get('accessLevel') as string,
              })
            }}
          >
            <div className={styles.formGroup}>
              <label htmlFor="rule-name">{t('sharing.ruleName')}</label>
              <input id="rule-name" name="name" required />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="rule-type">{t('sharing.ruleType')}</label>
              <select id="rule-type" name="ruleType" required>
                <option value="OWNER_BASED">{t('sharing.ownerBased')}</option>
                <option value="CRITERIA_BASED">{t('sharing.criteriaBased')}</option>
              </select>
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="shared-to">{t('sharing.sharedTo')}</label>
              <input id="shared-to" name="sharedTo" required />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="shared-to-type">{t('sharing.sharedToType')}</label>
              <select id="shared-to-type" name="sharedToType" required>
                <option value="ROLE">{t('sharing.role')}</option>
                <option value="GROUP">{t('sharing.group')}</option>
              </select>
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="access-level">{t('sharing.accessLevel')}</label>
              <select id="access-level" name="accessLevel" required>
                <option value="READ">{t('sharing.readOnly')}</option>
                <option value="READ_WRITE">{t('sharing.readWrite')}</option>
              </select>
            </div>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={`${styles.btn} ${styles.btnSecondary}`}
                onClick={() => setShowRuleModal(false)}
              >
                {t('common.cancel')}
              </button>
              <button type="submit" className={`${styles.btn} ${styles.btnPrimary}`}>
                {t('common.create')}
              </button>
            </div>
          </form>
        </div>
      </div>
    )
  }

  function renderGroupModal() {
    return (
      <div className={styles.modal}>
        <div
          className={styles.modalBackdrop}
          onClick={() => setShowGroupModal(false)}
          role="presentation"
        />
        <div className={styles.modalContent} role="dialog" aria-modal="true">
          <h3 className={styles.modalTitle}>{t('sharing.createGroup')}</h3>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              const formData = new FormData(e.currentTarget)
              createGroupMutation.mutate({
                name: formData.get('name') as string,
                description: (formData.get('description') as string) || undefined,
              })
            }}
          >
            <div className={styles.formGroup}>
              <label htmlFor="group-name">{t('sharing.groupName')}</label>
              <input id="group-name" name="name" required />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="group-desc">{t('sharing.groupDescription')}</label>
              <textarea id="group-desc" name="description" />
            </div>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={`${styles.btn} ${styles.btnSecondary}`}
                onClick={() => setShowGroupModal(false)}
              >
                {t('common.cancel')}
              </button>
              <button type="submit" className={`${styles.btn} ${styles.btnPrimary}`}>
                {t('common.create')}
              </button>
            </div>
          </form>
        </div>
      </div>
    )
  }
}

export default SharingSettingsPage
