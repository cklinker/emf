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

import React, { useEffect, useRef, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
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
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null)
  const [loginError, setLoginError] = useState<Error | null>(null)
  const [isLoggingIn, setIsLoggingIn] = useState(false)

  // Get the redirect path from location state
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/'

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

  // Auto-login if only one provider (skip if there was a previous error)
  useEffect(() => {
    if (
      !authLoading &&
      !configLoading &&
      providers.length === 1 &&
      !isAuthenticated &&
      !authError &&
      !autoLoginAttempted.current
    ) {
      autoLoginAttempted.current = true
      // Clear any persisted login error and redirect directly
      sessionStorage.removeItem('emf_auth_login_error')
      login(providers[0].id).catch(() => {
        // Error will be shown via authError from AuthContext
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, configLoading, isAuthenticated, authError])

  /**
   * Handle login with a specific provider
   */
  const handleLogin = async (providerId: string) => {
    setIsLoggingIn(true)
    setLoginError(null)
    setSelectedProvider(providerId)

    // Clear any persisted login error (user is explicitly retrying)
    sessionStorage.removeItem('emf_auth_login_error')

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
                      🔐
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
