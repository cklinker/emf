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

      if (response.status === 429 && attempt < MAX_RETRIES) {
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
      `API ${method} ${path} failed: max retries (${MAX_RETRIES}) exceeded due to rate limiting`,
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
