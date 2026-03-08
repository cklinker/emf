import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { Button } from '@/components/ui/button'
import {
  Package,
  Power,
  PowerOff,
  Trash2,
  Plus,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  XCircle,
  Loader2,
} from 'lucide-react'

interface ModuleAction {
  id: string
  tenantModuleId: string
  actionKey: string
  name: string
  category: string | null
  description: string | null
  configSchema: string | null
  inputSchema: string | null
  outputSchema: string | null
}

interface TenantModule {
  id: string
  tenantId: string
  moduleId: string
  name: string
  version: string
  description: string | null
  sourceUrl: string
  jarChecksum: string
  jarSizeBytes: number | null
  moduleClass: string
  manifest: string
  status: string
  installedBy: string
  installedAt: string
  updatedAt: string
  actions: ModuleAction[]
}

export interface ModulesPageProps {
  testId?: string
}

function statusBadge(status: string) {
  switch (status) {
    case 'ACTIVE':
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800">
          <CheckCircle2 size={12} />
          Active
        </span>
      )
    case 'DISABLED':
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
          <PowerOff size={12} />
          Disabled
        </span>
      )
    case 'INSTALLED':
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">
          <Package size={12} />
          Installed
        </span>
      )
    case 'FAILED':
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">
          <XCircle size={12} />
          Failed
        </span>
      )
    case 'INSTALLING':
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800">
          <Loader2 size={12} className="animate-spin" />
          Installing
        </span>
      )
    default:
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
          {status}
        </span>
      )
  }
}

export function ModulesPage({ testId = 'modules-page' }: ModulesPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [isInstallOpen, setIsInstallOpen] = useState(false)
  const [manifestInput, setManifestInput] = useState('')
  const [sourceUrlInput, setSourceUrlInput] = useState('')
  const [checksumInput, setChecksumInput] = useState('')
  const [expandedModule, setExpandedModule] = useState<string | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [moduleToDelete, setModuleToDelete] = useState<TenantModule | null>(null)

  const {
    data: modules,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['modules'],
    queryFn: () => apiClient.get<TenantModule[]>('/api/modules'),
  })

  const installMutation = useMutation({
    mutationFn: (data: { manifest: string; sourceUrl: string; checksum: string }) =>
      apiClient.post<TenantModule>('/api/modules/install', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules'] })
      showToast('Module installed successfully', 'success')
      setIsInstallOpen(false)
      setManifestInput('')
      setSourceUrlInput('')
      setChecksumInput('')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to install module', 'error')
    },
  })

  const enableMutation = useMutation({
    mutationFn: (moduleId: string) => apiClient.post(`/api/modules/${moduleId}/enable`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules'] })
      showToast('Module enabled', 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to enable module', 'error')
    },
  })

  const disableMutation = useMutation({
    mutationFn: (moduleId: string) => apiClient.post(`/api/modules/${moduleId}/disable`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules'] })
      showToast('Module disabled', 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to disable module', 'error')
    },
  })

  const uninstallMutation = useMutation({
    mutationFn: (moduleId: string) => apiClient.delete(`/api/modules/${moduleId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules'] })
      showToast('Module uninstalled', 'success')
      setDeleteDialogOpen(false)
      setModuleToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to uninstall module', 'error')
    },
  })

  const handleInstall = () => {
    if (!manifestInput.trim()) {
      showToast('Manifest JSON is required', 'error')
      return
    }
    try {
      JSON.parse(manifestInput)
    } catch {
      showToast('Manifest must be valid JSON', 'error')
      return
    }
    installMutation.mutate({
      manifest: manifestInput,
      sourceUrl: sourceUrlInput || 'local://upload',
      checksum: checksumInput || 'none',
    })
  }

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return <ErrorMessage error={error} onRetry={() => refetch()} />
  }

  const moduleList = modules || []

  return (
    <div data-testid={testId} className="mx-auto max-w-5xl px-6 py-6">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Modules</h1>
          <p className="mt-1 text-sm text-gray-500">
            Install and manage runtime modules that extend flow capabilities
          </p>
        </div>
        <Button onClick={() => setIsInstallOpen(true)} className="gap-2">
          <Plus size={16} />
          Install Module
        </Button>
      </div>

      {/* Install Module Sheet */}
      {isInstallOpen && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-medium text-gray-900">Install Module</h2>

          <div className="space-y-4">
            <div>
              <label
                htmlFor="module-manifest"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Module Manifest (JSON) *
              </label>
              <textarea
                id="module-manifest"
                value={manifestInput}
                onChange={(e) => setManifestInput(e.target.value)}
                className="h-40 w-full rounded-md border border-gray-300 p-3 font-mono text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder='{"id": "my-module", "name": "My Module", "version": "1.0.0", ...}'
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label
                  htmlFor="module-source-url"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Source URL
                </label>
                <input
                  id="module-source-url"
                  type="text"
                  value={sourceUrlInput}
                  onChange={(e) => setSourceUrlInput(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="https://modules.example.com/module.jar"
                />
              </div>
              <div>
                <label
                  htmlFor="module-checksum"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  SHA-256 Checksum
                </label>
                <input
                  id="module-checksum"
                  type="text"
                  value={checksumInput}
                  onChange={(e) => setChecksumInput(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="a1b2c3d4e5f6..."
                />
              </div>
            </div>

            <div className="flex justify-end gap-3">
              <Button
                variant="outline"
                onClick={() => {
                  setIsInstallOpen(false)
                  setManifestInput('')
                  setSourceUrlInput('')
                  setChecksumInput('')
                }}
              >
                Cancel
              </Button>
              <Button onClick={handleInstall} disabled={installMutation.isPending}>
                {installMutation.isPending ? 'Installing...' : 'Install'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Empty State */}
      {moduleList.length === 0 && !isInstallOpen && (
        <div className="rounded-lg border border-dashed border-gray-300 p-12 text-center">
          <Package size={48} className="mx-auto mb-4 text-gray-400" />
          <h3 className="text-lg font-medium text-gray-900">No modules installed</h3>
          <p className="mt-1 text-sm text-gray-500">
            Install a module to add new action handlers to your flows
          </p>
          <Button onClick={() => setIsInstallOpen(true)} className="mt-4 gap-2">
            <Plus size={16} />
            Install Module
          </Button>
        </div>
      )}

      {/* Module Cards */}
      {moduleList.length > 0 && (
        <div className="space-y-4">
          {moduleList.map((mod) => (
            <div key={mod.id} className="rounded-lg border border-gray-200 bg-white shadow-sm">
              {/* Module Header */}
              <div className="flex items-center justify-between px-6 py-4">
                <div className="flex items-center gap-4">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50">
                    <Package size={20} className="text-blue-600" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-medium text-gray-900">{mod.name}</h3>
                      <span className="text-xs text-gray-500">v{mod.version}</span>
                      {statusBadge(mod.status)}
                    </div>
                    {mod.description && (
                      <p className="mt-0.5 text-sm text-gray-500">{mod.description}</p>
                    )}
                    <p className="mt-0.5 text-xs text-gray-400">
                      {mod.actions.length} action{mod.actions.length !== 1 ? 's' : ''} &middot;
                      Installed {new Date(mod.installedAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {mod.status === 'ACTIVE' ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => disableMutation.mutate(mod.moduleId)}
                      disabled={disableMutation.isPending}
                      className="gap-1"
                    >
                      <PowerOff size={14} />
                      Disable
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => enableMutation.mutate(mod.moduleId)}
                      disabled={enableMutation.isPending}
                      className="gap-1"
                    >
                      <Power size={14} />
                      Enable
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setModuleToDelete(mod)
                      setDeleteDialogOpen(true)
                    }}
                    className="gap-1 text-red-600 hover:bg-red-50 hover:text-red-700"
                  >
                    <Trash2 size={14} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setExpandedModule(expandedModule === mod.id ? null : mod.id)}
                  >
                    {expandedModule === mod.id ? (
                      <ChevronUp size={16} />
                    ) : (
                      <ChevronDown size={16} />
                    )}
                  </Button>
                </div>
              </div>

              {/* Expanded Actions List */}
              {expandedModule === mod.id && (
                <div className="border-t border-gray-100 px-6 py-4">
                  <h4 className="mb-3 text-sm font-medium text-gray-700">Action Handlers</h4>
                  {mod.actions.length === 0 ? (
                    <p className="text-sm text-gray-400">No action handlers declared</p>
                  ) : (
                    <div className="space-y-2">
                      {mod.actions.map((action) => (
                        <div
                          key={action.id}
                          className="flex items-center justify-between rounded-md bg-gray-50 px-4 py-2.5"
                        >
                          <div>
                            <div className="flex items-center gap-2">
                              <code className="rounded bg-blue-50 px-1.5 py-0.5 text-xs font-medium text-blue-700">
                                {action.actionKey}
                              </code>
                              <span className="text-sm text-gray-900">{action.name}</span>
                            </div>
                            {action.description && (
                              <p className="mt-0.5 text-xs text-gray-500">{action.description}</p>
                            )}
                          </div>
                          {action.category && (
                            <span className="rounded-full bg-gray-200 px-2 py-0.5 text-xs text-gray-600">
                              {action.category}
                            </span>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={deleteDialogOpen}
        title="Uninstall Module"
        message={`Are you sure you want to uninstall "${moduleToDelete?.name}"? This will remove all its action handlers. Flows using these handlers will need to be updated.`}
        confirmLabel="Uninstall"
        confirmVariant="destructive"
        onConfirm={() => {
          if (moduleToDelete) {
            uninstallMutation.mutate(moduleToDelete.moduleId)
          }
        }}
        onCancel={() => {
          setDeleteDialogOpen(false)
          setModuleToDelete(null)
        }}
        isProcessing={uninstallMutation.isPending}
      />
    </div>
  )
}

export default ModulesPage
