/**
 * NoTenantPage
 *
 * Displayed when a user visits the root URL without a tenant slug.
 * Provides a clear error message indicating a tenant identifier is required.
 */

import React from 'react'

export function NoTenantPage(): React.ReactElement {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        fontFamily: 'Inter, system-ui, sans-serif',
        padding: '2rem',
        textAlign: 'center',
      }}
    >
      <h1 style={{ fontSize: '2rem', marginBottom: '1rem', color: '#1a1a2e' }}>Tenant Required</h1>
      <p style={{ fontSize: '1.125rem', color: '#555', maxWidth: '480px', lineHeight: 1.6 }}>
        A tenant identifier is required in the URL. Please navigate to a valid tenant URL, for
        example:{' '}
        <code style={{ background: '#f0f0f0', padding: '2px 6px', borderRadius: '4px' }}>
          /your-tenant/
        </code>
      </p>
    </div>
  )
}
