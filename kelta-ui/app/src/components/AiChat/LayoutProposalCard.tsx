import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { LayoutTemplate, X } from 'lucide-react'
import type { AiProposal, ProposedSection } from './types'

interface LayoutProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

export function LayoutProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: LayoutProposalCardProps) {
  const data = proposal.data
  const sections = (data.sections as ProposedSection[]) || []
  const isApplied = proposal.status === 'applied'
  const isDismissed = proposal.status === 'dismissed'

  return (
    <Card className="mx-4 my-2 border-primary/20">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <LayoutTemplate className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">{data.name as string}</CardTitle>
          <Badge variant="outline" className="text-xs">
            {data.layoutType as string}
          </Badge>
          <Badge variant="secondary" className="text-xs">
            {data.collectionName as string}
          </Badge>
          {isApplied && (
            <Badge className="bg-green-100 text-green-800 text-xs">Applied</Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className="pb-3 space-y-2">
        {sections.map((section, idx) => (
          <div key={idx} className="rounded border p-2">
            <p className="text-xs font-medium text-muted-foreground mb-1">
              {section.heading || `Section ${idx + 1}`}
              <span className="ml-2 text-xs opacity-60">
                ({section.columns} col{section.columns > 1 ? 's' : ''})
              </span>
            </p>
            <div
              className="grid gap-1"
              style={{ gridTemplateColumns: `repeat(${section.columns}, 1fr)` }}
            >
              {section.fieldPlacements?.map((fp, fpIdx) => (
                <div
                  key={fpIdx}
                  className="rounded bg-muted px-2 py-1 text-xs font-mono"
                  style={{ gridColumn: fp.columnNumber }}
                >
                  {fp.fieldName}
                </div>
              ))}
            </div>
          </div>
        ))}
      </CardContent>

      {!isApplied && !isDismissed && (
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
      )}
    </Card>
  )
}
