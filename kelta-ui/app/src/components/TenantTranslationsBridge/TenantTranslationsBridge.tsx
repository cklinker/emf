/**
 * TenantTranslationsBridge (app-intelligence slice 4)
 *
 * Pushes the bootstrap config's tenant-authored translation overlay into
 * I18nContext. Lives as a separate bridge (instead of I18nProvider reading
 * ConfigContext itself) so I18nProvider stays mountable without a ConfigProvider —
 * every existing test harness and the login shell keep working unchanged.
 * Renders nothing.
 */
import { useEffect } from 'react'
import { useConfig } from '@/context/ConfigContext'
import { useI18n } from '@/context/I18nContext'

export function TenantTranslationsBridge(): null {
  const { config } = useConfig()
  const { setTenantOverlay } = useI18n()
  const translations = config?.translations

  useEffect(() => {
    if (translations && Object.keys(translations).length > 0) {
      setTenantOverlay(translations)
    }
  }, [translations, setTenantOverlay])

  return null
}
