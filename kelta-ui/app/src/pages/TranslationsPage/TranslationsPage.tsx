/**
 * TranslationsPage (app-intelligence slice 4)
 *
 * Setup editor for the tenant-authored translation overlay (`ui-translations`):
 * locale filter, key search, add/edit/delete rows through the standard JSON:API.
 * Values are plain text (same `{{param}}` interpolation as the bundles); a
 * duplicate (locale, key) surfaces the server's unique-constraint error.
 */
import React, { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, Check, X, Languages } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useApi } from '@/context/ApiContext'
import { useI18n } from '@/context/I18nContext'

interface TranslationRow {
  id: string
  locale: string
  key: string
  value: string
}

/** Flatten a JSON:API list response into rows. */
function unwrapRows(body: unknown): TranslationRow[] {
  const data = (body as { data?: Array<Record<string, unknown>> })?.data ?? []
  return data.map((r) => {
    const attrs = (r.attributes ?? {}) as Record<string, unknown>
    return {
      id: String(r.id),
      locale: String(attrs.locale ?? ''),
      key: String(attrs.key ?? ''),
      value: String(attrs.value ?? ''),
    }
  })
}

const INPUT_CLASS = 'h-8 text-sm'

export function TranslationsPage(): React.ReactElement {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const { t, supportedLocales } = useI18n()

  const [localeFilter, setLocaleFilter] = useState<string>('en')
  const [search, setSearch] = useState('')
  const [draft, setDraft] = useState<{ key: string; value: string }>({ key: '', value: '' })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingValue, setEditingValue] = useState('')

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['ui-translations', localeFilter],
    queryFn: async () =>
      unwrapRows(
        await apiClient.get(
          `/api/ui-translations?filter[locale][eq]=${encodeURIComponent(localeFilter)}&page[size]=1000`
        )
      ),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['ui-translations'] })

  const createMutation = useMutation({
    mutationFn: (attrs: { locale: string; key: string; value: string }) =>
      apiClient.postResource('/api/ui-translations', {
        data: { type: 'ui-translations', attributes: attrs },
      }),
    onSuccess: () => {
      setDraft({ key: '', value: '' })
      invalidate()
    },
    onError: (e: Error) =>
      toast.error(
        e.message || t('translations.duplicate', 'That key already has a value for this locale')
      ),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, value }: { id: string; value: string }) =>
      apiClient.patch(`/api/ui-translations/${id}`, {
        data: { type: 'ui-translations', id, attributes: { value } },
      }),
    onSuccess: () => {
      setEditingId(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteResource(`/api/ui-translations/${id}`),
    onSuccess: invalidate,
    onError: (e: Error) => toast.error(e.message),
  })

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => r.key.toLowerCase().includes(q) || r.value.toLowerCase().includes(q))
  }, [rows, search])

  return (
    <div className="space-y-4 p-6" data-testid="translations-page">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="flex items-center gap-2 text-2xl font-semibold text-foreground">
          <Languages className="h-5 w-5" aria-hidden />
          {t('translations.title', 'Translations')}
        </h1>
        <select
          className="ml-auto rounded-md border border-border bg-background px-3 py-1.5 text-sm"
          value={localeFilter}
          onChange={(e) => setLocaleFilter(e.target.value)}
          aria-label={t('translations.locale', 'Locale')}
          data-testid="translations-locale-select"
        >
          {supportedLocales.map((loc) => (
            <option key={loc} value={loc}>
              {loc}
            </option>
          ))}
        </select>
        <Input
          className="w-64"
          placeholder={t('translations.search', 'Search keys or values…')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          data-testid="translations-search"
        />
      </header>

      <p className="text-sm text-muted-foreground">
        {t(
          'translations.hint',
          'Overrides replace the built-in text for this tenant. Keys are dotted paths (e.g. listPower.groupBy); use {{param}} placeholders exactly as the original string does.'
        )}
      </p>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow className="kelta-table-header hover:bg-transparent">
              <TableHead className="w-[40%]">{t('translations.key', 'Key')}</TableHead>
              <TableHead>{t('translations.value', 'Value')}</TableHead>
              <TableHead className="w-[90px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* Add row */}
            <TableRow className="bg-muted/30 hover:bg-muted/30">
              <TableCell>
                <Input
                  className={INPUT_CLASS}
                  placeholder="listPower.groupBy"
                  value={draft.key}
                  onChange={(e) => setDraft((d) => ({ ...d, key: e.target.value }))}
                  data-testid="translation-new-key"
                />
              </TableCell>
              <TableCell>
                <Input
                  className={INPUT_CLASS}
                  placeholder={t('translations.valuePlaceholder', 'Override text')}
                  value={draft.value}
                  onChange={(e) => setDraft((d) => ({ ...d, value: e.target.value }))}
                  data-testid="translation-new-value"
                />
              </TableCell>
              <TableCell>
                <Button
                  size="sm"
                  className="h-8"
                  disabled={!draft.key.trim() || !draft.value.trim() || createMutation.isPending}
                  onClick={() =>
                    createMutation.mutate({
                      locale: localeFilter,
                      key: draft.key.trim(),
                      value: draft.value,
                    })
                  }
                  data-testid="translation-add"
                >
                  <Plus className="mr-1 h-3.5 w-3.5" />
                  {t('translations.add', 'Add')}
                </Button>
              </TableCell>
            </TableRow>

            {isLoading ? (
              <TableRow>
                <TableCell colSpan={3} className="h-16 text-center text-muted-foreground">
                  {t('common.loading', 'Loading…')}
                </TableCell>
              </TableRow>
            ) : visible.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={3}
                  className="h-16 text-center text-muted-foreground"
                  data-testid="translations-empty"
                >
                  {t('translations.empty', 'No overrides for this locale yet.')}
                </TableCell>
              </TableRow>
            ) : (
              visible.map((row) => (
                <TableRow key={row.id} data-testid={`translation-row-${row.id}`}>
                  <TableCell className="font-mono text-xs">{row.key}</TableCell>
                  <TableCell>
                    {editingId === row.id ? (
                      <Input
                        className={INPUT_CLASS}
                        value={editingValue}
                        onChange={(e) => setEditingValue(e.target.value)}
                        data-testid={`translation-edit-value-${row.id}`}
                      />
                    ) : (
                      <span className="text-sm">{row.value}</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      {editingId === row.id ? (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={() =>
                              updateMutation.mutate({ id: row.id, value: editingValue })
                            }
                            aria-label={t('common.save', 'Save')}
                            data-testid={`translation-save-${row.id}`}
                          >
                            <Check className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={() => setEditingId(null)}
                            aria-label={t('common.cancel', 'Cancel')}
                          >
                            <X className="h-3.5 w-3.5" />
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={() => {
                              setEditingId(row.id)
                              setEditingValue(row.value)
                            }}
                            aria-label={t('common.edit', 'Edit')}
                            data-testid={`translation-edit-${row.id}`}
                          >
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-destructive"
                            onClick={() => deleteMutation.mutate(row.id)}
                            aria-label={t('common.delete', 'Delete')}
                            data-testid={`translation-delete-${row.id}`}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
