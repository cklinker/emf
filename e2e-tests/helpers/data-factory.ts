/**
 * Test data factory for creating and cleaning up test entities.
 *
 * Uses the Kelta API directly to set up test state before tests
 * and tear it down afterward.
 */

export interface ApiClient {
  baseUrl: string;
  token: string;
  tenantSlug: string;
}

interface JsonApiResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

interface JsonApiResponse {
  data: JsonApiResource | JsonApiResource[];
}

const MAX_RETRIES = 3;
const INITIAL_RETRY_DELAY_MS = 2_000;

/** How long to wait for storage to become ready after collection/field creation */
const STORAGE_READY_TIMEOUT_MS = 30_000;
const STORAGE_READY_POLL_MS = 2_000;

export class DataFactory {
  private createdEntities: Array<{ type: string; id: string }> = [];

  constructor(private readonly api: ApiClient) {}

  private async request(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<JsonApiResponse> {
    const url = `${this.api.baseUrl}/${this.api.tenantSlug}${path}`;

    for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      const response = await fetch(url, {
        method,
        headers: {
          "Content-Type": "application/vnd.api+json",
          Authorization: `Bearer ${this.api.token}`,
        },
        body: body ? JSON.stringify(body) : undefined,
      });

      // Retry on rate limiting (429) and transient server errors (500-503)
      const isRetryable =
        response.status === 429 ||
        (response.status >= 500 && response.status <= 503);
      if (isRetryable && attempt < MAX_RETRIES) {
        const retryAfter = response.headers.get("Retry-After");
        const delayMs = retryAfter
          ? parseInt(retryAfter, 10) * 1_000
          : INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt);
        await new Promise((resolve) => setTimeout(resolve, delayMs));
        continue;
      }

      if (!response.ok) {
        const error = await response.text();
        throw new Error(
          `API ${method} ${path} failed (${response.status}): ${error}`,
        );
      }

      if (response.status === 204) {
        return { data: { id: "", type: "", attributes: {} } };
      }

      return response.json() as Promise<JsonApiResponse>;
    }

    throw new Error(
      `API ${method} ${path} failed: max retries (${MAX_RETRIES}) exceeded`,
    );
  }

  /**
   * Poll the collection's record endpoint until storage is provisioned.
   *
   * After creating a collection and adding fields, the backend
   * asynchronously provisions the storage table. Attempting to
   * create records before it is ready returns a 500 storageError.
   * This method polls GET /api/{collectionName} until it stops
   * returning 500, ensuring the storage layer is ready.
   */
  async waitForStorageReady(
    collectionName: string,
    timeoutMs = STORAGE_READY_TIMEOUT_MS,
  ): Promise<void> {
    const url = `${this.api.baseUrl}/${this.api.tenantSlug}/api/${collectionName}`;
    const deadline = Date.now() + timeoutMs;

    while (Date.now() < deadline) {
      try {
        const response = await fetch(url, {
          method: "GET",
          headers: {
            "Content-Type": "application/vnd.api+json",
            Authorization: `Bearer ${this.api.token}`,
          },
        });

        // Any non-500 response means storage is ready (200 OK, or 404 empty list)
        if (response.status < 500) {
          return;
        }
      } catch {
        // Network error — keep retrying
      }

      await new Promise((resolve) =>
        setTimeout(resolve, STORAGE_READY_POLL_MS),
      );
    }

    throw new Error(
      `Storage for collection '${collectionName}' not ready after ${timeoutMs}ms`,
    );
  }

  async createCollection(
    overrides: Record<string, unknown> = {},
  ): Promise<JsonApiResource> {
    const uniqueName = `e2e_test_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const result = await this.request("POST", "/api/collections", {
      data: {
        type: "collections",
        attributes: {
          name: uniqueName,
          displayName: `E2E ${uniqueName}`,
          active: true,
          ...overrides,
        },
      },
    });

    const resource = result.data as JsonApiResource;
    this.createdEntities.push({ type: "collections", id: resource.id });
    return resource;
  }

  async addField(
    collectionId: string,
    field: {
      name: string;
      displayName: string;
      type: string;
      required?: boolean;
    },
  ): Promise<JsonApiResource> {
    const result = await this.request(
      "POST",
      `/api/collections/${collectionId}/fields`,
      {
        data: {
          type: "fields",
          attributes: field,
        },
      },
    );
    return result.data as JsonApiResource;
  }

  async createRecord(
    collectionName: string,
    attributes: Record<string, unknown>,
  ): Promise<JsonApiResource> {
    const result = await this.request("POST", `/api/${collectionName}`, {
      data: {
        type: collectionName,
        attributes,
      },
    });

    const resource = result.data as JsonApiResource;
    this.createdEntities.push({ type: collectionName, id: resource.id });
    return resource;
  }

  async cleanup(): Promise<void> {
    for (const entity of this.createdEntities.reverse()) {
      try {
        await this.request("DELETE", `/api/${entity.type}/${entity.id}`);
      } catch {
        // Ignore cleanup failures
      }
    }
    this.createdEntities = [];
  }
}
