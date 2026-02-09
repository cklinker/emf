/**
 * RecordActionsBar Component
 *
 * Provides a complete actions bar for record detail pages with:
 * - Back navigation button
 * - Primary edit button
 * - Favorite toggle button
 * - Dropdown menu with clone, submit for approval, and delete actions
 * - Approval status indicator pill
 *
 * Supports keyboard navigation, click-outside closing, and i18n.
 */

import React, { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowLeft, Star, ChevronDown } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useToast } from '../../components'
import type { ApiClient } from '../../services/apiClient'
import styles from './RecordActionsBar.module.css'

/**
 * Approval instance interface for tracking approval status
 */
interface ApprovalInstance {
  id: string
  tenantId: string
  collectionId: string
  recordId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | string
}

/**
 * Props for the RecordActionsBar component
 */
export interface RecordActionsBarProps {
  /** Collection name for the current record */
  collectionName: string
  /** ID of the current record */
  recordId: string
  /** Callback when edit button is clicked */
  onEdit: () => void
  /** Callback when delete action is selected */
  onDelete: () => void
  /** Callback when back button is clicked */
  onBack: () => void
  /** Whether this record is currently favorited */
  isFavorite: boolean
  /** Callback to toggle favorite status */
  onToggleFavorite: () => void
  /** API client instance for making requests */
  apiClient: ApiClient
}

/**
 * RecordActionsBar Component
 *
 * Renders a toolbar with back, edit, favorite, and dropdown actions
 * for a record detail page. Includes approval status display and
 * submission capability.
 */
export function RecordActionsBar({
  collectionName,
  recordId,
  onEdit,
  onDelete,
  onBack,
  isFavorite,
  onToggleFavorite,
  apiClient,
}: RecordActionsBarProps): React.ReactElement {
  const navigate = useNavigate()
  const { t } = useI18n()
  const { showToast } = useToast()

  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const menuItemsRef = useRef<(HTMLButtonElement | null)[]>([])
  const [focusedIndex, setFocusedIndex] = useState(-1)

  // Number of menu items (Clone, Submit for Approval, Delete)
  const MENU_ITEM_COUNT = 3

  // Close dropdown on click outside
  useEffect(() => {
    if (!dropdownOpen) return

    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
        setFocusedIndex(-1)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [dropdownOpen])

  // Focus first item when dropdown opens
  useEffect(() => {
    if (dropdownOpen && menuItemsRef.current[0]) {
      menuItemsRef.current[0].focus()
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFocusedIndex(0)
    }
  }, [dropdownOpen])

  // Toggle dropdown
  const handleToggleDropdown = useCallback(() => {
    setDropdownOpen((prev) => {
      if (prev) {
        setFocusedIndex(-1)
      }
      return !prev
    })
  }, [])

  // Handle keyboard navigation in dropdown
  const handleDropdownKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      switch (event.key) {
        case 'Escape':
          event.preventDefault()
          setDropdownOpen(false)
          setFocusedIndex(-1)
          toggleRef.current?.focus()
          break
        case 'ArrowDown':
          event.preventDefault()
          setFocusedIndex((prev) => {
            const next = prev < MENU_ITEM_COUNT - 1 ? prev + 1 : 0
            menuItemsRef.current[next]?.focus()
            return next
          })
          break
        case 'ArrowUp':
          event.preventDefault()
          setFocusedIndex((prev) => {
            const next = prev > 0 ? prev - 1 : MENU_ITEM_COUNT - 1
            menuItemsRef.current[next]?.focus()
            return next
          })
          break
        case 'Tab':
          setDropdownOpen(false)
          setFocusedIndex(-1)
          break
        default:
          break
      }
    },
    [MENU_ITEM_COUNT]
  )

  // Handle toggle button keyboard
  const handleToggleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'ArrowDown' && !dropdownOpen) {
        event.preventDefault()
        setDropdownOpen(true)
      } else if (event.key === 'Escape' && dropdownOpen) {
        event.preventDefault()
        setDropdownOpen(false)
        setFocusedIndex(-1)
      }
    },
    [dropdownOpen]
  )

  // Clone action
  const handleClone = useCallback(() => {
    setDropdownOpen(false)
    setFocusedIndex(-1)
    navigate(`/resources/${collectionName}/new?clone=${recordId}`)
  }, [navigate, collectionName, recordId])

  // Submit for approval mutation
  const submitApprovalMutation = useMutation({
    mutationFn: async () => {
      const params = new URLSearchParams({
        tenantId: 'default',
        collectionId: collectionName,
        recordId: recordId,
        processId: '',
        userId: 'system',
      })
      return apiClient.post(`/control/approvals/instances/submit?${params.toString()}`, {})
    },
    onSuccess: () => {
      showToast(t('recordActions.approvalSubmitted'), 'success')
    },
    onError: () => {
      showToast(t('recordActions.approvalError'), 'error')
    },
  })

  // Submit for approval action
  const handleSubmitForApproval = useCallback(() => {
    setDropdownOpen(false)
    setFocusedIndex(-1)
    submitApprovalMutation.mutate()
  }, [submitApprovalMutation])

  // Delete action
  const handleDelete = useCallback(() => {
    setDropdownOpen(false)
    setFocusedIndex(-1)
    onDelete()
  }, [onDelete])

  // Query approval status
  const { data: approvalInstances } = useQuery({
    queryKey: ['approval-instances', collectionName, recordId],
    queryFn: async () => {
      const result = await apiClient.get<ApprovalInstance[]>(
        '/control/approvals/instances?tenantId=default'
      )
      return result
    },
    enabled: !!collectionName && !!recordId,
  })

  // Find approval instance for this record
  const recordApproval = Array.isArray(approvalInstances)
    ? approvalInstances.find((instance) => instance.recordId === recordId)
    : undefined

  // Determine approval pill style
  const getApprovalPillClass = (status: string): string => {
    switch (status) {
      case 'PENDING':
        return styles.approvalPillPending
      case 'APPROVED':
        return styles.approvalPillApproved
      case 'REJECTED':
        return styles.approvalPillRejected
      default:
        return styles.approvalPillPending
    }
  }

  // Determine approval pill text
  const getApprovalPillText = (status: string): string => {
    switch (status) {
      case 'PENDING':
        return t('recordActions.pendingApproval')
      case 'APPROVED':
        return t('recordActions.approvedStatus')
      case 'REJECTED':
        return t('recordActions.rejectedStatus')
      default:
        return status
    }
  }

  return (
    <div className={styles.actionsBar} data-testid="record-actions-bar">
      {/* Left section - Back button */}
      <div className={styles.leftActions}>
        <button
          type="button"
          className={styles.backButton}
          onClick={onBack}
          aria-label={t('common.back')}
          data-testid="actions-back-button"
        >
          <ArrowLeft size={16} /> {t('common.back')}
        </button>

        {/* Approval status pill */}
        {recordApproval && (
          <span
            className={getApprovalPillClass(recordApproval.status)}
            data-testid="approval-status-pill"
          >
            {getApprovalPillText(recordApproval.status)}
          </span>
        )}
      </div>

      {/* Right section - Edit, Favorite, Dropdown */}
      <div className={styles.rightActions}>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={onEdit}
          aria-label={t('recordActions.edit')}
          data-testid="actions-edit-button"
        >
          {t('recordActions.edit')}
        </button>

        <button
          type="button"
          className={styles.favoriteButton}
          onClick={onToggleFavorite}
          aria-label={isFavorite ? t('favorites.remove') : t('favorites.add')}
          aria-pressed={isFavorite}
          data-testid="actions-favorite-button"
          title={isFavorite ? t('favorites.remove') : t('favorites.add')}
        >
          <Star size={16} fill={isFavorite ? 'currentColor' : 'none'} />
        </button>

        {/* Dropdown menu */}
        <div className={styles.dropdownContainer} ref={dropdownRef}>
          <button
            ref={toggleRef}
            type="button"
            className={styles.dropdownToggle}
            onClick={handleToggleDropdown}
            onKeyDown={handleToggleKeyDown}
            aria-haspopup="true"
            aria-expanded={dropdownOpen}
            aria-label={t('recordActions.moreActions')}
            data-testid="actions-dropdown-toggle"
          >
            <ChevronDown size={16} />
          </button>

          {dropdownOpen && (
            <div
              className={styles.dropdownMenu}
              role="menu"
              tabIndex={-1}
              aria-label={t('recordActions.moreActions')}
              onKeyDown={handleDropdownKeyDown}
              data-testid="actions-dropdown-menu"
            >
              {/* Clone */}
              <button
                ref={(el) => {
                  menuItemsRef.current[0] = el
                }}
                type="button"
                className={styles.dropdownItem}
                role="menuitem"
                tabIndex={focusedIndex === 0 ? 0 : -1}
                onClick={handleClone}
                data-testid="actions-clone"
              >
                {t('recordActions.clone')}
              </button>

              <div className={styles.dropdownDivider} role="separator" />

              {/* Submit for Approval */}
              <button
                ref={(el) => {
                  menuItemsRef.current[1] = el
                }}
                type="button"
                className={styles.dropdownItem}
                role="menuitem"
                tabIndex={focusedIndex === 1 ? 0 : -1}
                onClick={handleSubmitForApproval}
                data-testid="actions-submit-approval"
              >
                {t('recordActions.submitForApproval')}
              </button>

              <div className={styles.dropdownDivider} role="separator" />

              {/* Delete */}
              <button
                ref={(el) => {
                  menuItemsRef.current[2] = el
                }}
                type="button"
                className={styles.dropdownItemDanger}
                role="menuitem"
                tabIndex={focusedIndex === 2 ? 0 : -1}
                onClick={handleDelete}
                data-testid="actions-delete"
              >
                {t('recordActions.delete')}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default RecordActionsBar
