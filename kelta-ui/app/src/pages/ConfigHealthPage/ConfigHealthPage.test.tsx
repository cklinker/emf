/**
 * ConfigHealthPage Tests — renders the configuration-health score + findings from
 * GET /api/config-health (Rec 6). API flows through the SDK's mocked Axios.
 */

import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { ConfigHealthPage } from './ConfigHealthPage'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'

const report = {
  score: 72,
  rulesRun: 5,
  findingCount: 2,
  summary: { error: 1, warning: 1, info: 0 },
  findings: [
    {
      ruleKey: 'circular-master-detail',
      severity: 'ERROR',
      title: 'Circular master-detail relationship',
      detail: 'Orders → Items → Orders forms a cycle.',
      targetType: 'COLLECTION',
      targetId: 'c1',
      targetName: 'Orders',
    },
    {
      ruleKey: 'collection-without-layout',
      severity: 'WARNING',
      title: 'Collection has no layout',
      detail: 'Items has no page layout.',
      targetType: 'COLLECTION',
      targetId: 'c2',
      targetName: 'Items',
    },
  ],
}

// apiClient.get unwraps the HTTP body once; the body is the { data: report } envelope.
const httpBody = { data: report }

describe('ConfigHealthPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  it('renders the score and findings', async () => {
    mockAxios.get.mockResolvedValue({ data: httpBody })
    render(<ConfigHealthPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.getByTestId('config-health-score')).toBeInTheDocument())
    expect(screen.getByTestId('config-health-score')).toHaveTextContent('72')
    expect(screen.getByText('Circular master-detail relationship')).toBeInTheDocument()
    expect(screen.getByText('Collection has no layout')).toBeInTheDocument()
    expect(screen.getByTestId('config-health-findings')).toBeInTheDocument()
  })

  it('sorts findings with errors before warnings', async () => {
    mockAxios.get.mockResolvedValue({ data: httpBody })
    render(<ConfigHealthPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.getAllByTestId('config-health-finding')).toHaveLength(2))
    const findings = screen.getAllByTestId('config-health-finding')
    expect(findings[0]).toHaveTextContent('Circular master-detail relationship')
    expect(findings[1]).toHaveTextContent('Collection has no layout')
  })

  it('renders the healthy empty state when there are no findings', async () => {
    mockAxios.get.mockResolvedValue({
      data: { data: { ...report, findings: [], findingCount: 0 } },
    })
    render(<ConfigHealthPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.getByTestId('config-health-empty')).toBeInTheDocument())
    expect(screen.getByText(/everything looks healthy/i)).toBeInTheDocument()
  })

  it('renders an error message when the scan fails to load', async () => {
    mockAxios.get.mockRejectedValue(new Error('boom'))
    render(<ConfigHealthPage />, { wrapper: createTestWrapper() })

    await waitFor(() => expect(screen.queryByTestId('config-health-score')).not.toBeInTheDocument())
    expect(screen.queryByTestId('config-health-findings')).not.toBeInTheDocument()
  })
})
