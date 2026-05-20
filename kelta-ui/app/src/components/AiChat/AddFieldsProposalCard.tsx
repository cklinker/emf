import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Check, X, PlusCircle, CheckCircle, XCircle } from 'lucide-react'
import type { AiProposal, ProposedField } from './types'

interface AddFieldsProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

export function AddFieldsProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: AddFieldsProposalCardProps) {
  const data = proposal.data
  const fields = (data.fields as ProposedField[]) || []
  const collectionName = data.collectionName as string
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
        <PlusCircle className="h-3.5 w-3.5 shrink-0" />
        <span className="font-medium text-foreground">{fields.length} field(s)</span>
        <span>to</span>
        <Badge variant="outline" className="text-[10px] px-1 py-0">
          {collectionName}
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
          <PlusCircle className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">Add fields</CardTitle>
          <Badge variant="outline" className="text-xs">
            → {collectionName}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="pb-3">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="pb-1 text-left font-medium">Field</th>
              <th className="pb-1 text-left font-medium">Type</th>
              <th className="pb-1 text-center font-medium">Req</th>
            </tr>
          </thead>
          <tbody>
            {fields.map((field, idx) => (
              <tr key={idx} className="border-b border-dashed last:border-0">
                <td className="py-1 font-mono">{field.name}</td>
                <td className="py-1">{field.type}</td>
                <td className="py-1 text-center">
                  {field.nullable === false ? (
                    <Check className="mx-auto h-3 w-3 text-green-600" />
                  ) : (
                    ''
                  )}
                </td>
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
