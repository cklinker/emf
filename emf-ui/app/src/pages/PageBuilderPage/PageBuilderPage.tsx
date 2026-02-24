/**
 * PageBuilderPage Component
 *
 * Visual page editor for creating and editing UI pages.
 * Provides a list of all pages, a canvas for page layout,
 * a component palette for adding components, and a property panel
 * for editing component properties.
 *
 * Requirements:
 * - 7.1: Display list of all pages
 * - 7.2: Create new page action
 * - 7.3: Page editor with canvas area
 * - 7.4: Component palette for adding components
 * - 7.5: Property panel for editing component properties
 * - 7.6: Page configuration (path, title, layout)
 * - 7.7: Preview mode shows page as it will appear to end users
 * - 7.8: Save persists page configuration to backend
 * - 7.9: Publish makes page available to end users
 * - 7.10: Duplicate creates copy of existing page
 * - 12.5: Use registered custom page components when rendering pages
 */

import React, { useState, useCallback, useEffect, useRef, type ComponentType } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Image, FileEdit, Grid3x3, Square, Box } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { usePlugins } from '../../context/PluginContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import type { PageComponentProps } from '../../types/plugin'
import { cn } from '@/lib/utils'

/**
 * UI Page interface matching the API response
 */
export interface UIPage {
  id: string
  name: string
  path: string
  title: string
  layout: PageLayout
  components: PageComponent[]
  policies?: string[]
  published: boolean
  createdAt: string
  updatedAt: string
}

/**
 * Page layout configuration
 */
export interface PageLayout {
  type: 'single' | 'sidebar' | 'grid'
  config?: Record<string, unknown>
}

/**
 * Page component definition
 */
export interface PageComponent {
  id: string
  type: string
  props: Record<string, unknown>
  children?: PageComponent[]
  position: ComponentPosition
}

/**
 * Component position on the canvas
 */
export interface ComponentPosition {
  row: number
  column: number
  width: number
  height: number
}

/**
 * Form data for creating/editing a page
 */
interface PageFormData {
  name: string
  path: string
  title: string
  layoutType: 'single' | 'sidebar' | 'grid'
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string
  path?: string
  title?: string
}

/**
 * Available component types for the palette
 */
const AVAILABLE_COMPONENTS = [
  { type: 'heading', label: 'Heading', icon: 'H' },
  { type: 'text', label: 'Text', icon: 'T' },
  { type: 'button', label: 'Button', icon: 'B' },
  { type: 'image', label: 'Image', icon: 'I' },
  { type: 'form', label: 'Form', icon: 'F' },
  { type: 'table', label: 'Table', icon: '⊞' },
  { type: 'card', label: 'Card', icon: '▢' },
  { type: 'container', label: 'Container', icon: '◻' },
]

/**
 * Props for PageBuilderPage component
 */
export interface PageBuilderPageProps {
  /** Optional page ID for editing an existing page */
  pageId?: string
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Validate page form data
 */
function validateForm(data: PageFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}

  if (!data.name.trim()) {
    errors.name = t('builder.pages.validation.nameRequired')
  } else if (data.name.length > 50) {
    errors.name = t('builder.pages.validation.nameTooLong')
  } else if (!/^[a-z][a-z0-9_]*$/.test(data.name)) {
    errors.name = t('builder.pages.validation.nameFormat')
  }

  if (!data.path.trim()) {
    errors.path = t('builder.pages.validation.pathRequired')
  } else if (!data.path.startsWith('/')) {
    errors.path = t('builder.pages.validation.pathFormat')
  }

  if (!data.title.trim()) {
    errors.title = t('builder.pages.validation.titleRequired')
  } else if (data.title.length > 100) {
    errors.title = t('builder.pages.validation.titleTooLong')
  }

  return errors
}

/**
 * Generate a unique ID for components
 */
function generateId(): string {
  return `comp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

/**
 * Status Badge Component
 */
interface StatusBadgeProps {
  published: boolean
}

function StatusBadge({ published }: StatusBadgeProps): React.ReactElement {
  const { t } = useI18n()
  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-1 text-xs font-medium rounded-full',
        published
          ? 'text-green-800 bg-green-100 dark:text-green-300 dark:bg-green-900'
          : 'text-yellow-800 bg-yellow-100 dark:text-yellow-300 dark:bg-yellow-900'
      )}
      data-testid="status-badge"
    >
      {published ? t('builder.pages.published') : t('builder.pages.draft')}
    </span>
  )
}

/**
 * Component Palette Component
 */
interface ComponentPaletteProps {
  onDragStart: (componentType: string) => void
  onAddComponent: (componentType: string) => void
}

function ComponentPalette({
  onDragStart,
  onAddComponent,
}: ComponentPaletteProps): React.ReactElement {
  const { t } = useI18n()

  return (
    <div
      className="bg-background border border-border rounded-md p-4 overflow-y-auto"
      data-testid="component-palette"
    >
      <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
        {t('builder.pages.components')}
      </h3>
      <div className="grid grid-cols-2 max-md:grid-cols-4 gap-2">
        {AVAILABLE_COMPONENTS.map((comp) => (
          <button
            key={comp.type}
            type="button"
            className="flex flex-col items-center justify-center p-2 bg-muted border border-border rounded cursor-grab transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2 active:cursor-grabbing"
            draggable
            onDragStart={() => onDragStart(comp.type)}
            onClick={() => onAddComponent(comp.type)}
            aria-label={`Add ${comp.label} component`}
            data-testid={`palette-item-${comp.type}`}
          >
            <span className="text-lg mb-1">{comp.icon}</span>
            <span className="text-xs text-muted-foreground">{comp.label}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

/**
 * Property Panel Component
 */
interface PropertyPanelProps {
  component: PageComponent | null
  onChange: (updates: Partial<PageComponent>) => void
}

function PropertyPanel({ component, onChange }: PropertyPanelProps): React.ReactElement {
  const { t } = useI18n()

  if (!component) {
    return (
      <div
        className="bg-background border border-border rounded-md p-4 overflow-y-auto"
        data-testid="property-panel"
      >
        <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
          {t('builder.pages.properties')}
        </h3>
        <p className="text-muted-foreground text-sm text-center py-6">
          {t('builder.pages.selectComponent')}
        </p>
      </div>
    )
  }

  const handlePropChange = (key: string, value: unknown) => {
    onChange({
      props: { ...component.props, [key]: value },
    })
  }

  return (
    <div
      className="bg-background border border-border rounded-md p-4 overflow-y-auto"
      data-testid="property-panel"
    >
      <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
        {t('builder.pages.properties')}
      </h3>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-type">
            Component Type
          </label>
          <span id="prop-type" className="text-sm text-foreground capitalize">
            {component.type}
          </span>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-id">
            ID
          </label>
          <input
            id="prop-id"
            type="text"
            className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:bg-muted disabled:text-muted-foreground"
            value={component.id}
            disabled
            data-testid="property-id"
          />
        </div>
        {component.type === 'heading' && (
          <>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-text">
                Text
              </label>
              <input
                id="prop-text"
                type="text"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.text as string) || ''}
                onChange={(e) => handlePropChange('text', e.target.value)}
                placeholder="Enter heading text"
                data-testid="property-text"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-level">
                Level
              </label>
              <select
                id="prop-level"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.level as string) || 'h1'}
                onChange={(e) => handlePropChange('level', e.target.value)}
                data-testid="property-level"
              >
                <option value="h1">H1</option>
                <option value="h2">H2</option>
                <option value="h3">H3</option>
                <option value="h4">H4</option>
              </select>
            </div>
          </>
        )}
        {component.type === 'text' && (
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-content">
              Content
            </label>
            <textarea
              id="prop-content"
              className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 resize-y min-h-[80px]"
              value={(component.props.content as string) || ''}
              onChange={(e) => handlePropChange('content', e.target.value)}
              placeholder="Enter text content"
              rows={4}
              data-testid="property-content"
            />
          </div>
        )}
        {component.type === 'button' && (
          <>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-label">
                Label
              </label>
              <input
                id="prop-label"
                type="text"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.label as string) || ''}
                onChange={(e) => handlePropChange('label', e.target.value)}
                placeholder="Button label"
                data-testid="property-label"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-variant">
                Variant
              </label>
              <select
                id="prop-variant"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.variant as string) || 'primary'}
                onChange={(e) => handlePropChange('variant', e.target.value)}
                data-testid="property-variant"
              >
                <option value="primary">Primary</option>
                <option value="secondary">Secondary</option>
                <option value="danger">Danger</option>
              </select>
            </div>
          </>
        )}
        {component.type === 'image' && (
          <>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-src">
                Source URL
              </label>
              <input
                id="prop-src"
                type="text"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.src as string) || ''}
                onChange={(e) => handlePropChange('src', e.target.value)}
                placeholder="https://example.com/image.png"
                data-testid="property-src"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="prop-alt">
                Alt Text
              </label>
              <input
                id="prop-alt"
                type="text"
                className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={(component.props.alt as string) || ''}
                onChange={(e) => handlePropChange('alt', e.target.value)}
                placeholder="Image description"
                data-testid="property-alt"
              />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Preview Component - displays the page as it will appear to end users
 * Requirement 7.7: Preview mode shows page as it will appear to end users
 * Requirement 12.5: Use registered custom page components when rendering pages
 */
interface PreviewProps {
  page: UIPage | null
  components: PageComponent[]
  onClose: () => void
  getPageComponent?: (name: string) => ComponentType<PageComponentProps> | undefined
}

function Preview({
  page,
  components,
  onClose,
  getPageComponent,
}: PreviewProps): React.ReactElement {
  const { t } = useI18n()

  const renderPreviewComponent = (comp: PageComponent): React.ReactNode => {
    // Requirement 12.5: Check for custom page component first
    const CustomComponent = getPageComponent?.(comp.type)
    if (CustomComponent) {
      return (
        <div
          key={comp.id}
          className="p-4 bg-background border border-border rounded-md dark:bg-muted dark:border-border"
          data-testid={`preview-custom-${comp.id}`}
        >
          <CustomComponent config={comp.props} />
        </div>
      )
    }

    // Fall back to default component rendering
    switch (comp.type) {
      case 'heading': {
        const level = (comp.props.level as string) || 'h1'
        const text = (comp.props.text as string) || 'Heading'
        const HeadingTag = level as keyof JSX.IntrinsicElements
        return (
          <HeadingTag key={comp.id} className="m-0 text-foreground">
            {text}
          </HeadingTag>
        )
      }
      case 'text':
        return (
          <p key={comp.id} className="m-0 text-foreground leading-relaxed">
            {(comp.props.content as string) || 'Text content'}
          </p>
        )
      case 'button':
        return (
          <button
            key={comp.id}
            type="button"
            className={cn(
              'inline-block px-4 py-2 text-sm font-medium border-none rounded-md cursor-default',
              ((comp.props.variant as string) || 'primary') === 'primary' &&
                'text-primary-foreground bg-primary',
              (comp.props.variant as string) === 'secondary' &&
                'text-foreground bg-muted border border-border',
              (comp.props.variant as string) === 'danger' &&
                'text-primary-foreground bg-destructive'
            )}
            disabled
          >
            {(comp.props.label as string) || 'Button'}
          </button>
        )
      case 'image':
        return (
          <div key={comp.id} className="max-w-full">
            {(comp.props.src as string) ? (
              <img
                src={comp.props.src as string}
                alt={(comp.props.alt as string) || 'Image'}
                className="max-w-full h-auto rounded-md"
              />
            ) : (
              <div className="flex flex-col items-center justify-center p-6 bg-muted border border-dashed border-border rounded-md text-muted-foreground gap-2">
                <span>
                  <Image size={24} />
                </span>
                <span>{t('builder.pages.imagePlaceholder') || 'Image'}</span>
              </div>
            )}
          </div>
        )
      case 'form':
        return (
          <div key={comp.id} className="w-full">
            <div className="flex flex-col items-center justify-center p-6 bg-muted border border-dashed border-border rounded-md text-muted-foreground gap-2">
              <span>
                <FileEdit size={24} />
              </span>
              <span>Form Component</span>
            </div>
          </div>
        )
      case 'table':
        return (
          <div key={comp.id} className="w-full">
            <div className="flex flex-col items-center justify-center p-6 bg-muted border border-dashed border-border rounded-md text-muted-foreground gap-2">
              <span>
                <Grid3x3 size={24} />
              </span>
              <span>Table Component</span>
            </div>
          </div>
        )
      case 'card':
        return (
          <div key={comp.id} className="w-full">
            <div className="flex flex-col items-center justify-center p-6 bg-muted border border-dashed border-border rounded-md text-muted-foreground gap-2">
              <span>
                <Square size={24} />
              </span>
              <span>Card Component</span>
            </div>
          </div>
        )
      case 'container':
        return (
          <div key={comp.id} className="w-full">
            {comp.children?.map(renderPreviewComponent) || (
              <div className="flex flex-col items-center justify-center p-6 bg-muted border border-dashed border-border rounded-md text-muted-foreground gap-2">
                <span>
                  <Box size={24} />
                </span>
                <span>Container</span>
              </div>
            )}
          </div>
        )
      default:
        return (
          <div
            key={comp.id}
            className="p-4 bg-yellow-50 border border-yellow-300 rounded-md text-yellow-800 dark:bg-yellow-900/30 dark:border-yellow-700 dark:text-yellow-300"
          >
            Unknown component: {comp.type}
          </div>
        )
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/80 flex items-center justify-center z-[1100] p-4"
      data-testid="preview-overlay"
    >
      <div
        className="bg-background rounded-lg shadow-2xl w-full max-w-[1200px] max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-90 duration-300"
        data-testid="preview-container"
      >
        <header className="flex justify-between items-center px-6 py-4 border-b border-border bg-muted">
          <h2 className="m-0 text-lg font-semibold text-foreground">
            {t('builder.pages.preview')}: {page?.title || t('builder.pages.newPage')}
          </h2>
          <button
            type="button"
            className="flex items-center justify-center w-8 h-8 p-0 text-xl text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:bg-accent hover:text-foreground focus:outline-2 focus:outline-primary focus:outline-offset-2"
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="preview-close-button"
          >
            ×
          </button>
        </header>
        <div className="flex-1 overflow-y-auto p-6 min-h-[400px]" data-testid="preview-content">
          {components.length === 0 ? (
            <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
              <p>{t('builder.pages.canvasEmpty')}</p>
            </div>
          ) : (
            <div className="flex flex-col gap-4">{components.map(renderPreviewComponent)}</div>
          )}
        </div>
        <footer className="flex justify-between items-center px-6 py-4 border-t border-border bg-muted">
          <span className="text-sm text-muted-foreground">
            {t('builder.pages.pagePath')}:{' '}
            <code className="font-mono px-2 py-1 bg-background rounded">{page?.path || '/'}</code>
          </span>
          <button
            type="button"
            className="px-4 py-2 text-sm font-medium text-primary-foreground bg-primary border-none rounded-md cursor-pointer transition-colors hover:bg-primary/90 focus:outline-2 focus:outline-primary focus:outline-offset-2"
            onClick={onClose}
            data-testid="preview-exit-button"
          >
            {t('builder.pages.exitPreview') || 'Exit Preview'}
          </button>
        </footer>
      </div>
    </div>
  )
}

/**
 * Canvas Component - displays the page layout with components
 * Requirement 12.5: Use registered custom page components when rendering pages
 */
interface CanvasProps {
  components: PageComponent[]
  selectedId: string | null
  onSelect: (id: string | null) => void
  onDrop: (componentType: string) => void
  onDelete: (id: string) => void
  getPageComponent?: (name: string) => ComponentType<PageComponentProps> | undefined
}

function Canvas({
  components,
  selectedId,
  onSelect,
  onDrop,
  onDelete,
  getPageComponent,
}: CanvasProps): React.ReactElement {
  const { t } = useI18n()
  const [isDragOver, setIsDragOver] = useState(false)

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragOver(true)
  }

  const handleDragLeave = () => {
    setIsDragOver(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragOver(false)
    const componentType = e.dataTransfer.getData('componentType')
    if (componentType) {
      onDrop(componentType)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent, componentId: string) => {
    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault()
      onDelete(componentId)
    }
  }

  const renderComponent = (comp: PageComponent) => {
    const isSelected = selectedId === comp.id

    // Requirement 12.5: Check for custom page component
    const CustomComponent = getPageComponent?.(comp.type)
    const isCustomComponent = !!CustomComponent

    return (
      <div
        key={comp.id}
        className={cn(
          'bg-muted border border-border rounded p-2 cursor-pointer transition-[border-color,box-shadow] hover:border-muted-foreground/40 focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20',
          isSelected && 'border-primary ring-2 ring-primary/20',
          isCustomComponent &&
            'border-blue-300 bg-blue-50 hover:border-blue-500 dark:border-blue-800 dark:bg-blue-950/40 dark:hover:border-blue-400',
          isSelected &&
            isCustomComponent &&
            'border-blue-500 ring-2 ring-blue-500/20 dark:border-blue-400'
        )}
        onClick={() => onSelect(comp.id)}
        onKeyDown={(e) => handleKeyDown(e, comp.id)}
        tabIndex={0}
        role="button"
        aria-label={`${comp.type} component${isCustomComponent ? ' (custom)' : ''}`}
        aria-pressed={isSelected}
        data-testid={`canvas-component-${comp.id}`}
        data-custom={isCustomComponent ? 'true' : 'false'}
      >
        <div className="flex justify-between items-center mb-1">
          <span className="text-xs font-medium text-muted-foreground uppercase">
            {comp.type}
            {isCustomComponent && (
              <span
                className="inline-flex items-center ml-1 px-1 py-[2px] text-[10px] font-medium text-blue-700 bg-blue-100 rounded uppercase tracking-wide dark:text-blue-300 dark:bg-blue-900/60"
                data-testid={`custom-badge-${comp.id}`}
              >
                Custom
              </span>
            )}
          </span>
          <button
            type="button"
            className="flex items-center justify-center w-5 h-5 p-0 text-base text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:text-destructive hover:bg-destructive/10 focus:outline-2 focus:outline-primary focus:outline-offset-2"
            onClick={(e) => {
              e.stopPropagation()
              onDelete(comp.id)
            }}
            aria-label={`Delete ${comp.type} component`}
            data-testid={`delete-component-${comp.id}`}
          >
            ×
          </button>
        </div>
        <div className="text-sm text-foreground">
          {isCustomComponent ? (
            // Render custom component preview
            <div
              className="p-2 bg-background rounded min-h-[40px] dark:bg-muted"
              data-testid={`custom-preview-${comp.id}`}
            >
              <CustomComponent config={comp.props} />
            </div>
          ) : (
            // Default component previews
            <>
              {comp.type === 'heading' && (
                <span className="font-semibold">{(comp.props.text as string) || 'Heading'}</span>
              )}
              {comp.type === 'text' && (
                <span className="text-muted-foreground">
                  {(comp.props.content as string) || 'Text content'}
                </span>
              )}
              {comp.type === 'button' && (
                <span className="inline-block px-2 py-1 bg-primary text-primary-foreground rounded text-xs">
                  {(comp.props.label as string) || 'Button'}
                </span>
              )}
              {comp.type === 'image' && (
                <span className="text-muted-foreground">
                  <Image size={14} /> Image
                </span>
              )}
              {comp.type === 'form' && (
                <span className="text-muted-foreground">
                  <FileEdit size={14} /> Form
                </span>
              )}
              {comp.type === 'table' && (
                <span className="text-muted-foreground">
                  <Grid3x3 size={14} /> Table
                </span>
              )}
              {comp.type === 'card' && (
                <span className="text-muted-foreground">
                  <Square size={14} /> Card
                </span>
              )}
              {comp.type === 'container' && (
                <span className="text-muted-foreground">
                  <Box size={14} /> Container
                </span>
              )}
            </>
          )}
        </div>
      </div>
    )
  }

  return (
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/click-events-have-key-events
    <div
      className={cn(
        'bg-background border-2 border-dashed border-border rounded-md p-4 overflow-y-auto min-h-[400px] max-md:min-h-[300px] transition-[border-color,background-color]',
        isDragOver && 'border-primary bg-primary/5'
      )}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={() => onSelect(null)}
      data-testid="page-canvas"
      role="region"
      aria-label="Page canvas"
    >
      {components.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-full min-h-[300px] text-muted-foreground text-center">
          <p>{t('builder.pages.canvasEmpty')}</p>
          <p className="text-sm mt-2">{t('builder.pages.canvasHint')}</p>
        </div>
      ) : (
        // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions
        <div className="flex flex-col gap-2" onClick={(e) => e.stopPropagation()}>
          {components.map(renderComponent)}
        </div>
      )}
    </div>
  )
}

/**
 * Page Form Component - for creating/editing page configuration
 */
interface PageFormProps {
  page?: UIPage
  onSubmit: (data: PageFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PageForm({ page, onSubmit, onCancel, isSubmitting }: PageFormProps): React.ReactElement {
  const { t } = useI18n()
  const isEditing = !!page
  const [formData, setFormData] = useState<PageFormData>({
    name: page?.name ?? '',
    path: page?.path ?? '/',
    title: page?.title ?? '',
    layoutType: page?.layout?.type ?? 'single',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof PageFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof PageFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData, t)
      if (validationErrors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field as keyof FormErrors] }))
      }
    },
    [formData, t]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData, t)
      setErrors(validationErrors)
      setTouched({ name: true, path: true, title: true })

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

  const title = isEditing ? t('builder.pages.editPage') : t('builder.pages.createPage')

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-[1000] p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="page-form-overlay"
      role="presentation"
    >
      <div
        className="bg-background rounded-lg shadow-xl w-full max-w-[500px] max-h-[90vh] overflow-y-auto animate-in zoom-in-95 duration-200 max-md:max-w-full max-md:mx-2"
        role="dialog"
        aria-modal="true"
        aria-labelledby="page-form-title"
        data-testid="page-form-modal"
      >
        <div className="flex justify-between items-center p-6 border-b border-border">
          <h2 id="page-form-title" className="m-0 text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="flex items-center justify-center w-8 h-8 p-0 text-xl text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:bg-accent hover:text-foreground focus:outline-2 focus:outline-primary focus:outline-offset-2"
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="page-form-close"
          >
            ×
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-1">
              <label htmlFor="page-name" className="text-sm font-medium text-foreground">
                {t('builder.pages.pageName')}
                <span className="text-destructive ml-0.5" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="page-name"
                type="text"
                className={cn(
                  'px-4 py-2 text-sm text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/20 placeholder:text-muted-foreground',
                  touched.name && errors.name && 'border-destructive focus:ring-destructive/20'
                )}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('builder.pages.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'page-name-error' : 'page-name-hint'}
                disabled={isSubmitting}
                data-testid="page-name-input"
              />
              <span id="page-name-hint" className="text-xs text-muted-foreground mt-1">
                {t('builder.pages.nameHint')}
              </span>
              {touched.name && errors.name && (
                <span id="page-name-error" className="text-xs text-destructive mt-1" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="page-path" className="text-sm font-medium text-foreground">
                {t('builder.pages.pagePath')}
                <span className="text-destructive ml-0.5" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="page-path"
                type="text"
                className={cn(
                  'px-4 py-2 text-sm text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/20 placeholder:text-muted-foreground',
                  touched.path && errors.path && 'border-destructive focus:ring-destructive/20'
                )}
                value={formData.path}
                onChange={(e) => handleChange('path', e.target.value)}
                onBlur={() => handleBlur('path')}
                placeholder={t('builder.pages.pathPlaceholder')}
                aria-required="true"
                aria-invalid={touched.path && !!errors.path}
                aria-describedby={errors.path ? 'page-path-error' : 'page-path-hint'}
                disabled={isSubmitting}
                data-testid="page-path-input"
              />
              <span id="page-path-hint" className="text-xs text-muted-foreground mt-1">
                {t('builder.pages.pathHint')}
              </span>
              {touched.path && errors.path && (
                <span id="page-path-error" className="text-xs text-destructive mt-1" role="alert">
                  {errors.path}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="page-title" className="text-sm font-medium text-foreground">
                {t('builder.pages.pageTitle')}
                <span className="text-destructive ml-0.5" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="page-title"
                type="text"
                className={cn(
                  'px-4 py-2 text-sm text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/20 placeholder:text-muted-foreground',
                  touched.title && errors.title && 'border-destructive focus:ring-destructive/20'
                )}
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                onBlur={() => handleBlur('title')}
                placeholder={t('builder.pages.titlePlaceholder')}
                aria-required="true"
                aria-invalid={touched.title && !!errors.title}
                aria-describedby={errors.title ? 'page-title-error' : undefined}
                disabled={isSubmitting}
                data-testid="page-title-input"
              />
              {touched.title && errors.title && (
                <span id="page-title-error" className="text-xs text-destructive mt-1" role="alert">
                  {errors.title}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="page-layout" className="text-sm font-medium text-foreground">
                {t('builder.pages.layout')}
              </label>
              <select
                id="page-layout"
                className="px-4 py-2 text-sm text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/20"
                value={formData.layoutType}
                onChange={(e) => handleChange('layoutType', e.target.value)}
                disabled={isSubmitting}
                data-testid="page-layout-select"
              >
                <option value="single">{t('builder.pages.layoutSingle')}</option>
                <option value="sidebar">{t('builder.pages.layoutSidebar')}</option>
                <option value="grid">{t('builder.pages.layoutGrid')}</option>
              </select>
            </div>

            <div className="flex justify-end gap-2 mt-4 pt-4 border-t border-border max-md:flex-col">
              <button
                type="button"
                className="px-4 py-2 text-sm font-medium text-foreground bg-background border border-border rounded-md cursor-pointer transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2 max-md:w-full"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="page-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className="px-4 py-2 text-sm font-medium text-primary-foreground bg-primary border-none rounded-md cursor-pointer transition-colors hover:bg-primary/90 focus:outline-2 focus:outline-primary focus:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed max-md:w-full"
                disabled={isSubmitting}
                data-testid="page-form-submit"
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
 * PageBuilderPage Component
 *
 * Main page for building and editing UI pages in the EMF Admin UI.
 * Provides a visual editor with component palette, canvas, and property panel.
 *
 * Requirement 12.5: Integrates with plugin system to use custom page components
 */
export function PageBuilderPage({
  pageId,
  testId = 'page-builder-page',
}: PageBuilderPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Requirement 12.5: Get custom page components from plugin system
  const { getPageComponent } = usePlugins()

  // View mode: 'list' shows all pages, 'editor' shows the page editor
  const [viewMode, setViewMode] = useState<'list' | 'editor'>(pageId ? 'editor' : 'list')
  const [editingPageId, setEditingPageId] = useState<string | null>(pageId ?? null)

  // Form modal state
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingPage, setEditingPage] = useState<UIPage | undefined>(undefined)

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [pageToDelete, setPageToDelete] = useState<UIPage | null>(null)

  // Editor state
  const [components, setComponents] = useState<PageComponent[]>([])
  const [selectedComponentId, setSelectedComponentId] = useState<string | null>(null)
  const [, setDraggedComponentType] = useState<string | null>(null)
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)

  // Preview mode state (Requirement 7.7)
  const [isPreviewMode, setIsPreviewMode] = useState(false)

  // Fetch all pages query
  const {
    data: pages = [],
    isLoading: isLoadingPages,
    error: pagesError,
    refetch: refetchPages,
  } = useQuery({
    queryKey: ['ui-pages'],
    queryFn: () => apiClient.getList<UIPage>('/api/ui-pages'),
    enabled: viewMode === 'list',
  })

  // Fetch single page query for editing
  const { data: currentPage } = useQuery({
    queryKey: ['ui-page', editingPageId],
    queryFn: () => apiClient.getOne<UIPage>(`/api/ui-pages/${editingPageId}`),
    enabled: viewMode === 'editor' && !!editingPageId,
  })

  // Update components when page data loads
  const [loadedPageId, setLoadedPageId] = useState<string | null>(null)
  const currentPageId = currentPage?.id ?? null
  if (currentPageId && loadedPageId !== currentPageId) {
    setLoadedPageId(currentPageId)
    setComponents(currentPage?.components || [])
    setHasUnsavedChanges(false)
  }

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: Partial<UIPage>) => apiClient.postResource<UIPage>('/api/ui-pages', data),
    onSuccess: (newPage) => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      showToast(t('success.created', { item: t('builder.pages.page') }), 'success')
      handleCloseForm()
      // Open the editor for the new page
      setEditingPageId(newPage.id)
      setComponents([])
      setViewMode('editor')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<UIPage> }) =>
      apiClient.putResource<UIPage>(`/api/ui-pages/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      queryClient.invalidateQueries({ queryKey: ['ui-page', editingPageId] })
      showToast(t('success.updated', { item: t('builder.pages.page') }), 'success')
      setHasUnsavedChanges(false)
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteResource(`/api/ui-pages/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      showToast(t('success.deleted', { item: t('builder.pages.page') }), 'success')
      setDeleteDialogOpen(false)
      setPageToDelete(null)
      if (editingPageId === pageToDelete?.id) {
        setViewMode('list')
        setEditingPageId(null)
      }
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Publish mutation (Requirement 7.9)
  const publishMutation = useMutation({
    mutationFn: (id: string) => apiClient.postResource<UIPage>(`/api/ui-pages/${id}/publish`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      queryClient.invalidateQueries({ queryKey: ['ui-page', editingPageId] })
      showToast(t('builder.pages.publishSuccess') || 'Page published successfully', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Unpublish mutation
  const unpublishMutation = useMutation({
    mutationFn: (id: string) => apiClient.postResource<UIPage>(`/api/ui-pages/${id}/unpublish`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      queryClient.invalidateQueries({ queryKey: ['ui-page', editingPageId] })
      showToast(t('builder.pages.unpublishSuccess') || 'Page unpublished successfully', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Duplicate mutation (Requirement 7.10)
  const duplicateMutation = useMutation({
    mutationFn: (id: string) => apiClient.postResource<UIPage>(`/api/ui-pages/${id}/duplicate`, {}),
    onSuccess: (newPage) => {
      queryClient.invalidateQueries({ queryKey: ['ui-pages'] })
      showToast(t('builder.pages.duplicateSuccess') || 'Page duplicated successfully', 'success')
      // Open the editor for the duplicated page
      setEditingPageId(newPage.id)
      setComponents(newPage.components || [])
      setViewMode('editor')
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle create action
  const handleCreate = useCallback(() => {
    setEditingPage(undefined)
    setIsFormOpen(true)
  }, [])

  // Handle edit page config
  const handleEditConfig = useCallback((page: UIPage) => {
    setEditingPage(page)
    setIsFormOpen(true)
  }, [])

  // Handle close form
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingPage(undefined)
  }, [])

  // Handle form submit
  const handleFormSubmit = useCallback(
    (data: PageFormData) => {
      const pageData: Partial<UIPage> = {
        name: data.name,
        path: data.path,
        title: data.title,
        layout: { type: data.layoutType },
      }

      if (editingPage) {
        updateMutation.mutate({ id: editingPage.id, data: pageData })
      } else {
        createMutation.mutate({
          ...pageData,
          components: [],
          published: false,
        })
      }
    },
    [editingPage, createMutation, updateMutation]
  )

  // Handle delete action
  const handleDeleteClick = useCallback((page: UIPage) => {
    setPageToDelete(page)
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (pageToDelete) {
      deleteMutation.mutate(pageToDelete.id)
    }
  }, [pageToDelete, deleteMutation])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setPageToDelete(null)
  }, [])

  // Handle opening page editor
  const handleOpenEditor = useCallback((page: UIPage) => {
    setEditingPageId(page.id)
    setViewMode('editor')
  }, [])

  // Handle back to list
  const handleBackToList = useCallback(() => {
    if (hasUnsavedChanges) {
      // Could show a confirmation dialog here
    }
    setViewMode('list')
    setEditingPageId(null)
    setComponents([])
    setSelectedComponentId(null)
    setHasUnsavedChanges(false)
  }, [hasUnsavedChanges])

  // Handle component drag start
  const handleDragStart = useCallback((componentType: string) => {
    setDraggedComponentType(componentType)
  }, [])

  // Handle adding component
  const handleAddComponent = useCallback(
    (componentType: string) => {
      const newComponent: PageComponent = {
        id: generateId(),
        type: componentType,
        props: {},
        position: { row: components.length, column: 0, width: 12, height: 1 },
      }
      setComponents((prev) => [...prev, newComponent])
      setSelectedComponentId(newComponent.id)
      setHasUnsavedChanges(true)
    },
    [components.length]
  )

  // Handle component drop on canvas
  const handleCanvasDrop = useCallback(
    (componentType: string) => {
      handleAddComponent(componentType)
      setDraggedComponentType(null)
    },
    [handleAddComponent]
  )

  // Handle component selection
  const handleSelectComponent = useCallback((id: string | null) => {
    setSelectedComponentId(id)
  }, [])

  // Handle component property change
  const handleComponentChange = useCallback(
    (updates: Partial<PageComponent>) => {
      if (!selectedComponentId) return
      setComponents((prev) =>
        prev.map((comp) => (comp.id === selectedComponentId ? { ...comp, ...updates } : comp))
      )
      setHasUnsavedChanges(true)
    },
    [selectedComponentId]
  )

  // Handle component delete
  const handleDeleteComponent = useCallback(
    (id: string) => {
      setComponents((prev) => prev.filter((comp) => comp.id !== id))
      if (selectedComponentId === id) {
        setSelectedComponentId(null)
      }
      setHasUnsavedChanges(true)
    },
    [selectedComponentId]
  )

  // Handle save page
  const handleSavePage = useCallback(() => {
    if (!editingPageId) return
    updateMutation.mutate({
      id: editingPageId,
      data: { components },
    })
  }, [editingPageId, components, updateMutation])

  // Handle preview mode toggle (Requirement 7.7)
  const handleTogglePreview = useCallback(() => {
    setIsPreviewMode((prev) => !prev)
  }, [])

  // Handle close preview
  const handleClosePreview = useCallback(() => {
    setIsPreviewMode(false)
  }, [])

  // Handle publish page (Requirement 7.9)
  const handlePublishPage = useCallback(() => {
    if (!editingPageId) return
    publishMutation.mutate(editingPageId)
  }, [editingPageId, publishMutation])

  // Handle unpublish page
  const handleUnpublishPage = useCallback(() => {
    if (!editingPageId) return
    unpublishMutation.mutate(editingPageId)
  }, [editingPageId, unpublishMutation])

  // Handle duplicate page (Requirement 7.10)
  const handleDuplicatePage = useCallback(
    (pageId: string) => {
      duplicateMutation.mutate(pageId)
    },
    [duplicateMutation]
  )

  const selectedComponent = components.find((c) => c.id === selectedComponentId) || null
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isPublishing = publishMutation.isPending || unpublishMutation.isPending
  const isDuplicating = duplicateMutation.isPending

  // Render loading state
  if (viewMode === 'list' && isLoadingPages) {
    return (
      <div
        className="flex flex-col h-full min-h-0 p-6 w-full max-md:p-2 max-lg:p-4"
        data-testid={testId}
      >
        <div className="flex justify-center items-center min-h-[400px]">
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
  if (viewMode === 'list' && pagesError) {
    return (
      <div
        className="flex flex-col h-full min-h-0 p-6 w-full max-md:p-2 max-lg:p-4"
        data-testid={testId}
      >
        <ErrorMessage
          error={pagesError instanceof Error ? pagesError : new Error(t('errors.generic'))}
          onRetry={() => refetchPages()}
        />
      </div>
    )
  }

  // Render editor view
  if (viewMode === 'editor') {
    return (
      <div
        className="flex flex-col h-full min-h-0 p-6 w-full max-md:p-2 max-lg:p-4"
        data-testid={testId}
      >
        <header className="flex justify-between items-center pb-4 border-b border-border mb-4 max-md:flex-col max-md:gap-2">
          <div className="flex items-center gap-4 max-md:w-full max-md:justify-between">
            <button
              type="button"
              className="px-2 py-1 text-sm text-muted-foreground bg-transparent border-none cursor-pointer transition-colors hover:text-foreground focus:outline-2 focus:outline-primary focus:outline-offset-2"
              onClick={handleBackToList}
              aria-label={t('common.back')}
              data-testid="back-to-list-button"
            >
              ← {t('common.back')}
            </button>
            <h1 className="m-0 text-xl font-semibold text-foreground">
              {currentPage?.title || t('builder.pages.newPage')}
              {hasUnsavedChanges && (
                <span className="text-yellow-800 dark:text-yellow-300 ml-1">*</span>
              )}
            </h1>
            {currentPage && <StatusBadge published={currentPage.published} />}
          </div>
          <div className="flex items-center gap-2 max-md:w-full max-md:justify-between">
            {/* Preview button (Requirement 7.7) */}
            <button
              type="button"
              className="px-4 py-2 text-sm font-medium text-foreground bg-background border border-border rounded-md cursor-pointer transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2"
              onClick={handleTogglePreview}
              aria-label={t('builder.pages.preview')}
              data-testid="preview-page-button"
            >
              {t('builder.pages.preview')}
            </button>
            {currentPage && (
              <>
                {/* Duplicate button (Requirement 7.10) */}
                <button
                  type="button"
                  className="px-4 py-2 text-sm font-medium text-foreground bg-background border border-border rounded-md cursor-pointer transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
                  onClick={() => handleDuplicatePage(currentPage.id)}
                  disabled={isDuplicating}
                  aria-label={t('builder.pages.duplicate')}
                  data-testid="duplicate-page-button"
                >
                  {isDuplicating ? t('common.loading') : t('builder.pages.duplicate')}
                </button>
                {/* Publish/Unpublish button (Requirement 7.9) */}
                {currentPage.published ? (
                  <button
                    type="button"
                    className="px-4 py-2 text-sm font-medium text-yellow-800 bg-yellow-100 border border-yellow-300 rounded-md cursor-pointer transition-colors hover:bg-yellow-200 focus:outline-2 focus:outline-yellow-500 focus:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed dark:text-yellow-300 dark:bg-yellow-900/30 dark:border-yellow-700"
                    onClick={handleUnpublishPage}
                    disabled={isPublishing || hasUnsavedChanges}
                    aria-label={t('builder.pages.unpublish') || 'Unpublish'}
                    data-testid="unpublish-page-button"
                  >
                    {isPublishing
                      ? t('common.loading')
                      : t('builder.pages.unpublish') || 'Unpublish'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="px-4 py-2 text-sm font-medium text-primary-foreground bg-green-600 border-none rounded-md cursor-pointer transition-colors hover:bg-green-700 focus:outline-2 focus:outline-green-600 focus:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
                    onClick={handlePublishPage}
                    disabled={isPublishing || hasUnsavedChanges}
                    aria-label={t('builder.pages.publish')}
                    data-testid="publish-page-button"
                  >
                    {isPublishing ? t('common.loading') : t('builder.pages.publish')}
                  </button>
                )}
                <button
                  type="button"
                  className="px-4 py-2 text-sm font-medium text-foreground bg-background border border-border rounded-md cursor-pointer transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
                  onClick={() => handleEditConfig(currentPage)}
                  data-testid="edit-config-button"
                >
                  {t('builder.pages.editConfig')}
                </button>
              </>
            )}
            {/* Save button (Requirement 7.8) */}
            <button
              type="button"
              className="px-4 py-2 text-sm font-medium text-primary-foreground bg-primary border-none rounded-md cursor-pointer transition-colors hover:bg-primary/90 focus:outline-2 focus:outline-primary focus:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              onClick={handleSavePage}
              disabled={!hasUnsavedChanges || updateMutation.isPending}
              data-testid="save-page-button"
            >
              {updateMutation.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        </header>

        <div className="grid grid-cols-[200px_1fr_280px] max-lg:grid-cols-[160px_1fr_240px] max-md:grid-cols-1 max-md:grid-rows-[auto_1fr_auto] gap-4 flex-1 min-h-0 overflow-hidden">
          <ComponentPalette onDragStart={handleDragStart} onAddComponent={handleAddComponent} />
          <Canvas
            components={components}
            selectedId={selectedComponentId}
            onSelect={handleSelectComponent}
            onDrop={handleCanvasDrop}
            onDelete={handleDeleteComponent}
            getPageComponent={getPageComponent}
          />
          <PropertyPanel component={selectedComponent} onChange={handleComponentChange} />
        </div>

        {/* Preview mode overlay (Requirement 7.7, 12.5) */}
        {isPreviewMode && (
          <Preview
            page={currentPage || null}
            components={components}
            onClose={handleClosePreview}
            getPageComponent={getPageComponent}
          />
        )}

        {isFormOpen && (
          <PageForm
            page={editingPage}
            onSubmit={handleFormSubmit}
            onCancel={handleCloseForm}
            isSubmitting={isSubmitting}
          />
        )}
      </div>
    )
  }

  // Render list view
  return (
    <div
      className="flex flex-col h-full min-h-0 p-6 w-full max-md:p-2 max-lg:p-4"
      data-testid={testId}
    >
      <header className="flex justify-between items-center flex-wrap gap-4 mb-6 max-md:flex-col max-md:items-stretch">
        <h1 className="m-0 text-2xl max-md:text-xl font-semibold text-foreground">
          {t('builder.pages.title')}
        </h1>
        <button
          type="button"
          className="inline-flex items-center justify-center px-4 py-2 text-sm font-medium text-primary-foreground bg-primary border-none rounded-md cursor-pointer transition-colors hover:bg-primary/90 focus:outline-2 focus:outline-primary focus:outline-offset-2 max-md:w-full"
          onClick={handleCreate}
          aria-label={t('builder.pages.createPage')}
          data-testid="create-page-button"
        >
          {t('builder.pages.createPage')}
        </button>
      </header>

      {pages.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center p-12 text-center text-muted-foreground bg-muted rounded-md"
          data-testid="empty-state"
        >
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className="overflow-x-auto border border-border rounded-md bg-background">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label={t('builder.pages.title')}
            data-testid="pages-table"
          >
            <thead className="bg-muted">
              <tr role="row">
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('builder.pages.pageName')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('builder.pages.pagePath')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('builder.pages.pageTitle')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('collections.status')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('collections.updated')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="p-4 text-left font-semibold text-foreground border-b-2 border-border whitespace-nowrap"
                >
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {pages.map((page, index) => (
                <tr
                  key={page.id}
                  role="row"
                  className="transition-colors hover:bg-accent/50"
                  data-testid={`page-row-${index}`}
                >
                  <td
                    role="gridcell"
                    className="p-4 text-foreground border-b border-border/50 font-medium"
                  >
                    <button
                      type="button"
                      className="bg-transparent border-none p-0 text-primary cursor-pointer font-inherit no-underline hover:underline focus:outline-2 focus:outline-primary focus:outline-offset-2"
                      onClick={() => handleOpenEditor(page)}
                      data-testid={`page-name-${index}`}
                    >
                      {page.name}
                    </button>
                  </td>
                  <td
                    role="gridcell"
                    className="p-4 text-foreground border-b border-border/50 max-w-[200px]"
                  >
                    <code className="font-mono text-xs px-2 py-1 bg-muted rounded text-foreground">
                      {page.path}
                    </code>
                  </td>
                  <td role="gridcell" className="p-4 text-foreground border-b border-border/50">
                    {page.title}
                  </td>
                  <td role="gridcell" className="p-4 text-foreground border-b border-border/50">
                    <StatusBadge published={page.published} />
                  </td>
                  <td
                    role="gridcell"
                    className="p-4 text-muted-foreground border-b border-border/50 whitespace-nowrap"
                  >
                    {formatDate(new Date(page.updatedAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td
                    role="gridcell"
                    className="p-4 text-foreground border-b border-border/50 w-[1%] whitespace-nowrap"
                  >
                    <div className="flex gap-2">
                      <button
                        type="button"
                        className="px-2 py-1 text-xs font-medium text-foreground bg-background border border-border rounded cursor-pointer transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2"
                        onClick={() => handleOpenEditor(page)}
                        aria-label={`${t('common.edit')} ${page.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      {/* Duplicate button (Requirement 7.10) */}
                      <button
                        type="button"
                        className="px-2 py-1 text-xs font-medium text-foreground bg-background border border-border rounded cursor-pointer transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2"
                        onClick={() => handleDuplicatePage(page.id)}
                        disabled={isDuplicating}
                        aria-label={`${t('builder.pages.duplicate')} ${page.name}`}
                        data-testid={`duplicate-button-${index}`}
                      >
                        {t('builder.pages.duplicate')}
                      </button>
                      <button
                        type="button"
                        className="px-2 py-1 text-xs font-medium text-destructive bg-background border border-destructive/30 rounded cursor-pointer transition-colors hover:bg-destructive/5 hover:border-destructive focus:outline-2 focus:outline-primary focus:outline-offset-2"
                        onClick={() => handleDeleteClick(page)}
                        aria-label={`${t('common.delete')} ${page.name}`}
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

      {isFormOpen && (
        <PageForm
          page={editingPage}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('builder.pages.deletePage')}
        message={t('builder.pages.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PageBuilderPage
