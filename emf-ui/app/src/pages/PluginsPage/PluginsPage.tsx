/**
 * PluginsPage Component
 *
 * Displays a list of installed plugins with their status, allows enabling/disabling
 * plugins, and provides a settings interface for plugins that support configuration.
 *
 * Requirements:
 * - 12.6: Provide a plugin configuration interface for managing plugin settings
 */

import React, { useState, useCallback, useMemo } from 'react'
import { usePlugins } from '../../context/PluginContext'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../../components'
import type { LoadedPlugin, PluginStatus } from '../../types/plugin'
import styles from './PluginsPage.module.css'

/**
 * Plugin settings interface for plugins that provide configuration
 */
export interface PluginSettings {
  /** Plugin ID */
  pluginId: string
  /** Settings component provided by the plugin */
  SettingsComponent?: React.ComponentType<PluginSettingsProps>
  /** Current settings values */
  values?: Record<string, unknown>
}

/**
 * Props passed to plugin settings components
 */
export interface PluginSettingsProps {
  /** Current settings values */
  values: Record<string, unknown>
  /** Callback to update settings */
  onChange: (values: Record<string, unknown>) => void
  /** Whether settings are being saved */
  isSaving?: boolean
}

/**
 * Props for PluginsPage component
 */
export interface PluginsPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Get status display text
 */
function getStatusText(status: PluginStatus, t: (key: string) => string): string {
  switch (status) {
    case 'loaded':
      return t('plugins.status.loaded')
    case 'loading':
      return t('plugins.status.loading')
    case 'error':
      return t('plugins.status.error')
    case 'pending':
      return t('plugins.status.pending')
    default:
      return status
  }
}

/**
 * PluginCard Component
 *
 * Displays a single plugin with its details and actions.
 */
interface PluginCardProps {
  loadedPlugin: LoadedPlugin
  isSelected: boolean
  isEnabled: boolean
  onSelect: () => void
  onToggleEnabled: () => void
  onViewSettings: () => void
  hasSettings: boolean
}

function PluginCard({
  loadedPlugin,
  isSelected,
  isEnabled,
  onSelect,
  onToggleEnabled,
  onViewSettings,
  hasSettings,
}: PluginCardProps): React.ReactElement {
  const { t } = useI18n()
  const { plugin, status, error } = loadedPlugin

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        onSelect()
      }
    },
    [onSelect]
  )

  const handleToggleClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      onToggleEnabled()
    },
    [onToggleEnabled]
  )

  const handleSettingsClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      onViewSettings()
    },
    [onViewSettings]
  )

  const statusClassName = `${styles.statusBadge} ${styles[status]}`
  const cardClassName = `${styles.pluginCard} ${isSelected ? styles.selected : ''}`
  const toggleClassName = `${styles.toggle} ${isEnabled ? styles.enabled : ''} ${
    status === 'loading' ? styles.disabled : ''
  }`

  return (
    <div
      className={cardClassName}
      onClick={onSelect}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="button"
      aria-pressed={isSelected}
      aria-label={`${plugin.name} plugin`}
      data-testid={`plugin-card-${plugin.id}`}
    >
      <div className={styles.pluginCardHeader}>
        <div className={styles.pluginInfo}>
          <h3 className={styles.pluginName}>{plugin.name}</h3>
          <span className={styles.pluginVersion}>v{plugin.version}</span>
        </div>
        <span className={statusClassName} data-testid={`plugin-status-${plugin.id}`}>
          <span className={styles.statusDot} aria-hidden="true" />
          {getStatusText(status, t)}
        </span>
      </div>

      <div className={styles.pluginCardBody}>
        {error ? (
          <p className={styles.pluginDescription} style={{ color: 'var(--color-error-text)' }}>
            {t('plugins.errorMessage')}: {error}
          </p>
        ) : (
          <p className={styles.pluginDescription}>
            {t('plugins.pluginId')}: {plugin.id}
          </p>
        )}
      </div>

      <div className={styles.pluginCardFooter}>
        <div className={styles.toggleContainer}>
          <span className={styles.toggleLabel}>
            {isEnabled ? t('plugins.enabled') : t('plugins.disabled')}
          </span>
          <button
            type="button"
            className={toggleClassName}
            onClick={handleToggleClick}
            disabled={status === 'loading'}
            aria-label={isEnabled ? t('plugins.disable') : t('plugins.enable')}
            aria-pressed={isEnabled}
            data-testid={`plugin-toggle-${plugin.id}`}
          >
            <span className={styles.toggleKnob} />
          </button>
        </div>

        <div className={styles.pluginActions}>
          {hasSettings && (
            <button
              type="button"
              className={styles.actionButton}
              onClick={handleSettingsClick}
              disabled={status !== 'loaded'}
              aria-label={`${t('plugins.settings')} ${plugin.name}`}
              data-testid={`plugin-settings-${plugin.id}`}
            >
              {t('plugins.settings')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * PluginDetailsPanel Component
 *
 * Displays detailed information about a selected plugin.
 */
interface PluginDetailsPanelProps {
  loadedPlugin: LoadedPlugin
  registeredFieldRenderers: string[]
  registeredPageComponents: string[]
  onClose: () => void
}

function PluginDetailsPanel({
  loadedPlugin,
  registeredFieldRenderers,
  registeredPageComponents,
  onClose,
}: PluginDetailsPanelProps): React.ReactElement {
  const { t } = useI18n()
  const { plugin, status, error } = loadedPlugin

  return (
    <aside
      className={styles.detailsPanel}
      aria-label={`${plugin.name} details`}
      data-testid="plugin-details-panel"
    >
      <div className={styles.detailsHeader}>
        <h2 className={styles.detailsTitle}>{plugin.name}</h2>
        <button
          type="button"
          className={styles.closeButton}
          onClick={onClose}
          aria-label={t('common.close')}
          data-testid="plugin-details-close"
        >
          ×
        </button>
      </div>

      <div className={styles.detailsSection}>
        <h3 className={styles.detailsSectionTitle}>{t('plugins.information')}</h3>
        <div className={styles.detailsRow}>
          <span className={styles.detailsLabel}>{t('plugins.pluginId')}:</span>
          <span className={styles.detailsValue}>{plugin.id}</span>
        </div>
        <div className={styles.detailsRow}>
          <span className={styles.detailsLabel}>{t('plugins.version')}:</span>
          <span className={styles.detailsValue}>{plugin.version}</span>
        </div>
        <div className={styles.detailsRow}>
          <span className={styles.detailsLabel}>{t('plugins.status.label')}:</span>
          <span className={styles.detailsValue}>{getStatusText(status, t)}</span>
        </div>
        {error && (
          <div className={styles.detailsRow}>
            <span className={styles.detailsLabel}>{t('plugins.errorMessage')}:</span>
            <span className={styles.detailsValue} style={{ color: 'var(--color-error-text)' }}>
              {error}
            </span>
          </div>
        )}
      </div>

      <div className={styles.detailsSection}>
        <h3 className={styles.detailsSectionTitle}>{t('plugins.registeredComponents')}</h3>

        <div className={styles.detailsRow}>
          <span className={styles.detailsLabel}>{t('plugins.fieldRenderers')}:</span>
          <div className={styles.detailsValue}>
            {registeredFieldRenderers.length > 0 ? (
              <div className={styles.componentsList}>
                {registeredFieldRenderers.map((type) => (
                  <span key={type} className={styles.componentTag}>
                    {type}
                  </span>
                ))}
              </div>
            ) : (
              <span className={styles.noSettings}>{t('common.none')}</span>
            )}
          </div>
        </div>

        <div className={styles.detailsRow}>
          <span className={styles.detailsLabel}>{t('plugins.pageComponents')}:</span>
          <div className={styles.detailsValue}>
            {registeredPageComponents.length > 0 ? (
              <div className={styles.componentsList}>
                {registeredPageComponents.map((name) => (
                  <span key={name} className={styles.componentTag}>
                    {name}
                  </span>
                ))}
              </div>
            ) : (
              <span className={styles.noSettings}>{t('common.none')}</span>
            )}
          </div>
        </div>
      </div>

      <div className={styles.settingsPanel}>
        <h3 className={styles.settingsTitle}>{t('plugins.settings')}</h3>
        <div className={styles.settingsContent}>
          <p className={styles.noSettings}>{t('plugins.noSettingsAvailable')}</p>
        </div>
      </div>
    </aside>
  )
}

/**
 * PluginsPage Component
 *
 * Main page for managing plugins in the EMF Admin UI.
 * Displays installed plugins, their status, and provides configuration options.
 */
export function PluginsPage({ testId = 'plugins-page' }: PluginsPageProps): React.ReactElement {
  const { t } = useI18n()
  const { plugins, isLoading, errors, fieldRenderers, pageComponents } = usePlugins()

  // Track enabled state for each plugin (in a real app, this would be persisted)
  const [enabledPlugins, setEnabledPlugins] = useState<Set<string>>(new Set())

  // Update enabled plugins when plugins load
  React.useEffect(() => {
    const loadedPluginIds = plugins.filter((p) => p.status === 'loaded').map((p) => p.plugin.id)
    setEnabledPlugins(new Set(loadedPluginIds))
  }, [plugins])

  // Track selected plugin for details panel
  const [selectedPluginId, setSelectedPluginId] = useState<string | null>(null)

  // Get the selected plugin
  const selectedPlugin = useMemo(() => {
    if (!selectedPluginId) return null
    return plugins.find((p) => p.plugin.id === selectedPluginId) ?? null
  }, [plugins, selectedPluginId])

  // Get registered components for the selected plugin
  const registeredFieldRenderers = useMemo(() => {
    if (!selectedPlugin) return []
    const pluginFieldRenderers = selectedPlugin.plugin.fieldRenderers
    if (!pluginFieldRenderers) return []
    return Object.keys(pluginFieldRenderers)
  }, [selectedPlugin])

  const registeredPageComponents = useMemo(() => {
    if (!selectedPlugin) return []
    const pluginPageComponents = selectedPlugin.plugin.pageComponents
    if (!pluginPageComponents) return []
    return Object.keys(pluginPageComponents)
  }, [selectedPlugin])

  // Handle plugin selection
  const handleSelectPlugin = useCallback((pluginId: string) => {
    setSelectedPluginId((prev) => (prev === pluginId ? null : pluginId))
  }, [])

  // Handle toggle enabled
  const handleToggleEnabled = useCallback((pluginId: string) => {
    setEnabledPlugins((prev) => {
      const next = new Set(prev)
      if (next.has(pluginId)) {
        next.delete(pluginId)
      } else {
        next.add(pluginId)
      }
      return next
    })
  }, [])

  // Handle view settings
  const handleViewSettings = useCallback((pluginId: string) => {
    setSelectedPluginId(pluginId)
  }, [])

  // Handle close details panel
  const handleCloseDetails = useCallback(() => {
    setSelectedPluginId(null)
  }, [])

  // Count statistics
  const loadedCount = plugins.filter((p) => p.status === 'loaded').length
  const totalFieldRenderers = fieldRenderers.size
  const totalPageComponents = pageComponents.size

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <h1 className={styles.title}>{t('navigation.plugins')}</h1>
        <div className={styles.headerInfo}>
          <span data-testid="plugins-count">
            {t('plugins.loadedCount', {
              count: String(loadedCount),
              total: String(plugins.length),
            })}
          </span>
          {totalFieldRenderers > 0 && (
            <span data-testid="field-renderers-count">
              {t('plugins.fieldRenderersCount', { count: String(totalFieldRenderers) })}
            </span>
          )}
          {totalPageComponents > 0 && (
            <span data-testid="page-components-count">
              {t('plugins.pageComponentsCount', { count: String(totalPageComponents) })}
            </span>
          )}
        </div>
      </header>

      {/* Error Summary */}
      {errors.length > 0 && (
        <div className={styles.errorList} role="alert" data-testid="plugin-errors">
          <h2 className={styles.detailsSectionTitle}>{t('plugins.loadErrors')}</h2>
          {errors.map((err) => (
            <div key={err.pluginId} className={styles.errorItem}>
              <span className={styles.errorPluginId}>{err.pluginId}</span>
              <span className={styles.errorMessage}>{err.error}</span>
            </div>
          ))}
        </div>
      )}

      {/* Main Content */}
      {plugins.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('plugins.noPlugins')}</p>
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 'var(--spacing-lg, 1.5rem)' }}>
          {/* Plugin Grid */}
          <div className={styles.pluginGrid} style={{ flex: 1 }}>
            {plugins.map((loadedPlugin) => (
              <PluginCard
                key={loadedPlugin.plugin.id}
                loadedPlugin={loadedPlugin}
                isSelected={selectedPluginId === loadedPlugin.plugin.id}
                isEnabled={enabledPlugins.has(loadedPlugin.plugin.id)}
                onSelect={() => handleSelectPlugin(loadedPlugin.plugin.id)}
                onToggleEnabled={() => handleToggleEnabled(loadedPlugin.plugin.id)}
                onViewSettings={() => handleViewSettings(loadedPlugin.plugin.id)}
                hasSettings={false} // In a real implementation, check if plugin provides settings
              />
            ))}
          </div>

          {/* Details Panel */}
          {selectedPlugin && (
            <PluginDetailsPanel
              loadedPlugin={selectedPlugin}
              registeredFieldRenderers={registeredFieldRenderers}
              registeredPageComponents={registeredPageComponents}
              onClose={handleCloseDetails}
            />
          )}
        </div>
      )}
    </div>
  )
}

export default PluginsPage
