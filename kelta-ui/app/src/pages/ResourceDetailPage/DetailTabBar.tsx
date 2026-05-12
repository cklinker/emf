/**
 * DetailTabBar
 *
 * Bottom-of-page tab bar for record detail pages. Renders one tab per
 * configured related list (in layout sortOrder) followed by Notes,
 * Attachments, and System Information. Counts in parentheses display
 * record totals for related-list, Notes, and Attachments tabs.
 */

import React, { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Copy } from 'lucide-react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { RelatedList } from '@/components/RelatedList'
import { NotesSection } from '@/components/NotesSection/NotesSection'
import { AttachmentsSection } from '@/components/AttachmentsSection/AttachmentsSection'
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useCollectionStore } from '@/context/CollectionStoreContext'
import { parseDisplayColumns } from '@/components/LayoutRelatedLists/parseDisplayColumns'
import { useI18n } from '@/context/I18nContext'
import { useToast } from '@/components'
import type { LayoutRelatedListDto } from '@/hooks/usePageLayout'
import type { Note, Attachment } from '@/hooks/useRecordContext'
import type { ApiClient } from '@/services/apiClient'

const NOTES_TAB = '__notes__'
const ATTACHMENTS_TAB = '__attachments__'
const SYSTEM_TAB = '__system__'

interface UserDisplay {
  name: string
  linkTo: string
}

export interface DetailTabBarProps {
  /** Related list configurations from the resolved page layout (may be empty) */
  relatedLists: LayoutRelatedListDto[]
  /** Parent record id (UUID) */
  recordId: string
  /** Collection ID (UUID) */
  collectionId: string
  /** Tenant slug for building URLs */
  tenantSlug: string
  /** Raw JSON:API response from the parent record request (for include resolution) */
  includedData?: unknown
  /** Parent record object — provides id, created_at, updated_at, created_by, updated_by */
  resource: Record<string, unknown> & { id: string }
  /** Notes pre-fetched via useRecordContext */
  notes: Note[]
  /** Attachments pre-fetched via useRecordContext */
  attachments: Attachment[]
  /** API client */
  apiClient: ApiClient
  /** Invalidate notes/attachments cache after mutation */
  invalidateRecordContext: () => void
  /** Resolve a userId to a display name + link, or null when not yet resolved */
  getUserDisplay: (userId: string) => UserDisplay | null
}

function normalizeSortDirection(raw: string | null | undefined): 'asc' | 'desc' {
  return raw && raw.trim().toLowerCase() === 'desc' ? 'desc' : 'asc'
}

function useResolvedRelatedCollection(rl: LayoutRelatedListDto): {
  collectionName: string
  relationshipFieldName: string
} {
  const collectionStore = useCollectionStore()
  const collection =
    !rl.relatedCollectionName && rl.relatedCollectionId
      ? collectionStore.getCollectionById(rl.relatedCollectionId)
      : undefined
  const collectionName = rl.relatedCollectionName || collection?.name || ''
  let relationshipFieldName = rl.relationshipFieldName
  if (!relationshipFieldName && rl.relationshipFieldId) {
    relationshipFieldName = collectionStore.getFieldById(rl.relationshipFieldId)?.name || ''
  }
  return { collectionName, relationshipFieldName }
}

interface RelatedListTabTriggerProps {
  rl: LayoutRelatedListDto
  count: number | undefined
}

function RelatedListTabTrigger({ rl, count }: RelatedListTabTriggerProps): React.ReactElement {
  const { collectionName } = useResolvedRelatedCollection(rl)
  const { schema } = useCollectionSchema(collectionName || undefined)
  const fallback = collectionName
    ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1)
    : 'Related'
  const label = schema?.displayName ?? fallback
  return (
    <TabsTrigger value={rl.id} data-testid={`detail-tab-${collectionName || 'related'}`}>
      {label}
      {typeof count === 'number' ? ` (${count})` : ''}
    </TabsTrigger>
  )
}

interface RelatedListTabContentProps {
  rl: LayoutRelatedListDto
  parentRecordId: string
  tenantSlug: string
  includedData?: unknown
  onTotalChange: (n: number) => void
}

function RelatedListTabContent({
  rl,
  parentRecordId,
  tenantSlug,
  includedData,
  onTotalChange,
}: RelatedListTabContentProps): React.ReactElement | null {
  const { collectionName, relationshipFieldName } = useResolvedRelatedCollection(rl)
  if (!collectionName || !relationshipFieldName) return null
  return (
    <RelatedList
      collectionName={collectionName}
      foreignKeyField={relationshipFieldName}
      parentRecordId={parentRecordId}
      tenantSlug={tenantSlug}
      limit={rl.rowLimit}
      displayColumns={parseDisplayColumns(rl.displayColumns)}
      sortField={rl.sortField ?? undefined}
      sortDirection={normalizeSortDirection(rl.sortDirection)}
      includedData={includedData}
      onTotalChange={onTotalChange}
    />
  )
}

export function DetailTabBar({
  relatedLists,
  recordId,
  collectionId,
  tenantSlug,
  includedData,
  resource,
  notes,
  attachments,
  apiClient,
  invalidateRecordContext,
  getUserDisplay,
}: DetailTabBarProps): React.ReactElement {
  const { t, formatDate } = useI18n()
  const { showToast } = useToast()

  const sortedLists = useMemo(
    () => [...relatedLists].sort((a, b) => a.sortOrder - b.sortOrder),
    [relatedLists]
  )

  const [counts, setCounts] = useState<Record<string, number>>({})
  const setCount = useCallback((id: string, n: number) => {
    setCounts((prev) => (prev[id] === n ? prev : { ...prev, [id]: n }))
  }, [])

  const defaultTab = sortedLists[0]?.id ?? NOTES_TAB

  const copyId = useCallback(() => {
    void navigator.clipboard
      .writeText(resource.id)
      .then(() => showToast('Internal Id copied', 'success'))
      .catch(() => showToast('Copy failed', 'error'))
  }, [resource.id, showToast])

  const createdAt = (resource.created_at ?? resource.createdAt) as string | undefined
  const updatedAt = (resource.updated_at ?? resource.updatedAt) as string | undefined
  const createdBy = (resource.created_by ?? resource.createdBy) as string | number | undefined
  const updatedBy = (resource.updated_by ?? resource.updatedBy) as string | number | undefined

  return (
    <section
      className="rounded-md border border-border bg-card p-6 max-md:p-4"
      aria-labelledby="detail-tabs-heading"
      data-testid="detail-tab-bar"
    >
      <h2 id="detail-tabs-heading" className="sr-only">
        Record details
      </h2>
      <Tabs defaultValue={defaultTab} className="w-full">
        <div className="-mx-1 overflow-x-auto">
          <TabsList variant="line" className="flex w-max">
            {sortedLists.map((rl) => (
              <RelatedListTabTrigger key={rl.id} rl={rl} count={counts[rl.id]} />
            ))}
            <TabsTrigger value={NOTES_TAB} data-testid="detail-tab-notes">
              Notes ({notes.length})
            </TabsTrigger>
            <TabsTrigger value={ATTACHMENTS_TAB} data-testid="detail-tab-attachments">
              Attachments ({attachments.length})
            </TabsTrigger>
            <TabsTrigger value={SYSTEM_TAB} data-testid="detail-tab-system">
              System Information
            </TabsTrigger>
          </TabsList>
        </div>

        {sortedLists.map((rl) => (
          <TabsContent forceMount key={rl.id} value={rl.id} className="mt-4">
            <RelatedListTabContent
              rl={rl}
              parentRecordId={recordId}
              tenantSlug={tenantSlug}
              includedData={includedData}
              onTotalChange={(n) => setCount(rl.id, n)}
            />
          </TabsContent>
        ))}

        <TabsContent forceMount value={NOTES_TAB} className="mt-4">
          <NotesSection
            collectionId={collectionId}
            recordId={recordId}
            apiClient={apiClient}
            notes={notes}
            onMutate={invalidateRecordContext}
          />
        </TabsContent>

        <TabsContent forceMount value={ATTACHMENTS_TAB} className="mt-4">
          <AttachmentsSection
            collectionId={collectionId}
            recordId={recordId}
            apiClient={apiClient}
            attachments={attachments}
            onMutate={invalidateRecordContext}
          />
        </TabsContent>

        <TabsContent forceMount value={SYSTEM_TAB} className="mt-4">
          <div
            className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4 max-md:grid-cols-1"
            data-testid="system-information-panel"
          >
            <div className="flex flex-col gap-1">
              <span className="text-sm font-medium text-muted-foreground">Internal Id</span>
              <span className="inline-flex items-center gap-2">
                <code
                  className="break-all rounded bg-muted px-2 py-1 font-mono text-xs text-foreground"
                  data-testid="system-internal-id"
                >
                  {resource.id}
                </code>
                <Button
                  variant="ghost"
                  size="xs"
                  onClick={copyId}
                  aria-label="Copy Internal Id"
                  data-testid="system-internal-id-copy"
                >
                  <Copy size={12} aria-hidden="true" />
                </Button>
              </span>
            </div>
            {createdAt && (
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium text-muted-foreground">
                  {t('collections.created')}
                </span>
                <span className="text-base text-foreground" data-testid="system-created-at">
                  {formatDate(new Date(createdAt), {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            )}
            {updatedAt && (
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium text-muted-foreground">
                  {t('collections.updated')}
                </span>
                <span className="text-base text-foreground" data-testid="system-updated-at">
                  {formatDate(new Date(updatedAt), {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            )}
            {createdBy != null && (
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium text-muted-foreground">Created by</span>
                <span className="text-base text-foreground" data-testid="system-created-by">
                  {(() => {
                    const display = getUserDisplay(String(createdBy))
                    return display ? (
                      <Link
                        to={display.linkTo}
                        className="text-primary no-underline hover:text-primary/80 hover:underline"
                      >
                        {display.name}
                      </Link>
                    ) : (
                      String(createdBy)
                    )
                  })()}
                </span>
              </div>
            )}
            {updatedBy != null && (
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium text-muted-foreground">Last modified by</span>
                <span className="text-base text-foreground" data-testid="system-updated-by">
                  {(() => {
                    const display = getUserDisplay(String(updatedBy))
                    return display ? (
                      <Link
                        to={display.linkTo}
                        className="text-primary no-underline hover:text-primary/80 hover:underline"
                      >
                        {display.name}
                      </Link>
                    ) : (
                      String(updatedBy)
                    )
                  })()}
                </span>
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>
    </section>
  )
}

export default DetailTabBar
