/**
 * Unit tests for the client-side action runtime. Each action type is exercised with a fake
 * ActionRuntimeContext (vi.fn deps) and a fixed scope; bindings resolve against that scope before
 * dispatch. The URL scheme allow-list, the runFlow `{input}` outer-wrap + `data.id` read, the awaitResult
 * poll loop, and chain-stop-on-reject are all covered.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { ApiClient } from '@/services/apiClient'
import {
  executeAction,
  executeActions,
  pollFlowExecution,
  type ActionRuntimeContext,
} from './executeAction'
import type { BindingScope } from '../model/bindingScope'
import { UnsafeUrlError } from './urlSafety'

const SCOPE: BindingScope = {
  record: { id: 'ord-42', customer: 'u-9' },
  vars: { customerId: 'u-9', selectedId: 'sel-1' },
}

function jsonResp(body: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    statusText: '',
    json: async () => body,
    text: async () => JSON.stringify(body),
  }
}

function makeCtx(overrides: Partial<ActionRuntimeContext> = {}): {
  ctx: ActionRuntimeContext
  fetch: ReturnType<typeof vi.fn>
  postResource: ReturnType<typeof vi.fn>
  patchResource: ReturnType<typeof vi.fn>
  navigate: ReturnType<typeof vi.fn>
  invalidateQueries: ReturnType<typeof vi.fn>
  showToast: ReturnType<typeof vi.fn>
  setVar: ReturnType<typeof vi.fn>
} {
  const fetch = vi.fn()
  const postResource = vi.fn().mockResolvedValue({ id: 'new-1' })
  const patchResource = vi.fn().mockResolvedValue({ id: 'ord-42' })
  const navigate = vi.fn()
  const invalidateQueries = vi.fn().mockResolvedValue(undefined)
  const showToast = vi.fn()
  const setVar = vi.fn()
  const apiClient = { fetch, postResource, patchResource } as unknown as ApiClient
  const ctx: ActionRuntimeContext = {
    apiClient,
    navigate,
    queryClient: { invalidateQueries } as never,
    showToast,
    setVar,
    dataSourceQueryKey: (name: string) => ['page-data', 'home', name],
    scope: SCOPE,
    tenantSlug: 'acme',
    ...overrides,
  }
  return { ctx, fetch, postResource, patchResource, navigate, invalidateQueries, showToast, setVar }
}

describe('executeAction', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('showToast resolves a bound message and calls showToast with the level', async () => {
    const { ctx, showToast } = makeCtx()
    await executeAction(
      {
        action: 'showToast',
        level: 'success',
        message: { $bind: 'record.id', mode: 'path' },
      },
      ctx
    )
    expect(showToast).toHaveBeenCalledWith('ord-42', 'success')
  })

  it('setVar resolves the value and writes the variable', async () => {
    const { ctx, setVar } = makeCtx()
    await executeAction(
      { action: 'setVar', name: 'selectedId', value: { $bind: 'record.id', mode: 'path' } },
      ctx
    )
    expect(setVar).toHaveBeenCalledWith('selectedId', 'ord-42')
  })

  it('refreshData invalidates the named data source query key', async () => {
    const { ctx, invalidateQueries } = makeCtx()
    await executeAction({ action: 'refreshData', dataSource: 'ordersList' }, ctx)
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['page-data', 'home', 'ordersList'],
    })
  })

  it('createRecord posts resolved attributes to /api/{collection}', async () => {
    const { ctx, postResource } = makeCtx()
    await executeAction(
      {
        action: 'createRecord',
        collection: 'orders',
        attributes: { customer: { $bind: 'vars.customerId', mode: 'path' }, status: 'NEW' },
      },
      ctx
    )
    expect(postResource).toHaveBeenCalledWith('/api/orders', { customer: 'u-9', status: 'NEW' })
  })

  it('updateRecord patches /api/{collection}/{resolved recordId}', async () => {
    const { ctx, patchResource } = makeCtx()
    await executeAction(
      {
        action: 'updateRecord',
        collection: 'orders',
        recordId: { $bind: 'record.id', mode: 'path' },
        attributes: { status: 'SHIPPED' },
      },
      ctx
    )
    expect(patchResource).toHaveBeenCalledWith('/api/orders/ord-42', { status: 'SHIPPED' })
  })

  it('updateRecord rejects when recordId resolves to empty', async () => {
    const { ctx, patchResource } = makeCtx()
    await expect(
      executeAction(
        { action: 'updateRecord', collection: 'orders', recordId: '', attributes: {} },
        ctx
      )
    ).rejects.toThrow(/recordId/)
    expect(patchResource).not.toHaveBeenCalled()
  })

  describe('navigate', () => {
    it('appends resolved params and calls navigate', async () => {
      const { ctx, navigate } = makeCtx()
      await executeAction(
        { action: 'navigate', to: '/app/p/orders', params: { status: 'NEW' } },
        ctx
      )
      expect(navigate).toHaveBeenCalledWith('/app/p/orders?status=NEW')
    })

    it('opens a new tab when newTab is set', async () => {
      const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
      const { ctx, navigate } = makeCtx()
      await executeAction({ action: 'navigate', to: '/app/p/orders', newTab: true }, ctx)
      expect(openSpy).toHaveBeenCalledWith('/app/p/orders', '_blank', 'noopener')
      expect(navigate).not.toHaveBeenCalled()
    })

    it('blocks a javascript: target and does not navigate', async () => {
      const { ctx, navigate } = makeCtx()
      await expect(
        executeAction({ action: 'navigate', to: 'javascript:alert(1)' }, ctx)
      ).rejects.toThrow(UnsafeUrlError)
      expect(navigate).not.toHaveBeenCalled()
    })
  })

  describe('openUrl', () => {
    it('opens a new tab for an allowed URL', async () => {
      const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
      const { ctx } = makeCtx()
      await executeAction({ action: 'openUrl', url: 'https://example.com', newTab: true }, ctx)
      expect(openSpy).toHaveBeenCalledWith('https://example.com', '_blank', 'noopener')
    })

    it('assigns location for a same-window allowed URL', async () => {
      const assignSpy = vi.fn()
      vi.spyOn(window, 'location', 'get').mockReturnValue({ assign: assignSpy } as never)
      const { ctx } = makeCtx()
      await executeAction({ action: 'openUrl', url: 'https://example.com' }, ctx)
      expect(assignSpy).toHaveBeenCalledWith('https://example.com')
    })

    it('blocks a javascript: URL (SECURITY) and never assigns/opens', async () => {
      const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
      const assignSpy = vi.fn()
      vi.spyOn(window, 'location', 'get').mockReturnValue({ assign: assignSpy } as never)
      const { ctx } = makeCtx()
      await expect(
        executeAction({ action: 'openUrl', url: 'javascript:alert(1)' }, ctx)
      ).rejects.toThrow(UnsafeUrlError)
      expect(openSpy).not.toHaveBeenCalled()
      expect(assignSpy).not.toHaveBeenCalled()
    })

    it('blocks a {$bind} that RESOLVES to a javascript: URL (resolution runs first)', async () => {
      const assignSpy = vi.fn()
      vi.spyOn(window, 'location', 'get').mockReturnValue({ assign: assignSpy } as never)
      const scope: BindingScope = { record: { link: 'javascript:alert(1)' } }
      const { ctx } = makeCtx({ scope })
      await expect(
        executeAction({ action: 'openUrl', url: { $bind: 'record.link', mode: 'path' } }, ctx)
      ).rejects.toThrow(UnsafeUrlError)
      expect(assignSpy).not.toHaveBeenCalled()
    })
  })

  describe('runFlow', () => {
    it('posts {input} with the outer wrap added and bindings resolved (fire-and-forget)', async () => {
      const { ctx, fetch } = makeCtx()
      fetch.mockResolvedValueOnce(
        jsonResp({
          data: { type: 'flow-executions', id: 'ex-1', attributes: { status: 'RUNNING' } },
        })
      )
      await executeAction(
        {
          action: 'runFlow',
          flowId: 'flow-7c2a',
          input: { orderId: { $bind: 'record.id', mode: 'path' } },
        },
        ctx
      )
      expect(fetch).toHaveBeenCalledTimes(1)
      const [url, init] = fetch.mock.calls[0]
      expect(url).toBe('/api/flows/flow-7c2a/execute')
      expect(init.method).toBe('POST')
      expect(JSON.parse(init.body)).toEqual({ input: { orderId: 'ord-42' } })
    })

    it('does not poll when awaitResult is falsy', async () => {
      const { ctx, fetch } = makeCtx()
      fetch.mockResolvedValueOnce(
        jsonResp({
          data: { type: 'flow-executions', id: 'ex-1', attributes: { status: 'RUNNING' } },
        })
      )
      await executeAction({ action: 'runFlow', flowId: 'f1' }, ctx)
      expect(fetch).toHaveBeenCalledTimes(1) // execute only, no poll
    })
  })

  describe('runFlow awaitResult + poll', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    it('polls executions/{data.id} until COMPLETED', async () => {
      const { ctx, fetch } = makeCtx()
      fetch
        .mockResolvedValueOnce(
          jsonResp({
            data: { type: 'flow-executions', id: 'ex-1', attributes: { status: 'RUNNING' } },
          })
        )
        .mockResolvedValueOnce(
          jsonResp({
            data: { type: 'flow-executions', id: 'ex-1', attributes: { status: 'RUNNING' } },
          })
        )
        .mockResolvedValueOnce(
          jsonResp({
            data: { type: 'flow-executions', id: 'ex-1', attributes: { status: 'COMPLETED' } },
          })
        )
      const promise = executeAction({ action: 'runFlow', flowId: 'f1', awaitResult: true }, ctx)
      // Drain the execute call + first poll, then advance through the 1.5 s interval to the second poll.
      await vi.advanceTimersByTimeAsync(0)
      await vi.advanceTimersByTimeAsync(1500)
      await vi.advanceTimersByTimeAsync(1500)
      await expect(promise).resolves.toBeUndefined()
      // execute + 2 polls.
      expect(fetch).toHaveBeenCalledTimes(3)
      expect(fetch.mock.calls[1][0]).toBe('/api/flows/executions/ex-1')
    })

    it('rejects when the flow reports FAILED', async () => {
      const { ctx, fetch } = makeCtx()
      fetch
        .mockResolvedValueOnce(
          jsonResp({
            data: { type: 'flow-executions', id: 'ex-2', attributes: { status: 'RUNNING' } },
          })
        )
        .mockResolvedValueOnce(
          jsonResp({
            data: { type: 'flow-executions', id: 'ex-2', attributes: { status: 'FAILED' } },
          })
        )
      const promise = executeAction({ action: 'runFlow', flowId: 'f1', awaitResult: true }, ctx)
      const assertion = expect(promise).rejects.toThrow(/failed/i)
      await vi.advanceTimersByTimeAsync(0)
      await assertion
    })
  })

  describe('pollFlowExecution (direct)', () => {
    it('resolves on a terminal COMPLETED status', async () => {
      const fetch = vi.fn().mockResolvedValue(
        jsonResp({
          data: { type: 'flow-executions', id: 'x', attributes: { status: 'COMPLETED' } },
        })
      )
      const apiClient = { fetch } as unknown as ApiClient
      await expect(pollFlowExecution(apiClient, 'x')).resolves.toEqual({ status: 'COMPLETED' })
    })
  })
})

describe('executeActions (ordering + chain stop)', () => {
  it('runs actions in order', async () => {
    const calls: string[] = []
    const { ctx, setVar } = makeCtx()
    setVar.mockImplementation((name: string) => calls.push(name))
    await executeActions(
      [
        { action: 'setVar', name: 'a', value: '1' },
        { action: 'setVar', name: 'b', value: '2' },
        { action: 'setVar', name: 'c', value: '3' },
      ],
      ctx
    )
    expect(calls).toEqual(['a', 'b', 'c'])
  })

  it('stops the chain when an awaited action rejects (subsequent actions do not run)', async () => {
    const { ctx, navigate, setVar } = makeCtx()
    await expect(
      executeActions(
        [
          { action: 'setVar', name: 'a', value: '1' },
          { action: 'navigate', to: 'javascript:alert(1)' }, // throws (unsafe URL)
          { action: 'setVar', name: 'never', value: '2' },
        ],
        ctx
      )
    ).rejects.toThrow(UnsafeUrlError)
    expect(setVar).toHaveBeenCalledTimes(1)
    expect(setVar).toHaveBeenCalledWith('a', '1')
    expect(navigate).not.toHaveBeenCalled()
  })
})
