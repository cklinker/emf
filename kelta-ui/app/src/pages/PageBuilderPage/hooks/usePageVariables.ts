/**
 * Page-variable state (slice 2d). Seeds `vars` from each {@link PageVariable}'s `default` (coerced by
 * its declared `type`), exposes a `setVar` writer (used by 2e's `setVar` action) and a `reset`. The
 * resulting `vars` map feeds `scope.vars` for binding resolution.
 */
import { useCallback, useMemo, useState } from 'react'
import { toast } from 'sonner'
import type { PageVariable } from '../pageConfig'

export interface UsePageVariablesReturn {
  /** The live variable map, keyed by name — feeds `scope.vars`. */
  vars: Record<string, unknown>
  /** Set one variable's value (idempotent; triggers a re-render). */
  setVar: (name: string, value: unknown) => void
  /** Restore every variable to its declared default. */
  reset: () => void
}

/** Coerce a raw default into the variable's declared type. */
function coerceDefault(variable: PageVariable): unknown {
  const raw = variable.default
  switch (variable.type) {
    case 'number': {
      if (typeof raw === 'number') return raw
      if (typeof raw === 'string' && raw.trim() !== '') {
        const n = Number(raw)
        return Number.isNaN(n) ? 0 : n
      }
      return 0
    }
    case 'boolean':
      return typeof raw === 'boolean' ? raw : raw === 'true'
    case 'string':
      return raw == null ? '' : String(raw)
    case 'json':
    default:
      return raw ?? null
  }
}

/**
 * Build the initial `vars` map from the declared variables (last duplicate name wins).
 * Computed variables (slice 2) carry no state — they derive per render — so they are
 * excluded from seeding.
 */
function seedVars(variables: PageVariable[]): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const v of variables) {
    if (v && typeof v.name === 'string' && v.name.length > 0 && v.kind !== 'computed') {
      out[v.name] = coerceDefault(v)
    }
  }
  return out
}

export function usePageVariables(variables: PageVariable[]): UsePageVariablesReturn {
  // A stable signature of the declared variable set; re-seed only when it actually changes.
  const seedKey = useMemo(
    () => JSON.stringify(variables.map((v) => [v.name, v.type, v.default, v.kind])),
    [variables]
  )
  // Lazy initializer so the first render seeds without a manual-memo dependency the compiler rejects.
  const [state, setState] = useState<{ key: string; vars: Record<string, unknown> }>(() => ({
    key: seedKey,
    vars: seedVars(variables),
  }))

  // Re-seed during render when the declared set changes (React's "adjust state on prop change" pattern —
  // no effect, no cascading render). User edits via setVar keep the same key and are preserved.
  if (state.key !== seedKey) {
    setState({ key: seedKey, vars: seedVars(variables) })
  }

  // Computed variables are read-only — `setVar` (incl. the 2e action) rejects them here,
  // the single guard site.
  const computedNames = useMemo(
    () => new Set(variables.filter((v) => v.kind === 'computed').map((v) => v.name)),
    [variables]
  )

  const setVar = useCallback(
    (name: string, value: unknown) => {
      if (computedNames.has(name)) {
        toast.error(`"${name}" is a computed variable and cannot be set`)
        return
      }
      setState((prev) => ({ key: prev.key, vars: { ...prev.vars, [name]: value } }))
    },
    [computedNames]
  )

  const reset = useCallback(
    () => setState((prev) => ({ key: prev.key, vars: seedVars(variables) })),
    [variables]
  )

  return { vars: state.vars, setVar, reset }
}
