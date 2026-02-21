import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { render, screen, waitFor } from '@testing-library/react'
import { GovernorLimitsPage } from './GovernorLimitsPage'

const mockStatus = {
  limits: {
    apiCallsPerDay: 100000,
    storageGb: 10,
    maxUsers: 100,
    maxCollections: 200,
    maxFieldsPerCollection: 500,
    maxWorkflows: 50,
    maxReports: 200,
  },
  apiCallsUsed: 5000,
  apiCallsLimit: 100000,
  usersUsed: 25,
  usersLimit: 100,
  collectionsUsed: 10,
  collectionsLimit: 200,
}

function setupAxiosMocks() {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/control/governor-limits')) {
      return Promise.resolve({ data: mockStatus })
    }
    return Promise.resolve({ data: {} })
  })
  mockAxios.put.mockImplementation((url: string) => {
    if (url.includes('/control/governor-limits')) {
      return Promise.resolve({ data: mockStatus.limits })
    }
    return Promise.resolve({ data: {} })
  })
}

describe('GovernorLimitsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    setupAxiosMocks()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  describe('Rendering', () => {
    it('renders the page with title', async () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Governor Limits')).toBeInTheDocument()
      })
    })

    it('shows loading state initially', () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })
      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })

    it('displays usage metrics after loading', async () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('API Calls (Today)')).toBeInTheDocument()
      })

      expect(screen.getByText('Users')).toBeInTheDocument()
      expect(screen.getByText('Collections')).toBeInTheDocument()
    })

    it('displays all limits table', async () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('All Limits')).toBeInTheDocument()
      })

      expect(screen.getByText('API Calls Per Day')).toBeInTheDocument()
      expect(screen.getByText('Storage (GB)')).toBeInTheDocument()
      expect(screen.getByText('Maximum Users')).toBeInTheDocument()
      expect(screen.getByText('Maximum Collections')).toBeInTheDocument()
      expect(screen.getByText('Maximum Fields Per Collection')).toBeInTheDocument()
      expect(screen.getByText('Maximum Workflows')).toBeInTheDocument()
      expect(screen.getByText('Maximum Reports')).toBeInTheDocument()
    })

    it('displays limit values', async () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getAllByText('100,000').length).toBeGreaterThanOrEqual(1)
      })
    })
  })

  describe('Error handling', () => {
    it('shows error message on fetch failure', async () => {
      mockAxios.get.mockImplementation(() =>
        Promise.reject({ isAxiosError: true, response: { status: 500 }, message: 'fail' })
      )

      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Failed to load governor limits.')).toBeInTheDocument()
      })
    })
  })

  describe('Edit functionality', () => {
    it('does not show edit button for non-PLATFORM_ADMIN users', async () => {
      render(<GovernorLimitsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Governor Limits')).toBeInTheDocument()
      })

      expect(screen.queryByText('Edit Limits')).not.toBeInTheDocument()
    })
  })
})
