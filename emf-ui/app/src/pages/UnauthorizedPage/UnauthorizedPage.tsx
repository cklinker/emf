/**
 * UnauthorizedPage Component
 *
 * Displays when a user attempts to access a resource they don't have permission for.
 */

import React from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { ShieldOff } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useAuth } from '../../context/AuthContext'
import styles from './UnauthorizedPage.module.css'

/**
 * Props for the UnauthorizedPage component
 */
export interface UnauthorizedPageProps {
  /** Optional custom title */
  title?: string
  /** Optional custom message */
  message?: string
}

/**
 * UnauthorizedPage Component
 *
 * Shows an error message when the user lacks required permissions.
 * Provides options to go back, go home, or log out.
 */
export function UnauthorizedPage({ title, message }: UnauthorizedPageProps): React.ReactElement {
  const { t } = useI18n()
  const { logout, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  // Get required permissions from location state
  const state = location.state as {
    from?: { pathname: string }
    requiredRoles?: string[]
    requiredPolicies?: string[]
  } | null

  const handleGoBack = () => {
    navigate(-1)
  }

  const handleGoHome = () => {
    navigate('/')
  }

  const handleLogout = async () => {
    await logout()
  }

  return (
    <div className={styles.unauthorizedPage} data-testid="unauthorized-page">
      <div className={styles.container}>
        {/* Icon */}
        <div className={styles.icon} aria-hidden="true">
          <ShieldOff size={48} />
        </div>

        {/* Title */}
        <h1 className={styles.title}>{title || t('unauthorized.title')}</h1>

        {/* Message */}
        <p className={styles.message}>{message || t('unauthorized.message')}</p>

        {/* User info */}
        {user && (
          <p className={styles.userInfo}>
            {t('unauthorized.loggedInAs', { email: user.email || user.name || 'Unknown' })}
          </p>
        )}

        {/* Required permissions info */}
        {(state?.requiredRoles || state?.requiredPolicies) && (
          <div className={styles.requirements}>
            <p className={styles.requirementsTitle}>{t('unauthorized.requiredPermissions')}</p>
            {state.requiredRoles && state.requiredRoles.length > 0 && (
              <div className={styles.requirementsList}>
                <strong>{t('unauthorized.requiredRoles')}:</strong>
                <ul>
                  {state.requiredRoles.map((role) => (
                    <li key={role}>{role}</li>
                  ))}
                </ul>
              </div>
            )}
            {state.requiredPolicies && state.requiredPolicies.length > 0 && (
              <div className={styles.requirementsList}>
                <strong>{t('unauthorized.requiredPolicies')}:</strong>
                <ul>
                  {state.requiredPolicies.map((policy) => (
                    <li key={policy}>{policy}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* Actions */}
        <div className={styles.actions}>
          <button type="button" className={styles.primaryButton} onClick={handleGoHome}>
            {t('unauthorized.goHome')}
          </button>
          <button type="button" className={styles.secondaryButton} onClick={handleGoBack}>
            {t('unauthorized.goBack')}
          </button>
          <button type="button" className={styles.textButton} onClick={handleLogout}>
            {t('unauthorized.logout')}
          </button>
        </div>

        {/* Help link */}
        <p className={styles.helpText}>
          {t('unauthorized.helpText')}{' '}
          <Link to="/help" className={styles.helpLink}>
            {t('unauthorized.contactSupport')}
          </Link>
        </p>
      </div>
    </div>
  )
}

export default UnauthorizedPage
