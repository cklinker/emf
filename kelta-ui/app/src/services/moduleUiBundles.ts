import type { ApiClient } from './apiClient'

/**
 * Shape of the module rows `GET /api/modules` returns. Only the fields the loader needs.
 */
interface TenantModuleSummary {
  moduleId: string
  status: string
  uiBundlePath?: string | null
}

/** Modules whose bundle has already been evaluated, so a re-render never double-registers. */
const loadedModuleIds = new Set<string>()

/**
 * Loads the browser bundles of the tenant's active runtime modules, so a module can ship its UI
 * in the same single JAR as its backend code.
 *
 * <p>Each bundle's top-level code calls `ComponentRegistry.registerPageComponent(...)` from
 * `@kelta/plugin-sdk`; the caller syncs that registry into the host afterwards.
 *
 * The bundle is fetched through the authenticated `ApiClient` rather than imported straight from
 * its URL, because a bare `import()` cannot attach the session token. The fetched source is then
 * evaluated from a blob URL, which inherits this page's origin.
 *
 * **This executes publisher-authored JavaScript with the admin session's full DOM and cookie
 * access.** The trust anchor is the JAR signature the platform verified at install and re-verified
 * on read — there is no browser-side isolation. Serving a bundle from an unverified JAR would make
 * a bad module a stored-XSS vector across the tenant.
 *
 * A module that fails to load is logged and skipped: one broken module must not take the admin UI
 * down with it.
 */
export async function loadModuleUiBundles(apiClient: ApiClient): Promise<string[]> {
  let modules: TenantModuleSummary[]
  try {
    modules = await apiClient.get<TenantModuleSummary[]>('/api/modules')
  } catch (err) {
    // A tenant without the modules feature, or without permission to list them, is the normal
    // case — not an error worth surfacing in the UI.
    console.debug('[Module UI] Could not list modules; skipping module bundles', err)
    return []
  }

  const loaded: string[] = []
  for (const module of modules ?? []) {
    if (module.status !== 'ACTIVE' || !module.uiBundlePath) {
      continue
    }
    if (loadedModuleIds.has(module.moduleId)) {
      continue
    }

    let objectUrl: string | undefined
    try {
      const source = await apiClient.get<string>(`/api/modules/${module.moduleId}/ui-bundle.js`)
      objectUrl = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }))
      await import(/* @vite-ignore */ objectUrl)
      loadedModuleIds.add(module.moduleId)
      loaded.push(module.moduleId)
      console.info(`[Module UI] Loaded UI bundle for module: ${module.moduleId}`)
    } catch (err) {
      console.error(`[Module UI] Failed to load UI bundle for module ${module.moduleId}:`, err)
    } finally {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }
  return loaded
}

/** Test seam — drops the loaded-module memo so a fresh mount re-evaluates bundles. */
export function resetLoadedModuleUiBundles(): void {
  loadedModuleIds.clear()
}
