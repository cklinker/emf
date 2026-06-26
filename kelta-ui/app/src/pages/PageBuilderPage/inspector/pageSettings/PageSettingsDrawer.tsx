/**
 * Page-settings drawer (NEW host surface — slice 2d owns creating it). Opened from a "Page settings"
 * toolbar button, it stacks the Variables and Data-sources sections (1h later drops its Access field into
 * the reserved slot). Both sections are pure page-level config: the drawer edits page-level state that
 * `handleSavePage` persists into `config.variables` / `config.dataSources` via `mergeConfig`.
 */
import React from 'react'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { useI18n } from '@/context/I18nContext'
import type { PageDataSource, PageVariable } from '../../pageConfig'
import { VariablesSection } from './VariablesSection'
import { DataSourcesSection } from './DataSourcesSection'
import { AccessSection } from './AccessSection'
import { HomePageSection } from './HomePageSection'

export interface PageSettingsDrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  variables: PageVariable[]
  dataSources: PageDataSource[]
  onVariablesChange: (next: PageVariable[]) => void
  onDataSourcesChange: (next: PageDataSource[]) => void
  /** Slice 1h: the page's `config.access.requiredPermission` (undefined ⇒ no restriction). */
  requiredPermission?: string
  onRequiredPermissionChange: (permission: string | undefined) => void
  /** Whether this page overrides the app's default home (`config.isHomePage`). */
  isHomePage: boolean
  onIsHomePageChange: (isHomePage: boolean) => void
}

export function PageSettingsDrawer({
  open,
  onOpenChange,
  variables,
  dataSources,
  onVariablesChange,
  onDataSourcesChange,
  requiredPermission,
  onRequiredPermissionChange,
  isHomePage,
  onIsHomePageChange,
}: PageSettingsDrawerProps): React.ReactElement {
  const { t } = useI18n()
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full overflow-y-auto p-6 sm:max-w-md"
        data-testid="page-settings-drawer"
      >
        <SheetHeader className="p-0">
          <SheetTitle>{t('builder.pageSettings.title')}</SheetTitle>
          <SheetDescription className="sr-only">{t('builder.pageSettings.title')}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-6">
          <VariablesSection variables={variables} onChange={onVariablesChange} />
          <DataSourcesSection dataSources={dataSources} onChange={onDataSourcesChange} />
          <AccessSection
            requiredPermission={requiredPermission}
            onChange={onRequiredPermissionChange}
          />
          <HomePageSection isHomePage={isHomePage} onChange={onIsHomePageChange} />
        </div>
      </SheetContent>
    </Sheet>
  )
}
