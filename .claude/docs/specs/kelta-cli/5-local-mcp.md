# Slice 5 — Local MCP: `kelta mcp serve` + `kelta mcp install`

> Child of `specs/kelta-cli/README.md`. Exposes all CLI features over stdio MCP and bridges
> the hosted kelta-mcp toolsets behind profile-managed auth.

## 1. Goal & scope

`kelta mcp serve [--profile p] [--toolset user|admin|all] [--source auto|remote|local]` —
a stdio MCP server (TypeScript `@modelcontextprotocol/sdk`) composing two tool sources:

- **Remote bridge (default for platform tools)**: forwards JSON-RPC messages to hosted
  kelta-mcp at `{mcpUrl}/{tenantSlug}/mcp/{user|admin}` with the profile PAT as bearer.
  **`mcpUrl` defaults to the profile's API origin** — kelta-mcp is not a separate public
  host; the gateway routes `^/[a-z][a-z0-9-]+/mcp/(user|admin)` on the `api.*` host.
  `--mcp-url` overrides it for deployments that do front kelta-mcp separately.
  The hosted server is **stateless HTTP** (one POST per message, no session) — the bridge
  is a thin per-message forwarder; pod restarts are invisible by design. `tools/list` is
  fetched from the remote and merged. This keeps the 38 platform tools single-sourced in
  Java — zero duplication.
- **Local tools**: registry commands that have no hosted equivalent, auto-generated from
  the slice-1 registry (name `cli_<group>_<name>`, input schema from the same zod→schema
  path as `manifest`): `metadata export/diff/apply`, `sandbox *`, `promote *`,
  `token list/revoke`, `profile list`, `api` (gated, see Security), `sdk types`.
  Handlers run in-process against the resolved profile; results returned as structured
  JSON content.

Merge rules: remote names win verbatim (they match the `mcp__kelta-*` tools users already
know); local names are `cli_`-prefixed so collision is impossible. `--source local` runs
without the hosted server (still needs gateway reachability for the underlying REST calls);
`--source remote` disables local tools.

**`kelta mcp install <claude-code|claude-desktop|cursor|generic>`** — writes/prints client
config: default recommends the stdio bridge (`command: kelta`, `args: [mcp, serve,
--profile, p]`) so credentials stay in the CLI store; `--direct` variant emits the hosted
HTTP config with explicit header (today's copy-paste from `TokenCreatedDialog`, automated).
`generic` prints JSON for manual use.

Out of scope: serving MCP over HTTP locally (stdio only — clients that want HTTP use the
hosted server directly); MCP resources/prompts (tools only, matching hosted capabilities).

## 2. UI samples

```console
$ kelta mcp install claude-code --profile prod --toolset admin
Added MCP server "kelta-prod-admin" to ~/.claude.json (stdio → kelta mcp serve)

$ kelta mcp serve --profile prod --toolset all   # stdio; logs to stderr only
```

## 3. Data & API contracts

- Remote bridge forwards `initialize`, `tools/list`, `tools/call` POSTs; JSON-RPC ids are
  passed through unmodified; notifications get 204-equivalent swallow (hosted contract).
- Tool result shape for local tools: `content: [{type:"text", text: <json>}]` with
  `structuredContent` mirroring — same convention the hosted tools use.
- stdout carries MCP frames ONLY; all logging to stderr (hard rule — one stray
  `console.log` corrupts the stream; lint rule added).

## 4. DB migrations

N/A.

## 5. File-by-file code changes

- `src/mcp/{server,remoteBridge,localTools,merge}.ts`; `src/commands/mcp.ts` (serve,
  install); eslint `no-console` override scoped to the package with stderr logger util.
- `package.json`: `@modelcontextprotocol/sdk` dep.

## 6. Test plan

- Unit: merge/collision rules, `cli_` schema generation parity with `manifest` (shared
  code path, snapshot), stderr-only logging lint test.
- Integration: spawn `kelta mcp serve` against MSW-mocked gateway + a WireMock-style
  mocked hosted MCP; drive with the MCP SDK client: initialize → tools/list (merged set)
  → one remote call → one local call → error propagation.
- e2e (compose stack incl. kelta-mcp container): tools/list count matches hosted 38 +
  local set; `cli_metadata_export` writes a file.

## 7. Docs to update

- `status.md`; `kelta-mcp/CLAUDE.md` (connection section gains the CLI bridge as the
  recommended local setup); `kelta-web/README.md`.

## 8. Risks & open questions

- `cli_api` (raw escape hatch as an MCP tool) is powerful; ship it **disabled by default**
  (`--enable-api-tool` opt-in) so a hosted-toolset-shaped blast radius stays the default.
- Hosted `tools/list` adds admin tools over time — bridge is forward-compatible by
  construction (no local tool table to update).
- If the CLI binary (slice 6) is the installed artifact, `mcp install` must reference the
  absolute binary path on Windows (`kelta.exe`) — handled by the installer writing config
  with resolved paths.
