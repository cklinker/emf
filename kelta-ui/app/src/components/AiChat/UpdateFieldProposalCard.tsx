import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Pencil, X, CheckCircle, XCircle } from 'lucide-react'
import type { AiProposal, ProposedFieldChange } from './types'

interface UpdateFieldProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

export function UpdateFieldProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: UpdateFieldProposalCardProps) {
  const data = proposal.data
  const collectionName = data.collectionName as string
  const fieldName = data.fieldName as string
  const changes = (data.changes as ProposedFieldChange) || {}
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
        <Pencil className="h-3.5 w-3.5 shrink-0" />
        <span className="font-medium text-foreground">
          {collectionName}.{fieldName}
        </span>
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

  const changeEntries = Object.entries(changes).filter(([, v]) => v !== undefined)

  return (
    <Card className="mx-4 my-2 border-primary/20">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Pencil className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">Update field</CardTitle>
          <Badge variant="outline" className="text-xs">
            {collectionName}.{fieldName}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="pb-3">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="pb-1 text-left font-medium">Property</th>
              <th className="pb-1 text-left font-medium">New value</th>
            </tr>
          </thead>
          <tbody>
            {changeEntries.map(([key, value]) => (
              <tr key={key} className="border-b border-dashed last:border-0">
                <td className="py-1 font-mono">{key}</td>
                <td className="py-1">{Array.isArray(value) ? value.join(', ') : String(value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
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
