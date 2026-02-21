/**
 * LoginPage Component
 *
 * Displays the login page with OIDC provider selection.
 * Redirects to the OIDC provider for authentication.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import React, { useEffect, useRef, useState, useMemo } from 'react'
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { getTenantSlug } from '../../context/TenantContext'
import { useConfig } from '../../context/ConfigContext'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../../components/LoadingSpinner'
import { ErrorMessage } from '../../components/ErrorMessage'
import styles from './LoginPage.module.css'

/**
 * Props for the LoginPage component
 */
export interface LoginPageProps {
  /** Optional custom title */
  title?: string
}

/**
 * LoginPage Component
 *
 * Handles user authentication via OIDC providers.
 * Shows provider selection when multiple providers are configured.
 */
export function LoginPage({ title }: LoginPageProps): React.ReactElement {
  const { isAuthenticated, isLoading: authLoading, login, error: authError } = useAuth()
  const { config, isLoading: configLoading } = useConfig()
  const { t } = useI18n()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null)
  const [loginError, setLoginError] = useState<Error | null>(null)
  const [isLoggingIn, setIsLoggingIn] = useState(false)

  // Check if user just logged out — skip auto-login so they can pick a different account.
  // Uses sessionStorage flag (set by AuthContext.logout) as primary signal, because
  // the OIDC provider may strip query params from the post_logout_redirect_uri.
  // Also checks URL param as fallback.
  const justLoggedOut = useMemo(
    () =>
      sessionStorage.getItem('emf_auth_just_logged_out') === 'true' ||
      searchParams.get('logged_out') === 'true',
    [searchParams]
  )

  // Get the redirect path from location state
  const from =
    (location.state as { from?: { pathname: string } })?.from?.pathname || `/${getTenantSlug()}/app`

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated && !authLoading) {
      navigate(from, { replace: true })
    }
  }, [isAuthenticated, authLoading, navigate, from])

  // Get OIDC providers from config
  const providers = config?.oidcProviders || []

  // Track whether auto-login has been attempted to prevent duplicate calls
  const autoLoginAttempted = useRef(false)

  // Auto-login if only one provider (skip if there was a previous error or user just logged out)
  useEffect(() => {
    if (
      !authLoading &&
      !configLoading &&
      providers.length === 1 &&
      !isAuthenticated &&
      !authError &&
      !autoLoginAttempted.current &&
      !justLoggedOut
    ) {
      autoLoginAttempted.current = true
      // Clear any persisted login error and redirect directly
      sessionStorage.removeItem('emf_auth_login_error')
      login(providers[0].id).catch(() => {
        // Error will be shown via authError from AuthContext
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, configLoading, isAuthenticated, authError, justLoggedOut])

  /**
   * Handle login with a specific provider
   */
  const handleLogin = async (providerId: string) => {
    setIsLoggingIn(true)
    setLoginError(null)
    setSelectedProvider(providerId)

    // Clear any persisted login error and logout flag (user is explicitly logging in)
    sessionStorage.removeItem('emf_auth_login_error')
    sessionStorage.removeItem('emf_auth_just_logged_out')

    try {
      await login(providerId)
    } catch (err) {
      setLoginError(err instanceof Error ? err : new Error('Login failed'))
      setIsLoggingIn(false)
    }
  }

  // Show loading while checking auth or config
  if (authLoading || configLoading) {
    return (
      <div className={styles.loginPage} data-testid="login-page">
        <div className={styles.container}>
          <LoadingSpinner size="large" label={t('login.checking')} />
        </div>
      </div>
    )
  }

  // Show error if auth error occurred
  const error = loginError || authError

  return (
    <div className={styles.loginPage} data-testid="login-page">
      <div className={styles.container}>
        {/* Logo and branding */}
        <div className={styles.branding}>
          {config?.branding?.logoUrl && (
            <img
              src={config.branding.logoUrl}
              alt={config.branding.applicationName || 'Application logo'}
              className={styles.logo}
            />
          )}
          <h1 className={styles.title}>
            {title || config?.branding?.applicationName || t('login.title')}
          </h1>
        </div>

        {/* Error message */}
        {error && (
          <div className={styles.error}>
            <ErrorMessage
              error={error}
              onRetry={() => {
                setLoginError(null)
                if (selectedProvider) {
                  handleLogin(selectedProvider)
                }
              }}
            />
          </div>
        )}

        {/* Provider selection */}
        <div className={styles.providers}>
          <h2 className={styles.subtitle}>{t('login.selectProvider')}</h2>

          {providers.length === 0 ? (
            <p className={styles.noProviders}>{t('login.noProviders')}</p>
          ) : (
            <div className={styles.providerList}>
              {providers.map((provider) => (
                <button
                  key={provider.id}
                  type="button"
                  className={styles.providerButton}
                  onClick={() => handleLogin(provider.id)}
                  disabled={isLoggingIn}
                  aria-busy={isLoggingIn && selectedProvider === provider.id}
                >
                  {isLoggingIn && selectedProvider === provider.id ? (
                    <LoadingSpinner size="small" />
                  ) : (
                    <span className={styles.providerIcon} aria-hidden="true">
                      <KeyRound size={20} />
                    </span>
                  )}
                  <span className={styles.providerName}>{provider.name}</span>
                  <span className={styles.providerIssuer}>{provider.issuer}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <footer className={styles.footer}>
          <p className={styles.footerText}>{t('login.footer')}</p>
        </footer>
      </div>
    </div>
  )
}

export default LoginPage
