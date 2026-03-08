/**
 * One-time migration of localStorage keys from emf_* to kelta_*.
 * Preserves auth tokens, theme preferences, favorites, recent items, etc.
 * Runs once on app load, then sets a flag so it doesn't run again.
 */
export function migrateLocalStorage(): void {
  const migrationKey = 'kelta_storage_migrated'
  if (localStorage.getItem(migrationKey)) return

  const keysToMigrate: string[] = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith('emf_')) {
      keysToMigrate.push(key)
    }
  }

  for (const oldKey of keysToMigrate) {
    const newKey = oldKey.replace(/^emf_/, 'kelta_')
    const value = localStorage.getItem(oldKey)
    if (value !== null && localStorage.getItem(newKey) === null) {
      localStorage.setItem(newKey, value)
    }
    localStorage.removeItem(oldKey)
  }

  localStorage.setItem(migrationKey, 'true')
}
