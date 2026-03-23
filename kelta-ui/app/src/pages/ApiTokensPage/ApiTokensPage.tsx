import React, { useState, useCallback, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog } from '../../components'
import type { PersonalAccessToken } from '@kelta/sdk'
import { cn } from '@/lib/utils'

interface TokenFormData {
  name: string
  expiresInDays: number
}

interface FormErrors {
  name?: string
}

export interface ApiTokensPageProps {
  testId?: string
}

function validateForm(data: TokenFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 200) {
    errors.name = 'Name must be 200 characters or fewer'
  }
  return errors
}

export function ApiTokensPage({
  testId = 'api-tokens-page',
}: ApiTokensPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { keltaClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false)
  const [tokenToRevoke, setTokenToRevoke] = useState<PersonalAccessToken | null>(null)
  const [createdToken, setCreatedToken] = useState<string | null>(null)

  const {
    data: tokens,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['personal-tokens'],
    queryFn: () => keltaClient.admin.personalTokens.list(),
  })

  const tokenList: PersonalAccessToken[] = tokens ?? []

  const createMutation = useMutation({
    mutationFn: (data: TokenFormData) =>
      keltaClient.admin.personalTokens.create({
        name: data.name,
        expiresInDays: data.expiresInDays,
      }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['personal-tokens'] })
      showToast('Token created successfully', 'success')
      setIsFormOpen(false)
      setCreatedToken(result.token)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to create token', 'error')
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (tokenId: string) => keltaClient.admin.personalTokens.revoke(tokenId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personal-tokens'] })
      showToast('Token revoked', 'success')
      setRevokeDialogOpen(false)
      setTokenToRevoke(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to revoke token', 'error')
    },
  })

  const handleRevoke = useCallback((token: PersonalAccessToken) => {
    setTokenToRevoke(token)
    setRevokeDialogOpen(true)
  }, [])

  return (
    <div className="mx-auto max-w-[1200px] space-y-6 p-6" data-testid={testId}>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">API Tokens</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Create personal access tokens for programmatic API access.
          </p>
        </div>
        <button
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          onClick={() => setIsFormOpen(true)}
          data-testid="create-token-button"
        >
          Create Token
        </button>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive bg-destructive/10 p-4 text-sm text-destructive">
          Failed to load tokens. Please try again.
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center p-12 text-muted-foreground">
          Loading...
        </div>
      ) : tokenList.length === 0 ? (
        <div className="rounded-lg border border-border bg-card p-12 text-center text-muted-foreground">
          No API tokens yet. Create one to get started.
        </div>
      ) : (
        <div className="rounded-lg border border-border bg-card">
          <table className="w-full text-sm" data-testid="tokens-table">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Name</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Token</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Expires</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Last Used</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Created</th>
                <th className="px-4 py-3 text-right font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {tokenList.map((token) => (
                <tr key={token.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 font-medium text-foreground">{token.name}</td>
                  <td className="px-4 py-3">
                    <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
                      {token.tokenPrefix}...
                    </code>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {new Date(token.expiresAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleDateString() : 'Never'}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {new Date(token.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      className="rounded-md border border-destructive px-3 py-1 text-xs font-medium text-destructive hover:bg-destructive/10 disabled:opacity-50"
                      onClick={() => handleRevoke(token)}
                      disabled={revokeMutation.isPending}
                      data-testid={`revoke-token-${token.id}`}
                    >
                      Revoke
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <CreateTokenForm
          onSubmit={(data) => createMutation.mutate(data)}
          onCancel={() => setIsFormOpen(false)}
          isSubmitting={createMutation.isPending}
        />
      )}

      {createdToken && (
        <TokenCreatedDialog token={createdToken} onClose={() => setCreatedToken(null)} />
      )}

      <ConfirmDialog
        open={revokeDialogOpen}
        title="Revoke Token"
        message={`Are you sure you want to revoke "${tokenToRevoke?.name}"? This action cannot be undone. Any applications using this token will lose access.`}
        confirmLabel="Revoke"
        onConfirm={() => tokenToRevoke && revokeMutation.mutate(tokenToRevoke.id)}
        onCancel={() => {
          setRevokeDialogOpen(false)
          setTokenToRevoke(null)
        }}
        variant="danger"
      />
    </div>
  )
}

function CreateTokenForm({
  onSubmit,
  onCancel,
  isSubmitting,
}: {
  onSubmit: (data: TokenFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}): React.ReactElement {
  const [formData, setFormData] = useState<TokenFormData>({
    name: '',
    expiresInDays: 90,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData)
      setErrors(validationErrors)
      setTouched({ name: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit]
  )

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      role="presentation"
      data-testid="create-token-form-overlay"
    >
      <div
        className="w-full max-w-[500px] rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        data-testid="create-token-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 className="m-0 text-xl font-semibold text-foreground">Create API Token</h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <div>
              <label
                htmlFor="token-name"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Token Name <span className="ml-0.5 text-destructive">*</span>
              </label>
              <input
                ref={nameInputRef}
                id="token-name"
                type="text"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.name && errors.name ? 'border-destructive' : 'border-border'
                )}
                value={formData.name}
                onChange={(e) => {
                  setFormData((prev) => ({ ...prev, name: e.target.value }))
                  if (errors.name) setErrors((prev) => ({ ...prev, name: undefined }))
                }}
                onBlur={() => {
                  setTouched((prev) => ({ ...prev, name: true }))
                  const errs = validateForm(formData)
                  if (errs.name) setErrors((prev) => ({ ...prev, name: errs.name }))
                }}
                placeholder="e.g. CI/CD Pipeline"
                disabled={isSubmitting}
                data-testid="token-name-input"
              />
              {touched.name && errors.name && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="token-expiry"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Expiration
              </label>
              <select
                id="token-expiry"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                value={formData.expiresInDays}
                onChange={(e) =>
                  setFormData((prev) => ({ ...prev, expiresInDays: parseInt(e.target.value, 10) }))
                }
                disabled={isSubmitting}
                data-testid="token-expiry-select"
              >
                <option value={30}>30 days</option>
                <option value={60}>60 days</option>
                <option value={90}>90 days</option>
                <option value={180}>180 days</option>
                <option value={365}>365 days</option>
              </select>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded-md border border-border bg-secondary px-4 py-2 text-sm text-foreground hover:bg-muted disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                disabled={isSubmitting}
                data-testid="create-token-submit"
              >
                {isSubmitting ? 'Creating...' : 'Create Token'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

function TokenCreatedDialog({
  token,
  onClose,
}: {
  token: string
  onClose: () => void
}): React.ReactElement {
  const [copied, setCopied] = useState(false)

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(token).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }, [token])

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
      role="presentation"
      data-testid="token-created-dialog-overlay"
    >
      <div
        className="w-full max-w-[600px] rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        data-testid="token-created-dialog-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 className="m-0 text-xl font-semibold text-foreground">Token Created</h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onClose}
            aria-label="Close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 dark:border-amber-700 dark:bg-amber-950">
            <p className="mb-3 text-sm font-medium text-amber-800 dark:text-amber-300">
              Copy this token now. It will not be shown again.
            </p>
            <div className="flex items-center gap-2">
              <code
                className="flex-1 rounded bg-muted px-2 py-1.5 font-mono text-xs text-foreground break-all"
                data-testid="created-token-value"
              >
                {token}
              </code>
              <button
                type="button"
                className="shrink-0 rounded-md border border-border bg-secondary px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted"
                onClick={handleCopy}
                data-testid="copy-token-button"
              >
                {copied ? 'Copied!' : 'Copy'}
              </button>
            </div>
          </div>
          <div className="mt-4 flex justify-end">
            <button
              type="button"
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              onClick={onClose}
              data-testid="token-created-done"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
