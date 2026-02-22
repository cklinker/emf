/**
 * UserMenu Component
 *
 * Shared user menu dropdown used by both the admin Header and
 * the end-user TopNavBar. Shows user avatar (OIDC picture, Gravatar,
 * or initials fallback), language selector, theme toggle, and
 * context-specific navigation items.
 */

import { useState, useMemo, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Globe,
  Sun,
  Moon,
  Monitor,
  User as UserIcon,
  LogOut,
  Settings,
  ArrowLeft,
  Check,
} from 'lucide-react'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/context/I18nContext'
import { useTheme } from '@/context/ThemeContext'
import type { ThemeMode } from '@/context/ThemeContext'
import { useSystemPermissions } from '@/hooks/useSystemPermissions'
import { getGravatarUrl } from '@/utils/gravatar'
import type { User } from '@/types/auth'
import { ChevronDown } from 'lucide-react'

export interface UserMenuProps {
  /** Full User object (from auth context) */
  user: User
  /** Callback when logout is requested */
  onLogout: () => void
  /** Controls context-specific items (admin shows "Back to App", app shows "Switch to Setup") */
  variant: 'admin' | 'app'
  /** Hide user name on mobile (default: false) */
  compact?: boolean
}

/**
 * Get user initials for the avatar fallback.
 */
function getUserInitials(name?: string, email?: string): string {
  if (name) {
    const parts = name.split(' ')
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
    }
    return name.substring(0, 2).toUpperCase()
  }
  if (email) {
    return email.substring(0, 2).toUpperCase()
  }
  return 'U'
}

export function UserMenu({
  user,
  onLogout,
  variant,
  compact = false,
}: UserMenuProps): React.ReactElement {
  const navigate = useNavigate()
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const { t, locale, setLocale, supportedLocales, getLocaleDisplayName } = useI18n()
  const { mode, setMode, resolvedMode } = useTheme()
  const { hasPermission } = useSystemPermissions()
  const [gravatarFailed, setGravatarFailed] = useState(false)

  const canAccessSetup = hasPermission('VIEW_SETUP')

  // Compute Gravatar URL once (only when email changes)
  const gravatarUrl = useMemo(() => getGravatarUrl(user.email, 64), [user.email])

  // Resolve the avatar image URL: OIDC picture > Gravatar > initials fallback
  const avatarImageUrl = user.picture || (!gravatarFailed && gravatarUrl) || null

  const displayName = user.name || user.email || 'User'

  const userInitials = getUserInitials(user.name, user.email)

  const handleThemeChange = useCallback(
    (newMode: ThemeMode) => {
      setMode(newMode)
    },
    [setMode]
  )

  const handleLocaleChange = useCallback(
    (newLocale: string) => {
      setLocale(newLocale)
    },
    [setLocale]
  )

  const handleNavigateToSetup = useCallback(() => {
    navigate(`/${tenantSlug}/setup`)
  }, [navigate, tenantSlug])

  const handleNavigateToApp = useCallback(() => {
    navigate(`/${tenantSlug}/app`)
  }, [navigate, tenantSlug])

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="flex h-8 items-center gap-2 px-2"
          data-testid="user-menu-button"
        >
          <Avatar className="h-7 w-7">
            {avatarImageUrl && (
              <AvatarImage
                src={avatarImageUrl}
                alt=""
                onError={() => setGravatarFailed(true)}
                data-testid="user-avatar-image"
              />
            )}
            <AvatarFallback
              className="bg-primary text-[10px] text-primary-foreground"
              data-testid="user-avatar-initials"
            >
              {userInitials}
            </AvatarFallback>
          </Avatar>
          {!compact && (
            <span
              className="hidden max-w-[150px] overflow-hidden text-ellipsis whitespace-nowrap text-sm font-medium md:inline"
              data-testid="user-name"
            >
              {displayName}
            </span>
          )}
          <ChevronDown className="hidden h-3 w-3 text-muted-foreground md:block" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56" data-testid="user-dropdown-menu">
        {/* User info header */}
        <DropdownMenuLabel>
          <div className="flex flex-col space-y-1">
            <p className="text-sm font-medium leading-none">{displayName}</p>
            {user.email && (
              <p className="text-xs leading-none text-muted-foreground">{user.email}</p>
            )}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />

        {/* Language selector submenu */}
        <DropdownMenuSub>
          <DropdownMenuSubTrigger data-testid="language-menu-trigger">
            <Globe className="mr-2 h-4 w-4" />
            {t('userMenu.language')}
          </DropdownMenuSubTrigger>
          <DropdownMenuSubContent data-testid="language-menu">
            {supportedLocales.map((loc) => (
              <DropdownMenuItem
                key={loc}
                onClick={() => handleLocaleChange(loc)}
                data-testid={`language-option-${loc}`}
              >
                {getLocaleDisplayName(loc)}
                {locale === loc && <Check className="ml-auto h-4 w-4 text-primary" />}
              </DropdownMenuItem>
            ))}
          </DropdownMenuSubContent>
        </DropdownMenuSub>

        {/* Theme selector submenu */}
        <DropdownMenuSub>
          <DropdownMenuSubTrigger data-testid="theme-menu-trigger">
            {resolvedMode === 'dark' ? (
              <Moon className="mr-2 h-4 w-4" />
            ) : (
              <Sun className="mr-2 h-4 w-4" />
            )}
            {t('userMenu.theme')}
          </DropdownMenuSubTrigger>
          <DropdownMenuSubContent data-testid="theme-menu">
            <DropdownMenuItem
              onClick={() => handleThemeChange('light')}
              data-testid="theme-option-light"
            >
              <Sun className="mr-2 h-4 w-4" />
              {t('userMenu.lightMode')}
              {mode === 'light' && <Check className="ml-auto h-4 w-4 text-primary" />}
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleThemeChange('dark')}
              data-testid="theme-option-dark"
            >
              <Moon className="mr-2 h-4 w-4" />
              {t('userMenu.darkMode')}
              {mode === 'dark' && <Check className="ml-auto h-4 w-4 text-primary" />}
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleThemeChange('system')}
              data-testid="theme-option-system"
            >
              <Monitor className="mr-2 h-4 w-4" />
              {t('userMenu.systemMode')}
              {mode === 'system' && <Check className="ml-auto h-4 w-4 text-primary" />}
            </DropdownMenuItem>
          </DropdownMenuSubContent>
        </DropdownMenuSub>

        <DropdownMenuSeparator />

        {/* Profile */}
        <DropdownMenuItem data-testid="profile-menu-item">
          <UserIcon className="mr-2 h-4 w-4" />
          {t('userMenu.profile')}
        </DropdownMenuItem>

        {/* Context-specific navigation */}
        {variant === 'app' && canAccessSetup && (
          <DropdownMenuItem onClick={handleNavigateToSetup} data-testid="switch-to-setup">
            <Settings className="mr-2 h-4 w-4" />
            {t('userMenu.switchToSetup')}
          </DropdownMenuItem>
        )}

        {variant === 'admin' && (
          <DropdownMenuItem onClick={handleNavigateToApp} data-testid="back-to-app-menu">
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('userMenu.backToApp')}
          </DropdownMenuItem>
        )}

        <DropdownMenuSeparator />

        {/* Logout */}
        <DropdownMenuItem onClick={onLogout} data-testid="logout-button">
          <LogOut className="mr-2 h-4 w-4" />
          {t('userMenu.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default UserMenu
