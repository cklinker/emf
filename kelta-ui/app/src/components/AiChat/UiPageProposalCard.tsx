/**
 * UiPageProposalCard (app-intelligence slice 2) — renders a `propose_ui_page`
 * proposal: page name/title, widget-tree outline (capped), variable/data-source
 * badges, Apply/Dismiss. Applying creates an UNPUBLISHED draft; the applied state
 * links to the Page Builder for review + publish.
 */
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { FileText, X, CheckCircle, XCircle, ExternalLink } from 'lucide-react'
import type { AiProposal } from './types'

interface UiPageProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

interface ProposedNode {
  type?: string
  children?: ProposedNode[]
}

/** Flatten the tree into indented outline rows, capped so huge pages stay scannable. */
function outline(
  nodes: ProposedNode[],
  depth = 0,
  acc: Array<{ type: string; depth: number }> = []
) {
  for (const node of nodes) {
    if (acc.length >= 20) return acc
    acc.push({ type: node.type ?? 'unknown', depth })
    if (node.children?.length) outline(node.children, depth + 1, acc)
  }
  return acc
}

function countNodes(nodes: ProposedNode[]): number {
  let n = 0
  for (const node of nodes) {
    n += 1 + (node.children?.length ? countNodes(node.children) : 0)
  }
  return n
}

export function UiPageProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: UiPageProposalCardProps) {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const data = proposal.data
  const components = (data.components as ProposedNode[]) || []
  const variables = (data.variables as unknown[]) || []
  const dataSources = (data.dataSources as Array<{ name?: string }>) || []
  const total = countNodes(components)
  const rows = outline(components)
  const isApplied = proposal.status === 'applied'
  const isDismissed = proposal.status === 'dismissed'

  if (isApplied || isDismissed) {
    return (
      <div className="mx-4 my-2 flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-xs text-muted-foreground">
        {isApplied ? (
          <CheckCircle className="h-3.5 w-3.5 shrink-0 text-green-600" />
        ) : (
          <XCircle className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        )}
        <FileText className="h-3.5 w-3.5 shrink-0" />
        <span className="font-medium text-foreground">{(data.title || data.name) as string}</span>
        {isApplied ? (
          <>
            <Badge className="bg-green-100 text-[10px] text-green-800 dark:bg-green-900 dark:text-green-300">
              Draft created
            </Badge>
            <Link
              to={`/${tenantSlug}/pages`}
              className="ml-auto inline-flex items-center gap-1 text-primary underline underline-offset-2"
              data-testid="ui-page-open-builder"
            >
              <ExternalLink className="h-3 w-3" aria-hidden />
              Open Page Builder
            </Link>
          </>
        ) : (
          <Badge variant="secondary" className="ml-auto text-[10px]">
            Dismissed
          </Badge>
        )}
      </div>
    )
  }

  return (
    <Card className="mx-4 my-2 border-primary/20" data-testid="ui-page-proposal-card">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <FileText className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">
            {(data.title || data.name) as string}
          </CardTitle>
          <Badge variant="outline" className="text-xs">
            {total} widget{total === 1 ? '' : 's'}
          </Badge>
          {variables.length > 0 && (
            <Badge variant="secondary" className="text-xs">
              {variables.length} var{variables.length === 1 ? '' : 's'}
            </Badge>
          )}
          {dataSources.length > 0 && (
            <Badge variant="secondary" className="text-xs">
              {dataSources.length} source{dataSources.length === 1 ? '' : 's'}
            </Badge>
          )}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Created as an unpublished draft — review and publish in the Page Builder.
        </p>
      </CardHeader>

      <CardContent className="space-y-0.5 pb-3">
        {rows.map((row, idx) => (
          <div
            key={idx}
            className="rounded bg-muted px-2 py-0.5 font-mono text-xs"
            style={{ marginLeft: row.depth * 12 }}
          >
            {row.type}
          </div>
        ))}
        {total > rows.length && (
          <p className="px-2 text-xs text-muted-foreground">+{total - rows.length} more…</p>
        )}
        {dataSources.length > 0 && (
          <p className="pt-1 text-xs text-muted-foreground">
            Data: {dataSources.map((s) => s.name).join(', ')}
          </p>
        )}
      </CardContent>

      <CardFooter className="gap-2 pt-0">
        <Button size="sm" onClick={() => onApply(proposal.id)} disabled={isApplying}>
          {isApplying ? 'Applying...' : 'Apply'}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => onDismiss(proposal.id)}
          disabled={isApplying}
        >
          <X className="mr-1 h-3 w-3" />
          Dismiss
        </Button>
      </CardFooter>
    </Card>
  )
}
