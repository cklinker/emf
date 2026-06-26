/**
 * Page-settings "App home" section. Toggles `config.isHomePage` — when on, this page overrides the
 * end-user app's default landing page (`/app/home`). At most one page per tenant should be the home;
 * the runtime resolver picks the first published + active page that sets the flag.
 */
import React from 'react'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/context/I18nContext'

export interface HomePageSectionProps {
  isHomePage: boolean
  onChange: (isHomePage: boolean) => void
}

export function HomePageSection({
  isHomePage,
  onChange,
}: HomePageSectionProps): React.ReactElement {
  const { t } = useI18n()
  return (
    <section className="flex flex-col gap-2" data-testid="page-settings-home">
      <h3 className="text-sm font-semibold text-foreground">
        {t('builder.pageSettings.home.title')}
      </h3>
      <div className="flex items-start gap-2">
        <input
          id="page-is-home"
          type="checkbox"
          className="mt-0.5 h-4 w-4 accent-primary"
          checked={isHomePage}
          onChange={(e) => onChange(e.target.checked)}
          data-testid="page-home-checkbox"
        />
        <div className="flex flex-col gap-1">
          <Label htmlFor="page-is-home" className="text-sm text-foreground">
            {t('builder.pageSettings.home.label')}
          </Label>
          <p className="text-xs text-muted-foreground">{t('builder.pageSettings.home.hint')}</p>
        </div>
      </div>
    </section>
  )
}
