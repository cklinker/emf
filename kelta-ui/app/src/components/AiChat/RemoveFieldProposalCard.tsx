import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Trash2, X, AlertTriangle, CheckCircle, XCircle } from 'lucide-react'
import type { AiProposal } from './types'

interface RemoveFieldProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

export function RemoveFieldProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: RemoveFieldProposalCardProps) {
  const data = proposal.data
  const collectionName = data.collectionName as string
  const fieldName = data.fieldName as string
  const reason = data.reason as string | undefined
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
        <Trash2 className="h-3.5 w-3.5 shrink-0" />
        <span className="font-medium text-foreground">
          {collectionName}.{fieldName}
        </span>
        <span className="ml-auto">
          {isApplied ? (
            <Badge className="bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300 text-[10px]">
              Removed
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
    <Card className="mx-4 my-2 border-destructive/40 bg-destructive/5">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Trash2 className="h-4 w-4 text-destructive" />
          <CardTitle className="text-sm font-semibold">Remove field</CardTitle>
          <Badge variant="outline" className="text-xs">
            {collectionName}.{fieldName}
          </Badge>
        </div>
        <div className="flex items-start gap-2 rounded border border-destructive/30 bg-background p-2 mt-2">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 text-destructive shrink-0" />
          <p className="text-xs">Destructive — the column and all its data will be deleted.</p>
        </div>
      </CardHeader>

      {reason && (
        <CardContent className="pb-3 text-xs text-muted-foreground">
          <span className="font-medium text-foreground">Reason:</span> {reason}
        </CardContent>
      )}

      <CardFooter className="gap-2 pt-0">
        <Button
          size="sm"
          variant="destructive"
          onClick={() => onApply(proposal.id)}
          disabled={isApplying}
        >
          {isApplying ? 'Removing...' : 'Remove field'}
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
