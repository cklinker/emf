import type { MappingMap, SerializedMapping, TargetBinding } from './types'

/**
 * Round-trip helpers between the visual editor and the JSON form the worker
 * payload mapper expects. The wire shape follows the platform mapper rules:
 *
 *   { foo: "constant value" }                       — bare string literal
 *   { foo: "${$.path}" }                            — placeholder substitution
 *   { foo: "=jsonata expression" }                  — JSONata expression
 *
 * Nested target paths (e.g. "user.email") are reified back into nested objects
 * so {@code mapToObject} on the worker side gets the shape it expects.
 */

export function serializeBindings(bindings: MappingMap): SerializedMapping {
  const out: SerializedMapping = {}
  for (const [path, binding] of Object.entries(bindings)) {
    if (binding.kind === 'unset' || binding.value === '') continue
    setNested(out, path, encodeBinding(binding))
  }
  return out
}

export function deserializeMapping(
  template: unknown,
  knownTargets: string[]
): MappingMap {
  const result: MappingMap = {}
  for (const target of knownTargets) {
    const value = readNested(template, target)
    if (value === undefined) {
      result[target] = { kind: 'unset', value: '' }
    } else if (typeof value === 'string') {
      result[target] = decodeBindingFromString(value)
    } else {
      // Object/array values get serialized to JSON and treated as constants.
      result[target] = { kind: 'constant', value: JSON.stringify(value) }
    }
  }
  return result
}

function encodeBinding(binding: TargetBinding): unknown {
  switch (binding.kind) {
    case 'variable':
      // Allow either bare path or already-tokenized form.
      return binding.value.startsWith('${') ? binding.value : `\${${binding.value}}`
    case 'expression':
      return binding.value.startsWith('=') ? binding.value : `=${binding.value}`
    case 'constant':
      return binding.value
    default:
      return undefined
  }
}

function decodeBindingFromString(value: string): TargetBinding {
  if (value.startsWith('${') && value.endsWith('}')) {
    return { kind: 'variable', value: value.slice(2, -1) }
  }
  if (value.startsWith('=')) {
    return { kind: 'expression', value: value.slice(1) }
  }
  return { kind: 'constant', value }
}

function setNested(target: Record<string, unknown>, path: string, value: unknown) {
  const segments = path.split('.')
  let cursor: Record<string, unknown> = target
  for (let i = 0; i < segments.length - 1; i++) {
    const seg = segments[i]
    if (typeof cursor[seg] !== 'object' || cursor[seg] === null) {
      cursor[seg] = {}
    }
    cursor = cursor[seg] as Record<string, unknown>
  }
  cursor[segments[segments.length - 1]] = value
}

function readNested(template: unknown, path: string): unknown {
  if (template == null || typeof template !== 'object') return undefined
  const segments = path.split('.')
  let cursor: unknown = template
  for (const seg of segments) {
    if (cursor == null || typeof cursor !== 'object') return undefined
    cursor = (cursor as Record<string, unknown>)[seg]
  }
  return cursor
}
