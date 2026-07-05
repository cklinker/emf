import { Command } from 'commander';
import type { AxiosInstance } from 'axios';
import { createClient } from '../client.js';

/** Default delay between polls for `--wait` commands. */
const DEFAULT_POLL_INTERVAL_MS = 2000;
/** Default maximum number of polls before a `--wait` command gives up (~5 min). */
const DEFAULT_MAX_ATTEMPTS = 150;

const ENV_TERMINAL_STATUSES = new Set(['ACTIVE', 'FAILED']);
const PROMOTION_TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED']);
const PROMOTION_TYPES = ['FULL', 'SELECTIVE'];
const CONFLICT_MODES = ['SKIP', 'OVERWRITE'];

export interface EnvironmentAttrs {
  name?: string;
  description?: string;
  type?: string;
  status?: string;
  sandbox_slug?: string;
  sandbox_tenant_id?: string;
  remote_base_url?: string;
  /** Returned once on sandbox creation. */
  sandboxSlug?: string;
  /** Returned once on sandbox creation. */
  adminUsername?: string;
  /** Returned once on sandbox creation — never shown again. */
  adminInitialPassword?: string;
}

export interface PromotionAttrs {
  status?: string;
  promotion_type?: string;
  conflict_mode?: string;
  items_promoted?: number;
  items_skipped?: number;
  items_failed?: number;
  source_env_name?: string;
  target_env_name?: string;
  error_message?: string;
}

interface JsonApiResource<A> {
  id: string;
  type?: string;
  attributes?: A;
}

interface JsonApiListResponse<A> {
  data?: JsonApiResource<A>[];
}

interface JsonApiSingleResponse<A> {
  data?: JsonApiResource<A>;
}

export type EnvironmentResource = JsonApiResource<EnvironmentAttrs>;
export type PromotionResource = JsonApiResource<PromotionAttrs>;

export interface PromotionItem {
  itemType: string;
  itemName: string;
}

export interface PromotionChange {
  action?: string;
  type?: string;
  name?: string;
}

export interface PromotionPreview {
  changes?: PromotionChange[];
}

export interface WaitOptions {
  wait?: boolean;
  intervalMs?: number;
  maxAttempts?: number;
}

export interface SandboxCreateOptions {
  name: string;
  description?: string;
}

export interface PromoteCreateOptions {
  sourceEnvId: string;
  targetEnvId: string;
  promotionType: string;
  conflictMode: string;
  items?: PromotionItem[];
}

function requestError(res: { status: number; data: unknown }): Error {
  return new Error(`Request failed (status ${String(res.status)}): ${JSON.stringify(res.data)}`);
}

function isSuccess(status: number): boolean {
  return status >= 200 && status < 300;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Parse a repeatable `--item TYPE:name` spec, splitting on the first colon. */
export function parseItemSpec(spec: string): PromotionItem {
  const idx = spec.indexOf(':');
  if (idx <= 0 || idx === spec.length - 1) {
    throw new Error(`Invalid --item "${spec}" (expected TYPE:name, e.g. collection:orders)`);
  }
  return { itemType: spec.slice(0, idx), itemName: spec.slice(idx + 1) };
}

/** Uppercase and validate an enum-style CLI choice. */
function normalizeChoice(value: string, allowed: string[], label: string): string {
  const upper = value.toUpperCase();
  if (!allowed.includes(upper)) {
    throw new Error(`Invalid ${label} "${value}" (expected ${allowed.join(' or ')})`);
  }
  return upper;
}

/** Create a sandbox environment — POST /api/environments (202). */
export async function runSandboxCreate(
  client: AxiosInstance,
  opts: SandboxCreateOptions
): Promise<EnvironmentResource> {
  const attributes: Record<string, unknown> = { name: opts.name, type: 'SANDBOX' };
  if (opts.description !== undefined) {
    attributes.description = opts.description;
  }
  const res = await client.post<JsonApiSingleResponse<EnvironmentAttrs>>('/api/environments', {
    data: { attributes },
  });
  if (!isSuccess(res.status)) throw requestError(res);
  const resource = res.data?.data;
  if (!resource) throw new Error('Environment creation returned no resource');
  return resource;
}

/** List environments — GET /api/environments. */
export async function runSandboxList(client: AxiosInstance): Promise<EnvironmentResource[]> {
  const res = await client.get<JsonApiListResponse<EnvironmentAttrs>>('/api/environments');
  if (res.status !== 200) throw requestError(res);
  return Array.isArray(res.data?.data) ? res.data.data : [];
}

async function fetchEnvironment(
  client: AxiosInstance,
  envId: string
): Promise<EnvironmentResource> {
  const envs = await runSandboxList(client);
  const env = envs.find((e) => e.id === envId);
  if (!env) throw new Error(`Environment ${envId} not found`);
  return env;
}

/**
 * Show an environment's status. With {@code wait}, polls until the environment
 * reaches a terminal status (ACTIVE or FAILED) or the attempt budget runs out.
 */
export async function runSandboxStatus(
  client: AxiosInstance,
  envId: string,
  opts: WaitOptions = {}
): Promise<EnvironmentResource> {
  let env = await fetchEnvironment(client, envId);
  if (!opts.wait) return env;

  const intervalMs = opts.intervalMs ?? DEFAULT_POLL_INTERVAL_MS;
  const maxAttempts = opts.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (ENV_TERMINAL_STATUSES.has(env.attributes?.status ?? '')) return env;
    await sleep(intervalMs);
    env = await fetchEnvironment(client, envId);
  }
  if (ENV_TERMINAL_STATUSES.has(env.attributes?.status ?? '')) return env;
  throw new Error(
    `Timed out waiting for environment ${envId} (last status: ${env.attributes?.status ?? 'unknown'})`
  );
}

/** Refresh a sandbox from its source — POST /api/environments/{id}/refresh (202). */
export async function runSandboxRefresh(client: AxiosInstance, envId: string): Promise<void> {
  const res = await client.post<unknown>(`/api/environments/${envId}/refresh`);
  if (!isSuccess(res.status)) throw requestError(res);
}

/** Create a promotion — POST /api/promotions. */
export async function runPromoteCreate(
  client: AxiosInstance,
  opts: PromoteCreateOptions
): Promise<PromotionResource> {
  const attributes: Record<string, unknown> = {
    sourceEnvId: opts.sourceEnvId,
    targetEnvId: opts.targetEnvId,
    promotionType: opts.promotionType,
    conflictMode: opts.conflictMode,
  };
  if (opts.items && opts.items.length > 0) {
    attributes.items = opts.items;
  }
  const res = await client.post<JsonApiSingleResponse<PromotionAttrs>>('/api/promotions', {
    data: { attributes },
  });
  if (!isSuccess(res.status)) throw requestError(res);
  const resource = res.data?.data;
  if (!resource) throw new Error('Promotion creation returned no resource');
  return resource;
}

/** Preview the changes a promotion would make — GET /api/promotions/preview. */
export async function runPromotePreview(
  client: AxiosInstance,
  sourceEnvId: string
): Promise<PromotionPreview> {
  const res = await client.get<PromotionPreview>(
    `/api/promotions/preview?sourceEnvId=${sourceEnvId}`
  );
  if (res.status !== 200) throw requestError(res);
  return res.data ?? {};
}

/** Fetch a promotion — GET /api/promotions/{id}. */
export async function runPromoteStatus(
  client: AxiosInstance,
  id: string
): Promise<PromotionResource> {
  const res = await client.get<JsonApiSingleResponse<PromotionAttrs>>(`/api/promotions/${id}`);
  if (res.status !== 200) throw requestError(res);
  const resource = res.data?.data;
  if (!resource) throw new Error(`Promotion ${id} not found`);
  return resource;
}

/** Approve a promotion — POST /api/promotions/{id}/approve (409 when approver == creator). */
export async function runPromoteApprove(
  client: AxiosInstance,
  id: string
): Promise<PromotionResource | undefined> {
  const res = await client.post<JsonApiSingleResponse<PromotionAttrs>>(
    `/api/promotions/${id}/approve`
  );
  if (res.status === 409) {
    throw new Error('Approval rejected (409): a promotion cannot be approved by its creator.');
  }
  if (!isSuccess(res.status)) throw requestError(res);
  return res.data?.data;
}

/**
 * Execute a promotion — POST /api/promotions/{id}/execute (202). Without
 * {@code wait} this returns {@code null} once execution is accepted. With
 * {@code wait} it polls GET /api/promotions/{id} until the promotion reaches
 * a terminal status (COMPLETED or FAILED) and returns the final resource.
 */
export async function runPromoteExecute(
  client: AxiosInstance,
  id: string,
  opts: WaitOptions = {}
): Promise<PromotionResource | null> {
  const res = await client.post<unknown>(`/api/promotions/${id}/execute`);
  if (!isSuccess(res.status)) throw requestError(res);
  if (!opts.wait) return null;

  const intervalMs = opts.intervalMs ?? DEFAULT_POLL_INTERVAL_MS;
  const maxAttempts = opts.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const promotion = await runPromoteStatus(client, id);
    if (PROMOTION_TERMINAL_STATUSES.has(promotion.attributes?.status ?? '')) {
      return promotion;
    }
    await sleep(intervalMs);
  }
  throw new Error(`Timed out waiting for promotion ${id} to finish`);
}

/** Roll back an executed promotion — POST /api/promotions/{id}/rollback. */
export async function runPromoteRollback(client: AxiosInstance, id: string): Promise<void> {
  const res = await client.post<unknown>(`/api/promotions/${id}/rollback`);
  if (!isSuccess(res.status)) throw requestError(res);
}

function fail(e: unknown): void {
  process.stderr.write(`${(e as Error).message}\n`);
  process.exit(1);
}

function printEnvironment(env: EnvironmentResource): void {
  const attrs = env.attributes ?? {};
  process.stdout.write(`Environment: ${attrs.name ?? env.id}\n`);
  process.stdout.write(`  ID:     ${env.id}\n`);
  process.stdout.write(`  Type:   ${attrs.type ?? ''}\n`);
  process.stdout.write(`  Status: ${attrs.status ?? ''}\n`);
}

function printPromotion(promotion: PromotionResource): void {
  const attrs = promotion.attributes ?? {};
  process.stdout.write(`Promotion ${promotion.id}\n`);
  process.stdout.write(`  Status:  ${attrs.status ?? ''}\n`);
  process.stdout.write(
    `  Type:    ${attrs.promotion_type ?? ''} (conflict: ${attrs.conflict_mode ?? ''})\n`
  );
  process.stdout.write(`  Source:  ${attrs.source_env_name ?? ''}\n`);
  process.stdout.write(`  Target:  ${attrs.target_env_name ?? ''}\n`);
  process.stdout.write(
    `  Items:   ${String(attrs.items_promoted ?? 0)} promoted, ` +
      `${String(attrs.items_skipped ?? 0)} skipped, ${String(attrs.items_failed ?? 0)} failed\n`
  );
  if (attrs.error_message) {
    process.stdout.write(`  Error:   ${attrs.error_message}\n`);
  }
}

function registerSandboxCommands(program: Command): void {
  const sandbox = program.command('sandbox').description('Create and manage sandbox environments');

  sandbox
    .command('create')
    .description('Create a sandbox environment (prints one-time admin credentials)')
    .requiredOption('-n, --name <name>', 'Environment name')
    .option('-d, --description <description>', 'Environment description')
    .action(async (opts: SandboxCreateOptions) => {
      try {
        const env = await runSandboxCreate(createClient(), opts);
        const attrs = env.attributes ?? {};
        process.stdout.write(
          `Sandbox environment created (id: ${env.id}, status: ${attrs.status ?? 'PENDING'})\n`
        );
        if (attrs.sandboxSlug || attrs.adminUsername || attrs.adminInitialPassword) {
          process.stdout.write(
            '\nOne-time admin credentials — store them now, they will NOT be shown again:\n'
          );
          process.stdout.write(`  Sandbox slug:   ${attrs.sandboxSlug ?? ''}\n`);
          process.stdout.write(`  Admin username: ${attrs.adminUsername ?? ''}\n`);
          process.stdout.write(`  Admin password: ${attrs.adminInitialPassword ?? ''}\n`);
        }
      } catch (e) {
        fail(e);
      }
    });

  sandbox
    .command('list')
    .description('List environments')
    .action(async () => {
      try {
        const envs = await runSandboxList(createClient());
        if (envs.length === 0) {
          process.stdout.write('No environments found.\n');
          return;
        }
        process.stdout.write(
          `${'Name'.padEnd(28)} ${'Type'.padEnd(12)} ${'Status'.padEnd(14)} Slug/Remote\n`
        );
        process.stdout.write('-'.repeat(80) + '\n');
        for (const env of envs) {
          const attrs = env.attributes ?? {};
          const target =
            attrs.sandbox_slug ?? attrs.remote_base_url ?? attrs.sandbox_tenant_id ?? '';
          process.stdout.write(
            `${(attrs.name ?? env.id).padEnd(28)} ${(attrs.type ?? '').padEnd(12)} ` +
              `${(attrs.status ?? '').padEnd(14)} ${target}\n`
          );
        }
      } catch (e) {
        fail(e);
      }
    });

  sandbox
    .command('status <envId>')
    .description('Show environment status (with --wait, poll until ACTIVE or FAILED)')
    .option('--wait', 'Poll until the environment reaches ACTIVE or FAILED')
    .action(async (envId: string, opts: { wait?: boolean }) => {
      try {
        const env = await runSandboxStatus(createClient(), envId, { wait: opts.wait });
        printEnvironment(env);
        if (env.attributes?.status === 'FAILED') {
          process.exit(1);
        }
      } catch (e) {
        fail(e);
      }
    });

  sandbox
    .command('refresh <envId>')
    .description('Refresh a sandbox environment from its source')
    .action(async (envId: string) => {
      try {
        await runSandboxRefresh(createClient(), envId);
        process.stdout.write(`Refresh started for environment ${envId}.\n`);
      } catch (e) {
        fail(e);
      }
    });
}

function registerPromoteCommands(program: Command): void {
  const promote = program.command('promote').description('Promote metadata between environments');

  const collectItem = (value: string, previous: string[]): string[] => previous.concat([value]);

  promote
    .command('create')
    .description('Create a promotion from a source to a target environment')
    .requiredOption('-s, --source <envId>', 'Source environment id')
    .requiredOption('-t, --target <envId>', 'Target environment id')
    .option('--type <type>', 'Promotion type: FULL or SELECTIVE', 'FULL')
    .option('--conflict <mode>', 'Conflict mode: skip or overwrite', 'skip')
    .option(
      '--item <spec>',
      'Item to promote as TYPE:name (repeatable, for SELECTIVE promotions)',
      collectItem,
      [] as string[]
    )
    .action(
      async (opts: {
        source: string;
        target: string;
        type: string;
        conflict: string;
        item: string[];
      }) => {
        try {
          const promotion = await runPromoteCreate(createClient(), {
            sourceEnvId: opts.source,
            targetEnvId: opts.target,
            promotionType: normalizeChoice(opts.type, PROMOTION_TYPES, 'promotion type'),
            conflictMode: normalizeChoice(opts.conflict, CONFLICT_MODES, 'conflict mode'),
            items: opts.item.map(parseItemSpec),
          });
          process.stdout.write(
            `Promotion created (id: ${promotion.id}, status: ${
              promotion.attributes?.status ?? 'PENDING'
            })\n`
          );
        } catch (e) {
          fail(e);
        }
      }
    );

  promote
    .command('preview')
    .description('Preview the changes a promotion from a source environment would make')
    .requiredOption('-s, --source <envId>', 'Source environment id')
    .action(async (opts: { source: string }) => {
      try {
        const preview = await runPromotePreview(createClient(), opts.source);
        const changes = preview.changes ?? [];
        if (changes.length === 0) {
          process.stdout.write('No changes to promote.\n');
          return;
        }
        process.stdout.write(`${String(changes.length)} change(s):\n`);
        process.stdout.write(`  ${'Action'.padEnd(10)} ${'Type'.padEnd(18)} Name\n`);
        process.stdout.write(`  ${'-'.repeat(60)}\n`);
        for (const change of changes) {
          process.stdout.write(
            `  ${(change.action ?? '').padEnd(10)} ${(change.type ?? '').padEnd(18)} ` +
              `${change.name ?? ''}\n`
          );
        }
      } catch (e) {
        fail(e);
      }
    });

  promote
    .command('approve <id>')
    .description('Approve a promotion (a promotion cannot be approved by its creator)')
    .action(async (id: string) => {
      try {
        const promotion = await runPromoteApprove(createClient(), id);
        const status = promotion?.attributes?.status;
        process.stdout.write(`Promotion ${id} approved${status ? ` (status: ${status})` : ''}.\n`);
      } catch (e) {
        fail(e);
      }
    });

  promote
    .command('execute <id>')
    .description('Execute a promotion (with --wait, poll until COMPLETED or FAILED)')
    .option('--wait', 'Poll until the promotion reaches COMPLETED or FAILED')
    .action(async (id: string, opts: { wait?: boolean }) => {
      try {
        const promotion = await runPromoteExecute(createClient(), id, { wait: opts.wait });
        if (!promotion) {
          process.stdout.write(
            `Execution started for promotion ${id}. Check progress with: kelta promote status ${id}\n`
          );
          return;
        }
        printPromotion(promotion);
        if (promotion.attributes?.status === 'FAILED') {
          process.exit(1);
        }
      } catch (e) {
        fail(e);
      }
    });

  promote
    .command('status <id>')
    .description('Show a promotion and its item counts')
    .action(async (id: string) => {
      try {
        printPromotion(await runPromoteStatus(createClient(), id));
      } catch (e) {
        fail(e);
      }
    });

  promote
    .command('rollback <id>')
    .description('Roll back an executed promotion')
    .action(async (id: string) => {
      try {
        await runPromoteRollback(createClient(), id);
        process.stdout.write(`Rollback started for promotion ${id}.\n`);
      } catch (e) {
        fail(e);
      }
    });
}

export function registerEnvironmentCommands(program: Command): void {
  registerSandboxCommands(program);
  registerPromoteCommands(program);
}
