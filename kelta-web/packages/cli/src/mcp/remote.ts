import axios from 'axios';
import { CliError, EXIT } from '../errors.js';

export type Toolset = 'user' | 'admin';

export interface RemoteTool {
  name: string;
  description?: string;
  inputSchema?: unknown;
  annotations?: unknown;
}

interface JsonRpcResponse {
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

/**
 * Hosted MCP base URL for a profile: **the API origin itself**.
 *
 * kelta-mcp is not a separate public host — the gateway routes the MCP paths
 * (`Host(api.…) && PathRegexp(^/[a-z][a-z0-9-]+/mcp/(user|admin)…)`), so
 * `{apiUrl}/{slug}/mcp/{toolset}` is the real endpoint. An earlier version
 * invented an `api.` → `mcp.` host swap by symmetry with the auth URL; that
 * host does not exist, so every bridge request 404'd and the server silently
 * degraded to local-only tools. Override with `--mcp-url` when a deployment
 * really does front kelta-mcp on its own host.
 */
export function deriveMcpUrl(apiUrl: string): string | undefined {
  try {
    return new URL(apiUrl).origin;
  } catch {
    return undefined;
  }
}

/**
 * One hosted kelta-mcp endpoint (`{mcpUrl}/{slug}/mcp/{toolset}`). The hosted
 * server is STATELESS — every POST is one self-contained JSON-RPC exchange —
 * so the bridge needs no session and survives pod restarts by construction.
 */
export class RemoteEndpoint {
  private nextId = 1;

  constructor(
    private readonly mcpUrl: string,
    private readonly tenantSlug: string,
    private readonly toolset: Toolset,
    private readonly token: string
  ) {}

  get name(): Toolset {
    return this.toolset;
  }

  private get url(): string {
    return `${this.mcpUrl.replace(/\/$/, '')}/${this.tenantSlug}/mcp/${this.toolset}`;
  }

  async call(method: string, params?: unknown): Promise<unknown> {
    const response = await axios.post<JsonRpcResponse>(
      this.url,
      { jsonrpc: '2.0', id: this.nextId++, method, ...(params !== undefined ? { params } : {}) },
      {
        headers: {
          Authorization: `Bearer ${this.token}`,
          'Content-Type': 'application/json',
          Accept: 'application/json, text/event-stream',
        },
      }
    );
    const body = response.data;
    if (body.error) {
      throw new CliError(`Hosted MCP (${this.toolset}): ${body.error.message}`, {
        code: 'REMOTE_MCP_ERROR',
        exitCode: EXIT.API,
      });
    }
    return body.result;
  }

  async listTools(): Promise<RemoteTool[]> {
    const result = (await this.call('tools/list', {})) as { tools?: RemoteTool[] } | undefined;
    return result?.tools ?? [];
  }
}
