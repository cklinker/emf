/**
 * CustomPolicyEditor Component
 *
 * Manages custom ABAC rules for a profile. Supports both a visual rule builder
 * (dropdown-based) and an advanced CEL expression editor.
 *
 * Each rule defines a condition under which an action on a collection is
 * allowed or denied for the profile.
 */

import React, { useState, useCallback } from 'react'
import { Plus, Trash2, ToggleLeft, ToggleRight } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import { useCollectionSummaries } from '@/hooks/useCollectionSummaries'
import { useToast } from '@/components'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { CelExpressionEditor } from './CelExpressionEditor'

interface CustomRule {
  id: string
  profileId: string
  tenantId: string
  collectionId: string
  action: string
  effect: string
  conditionType: 'visual' | 'cel'
  condition: VisualCondition | CelCondition
  enabled: boolean
}

interface VisualCondition {
  field: string
  operator: string
  value: string
}

interface CelCondition {
  expression: string
}

const ACTIONS = ['read', 'create', 'edit', 'delete'] as const
const EFFECTS = ['allow', 'deny'] as const
const OPERATORS = [
  { value: 'equals', label: 'equals' },
  { value: 'not_equals', label: 'not equals' },
  { value: 'in', label: 'in' },
  { value: 'contains', label: 'contains' },
  { value: 'greater_than', label: 'greater than' },
  { value: 'less_than', label: 'less than' },
] as const

export interface CustomPolicyEditorProps {
  /** Profile ID */
  profileId: string
  /** Tenant ID */
  tenantId: string
  /** Whether the editor is read-only */
  readOnly?: boolean
  /** Test ID for the component */
  testId?: string
}

export function CustomPolicyEditor({
  profileId,
  tenantId,
  readOnly = false,
  testId = 'custom-policy-editor',
}: CustomPolicyEditorProps): React.ReactElement {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const { summaries: collections } = useCollectionSummaries()

  // Fetch existing rules
  const { data: rules = [] } = useQuery<CustomRule[]>({
    queryKey: ['custom-rules', profileId],
    queryFn: async () => {
      const response = await apiClient.get(`/api/admin/profiles/${profileId}/custom-rules`)
      return Array.isArray(response) ? response : []
    },
    enabled: !!profileId,
  })

  // Create rule mutation
  const createMutation = useMutation({
    mutationFn: async (rule: Partial<CustomRule>) => {
      return apiClient.post(`/api/admin/profiles/${profileId}/custom-rules`, rule)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['custom-rules', profileId] })
      showToast('Rule created successfully', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to create rule', 'error')
    },
  })

  // Delete rule mutation
  const deleteMutation = useMutation({
    mutationFn: async (ruleId: string) => {
      return apiClient.delete(`/api/admin/profiles/${profileId}/custom-rules/${ruleId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['custom-rules', profileId] })
      showToast('Rule deleted', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to delete rule', 'error')
    },
  })

  // New rule form state
  const [showForm, setShowForm] = useState(false)
  const [isAdvanced, setIsAdvanced] = useState(false)
  const [newRule, setNewRule] = useState({
    collectionId: '',
    action: 'read' as string,
    effect: 'deny' as string,
    field: '',
    operator: 'equals',
    value: '',
    celExpression: '',
  })

  const handleCreateRule = useCallback(() => {
    const condition = isAdvanced
      ? { expression: newRule.celExpression }
      : { field: newRule.field, operator: newRule.operator, value: newRule.value }

    createMutation.mutate({
      tenantId,
      collectionId: newRule.collectionId,
      action: newRule.action,
      effect: newRule.effect,
      conditionType: isAdvanced ? 'cel' : 'visual',
      condition,
      enabled: true,
    })

    setShowForm(false)
    setNewRule({
      collectionId: '',
      action: 'read',
      effect: 'deny',
      field: '',
      operator: 'equals',
      value: '',
      celExpression: '',
    })
  }, [createMutation, tenantId, newRule, isAdvanced])

  const getCollectionName = useCallback(
    (collectionId: string) => {
      const col = collections.find((c) => c.id === collectionId)
      return col?.displayName ?? col?.name ?? collectionId
    },
    [collections]
  )

  return (
    <div className="space-y-4" data-testid={testId}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-foreground">Custom Authorization Rules</h3>
        {!readOnly && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setShowForm(!showForm)}
            data-testid={`${testId}-add-button`}
          >
            <Plus size={14} className="mr-1" />
            Add Rule
          </Button>
        )}
      </div>

      {/* New rule form */}
      {showForm && (
        <div
          className="space-y-3 rounded-lg border border-border bg-card p-4"
          data-testid={`${testId}-form`}
        >
          <div className="grid grid-cols-3 gap-3">
            <div>
              <Label className="mb-1 text-xs">Collection</Label>
              <Select
                value={newRule.collectionId}
                onValueChange={(v) => setNewRule((prev) => ({ ...prev, collectionId: v }))}
              >
                <SelectTrigger data-testid={`${testId}-collection-select`}>
                  <SelectValue placeholder="Select collection" />
                </SelectTrigger>
                <SelectContent>
                  {collections.map((col) => (
                    <SelectItem key={col.id} value={col.id}>
                      {col.displayName ?? col.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label className="mb-1 text-xs">Action</Label>
              <Select
                value={newRule.action}
                onValueChange={(v) => setNewRule((prev) => ({ ...prev, action: v }))}
              >
                <SelectTrigger data-testid={`${testId}-action-select`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ACTIONS.map((a) => (
                    <SelectItem key={a} value={a}>
                      {a}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label className="mb-1 text-xs">Effect</Label>
              <Select
                value={newRule.effect}
                onValueChange={(v) => setNewRule((prev) => ({ ...prev, effect: v }))}
              >
                <SelectTrigger data-testid={`${testId}-effect-select`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {EFFECTS.map((e) => (
                    <SelectItem key={e} value={e}>
                      {e}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Mode toggle */}
          <div className="flex items-center gap-2">
            <Switch
              checked={isAdvanced}
              onCheckedChange={setIsAdvanced}
              data-testid={`${testId}-advanced-toggle`}
            />
            <Label className="text-xs text-muted-foreground">
              {isAdvanced ? 'Advanced (CEL)' : 'Visual'}
            </Label>
          </div>

          {/* Condition editor */}
          {isAdvanced ? (
            <CelExpressionEditor
              value={newRule.celExpression}
              onChange={(v) => setNewRule((prev) => ({ ...prev, celExpression: v }))}
              testId={`${testId}-cel`}
            />
          ) : (
            <div className="grid grid-cols-3 gap-3">
              <div>
                <Label className="mb-1 text-xs">Field</Label>
                <Input
                  value={newRule.field}
                  onChange={(e) => setNewRule((prev) => ({ ...prev, field: e.target.value }))}
                  placeholder="e.g., status"
                  data-testid={`${testId}-field-input`}
                />
              </div>
              <div>
                <Label className="mb-1 text-xs">Operator</Label>
                <Select
                  value={newRule.operator}
                  onValueChange={(v) => setNewRule((prev) => ({ ...prev, operator: v }))}
                >
                  <SelectTrigger data-testid={`${testId}-operator-select`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {OPERATORS.map((op) => (
                      <SelectItem key={op.value} value={op.value}>
                        {op.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="mb-1 text-xs">Value</Label>
                <Input
                  value={newRule.value}
                  onChange={(e) => setNewRule((prev) => ({ ...prev, value: e.target.value }))}
                  placeholder="e.g., closed"
                  data-testid={`${testId}-value-input`}
                />
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setShowForm(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleCreateRule}
              disabled={createMutation.isPending}
              data-testid={`${testId}-save-button`}
            >
              {createMutation.isPending ? 'Creating...' : 'Create Rule'}
            </Button>
          </div>
        </div>
      )}

      {/* Existing rules list */}
      {rules.length === 0 ? (
        <div className="rounded-lg border border-border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          No custom rules defined. Click "Add Rule" to create one.
        </div>
      ) : (
        <div className="space-y-2">
          {rules.map((rule) => (
            <div
              key={rule.id}
              className="flex items-center justify-between rounded-lg border border-border bg-card px-4 py-3"
              data-testid={`${testId}-rule-${rule.id}`}
            >
              <div className="flex items-center gap-3">
                {rule.enabled ? (
                  <ToggleRight size={16} className="text-green-500" />
                ) : (
                  <ToggleLeft size={16} className="text-muted-foreground" />
                )}
                <div className="text-sm">
                  <span className="font-medium capitalize">{rule.effect}</span>{' '}
                  <span className="text-muted-foreground">{rule.action}</span>{' '}
                  <span className="text-muted-foreground">on</span>{' '}
                  <span className="font-medium">{getCollectionName(rule.collectionId)}</span>
                  {rule.conditionType === 'cel' ? (
                    <span className="ml-2 text-xs text-muted-foreground">
                      [CEL: {(rule.condition as CelCondition).expression}]
                    </span>
                  ) : (
                    <span className="ml-2 text-xs text-muted-foreground">
                      [when {(rule.condition as VisualCondition).field}{' '}
                      {(rule.condition as VisualCondition).operator}{' '}
                      {(rule.condition as VisualCondition).value}]
                    </span>
                  )}
                </div>
              </div>
              {!readOnly && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => deleteMutation.mutate(rule.id)}
                  disabled={deleteMutation.isPending}
                  data-testid={`${testId}-delete-${rule.id}`}
                >
                  <Trash2 size={14} />
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default CustomPolicyEditor
