import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { AxiosInstance } from 'axios';
import { AdminClient } from './AdminClient';
import type { CreateBulkJobRequest } from './types';

const createMockAxios = () =>
  ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  }) as unknown as AxiosInstance;

describe('AdminClient bulkJobs', () => {
  let axios: AxiosInstance;
  let client: AdminClient;
  let mockPost: ReturnType<typeof vi.fn>;
  let mockGet: ReturnType<typeof vi.fn>;
  let mockPatch: ReturnType<typeof vi.fn>;

  const jobResponse = {
    data: {
      type: 'bulk-jobs',
      id: 'job-1',
      attributes: {
        status: 'QUEUED',
        operation: 'INSERT',
        collectionId: 'col-1',
        totalRecords: 2,
        processedRecords: 0,
        successRecords: 0,
        errorRecords: 0,
        batchSize: 200,
      },
    },
  };

  beforeEach(() => {
    axios = createMockAxios();
    client = new AdminClient(axios);
    mockPost = axios.post as ReturnType<typeof vi.fn>;
    mockGet = axios.get as ReturnType<typeof vi.fn>;
    mockPatch = axios.patch as ReturnType<typeof vi.fn>;
  });

  describe('create()', () => {
    it('sends the plain request body (not a JSON:API envelope) to /api/bulk-jobs', async () => {
      mockPost.mockResolvedValue({ data: jobResponse });
      const request: CreateBulkJobRequest = {
        collectionId: 'col-1',
        operation: 'INSERT',
        records: [{ name: 'a' }, { name: 'b' }],
      };

      await client.bulkJobs.create('tenant-1', 'user-1', request);

      expect(mockPost).toHaveBeenCalledWith('/api/bulk-jobs', request);
      const body = mockPost.mock.calls[0][1];
      expect(body.collectionId).toBe('col-1');
      expect(body.operation).toBe('INSERT');
      expect(body.records).toEqual([{ name: 'a' }, { name: 'b' }]);
      expect(body.data).toBeUndefined();
    });

    it('passes optional externalIdField and batchSize at the top level', async () => {
      mockPost.mockResolvedValue({ data: jobResponse });
      const request: CreateBulkJobRequest = {
        collectionId: 'col-1',
        operation: 'UPSERT',
        externalIdField: 'externalId',
        batchSize: 50,
        records: [{ externalId: 'x1' }],
      };

      await client.bulkJobs.create('tenant-1', 'user-1', request);

      const body = mockPost.mock.calls[0][1];
      expect(body.externalIdField).toBe('externalId');
      expect(body.batchSize).toBe(50);
    });

    it('unwraps the JSON:API response to a flat BulkJob', async () => {
      mockPost.mockResolvedValue({ data: jobResponse });

      const job = await client.bulkJobs.create('tenant-1', 'user-1', {
        collectionId: 'col-1',
        operation: 'INSERT',
        records: [{ name: 'a' }],
      });

      expect(job.id).toBe('job-1');
      expect(job.status).toBe('QUEUED');
      expect(job.collectionId).toBe('col-1');
      expect(job.totalRecords).toBe(2);
    });
  });

  describe('list()', () => {
    it('unwraps the JSON:API list from /api/bulk-jobs', async () => {
      mockGet.mockResolvedValue({ data: { data: [jobResponse.data] } });

      const jobs = await client.bulkJobs.list();

      expect(mockGet).toHaveBeenCalledWith('/api/bulk-jobs');
      expect(jobs).toHaveLength(1);
      expect(jobs[0].id).toBe('job-1');
    });
  });

  describe('get()', () => {
    it('fetches a single job by id', async () => {
      mockGet.mockResolvedValue({ data: jobResponse });

      const job = await client.bulkJobs.get('job-1');

      expect(mockGet).toHaveBeenCalledWith('/api/bulk-jobs/job-1');
      expect(job.id).toBe('job-1');
    });
  });

  describe('abort()', () => {
    it('patches the job with a JSON:API ABORTED body', async () => {
      mockPatch.mockResolvedValue({ data: jobResponse });

      await client.bulkJobs.abort('job-1');

      expect(mockPatch).toHaveBeenCalledWith('/api/bulk-jobs/job-1', {
        data: {
          type: 'bulk-jobs',
          id: 'job-1',
          attributes: { status: 'ABORTED' },
        },
      });
    });
  });

  describe('getResults() / getErrors()', () => {
    it('fetches results and error-filtered results', async () => {
      mockGet.mockResolvedValue({ data: { data: [] } });

      await client.bulkJobs.getResults('job-1');
      await client.bulkJobs.getErrors('job-1');

      expect(mockGet).toHaveBeenNthCalledWith(1, '/api/bulk-jobs/job-1/bulk-job-results');
      expect(mockGet).toHaveBeenNthCalledWith(
        2,
        '/api/bulk-jobs/job-1/bulk-job-results?filter[status][eq]=ERROR'
      );
    });
  });
});
