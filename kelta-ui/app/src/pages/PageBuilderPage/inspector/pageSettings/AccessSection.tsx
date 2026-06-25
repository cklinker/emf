/**
 * Page-settings "Access" section (slice 1h). Sets `config.access.requiredPermission` from the tenant's
 * system-permission catalog. Selecting "Anyone (published)" clears the restriction. The backend gates
 * render on this permission and returns 404 (not 403) to a denied caller, so a restricted page's
 * existence is not leaked — surfaced to the author via the hint below.
 */
import React from 'react'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/context/I18nContext'
import { SYSTEM_PERMISSIONS } from '@/components/SecurityEditor/SystemPermissionChecklist'

const ANYONE = '__anyone__'

export interface AccessSectionProps {
  requiredPermission: string | undefined
  onChange: (permission: string | undefined) => void
}

export function AccessSection({
  requiredPermission,
  onChange,
}: AccessSectionProps): React.ReactElement {
  const { t } = useI18n()
  return (
    <section className="flex flex-col gap-2" data-testid="page-settings-access">
      <h3 className="text-sm font-semibold text-foreground">
        {t('builder.pageSettings.access.title')}
      </h3>
      <div className="flex flex-col gap-1">
        <Label htmlFor="page-access-permission" className="text-xs text-muted-foreground">
          {t('builder.pageSettings.access.label')}
        </Label>
        <select
          id="page-access-permission"
          className="rounded-md border border-border bg-background px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          value={requiredPermission || ANYONE}
          onChange={(e) => onChange(e.target.value === ANYONE ? undefined : e.target.value)}
          data-testid="page-access-select"
        >
          <option value={ANYONE}>{t('builder.pageSettings.access.anyone')}</option>
          {SYSTEM_PERMISSIONS.map((perm) => (
            <option key={perm.name} value={perm.name}>
              {perm.label}
            </option>
          ))}
        </select>
        <p className="text-xs text-muted-foreground">{t('builder.pageSettings.access.hint')}</p>
      </div>
    </section>
  )
}
