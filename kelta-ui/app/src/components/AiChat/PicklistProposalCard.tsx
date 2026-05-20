import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { List, X, CheckCircle, XCircle } from 'lucide-react'
import type { AiProposal, ProposedPicklistValue } from './types'

interface PicklistProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

export function PicklistProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: PicklistProposalCardProps) {
  const data = proposal.data
  const name = data.name as string
  const values = (data.values as ProposedPicklistValue[]) || []
  const restricted = data.restricted === true
  const isApplied = proposal.status === 'applied'
  const isDismissed = proposal.status === 'dismissed'
  const isResolved = isApplied || isDismissed

  if (isResolved) {
    return (
      <div className="mx-4 my-2 flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-xs text-muted-foreground">
        {isApplied ? (
          <CheckCircle className="h-3.5 w-3.5 text-green-600 shrink-0" />
        ) : (
          <XCircle className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        )}
        <List className="h-3.5 w-3.5 shrink-0" />
        <span className="font-medium text-foreground">{name}</span>
        <Badge variant="outline" className="text-[10px] px-1 py-0">
          {values.length} values
        </Badge>
        <span className="ml-auto">
          {isApplied ? (
            <Badge className="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300 text-[10px]">
              Applied
            </Badge>
          ) : (
            <Badge variant="secondary" className="text-[10px]">
              Dismissed
            </Badge>
          )}
        </span>
      </div>
    )
  }

  return (
    <Card className="mx-4 my-2 border-primary/20">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <List className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">{name}</CardTitle>
          {restricted && (
            <Badge variant="outline" className="text-xs">
              Restricted
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className="pb-3">
        <div className="flex flex-wrap gap-1">
          {values.map((v, idx) => (
            <Badge key={idx} variant="secondary" className="text-xs">
              {v.label ?? v.value}
            </Badge>
          ))}
        </div>
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
