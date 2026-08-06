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
 * Derive the hosted MCP base URL from the API URL by swapping a leading
 * `api.` host label for `mcp.` (api.kelta.io → mcp.kelta.io).
 */
export function deriveMcpUrl(apiUrl: string): string | undefined {
  try {
    const url = new URL(apiUrl);
    if (url.hostname.startsWith('api.')) {
      url.hostname = 'mcp.' + url.hostname.slice(4);
      url.pathname = '';
      return url.origin;
    }
  } catch {
    // fall through
  }
  return undefined;
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
