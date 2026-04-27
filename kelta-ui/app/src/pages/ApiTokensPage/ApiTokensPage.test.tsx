/**
 * Focused tests for the helpers added in Phase 10 of the kelta-mcp
 * rollout: the `claude mcp add` command builder + the MCP base URL
 * resolver. These compose into the "Use with Claude Code" panel
 * shown after token creation.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { resolveMcpBaseUrl, buildMcpAddCommand } from './ApiTokensPage'

describe('buildMcpAddCommand', () => {
  it('builds a user-profile command with the token in single quotes', () => {
    const cmd = buildMcpAddCommand('user', 'https://emf.rzware.com', 'klt_abc123')
    expect(cmd).toBe(
      "claude mcp add kelta-user --transport http --url https://emf.rzware.com/mcp/user --header 'Authorization: Bearer klt_abc123'"
    )
  })

  it('builds an admin-profile command at the right URL', () => {
    const cmd = buildMcpAddCommand('admin', 'https://emf.rzware.com', 'klt_xyz')
    expect(cmd).toContain('--url https://emf.rzware.com/mcp/admin')
    expect(cmd).toContain('kelta-admin')
  })

  it('uses single quotes around the auth header so the token is opaque to the shell', () => {
    const cmd = buildMcpAddCommand('user', 'https://emf.rzware.com', 'klt_token_with$pecial')
    expect(cmd).toContain("'Authorization: Bearer klt_token_with$pecial'")
  })
})

describe('resolveMcpBaseUrl', () => {
  const originalEnv = import.meta.env.VITE_API_BASE_URL

  beforeEach(() => {
    // jsdom always sets window.location; reset the env var explicitly.
    vi.stubEnv('VITE_API_BASE_URL', '')
  })

  afterEach(() => {
    if (originalEnv !== undefined) {
      vi.stubEnv('VITE_API_BASE_URL', originalEnv)
    }
    vi.unstubAllEnvs()
  })

  it('prefers VITE_API_BASE_URL when set', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://emf.rzware.com')
    expect(resolveMcpBaseUrl()).toBe('https://emf.rzware.com')
  })

  it('falls back to window.location.origin when env is empty (local dev)', () => {
    expect(resolveMcpBaseUrl()).toBe(window.location.origin)
  })
})
