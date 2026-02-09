/**
 * MenuBuilderPage Component
 *
 * Menu configuration editor for creating and editing navigation menus.
 * Provides a list of all menus, a tree view editor for menu items,
 * drag-and-drop reordering, and nested menu item support.
 *
 * Requirements:
 * - 8.1: Display list of all menus
 * - 8.2: Create new menu action
 * - 8.3: Menu editor with tree view for items
 * - 8.4: Support drag-and-drop reordering of menu items
 * - 8.5: Support nested menu items (submenus)
 * - 8.6: Configure label, path, icon, access policies
 * - 8.7: Save menu configuration via API
 * - 8.8: Display menu preview
 */

import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './MenuBuilderPage.module.css'

/**
 * UI Menu interface matching the API response
 */
export interface UIMenu {
  id: string
  name: string
  items: UIMenuItem[]
  createdAt: string
  updatedAt: string
}

/**
 * UI Menu Item interface
 */
export interface UIMenuItem {
  id: string
  label: string
  path?: string
  icon?: string
  order: number
  children?: UIMenuItem[]
  policies?: string[]
}

/**
 * Form data for creating/editing a menu
 */
interface MenuFormData {
  name: string
}

/**
 * Form data for creating/editing a menu item
 */
interface MenuItemFormData {
  label: string
  path: string
  icon: string
  policies: string[]
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string
  label?: string
  path?: string
}

/**
 * Props for MenuBuilderPage component
 */
export interface MenuBuilderPageProps {
  /** Optional menu ID for editing an existing menu */
  menuId?: string
  /** Optional test ID for testing */
  testId?: string
}

// API functions using apiClient
async function fetchMenus(apiClient: any): Promise<UIMenu[]> {
  return apiClient.get('/control/ui/menus')
}

async function fetchMenu(apiClient: any, id: string): Promise<UIMenu> {
  return apiClient.get(`/control/ui/menus/${id}`)
}

async function createMenu(apiClient: any, data: Partial<UIMenu>): Promise<UIMenu> {
  return apiClient.post('/control/ui/menus', data)
}

async function updateMenu(apiClient: any, id: string, data: Partial<UIMenu>): Promise<UIMenu> {
  return apiClient.put(`/control/ui/menus/${id}`, data)
}

async function deleteMenu(apiClient: any, id: string): Promise<void> {
  return apiClient.delete(`/control/ui/menus/${id}`)
}

/**
 * Policy interface for access control
 */
interface Policy {
  id: string
  name: string
  description?: string
}

/**
 * Fetch available policies for access control
 */
async function fetchPolicies(apiClient: any): Promise<Policy[]> {
  try {
    return await apiClient.get('/control/policies')
  } catch {
    // Return empty array if policies endpoint fails - policies are optional
    return []
  }
}

/**
 * Validate menu form data
 */
function validateMenuForm(data: MenuFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}

  if (!data.name.trim()) {
    errors.name = t('builder.menus.validation.nameRequired')
  } else if (data.name.length > 50) {
    errors.name = t('builder.menus.validation.nameTooLong')
  } else if (!/^[a-z][a-z0-9_]*$/.test(data.name)) {
    errors.name = t('builder.menus.validation.nameFormat')
  }

  return errors
}

/**
 * Validate menu item form data
 */
function validateMenuItemForm(data: MenuItemFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}

  if (!data.label.trim()) {
    errors.label = t('builder.menus.validation.labelRequired')
  } else if (data.label.length > 100) {
    errors.label = t('builder.menus.validation.labelTooLong')
  }

  if (data.path && !data.path.startsWith('/')) {
    errors.path = t('builder.menus.validation.pathFormat')
  }

  return errors
}

/**
 * Generate a unique ID for menu items
 */
function generateId(): string {
  return `item_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

/**
 * Available icons for menu items
 */
const AVAILABLE_ICONS = [
  { value: '', label: 'None' },
  { value: 'home', label: '🏠 Home' },
  { value: 'dashboard', label: '📊 Dashboard' },
  { value: 'settings', label: '⚙️ Settings' },
  { value: 'users', label: '👥 Users' },
  { value: 'folder', label: '📁 Folder' },
  { value: 'document', label: '📄 Document' },
  { value: 'chart', label: '📈 Chart' },
  { value: 'lock', label: '🔒 Lock' },
  { value: 'key', label: '🔑 Key' },
  { value: 'database', label: '🗄️ Database' },
  { value: 'code', label: '💻 Code' },
]

/**
 * Menu Form Component - for creating/editing menu configuration
 */
interface MenuFormProps {
  menu?: UIMenu
  onSubmit: (data: MenuFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function MenuForm({ menu, onSubmit, onCancel, isSubmitting }: MenuFormProps): React.ReactElement {
  const { t } = useI18n()
  const isEditing = !!menu
  const [formData, setFormData] = useState<MenuFormData>({
    name: menu?.name ?? '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof MenuFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof MenuFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateMenuForm(formData, t)
      if (validationErrors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field as keyof FormErrors] }))
      }
    },
    [formData, t]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateMenuForm(formData, t)
      setErrors(validationErrors)
      setTouched({ name: true })

      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit, t]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? t('builder.menus.editMenu') : t('builder.menus.createMenu')

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="menu-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="menu-form-title"
        data-testid="menu-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="menu-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="menu-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="menu-name" className={styles.formLabel}>
                {t('builder.menus.menuName')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="menu-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('builder.menus.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'menu-name-error' : 'menu-name-hint'}
                disabled={isSubmitting}
                data-testid="menu-name-input"
              />
              <span id="menu-name-hint" className={styles.formHint}>
                {t('builder.menus.nameHint')}
              </span>
              {touched.name && errors.name && (
                <span id="menu-name-error" className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="menu-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="menu-form-submit"
              >
                {isSubmitting ? t('common.loading') : t('common.save')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

/**
 * Menu Item Form Component - for creating/editing menu items
 */
interface MenuItemFormProps {
  item?: UIMenuItem
  onSubmit: (data: MenuItemFormData) => void
  onCancel: () => void
  isSubmitting: boolean
  availablePolicies: Policy[]
}

function MenuItemForm({
  item,
  onSubmit,
  onCancel,
  isSubmitting,
  availablePolicies,
}: MenuItemFormProps): React.ReactElement {
  const { t } = useI18n()
  const isEditing = !!item
  const [formData, setFormData] = useState<MenuItemFormData>({
    label: item?.label ?? '',
    path: item?.path ?? '',
    icon: item?.icon ?? '',
    policies: item?.policies ?? [],
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const labelInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    labelInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof MenuItemFormData, value: string | string[]) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof MenuItemFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateMenuItemForm(formData, t)
      if (validationErrors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field as keyof FormErrors] }))
      }
    },
    [formData, t]
  )

  const handlePolicyToggle = useCallback((policyId: string) => {
    setFormData((prev) => {
      const currentPolicies = prev.policies || []
      const newPolicies = currentPolicies.includes(policyId)
        ? currentPolicies.filter((id) => id !== policyId)
        : [...currentPolicies, policyId]
      return { ...prev, policies: newPolicies }
    })
  }, [])

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateMenuItemForm(formData, t)
      setErrors(validationErrors)
      setTouched({ label: true, path: true })

      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit, t]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? t('builder.menus.editItem') : t('builder.menus.addItem')

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="menu-item-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="menu-item-form-title"
        data-testid="menu-item-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="menu-item-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="menu-item-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="item-label" className={styles.formLabel}>
                {t('builder.menus.itemLabel')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={labelInputRef}
                id="item-label"
                type="text"
                className={`${styles.formInput} ${touched.label && errors.label ? styles.hasError : ''}`}
                value={formData.label}
                onChange={(e) => handleChange('label', e.target.value)}
                onBlur={() => handleBlur('label')}
                placeholder={t('builder.menus.labelPlaceholder')}
                aria-required="true"
                aria-invalid={touched.label && !!errors.label}
                aria-describedby={errors.label ? 'item-label-error' : undefined}
                disabled={isSubmitting}
                data-testid="item-label-input"
              />
              {touched.label && errors.label && (
                <span id="item-label-error" className={styles.formError} role="alert">
                  {errors.label}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="item-path" className={styles.formLabel}>
                {t('builder.menus.itemPath')}
              </label>
              <input
                id="item-path"
                type="text"
                className={`${styles.formInput} ${touched.path && errors.path ? styles.hasError : ''}`}
                value={formData.path}
                onChange={(e) => handleChange('path', e.target.value)}
                onBlur={() => handleBlur('path')}
                placeholder={t('builder.menus.pathPlaceholder')}
                aria-invalid={touched.path && !!errors.path}
                aria-describedby={errors.path ? 'item-path-error' : 'item-path-hint'}
                disabled={isSubmitting}
                data-testid="item-path-input"
              />
              <span id="item-path-hint" className={styles.formHint}>
                {t('builder.menus.pathHint')}
              </span>
              {touched.path && errors.path && (
                <span id="item-path-error" className={styles.formError} role="alert">
                  {errors.path}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="item-icon" className={styles.formLabel}>
                {t('builder.menus.itemIcon')}
              </label>
              <select
                id="item-icon"
                className={styles.formSelect}
                value={formData.icon}
                onChange={(e) => handleChange('icon', e.target.value)}
                disabled={isSubmitting}
                data-testid="item-icon-select"
              >
                {AVAILABLE_ICONS.map((icon) => (
                  <option key={icon.value} value={icon.value}>
                    {icon.label}
                  </option>
                ))}
              </select>
            </div>

            <div className={styles.formGroup}>
              <label className={styles.formLabel}>{t('builder.menus.accessPolicies')}</label>
              <div className={styles.policiesContainer} data-testid="policies-container">
                {availablePolicies.length === 0 ? (
                  <p className={styles.noPolicies} data-testid="no-policies-message">
                    {t('builder.menus.noPolicies')}
                  </p>
                ) : (
                  <div
                    className={styles.policiesList}
                    role="group"
                    aria-label={t('builder.menus.accessPolicies')}
                  >
                    {availablePolicies.map((policy) => (
                      <label
                        key={policy.id}
                        className={styles.policyCheckbox}
                        data-testid={`policy-checkbox-${policy.id}`}
                      >
                        <input
                          type="checkbox"
                          checked={formData.policies.includes(policy.id)}
                          onChange={() => handlePolicyToggle(policy.id)}
                          disabled={isSubmitting}
                          data-testid={`policy-input-${policy.id}`}
                        />
                        <span className={styles.policyName}>{policy.name}</span>
                        {policy.description && (
                          <span className={styles.policyDescription}>{policy.description}</span>
                        )}
                      </label>
                    ))}
                  </div>
                )}
              </div>
              <span className={styles.formHint}>{t('builder.menus.accessPoliciesHint')}</span>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="menu-item-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="menu-item-form-submit"
              >
                {isSubmitting ? t('common.loading') : t('common.save')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

/**
 * Menu Tree Item Component - renders a single menu item with children
 * Supports drag-and-drop reordering (Requirement 8.4)
 * Supports nested items (Requirement 8.5)
 */
interface MenuTreeItemProps {
  item: UIMenuItem
  depth: number
  onEdit: (item: UIMenuItem) => void
  onDelete: (itemId: string) => void
  onAddChild: (parentId: string) => void
  onDragStart: (e: React.DragEvent, itemId: string) => void
  onDragOver: (e: React.DragEvent, itemId: string) => void
  onDrop: (e: React.DragEvent, targetId: string) => void
  onDragEnd: () => void
  dragOverId: string | null
  isDragging: boolean
}

function MenuTreeItem({
  item,
  depth,
  onEdit,
  onDelete,
  onAddChild,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
  dragOverId,
  isDragging,
}: MenuTreeItemProps): React.ReactElement {
  const { t } = useI18n()
  const [isExpanded, setIsExpanded] = useState(true)
  const hasChildren = item.children && item.children.length > 0
  const isDragOver = dragOverId === item.id

  const getIconEmoji = (iconName?: string): string => {
    const iconMap: Record<string, string> = {
      home: '🏠',
      dashboard: '📊',
      settings: '⚙️',
      users: '👥',
      folder: '📁',
      document: '📄',
      chart: '📈',
      lock: '🔒',
      key: '🔑',
      database: '🗄️',
      code: '💻',
    }
    return iconMap[iconName || ''] || '📌'
  }

  return (
    <div className={styles.treeItemContainer}>
      <div
        className={`${styles.treeItem} ${isDragOver ? styles.treeItemDragOver : ''} ${isDragging ? styles.treeItemDragging : ''}`}
        style={{ paddingLeft: `${depth * 24 + 8}px` }}
        draggable
        onDragStart={(e) => onDragStart(e, item.id)}
        onDragOver={(e) => onDragOver(e, item.id)}
        onDrop={(e) => onDrop(e, item.id)}
        onDragEnd={onDragEnd}
        data-testid={`tree-item-${item.id}`}
      >
        <div className={styles.treeItemContent}>
          {hasChildren && (
            <button
              type="button"
              className={styles.expandButton}
              onClick={() => setIsExpanded(!isExpanded)}
              aria-expanded={isExpanded}
              aria-label={isExpanded ? 'Collapse' : 'Expand'}
              data-testid={`expand-button-${item.id}`}
            >
              {isExpanded ? '▼' : '▶'}
            </button>
          )}
          {!hasChildren && <span className={styles.expandPlaceholder} />}
          <span className={styles.itemIcon}>{getIconEmoji(item.icon)}</span>
          <span className={styles.itemLabel}>{item.label}</span>
          {item.path && (
            <span className={styles.itemPath}>
              <code>{item.path}</code>
            </span>
          )}
        </div>
        <div className={styles.treeItemActions}>
          <button
            type="button"
            className={styles.treeActionButton}
            onClick={() => onAddChild(item.id)}
            aria-label={`Add child to ${item.label}`}
            title={t('builder.menus.addChild')}
            data-testid={`add-child-button-${item.id}`}
          >
            +
          </button>
          <button
            type="button"
            className={styles.treeActionButton}
            onClick={() => onEdit(item)}
            aria-label={`Edit ${item.label}`}
            title={t('common.edit')}
            data-testid={`edit-item-button-${item.id}`}
          >
            ✏️
          </button>
          <button
            type="button"
            className={`${styles.treeActionButton} ${styles.deleteActionButton}`}
            onClick={() => onDelete(item.id)}
            aria-label={`Delete ${item.label}`}
            title={t('common.delete')}
            data-testid={`delete-item-button-${item.id}`}
          >
            🗑️
          </button>
        </div>
      </div>
      {hasChildren && isExpanded && (
        <div className={styles.treeChildren} data-testid={`children-${item.id}`}>
          {item.children!.map((child) => (
            <MenuTreeItem
              key={child.id}
              item={child}
              depth={depth + 1}
              onEdit={onEdit}
              onDelete={onDelete}
              onAddChild={onAddChild}
              onDragStart={onDragStart}
              onDragOver={onDragOver}
              onDrop={onDrop}
              onDragEnd={onDragEnd}
              dragOverId={dragOverId}
              isDragging={isDragging}
            />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Menu Tree View Component - displays menu items in a tree structure
 * Requirement 8.3: Menu editor with tree view for items
 */
interface MenuTreeViewProps {
  items: UIMenuItem[]
  onEdit: (item: UIMenuItem) => void
  onDelete: (itemId: string) => void
  onAddChild: (parentId: string | null) => void
  onReorder: (draggedId: string, targetId: string) => void
}

function MenuTreeView({
  items,
  onEdit,
  onDelete,
  onAddChild,
  onReorder,
}: MenuTreeViewProps): React.ReactElement {
  const { t } = useI18n()
  const [draggedItemId, setDraggedItemId] = useState<string | null>(null)
  const [dragOverId, setDragOverId] = useState<string | null>(null)

  const handleDragStart = useCallback((e: React.DragEvent, itemId: string) => {
    e.dataTransfer.setData('text/plain', itemId)
    e.dataTransfer.effectAllowed = 'move'
    setDraggedItemId(itemId)
  }, [])

  const handleDragOver = useCallback(
    (e: React.DragEvent, itemId: string) => {
      e.preventDefault()
      e.dataTransfer.dropEffect = 'move'
      if (draggedItemId !== itemId) {
        setDragOverId(itemId)
      }
    },
    [draggedItemId]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent, targetId: string) => {
      e.preventDefault()
      const draggedId = e.dataTransfer.getData('text/plain')
      if (draggedId && draggedId !== targetId) {
        onReorder(draggedId, targetId)
      }
      setDraggedItemId(null)
      setDragOverId(null)
    },
    [onReorder]
  )

  const handleDragEnd = useCallback(() => {
    setDraggedItemId(null)
    setDragOverId(null)
  }, [])

  const handleAddChildWrapper = useCallback(
    (parentId: string) => {
      onAddChild(parentId)
    },
    [onAddChild]
  )

  if (items.length === 0) {
    return (
      <div className={styles.treeEmpty} data-testid="tree-empty">
        <p>{t('builder.menus.noItems')}</p>
        <p className={styles.treeEmptyHint}>{t('builder.menus.addItemHint')}</p>
      </div>
    )
  }

  return (
    <div
      className={styles.treeView}
      role="tree"
      aria-label={t('builder.menus.menuItems')}
      data-testid="menu-tree-view"
    >
      {items.map((item) => (
        <MenuTreeItem
          key={item.id}
          item={item}
          depth={0}
          onEdit={onEdit}
          onDelete={onDelete}
          onAddChild={handleAddChildWrapper}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
          onDragEnd={handleDragEnd}
          dragOverId={dragOverId}
          isDragging={draggedItemId === item.id}
        />
      ))}
    </div>
  )
}

/**
 * Menu Preview Component - displays the menu as it will appear
 * Requirement 8.8: Display menu preview
 */
interface MenuPreviewProps {
  menu: UIMenu | null
  items: UIMenuItem[]
  availablePolicies: Policy[]
}

function MenuPreview({ menu, items, availablePolicies }: MenuPreviewProps): React.ReactElement {
  const { t } = useI18n()

  const getIconEmoji = (iconName?: string): string => {
    const iconMap: Record<string, string> = {
      home: '🏠',
      dashboard: '📊',
      settings: '⚙️',
      users: '👥',
      folder: '📁',
      document: '📄',
      chart: '📈',
      lock: '🔒',
      key: '🔑',
      database: '🗄️',
      code: '💻',
    }
    return iconMap[iconName || ''] || '📌'
  }

  const getPolicyNames = (policyIds?: string[]): string[] => {
    if (!policyIds || policyIds.length === 0) return []
    return policyIds
      .map((id) => availablePolicies.find((p) => p.id === id)?.name)
      .filter((name): name is string => !!name)
  }

  const renderPreviewItem = (item: UIMenuItem, depth: number = 0): React.ReactNode => {
    const policyNames = getPolicyNames(item.policies)

    return (
      <div
        key={item.id}
        className={styles.previewItem}
        style={{ paddingLeft: `${depth * 16}px` }}
        data-testid={`preview-item-${item.id}`}
      >
        <div className={styles.previewItemMain}>
          <span className={styles.previewIcon}>{getIconEmoji(item.icon)}</span>
          <span className={styles.previewLabel}>{item.label}</span>
          {item.path && (
            <span className={styles.previewPath}>
              <code>{item.path}</code>
            </span>
          )}
        </div>
        {policyNames.length > 0 && (
          <div className={styles.previewPolicies} data-testid={`preview-policies-${item.id}`}>
            <span className={styles.previewPoliciesIcon}>🔒</span>
            <span className={styles.previewPoliciesText}>{policyNames.join(', ')}</span>
          </div>
        )}
        {item.children && item.children.length > 0 && (
          <div className={styles.previewChildren}>
            {item.children.map((child) => renderPreviewItem(child, depth + 1))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className={styles.preview} data-testid="menu-preview">
      <h3 className={styles.previewTitle}>{t('builder.menus.preview')}</h3>
      <div className={styles.previewContent}>
        {items.length === 0 ? (
          <p className={styles.previewEmpty}>{t('builder.menus.noItems')}</p>
        ) : (
          <nav className={styles.previewNav} aria-label={menu?.name || 'Menu preview'}>
            {items.map((item) => renderPreviewItem(item))}
          </nav>
        )}
      </div>
    </div>
  )
}

/**
 * Helper function to find and update an item in the tree
 */
function updateItemInTree(
  items: UIMenuItem[],
  itemId: string,
  updater: (item: UIMenuItem) => UIMenuItem
): UIMenuItem[] {
  return items.map((item) => {
    if (item.id === itemId) {
      return updater(item)
    }
    if (item.children && item.children.length > 0) {
      return {
        ...item,
        children: updateItemInTree(item.children, itemId, updater),
      }
    }
    return item
  })
}

/**
 * Helper function to remove an item from the tree
 */
function removeItemFromTree(items: UIMenuItem[], itemId: string): UIMenuItem[] {
  return items
    .filter((item) => item.id !== itemId)
    .map((item) => {
      if (item.children && item.children.length > 0) {
        return {
          ...item,
          children: removeItemFromTree(item.children, itemId),
        }
      }
      return item
    })
}

/**
 * Helper function to add a child item to a parent
 */
function addChildToItem(items: UIMenuItem[], parentId: string, newItem: UIMenuItem): UIMenuItem[] {
  return items.map((item) => {
    if (item.id === parentId) {
      return {
        ...item,
        children: [...(item.children || []), newItem],
      }
    }
    if (item.children && item.children.length > 0) {
      return {
        ...item,
        children: addChildToItem(item.children, parentId, newItem),
      }
    }
    return item
  })
}

/**
 * Helper function to find an item in the tree
 */
function findItemInTree(items: UIMenuItem[], itemId: string): UIMenuItem | null {
  for (const item of items) {
    if (item.id === itemId) {
      return item
    }
    if (item.children && item.children.length > 0) {
      const found = findItemInTree(item.children, itemId)
      if (found) return found
    }
  }
  return null
}

/**
 * Helper function to move an item in the tree (for drag-and-drop)
 */
function moveItemInTree(items: UIMenuItem[], draggedId: string, targetId: string): UIMenuItem[] {
  // Find the dragged item
  const draggedItem = findItemInTree(items, draggedId)
  if (!draggedItem) return items

  // Remove the dragged item from its current position
  let newItems = removeItemFromTree(items, draggedId)

  // Find the target item and insert the dragged item after it
  const insertAfter = (arr: UIMenuItem[], targetId: string, item: UIMenuItem): UIMenuItem[] => {
    const result: UIMenuItem[] = []
    for (const current of arr) {
      result.push(current)
      if (current.id === targetId) {
        result.push(item)
      } else if (current.children && current.children.length > 0) {
        const updatedChildren = insertAfter(current.children, targetId, item)
        if (updatedChildren !== current.children) {
          result[result.length - 1] = { ...current, children: updatedChildren }
        }
      }
    }
    return result
  }

  newItems = insertAfter(newItems, targetId, draggedItem)

  // Update order values
  const updateOrders = (arr: UIMenuItem[]): UIMenuItem[] => {
    return arr.map((item, index) => ({
      ...item,
      order: index,
      children: item.children ? updateOrders(item.children) : undefined,
    }))
  }

  return updateOrders(newItems)
}

/**
 * MenuBuilderPage Component
 *
 * Main page for building and editing navigation menus in the EMF Admin UI.
 * Provides a tree view editor with drag-and-drop reordering and nested item support.
 */
export function MenuBuilderPage({
  menuId,
  testId = 'menu-builder-page',
}: MenuBuilderPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { showToast } = useToast()

  // View mode: 'list' shows all menus, 'editor' shows the menu editor
  const [viewMode, setViewMode] = useState<'list' | 'editor'>(menuId ? 'editor' : 'list')
  const [editingMenuId, setEditingMenuId] = useState<string | null>(menuId ?? null)

  // Form modal state
  const [isMenuFormOpen, setIsMenuFormOpen] = useState(false)
  const [editingMenu, setEditingMenu] = useState<UIMenu | undefined>(undefined)

  // Menu item form state
  const [isItemFormOpen, setIsItemFormOpen] = useState(false)
  const [editingItem, setEditingItem] = useState<UIMenuItem | undefined>(undefined)
  const [parentItemId, setParentItemId] = useState<string | null>(null)

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [menuToDelete, setMenuToDelete] = useState<UIMenu | null>(null)
  const [itemToDelete, setItemToDelete] = useState<string | null>(null)

  // Editor state
  const [menuItems, setMenuItems] = useState<UIMenuItem[]>([])
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)

  // Fetch all menus query - always enabled so we have menu data in editor mode
  const {
    data: menus = [],
    isLoading: isLoadingMenus,
    error: menusError,
    refetch: refetchMenus,
  } = useQuery({
    queryKey: ['ui-menus'],
    queryFn: () => fetchMenus(apiClient),
  })

  // Fetch available policies for access control (Requirement 8.6)
  const { data: availablePolicies = [] } = useQuery({
    queryKey: ['policies'],
    queryFn: () => fetchPolicies(apiClient),
    enabled: viewMode === 'editor',
  })

  // Fetch single menu query for editing - get from the menus list
  const currentMenu = useMemo(() => {
    if (!editingMenuId || !menus) return undefined
    return menus.find((m) => m.id === editingMenuId)
  }, [editingMenuId, menus])

  const isLoadingMenu = isLoadingMenus

  // Update menu items when menu data loads
  useEffect(() => {
    if (currentMenu) {
      setMenuItems(currentMenu.items || [])
      setHasUnsavedChanges(false)
    }
  }, [currentMenu])

  // Create menu mutation
  const createMenuMutation = useMutation({
    mutationFn: (data: Partial<UIMenu>) => createMenu(apiClient, data),
    onSuccess: (newMenu) => {
      queryClient.invalidateQueries({ queryKey: ['ui-menus'] })
      showToast(t('success.created', { item: t('builder.menus.menu') }), 'success')
      handleCloseMenuForm()
      // Open the editor for the new menu
      setEditingMenuId(newMenu.id)
      setMenuItems([])
      setViewMode('editor')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Update menu mutation
  const updateMenuMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<UIMenu> }) =>
      updateMenu(apiClient, id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-menus'] })
      queryClient.invalidateQueries({ queryKey: ['ui-menu', editingMenuId] })
      showToast(t('success.updated', { item: t('builder.menus.menu') }), 'success')
      setHasUnsavedChanges(false)
      handleCloseMenuForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Delete menu mutation
  const deleteMenuMutation = useMutation({
    mutationFn: (id: string) => deleteMenu(apiClient, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-menus'] })
      showToast(t('success.deleted', { item: t('builder.menus.menu') }), 'success')
      setDeleteDialogOpen(false)
      setMenuToDelete(null)
      if (editingMenuId === menuToDelete?.id) {
        setViewMode('list')
        setEditingMenuId(null)
      }
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle create menu action
  const handleCreateMenu = useCallback(() => {
    setEditingMenu(undefined)
    setIsMenuFormOpen(true)
  }, [])

  // Handle edit menu config
  const handleEditMenuConfig = useCallback((menu: UIMenu) => {
    setEditingMenu(menu)
    setIsMenuFormOpen(true)
  }, [])

  // Handle close menu form
  const handleCloseMenuForm = useCallback(() => {
    setIsMenuFormOpen(false)
    setEditingMenu(undefined)
  }, [])

  // Handle menu form submit
  const handleMenuFormSubmit = useCallback(
    (data: MenuFormData) => {
      const menuData: Partial<UIMenu> = {
        name: data.name,
      }

      if (editingMenu) {
        updateMenuMutation.mutate({ id: editingMenu.id, data: menuData })
      } else {
        createMenuMutation.mutate({
          ...menuData,
          items: [],
        })
      }
    },
    [editingMenu, createMenuMutation, updateMenuMutation]
  )

  // Handle delete menu action
  const handleDeleteMenuClick = useCallback((menu: UIMenu) => {
    setMenuToDelete(menu)
    setItemToDelete(null)
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete menu confirmation
  const handleDeleteMenuConfirm = useCallback(() => {
    if (menuToDelete) {
      deleteMenuMutation.mutate(menuToDelete.id)
    } else if (itemToDelete) {
      setMenuItems((prev) => removeItemFromTree(prev, itemToDelete))
      setHasUnsavedChanges(true)
      setDeleteDialogOpen(false)
      setItemToDelete(null)
      showToast(t('success.deleted', { item: t('builder.menus.item') }), 'success')
    }
  }, [menuToDelete, itemToDelete, deleteMenuMutation, showToast, t])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setMenuToDelete(null)
    setItemToDelete(null)
  }, [])

  // Handle opening menu editor
  const handleOpenEditor = useCallback((menu: UIMenu) => {
    setEditingMenuId(menu.id)
    setViewMode('editor')
  }, [])

  // Handle back to list
  const handleBackToList = useCallback(() => {
    if (hasUnsavedChanges) {
      // Could show a confirmation dialog here
    }
    setViewMode('list')
    setEditingMenuId(null)
    setMenuItems([])
    setHasUnsavedChanges(false)
  }, [hasUnsavedChanges])

  // Handle add menu item
  const handleAddItem = useCallback((parentId: string | null) => {
    setParentItemId(parentId)
    setEditingItem(undefined)
    setIsItemFormOpen(true)
  }, [])

  // Handle edit menu item
  const handleEditItem = useCallback((item: UIMenuItem) => {
    setEditingItem(item)
    setParentItemId(null)
    setIsItemFormOpen(true)
  }, [])

  // Handle delete menu item
  const handleDeleteItem = useCallback((itemId: string) => {
    setItemToDelete(itemId)
    setMenuToDelete(null)
    setDeleteDialogOpen(true)
  }, [])

  // Handle close item form
  const handleCloseItemForm = useCallback(() => {
    setIsItemFormOpen(false)
    setEditingItem(undefined)
    setParentItemId(null)
  }, [])

  // Handle item form submit
  const handleItemFormSubmit = useCallback(
    (data: MenuItemFormData) => {
      if (editingItem) {
        // Update existing item (Requirement 8.6: Configure label, path, icon, access policies)
        setMenuItems((prev) =>
          updateItemInTree(prev, editingItem.id, (item) => ({
            ...item,
            label: data.label,
            path: data.path || undefined,
            icon: data.icon || undefined,
            policies: data.policies.length > 0 ? data.policies : undefined,
          }))
        )
      } else {
        // Create new item (Requirement 8.6: Configure label, path, icon, access policies)
        const newItem: UIMenuItem = {
          id: generateId(),
          label: data.label,
          path: data.path || undefined,
          icon: data.icon || undefined,
          order: menuItems.length,
          policies: data.policies.length > 0 ? data.policies : undefined,
        }

        if (parentItemId) {
          // Add as child of parent
          setMenuItems((prev) => addChildToItem(prev, parentItemId, newItem))
        } else {
          // Add to root level
          setMenuItems((prev) => [...prev, newItem])
        }
      }

      setHasUnsavedChanges(true)
      handleCloseItemForm()
      showToast(
        editingItem
          ? t('success.updated', { item: t('builder.menus.item') })
          : t('success.created', { item: t('builder.menus.item') }),
        'success'
      )
    },
    [editingItem, parentItemId, menuItems.length, handleCloseItemForm, showToast, t]
  )

  // Handle reorder (drag-and-drop)
  const handleReorder = useCallback((draggedId: string, targetId: string) => {
    setMenuItems((prev) => moveItemInTree(prev, draggedId, targetId))
    setHasUnsavedChanges(true)
  }, [])

  // Handle save menu
  const handleSaveMenu = useCallback(() => {
    if (!editingMenuId) return
    updateMenuMutation.mutate({
      id: editingMenuId,
      data: { items: menuItems },
    })
  }, [editingMenuId, menuItems, updateMenuMutation])

  const isSubmitting = createMenuMutation.isPending || updateMenuMutation.isPending

  // Render loading state
  if (viewMode === 'list' && isLoadingMenus) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
  if (viewMode === 'list' && menusError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={menusError instanceof Error ? menusError : new Error(t('errors.generic'))}
          onRetry={() => refetchMenus()}
        />
      </div>
    )
  }

  // Render editor view
  if (viewMode === 'editor') {
    return (
      <div className={styles.container} data-testid={testId}>
        <header className={styles.editorHeader}>
          <div className={styles.editorHeaderLeft}>
            <button
              type="button"
              className={styles.backButton}
              onClick={handleBackToList}
              aria-label={t('common.back')}
              data-testid="back-to-list-button"
            >
              ← {t('common.back')}
            </button>
            <h1 className={styles.editorTitle}>
              {currentMenu?.name || t('builder.menus.newMenu')}
              {hasUnsavedChanges && <span className={styles.unsavedIndicator}>*</span>}
            </h1>
          </div>
          <div className={styles.editorHeaderRight}>
            {currentMenu && (
              <button
                type="button"
                className={styles.configButton}
                onClick={() => handleEditMenuConfig(currentMenu)}
                data-testid="edit-config-button"
              >
                {t('builder.menus.editMenu')}
              </button>
            )}
            <button
              type="button"
              className={styles.saveButton}
              onClick={handleSaveMenu}
              disabled={!hasUnsavedChanges || updateMenuMutation.isPending}
              data-testid="save-menu-button"
            >
              {updateMenuMutation.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        </header>

        <div className={styles.editorLayout}>
          <div className={styles.editorMain}>
            <div className={styles.editorToolbar}>
              <button
                type="button"
                className={styles.addItemButton}
                onClick={() => handleAddItem(null)}
                data-testid="add-item-button"
              >
                {t('builder.menus.addItem')}
              </button>
            </div>
            <MenuTreeView
              items={menuItems}
              onEdit={handleEditItem}
              onDelete={handleDeleteItem}
              onAddChild={handleAddItem}
              onReorder={handleReorder}
            />
          </div>
          <MenuPreview
            menu={currentMenu || null}
            items={menuItems}
            availablePolicies={availablePolicies}
          />
        </div>

        {isMenuFormOpen && (
          <MenuForm
            menu={editingMenu}
            onSubmit={handleMenuFormSubmit}
            onCancel={handleCloseMenuForm}
            isSubmitting={isSubmitting}
          />
        )}

        {isItemFormOpen && (
          <MenuItemForm
            item={editingItem}
            onSubmit={handleItemFormSubmit}
            onCancel={handleCloseItemForm}
            isSubmitting={false}
            availablePolicies={availablePolicies}
          />
        )}

        <ConfirmDialog
          open={deleteDialogOpen}
          title={menuToDelete ? t('builder.menus.deleteMenu') : t('builder.menus.deleteItem')}
          message={
            menuToDelete
              ? t('builder.menus.confirmDeleteMenu')
              : t('builder.menus.confirmDeleteItem')
          }
          confirmLabel={t('common.delete')}
          cancelLabel={t('common.cancel')}
          onConfirm={handleDeleteMenuConfirm}
          onCancel={handleDeleteCancel}
          variant="danger"
        />
      </div>
    )
  }

  // Render list view
  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>{t('builder.menus.title')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreateMenu}
          aria-label={t('builder.menus.createMenu')}
          data-testid="create-menu-button"
        >
          {t('builder.menus.createMenu')}
        </button>
      </header>

      {menus.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('builder.menus.title')}
            data-testid="menus-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('builder.menus.menuName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('builder.menus.itemCount')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.updated')}
                </th>
                <th role="columnheader" scope="col">
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {menus.map((menu, index) => (
                <tr
                  key={menu.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`menu-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    <button
                      type="button"
                      className={styles.nameLink}
                      onClick={() => handleOpenEditor(menu)}
                      data-testid={`menu-name-${index}`}
                    >
                      {menu.name}
                    </button>
                  </td>
                  <td role="gridcell" className={styles.countCell}>
                    {menu.items?.length || 0} {t('builder.menus.items')}
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(menu.updatedAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleOpenEditor(menu)}
                        aria-label={`${t('common.edit')} ${menu.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteMenuClick(menu)}
                        aria-label={`${t('common.delete')} ${menu.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        {t('common.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isMenuFormOpen && (
        <MenuForm
          menu={editingMenu}
          onSubmit={handleMenuFormSubmit}
          onCancel={handleCloseMenuForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('builder.menus.deleteMenu')}
        message={t('builder.menus.confirmDeleteMenu')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteMenuConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default MenuBuilderPage
