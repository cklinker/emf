import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

export interface AiAgentsPageProps {
  testId?: string
}

interface AgentDefinition {
  id: string
  tenantId: string
  name: string
  description: string | null
  systemPrompt: string
  model: string | null
  maxTokens: number | null
  allowedTools: string[]
  monthlyTokenBudget: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

interface AgentToolTrace {
  name: string
  input: Record<string, unknown>
  resultJson: string
  isError: boolean
  permitted: boolean
}

interface AgentRunResult {
  finalText: string
  toolCalls: AgentToolTrace[]
  inputTokens: number
  outputTokens: number
  iterations: number
  stopReason: string | null
  budgetExceeded: boolean
  maxIterationsReached: boolean
}

interface AgentFormData {
  name: string
  description: string
  systemPrompt: string
  model: string
  maxTokens: string
  allowedTools: string
  monthlyTokenBudget: string
  enabled: boolean
}

interface FormErrors {
  name?: string
  systemPrompt?: string
}

function toFormData(agent?: AgentDefinition): AgentFormData {
  return {
    name: agent?.name ?? '',
    description: agent?.description ?? '',
    systemPrompt: agent?.systemPrompt ?? '',
    model: agent?.model ?? '',
    maxTokens: agent?.maxTokens != null ? String(agent.maxTokens) : '',
    allowedTools: agent?.allowedTools?.join(', ') ?? '',
    monthlyTokenBudget: agent?.monthlyTokenBudget != null ? String(agent.monthlyTokenBudget) : '',
    enabled: agent?.enabled ?? true,
  }
}

function toRequestBody(data: AgentFormData): Record<string, unknown> {
  const allowedTools = data.allowedTools
    .split(',')
    .map((t) => t.trim())
    .filter((t) => t.length > 0)
  return {
    name: data.name.trim(),
    description: data.description.trim() || null,
    systemPrompt: data.systemPrompt,
    model: data.model.trim() || null,
    maxTokens: data.maxTokens.trim() ? Number(data.maxTokens) : null,
    allowedTools,
    monthlyTokenBudget: data.monthlyTokenBudget.trim() ? Number(data.monthlyTokenBudget) : null,
    enabled: data.enabled,
  }
}

function validate(data: AgentFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.trim().length > 255) {
    errors.name = 'Name must be at most 255 characters'
  }
  if (!data.systemPrompt.trim()) {
    errors.systemPrompt = 'System prompt is required'
  }
  return errors
}

const inputClass =
  'w-full rounded-md border border-border bg-background px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'

function AgentForm({
  agent,
  onSubmit,
  onCancel,
  isSubmitting,
}: {
  agent?: AgentDefinition
  onSubmit: (data: AgentFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}): React.ReactElement {
  const [formData, setFormData] = useState<AgentFormData>(() => toFormData(agent))
  const [errors, setErrors] = useState<FormErrors>({})

  const handleChange = useCallback(
    (field: keyof AgentFormData, value: string | boolean) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (field in errors) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const found = validate(formData)
      if (Object.keys(found).length > 0) {
        setErrors(found)
        return
      }
      onSubmit(formData)
    },
    [formData, onSubmit]
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div
        className="max-h-[90vh] w-full max-w-[640px] overflow-y-auto rounded-lg bg-background shadow-xl"
        data-testid="agent-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 className="text-lg font-semibold">{agent ? 'Edit Agent' : 'New Agent'}</h2>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Close"
            className="text-muted-foreground"
          >
            ×
          </button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4 p-6">
          <div>
            <label htmlFor="af-name" className="mb-1 block text-sm font-medium">
              Name
            </label>
            <input
              id="af-name"
              data-testid="agent-name-input"
              className={cn(inputClass, errors.name && 'border-destructive')}
              value={formData.name}
              onChange={(e) => handleChange('name', e.target.value)}
            />
            {errors.name && (
              <span className="text-xs text-destructive" role="alert">
                {errors.name}
              </span>
            )}
          </div>

          <div>
            <label htmlFor="af-description" className="mb-1 block text-sm font-medium">
              Description
            </label>
            <input
              id="af-description"
              className={inputClass}
              value={formData.description}
              onChange={(e) => handleChange('description', e.target.value)}
            />
          </div>

          <div>
            <label htmlFor="af-system-prompt" className="mb-1 block text-sm font-medium">
              System Prompt
            </label>
            <textarea
              id="af-system-prompt"
              data-testid="agent-system-prompt-input"
              className={cn(
                inputClass,
                'min-h-[120px] resize-y',
                errors.systemPrompt && 'border-destructive'
              )}
              value={formData.systemPrompt}
              onChange={(e) => handleChange('systemPrompt', e.target.value)}
            />
            {errors.systemPrompt && (
              <span className="text-xs text-destructive" role="alert">
                {errors.systemPrompt}
              </span>
            )}
          </div>

          <div>
            <label htmlFor="af-tools" className="mb-1 block text-sm font-medium">
              Allowed tools
            </label>
            <input
              id="af-tools"
              data-testid="agent-tools-input"
              className={inputClass}
              placeholder="comma-separated, e.g. search, get_record"
              value={formData.allowedTools}
              onChange={(e) => handleChange('allowedTools', e.target.value)}
            />
            <span className="text-xs text-muted-foreground">
              The agent may call only these tools. Leave blank for a text-only agent.
            </span>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label htmlFor="af-model" className="mb-1 block text-sm font-medium">
                Model
              </label>
              <input
                id="af-model"
                className={inputClass}
                placeholder="default"
                value={formData.model}
                onChange={(e) => handleChange('model', e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="af-max-tokens" className="mb-1 block text-sm font-medium">
                Max tokens
              </label>
              <input
                id="af-max-tokens"
                type="number"
                className={inputClass}
                placeholder="default"
                value={formData.maxTokens}
                onChange={(e) => handleChange('maxTokens', e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="af-budget" className="mb-1 block text-sm font-medium">
                Monthly budget
              </label>
              <input
                id="af-budget"
                type="number"
                className={inputClass}
                placeholder="tenant quota"
                value={formData.monthlyTokenBudget}
                onChange={(e) => handleChange('monthlyTokenBudget', e.target.value)}
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              data-testid="agent-enabled-input"
              checked={formData.enabled}
              onChange={(e) => handleChange('enabled', e.target.checked)}
            />
            Enabled
          </label>

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
            <Button type="submit" data-testid="agent-form-submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

function RunPanel({
  agent,
  onClose,
}: {
  agent: AgentDefinition
  onClose: () => void
}): React.ReactElement {
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const [input, setInput] = useState('')
  const [result, setResult] = useState<AgentRunResult | null>(null)

  const runMutation = useMutation({
    mutationFn: (text: string) =>
      apiClient.post<AgentRunResult>(`/api/ai/agents/${agent.id}/run`, { input: text }),
    onSuccess: (data) => setResult(data),
    onError: (err: Error) => showToast(err.message || 'Run failed', 'error'),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div
        className="max-h-[90vh] w-full max-w-[720px] overflow-y-auto rounded-lg bg-background shadow-xl"
        data-testid="run-panel"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 className="text-lg font-semibold">Run “{agent.name}”</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground"
          >
            ×
          </button>
        </div>
        <div className="space-y-4 p-6">
          <textarea
            data-testid="run-input"
            className={cn(inputClass, 'min-h-[80px] resize-y')}
            placeholder="Message for the agent…"
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
          <div className="flex justify-end">
            <Button
              data-testid="run-submit"
              disabled={runMutation.isPending || input.trim().length === 0}
              onClick={() => runMutation.mutate(input)}
            >
              {runMutation.isPending ? 'Running…' : 'Run'}
            </Button>
          </div>

          {result && (
            <div data-testid="run-result" className="space-y-3 rounded-md border border-border p-4">
              <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                <span>{result.iterations} iteration(s)</span>
                <span>{result.inputTokens + result.outputTokens} tokens</span>
                <span>stop: {result.stopReason ?? '—'}</span>
                {result.budgetExceeded && <span className="text-destructive">budget exceeded</span>}
                {result.maxIterationsReached && (
                  <span className="text-destructive">max iterations</span>
                )}
              </div>
              <pre className="whitespace-pre-wrap text-sm">{result.finalText || '(no text)'}</pre>
              {result.toolCalls.length > 0 && (
                <div>
                  <p className="mb-1 text-xs font-semibold">Tool calls</p>
                  <ul className="space-y-1">
                    {result.toolCalls.map((tc, i) => (
                      <li key={i} className="text-xs">
                        <span className="font-mono">{tc.name}</span>{' '}
                        {!tc.permitted && <span className="text-destructive">(refused)</span>}
                        {tc.isError && <span className="text-destructive">(error)</span>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export function AiAgentsPage({ testId = 'ai-agents-page' }: AiAgentsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editing, setEditing] = useState<AgentDefinition | undefined>()
  const [runningAgent, setRunningAgent] = useState<AgentDefinition | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AgentDefinition | null>(null)

  const {
    data: agents,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['ai-agents'],
    queryFn: () => apiClient.get<AgentDefinition[]>('/api/ai/agents'),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ai-agents'] })

  const createMutation = useMutation({
    mutationFn: (data: AgentFormData) => apiClient.post('/api/ai/agents', toRequestBody(data)),
    onSuccess: () => {
      invalidate()
      showToast('Agent created', 'success')
      setIsFormOpen(false)
    },
    onError: (err: Error) => showToast(err.message || 'Failed to create agent', 'error'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: AgentFormData }) =>
      apiClient.put(`/api/ai/agents/${id}`, toRequestBody(data)),
    onSuccess: () => {
      invalidate()
      showToast('Agent updated', 'success')
      setIsFormOpen(false)
    },
    onError: (err: Error) => showToast(err.message || 'Failed to update agent', 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/ai/agents/${id}`),
    onSuccess: () => {
      invalidate()
      showToast('Agent deleted', 'success')
      setDeleteTarget(null)
    },
    onError: (err: Error) => showToast(err.message || 'Failed to delete agent', 'error'),
  })

  const handleSubmit = useCallback(
    (data: AgentFormData) => {
      if (editing) {
        updateMutation.mutate({ id: editing.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editing, createMutation, updateMutation]
  )

  if (isLoading) {
    return <LoadingSpinner size="large" />
  }
  if (error) {
    return <ErrorMessage error={error as Error} onRetry={() => refetch()} />
  }

  const list = agents ?? []

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">AI Agents</h1>
          <p className="text-sm text-muted-foreground">
            Governed assistants with a fixed prompt, an allowed tool subset, and token budgets.
          </p>
        </div>
        <Button
          data-testid="new-agent-button"
          onClick={() => {
            setEditing(undefined)
            setIsFormOpen(true)
          }}
        >
          New Agent
        </Button>
      </header>

      {list.length === 0 ? (
        <div className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground">
          No agents yet. Create one to get started.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table role="grid" className="w-full border-collapse" aria-label="AI Agents">
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold"
                >
                  Name
                </th>
                <th
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold"
                >
                  Model
                </th>
                <th
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold"
                >
                  Tools
                </th>
                <th
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold"
                >
                  Status
                </th>
                <th
                  scope="col"
                  className="border-b border-border px-4 py-3 text-right text-xs font-semibold"
                >
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {list.map((agent) => (
                <tr
                  key={agent.id}
                  role="row"
                  className="border-b border-border last:border-b-0 hover:bg-muted/50"
                >
                  <td role="gridcell" className="px-4 py-3 text-sm font-medium">
                    {agent.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-muted-foreground">
                    {agent.model ?? 'default'}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-muted-foreground">
                    {agent.allowedTools.length}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm">
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs',
                        agent.enabled
                          ? 'bg-primary/10 text-primary'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {agent.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        data-testid={`run-agent-button-${agent.id}`}
                        onClick={() => setRunningAgent(agent)}
                      >
                        Run
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        data-testid={`edit-button-${agent.id}`}
                        onClick={() => {
                          setEditing(agent)
                          setIsFormOpen(true)
                        }}
                      >
                        Edit
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        data-testid={`delete-button-${agent.id}`}
                        onClick={() => setDeleteTarget(agent)}
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <AgentForm
          agent={editing}
          onSubmit={handleSubmit}
          onCancel={() => setIsFormOpen(false)}
          isSubmitting={createMutation.isPending || updateMutation.isPending}
        />
      )}

      {runningAgent && <RunPanel agent={runningAgent} onClose={() => setRunningAgent(null)} />}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete agent"
        message={`Delete “${deleteTarget?.name}”? This cannot be undone.`}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
        variant="danger"
      />
    </div>
  )
}

export default AiAgentsPage
