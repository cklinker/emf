/**
 * Binding-context helpers (slice 2d). `buildScopeNamespaces(contract)` derives the inspector picker's
 * `staticNamespaces` (`vars` / `page` / `data`) from what the page already declares, so the author sees
 * exactly the page's variables and data-source names with zero extra config. `record` cascades into the
 * bound collection schema via `rootCollectionId` (passed separately) and is not a static namespace here.
 */
import type { StaticNamespace } from '@/components/FieldExpressionPicker/types'
import type { PageDataSource, PageVariable } from '../pageConfig'

interface ScopeNamespaceContract {
  variables?: PageVariable[]
  dataSources?: PageDataSource[]
}

/** Map a `PageVariable.type` to the picker's leaf field type. */
function varLeafType(type: PageVariable['type']): string {
  return type === 'json' ? 'json' : type
}

/**
 * Build the `vars` / `page` / `data` static namespaces for the FieldExpressionPicker from the page's
 * declared variables + data sources. `record` is supplied to the picker via `rootCollectionId`
 * (cascading real schema), not as a static namespace.
 */
export function buildScopeNamespaces(contract: ScopeNamespaceContract): StaticNamespace[] {
  const variables = contract.variables ?? []
  const dataSources = contract.dataSources ?? []

  return [
    {
      name: 'vars',
      label: 'Page variables',
      fields: variables
        .filter((v) => typeof v.name === 'string' && v.name.length > 0)
        .map((v) => ({ name: v.name, displayName: v.name, type: varLeafType(v.type) })),
    },
    {
      name: 'page',
      label: 'Route / page',
      fields: [
        { name: 'slug', displayName: 'Slug', type: 'string' },
        { name: 'path', displayName: 'Path', type: 'string' },
        { name: 'params', displayName: 'Params', type: 'json' },
      ],
    },
    {
      name: 'data',
      label: 'Data sources',
      fields: dataSources
        .filter((d) => typeof d.name === 'string' && d.name.length > 0)
        .map((d) => ({ name: d.name, displayName: d.name, type: 'json' })),
    },
  ]
}
