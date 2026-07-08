import { describe, it, expect, vi } from 'vitest'
import { runBulkUpdate, type BulkJobApi } from './bulkUpdate'

function makeApi(pollResponses: Array<Record<string, unknown> | undefined>): BulkJobApi & {
  post: ReturnType<typeof vi.fn>
  get: ReturnType<typeof vi.fn>
} {
  let call = 0
  return {
    post: vi.fn().mockResolvedValue({ data: { id: 'job-1' } }),
    get: vi.fn().mockImplementation(() => {
      const attributes = pollResponses[Math.min(call, pollResponses.length - 1)]
      call += 1
      return Promise.resolve({ data: { attributes } })
    }),
  }
}

describe('runBulkUpdate', () => {
  it('submits the UPDATE job and returns the terminal counts', async () => {
    const api = makeApi([{ status: 'COMPLETED', successRecords: 2, errorRecords: 0 }])
    const result = await runBulkUpdate(api, 'col-1', [
      { id: 'r1', status: 'active' },
      { id: 'r2', status: 'active' },
    ])
    expect(api.post).toHaveBeenCalledWith('/api/bulk-jobs', {
      collectionId: 'col-1',
      operation: 'UPDATE',
      records: [
        { id: 'r1', status: 'active' },
        { id: 'r2', status: 'active' },
      ],
    })
    expect(result).toEqual({
      jobId: 'job-1',
      status: 'COMPLETED',
      successRecords: 2,
      errorRecords: 0,
    })
  })

  it('polls until a terminal status appears', async () => {
    const api = makeApi([
      { status: 'RUNNING' },
      { status: 'RUNNING' },
      { status: 'COMPLETED', successRecords: 1, errorRecords: 1 },
    ])
    const result = await runBulkUpdate(api, 'col-1', [{ id: 'r1', a: 1 }], { pollIntervalMs: 0 })
    expect(api.get).toHaveBeenCalledTimes(3)
    expect(result.status).toBe('COMPLETED')
    expect(result.errorRecords).toBe(1)
  })

  it('reports FAILED as the terminal status without throwing', async () => {
    const api = makeApi([{ status: 'FAILED', successRecords: 0, errorRecords: 3 }])
    const result = await runBulkUpdate(api, 'col-1', [{ id: 'r1' }])
    expect(result.status).toBe('FAILED')
    expect(result.errorRecords).toBe(3)
  })

  it('returns status undefined when the job outlives the poll ceiling', async () => {
    const api = makeApi([{ status: 'RUNNING' }])
    const result = await runBulkUpdate(api, 'col-1', [{ id: 'r1' }], {
      pollIntervalMs: 0,
      maxPollAttempts: 3,
    })
    expect(api.get).toHaveBeenCalledTimes(3)
    expect(result).toEqual({
      jobId: 'job-1',
      status: undefined,
      successRecords: 0,
      errorRecords: 0,
    })
  })

  it('throws when the job is not created', async () => {
    const api = makeApi([])
    api.post.mockResolvedValue({ data: {} })
    await expect(runBulkUpdate(api, 'col-1', [{ id: 'r1' }])).rejects.toThrow(
      'Bulk job was not created'
    )
    expect(api.get).not.toHaveBeenCalled()
  })
})
