interface JsonApiResource {
  id?: string;
  type?: string;
  attributes?: Record<string, unknown>;
  relationships?: Record<string, { data?: { id?: string } | { id?: string }[] | null }>;
}

interface JsonApiBody {
  data?: JsonApiResource | JsonApiResource[] | null;
}

function isResource(value: unknown): value is JsonApiResource {
  return (
    typeof value === 'object' &&
    value !== null &&
    ('attributes' in value || 'id' in value) &&
    !Array.isArray(value)
  );
}

/**
 * Flatten one JSON:API resource to `{ id, ...attributes }` plus to-one
 * relationship ids (`<rel>: id`) and to-many id arrays. This is the default
 * machine/table shape; `--raw` bypasses it.
 */
export function flattenResource(resource: JsonApiResource): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (resource.id !== undefined) out.id = resource.id;
  Object.assign(out, resource.attributes ?? {});
  for (const [name, rel] of Object.entries(resource.relationships ?? {})) {
    if (name in out) continue;
    const data = rel?.data;
    if (Array.isArray(data)) out[name] = data.map((d) => d.id);
    else if (data && typeof data === 'object') out[name] = data.id;
    else out[name] = null;
  }
  return out;
}

/**
 * Flatten a JSON:API response body: list → array of flat rows, single → flat
 * object. Anything that isn't a JSON:API envelope passes through unchanged.
 */
export function flattenBody(body: unknown): unknown {
  if (typeof body !== 'object' || body === null || !('data' in body)) return body;
  const data = (body as JsonApiBody).data;
  if (Array.isArray(data)) return data.filter(isResource).map(flattenResource);
  if (isResource(data)) return flattenResource(data);
  return body;
}

/** Extract ids for `--quiet` from a flattened (or raw) response body. */
export function extractIds(body: unknown): string[] {
  const flat = flattenBody(body);
  const rows = Array.isArray(flat) ? flat : [flat];
  return rows
    .map((row) =>
      typeof row === 'object' && row !== null && 'id' in row
        ? String((row as { id: unknown }).id)
        : undefined
    )
    .filter((id): id is string => id !== undefined);
}
