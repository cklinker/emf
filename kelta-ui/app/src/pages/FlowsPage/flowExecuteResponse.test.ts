/**
 * Flow-execute response-shape contract.
 *
 * `POST /api/flows/{flowId}/execute` (FlowExecutionController) returns a JSON:API
 * envelope — `{ data: { type: 'flow-executions', id, attributes: { flowId, status } } }`
 * — NOT a flat `{ executionId }`. Both `FlowsPage` (run → navigate to the debug view) and
 * `FlowDesignerPage` (test → open the debug tab) parse the execution id via
 * `unwrapResource(json).id`. Reading a top-level `response.executionId` (the prior bug)
 * resolves to `undefined`, so the post-execute navigation/poll silently no-ops.
 *
 * This pins the contract those two call sites depend on.
 */
import { describe, it, expect } from 'vitest'
import { unwrapResource } from '../../utils/jsonapi'

const executeResponse = {
  data: {
    type: 'flow-executions',
    id: 'exec-123',
    attributes: { flowId: 'flow-1', status: 'RUNNING' },
  },
}

describe('flow-execute response parsing', () => {
  it('extracts the execution id from data.id via unwrapResource', () => {
    const result = unwrapResource<{ id: string; flowId: string; status: string }>(executeResponse)

    expect(result.id).toBe('exec-123')
    expect(result.flowId).toBe('flow-1')
    expect(result.status).toBe('RUNNING')
  })

  it('has no top-level executionId — guards the prior silent no-op regression', () => {
    expect((executeResponse as Record<string, unknown>).executionId).toBeUndefined()
  })
})
