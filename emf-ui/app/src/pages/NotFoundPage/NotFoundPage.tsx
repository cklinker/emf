/**
 * NotFoundPage Component
 *
 * Displays a 404 error page when a route is not found.
 */

import React from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { useI18n } from '../../context/I18nContext'
import styles from './NotFoundPage.module.css'

/**
 * Props for the NotFoundPage component
 */
export interface NotFoundPageProps {
  /** Optional custom title */
  title?: string
  /** Optional custom message */
  message?: string
}

/**
 * NotFoundPage Component
 *
 * Shows a 404 error message with navigation options.
 */
export function NotFoundPage({ title, message }: NotFoundPageProps): React.ReactElement {
  const { t } = useI18n()
  const navigate = useNavigate()
  const location = useLocation()

  const handleGoBack = () => {
    navigate(-1)
  }

  const handleGoHome = () => {
    navigate('/')
  }

  return (
    <div className={styles.notFoundPage} data-testid="not-found-page">
      <div className={styles.container}>
        {/* Error code */}
        <p className={styles.errorCode}>404</p>

        {/* Title */}
        <h1 className={styles.title}>{title || t('notFound.title')}</h1>

        {/* Message */}
        <p className={styles.message}>
          {message || t('notFound.message')}
          <code className={styles.path}>{location.pathname}</code>
        </p>

        {/* Actions */}
        <div className={styles.actions}>
          <button type="button" className={styles.primaryButton} onClick={handleGoHome}>
            {t('notFound.goHome')}
          </button>
          <button type="button" className={styles.secondaryButton} onClick={handleGoBack}>
            {t('notFound.goBack')}
          </button>
        </div>

        {/* Suggestions */}
        <div className={styles.suggestions}>
          <p className={styles.suggestionsTitle}>{t('notFound.suggestions')}</p>
          <ul className={styles.suggestionsList}>
            <li>
              <Link to="/" className={styles.suggestionLink}>
                {t('notFound.dashboard')}
              </Link>
            </li>
            <li>
              <Link to="/collections" className={styles.suggestionLink}>
                {t('notFound.collections')}
              </Link>
            </li>
            <li>
              <Link to="/resources" className={styles.suggestionLink}>
                {t('notFound.resources')}
              </Link>
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}

export default NotFoundPage
