import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Check, X, Database } from 'lucide-react'
import type { AiProposal, ProposedField } from './types'

interface CollectionProposalCardProps {
  proposal: AiProposal
  onApply: (proposalId: string) => void
  onDismiss: (proposalId: string) => void
  isApplying?: boolean
}

const fieldTypeBadgeColors: Record<string, string> = {
  STRING: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
  INTEGER: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  LONG: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  DOUBLE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  BOOLEAN: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300',
  DATE: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300',
  DATETIME: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300',
  EMAIL: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-300',
  PHONE: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-300',
  URL: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-300',
  PICKLIST: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-300',
  MULTI_PICKLIST: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-300',
  REFERENCE: 'bg-pink-100 text-pink-800 dark:bg-pink-900 dark:text-pink-300',
  MASTER_DETAIL: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300',
  CURRENCY: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-300',
}

export function CollectionProposalCard({
  proposal,
  onApply,
  onDismiss,
  isApplying,
}: CollectionProposalCardProps) {
  const data = proposal.data
  const fields = (data.fields as ProposedField[]) || []
  const isApplied = proposal.status === 'applied'
  const isDismissed = proposal.status === 'dismissed'

  return (
    <Card className="mx-4 my-2 border-primary/20">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Database className="h-4 w-4 text-primary" />
          <CardTitle className="text-sm font-semibold">
            {data.displayName as string}
          </CardTitle>
          <Badge variant="outline" className="text-xs">
            {data.name as string}
          </Badge>
          {isApplied && (
            <Badge className="bg-green-100 text-green-800 text-xs">Applied</Badge>
          )}
        </div>
        {data.description && (
          <p className="text-xs text-muted-foreground">{data.description as string}</p>
        )}
      </CardHeader>

      <CardContent className="pb-3">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b">
                <th className="pb-1 text-left font-medium">Field</th>
                <th className="pb-1 text-left font-medium">Type</th>
                <th className="pb-1 text-center font-medium">Req</th>
                <th className="pb-1 text-center font-medium">Unique</th>
              </tr>
            </thead>
            <tbody>
              {fields.map((field, idx) => (
                <tr key={idx} className="border-b border-dashed last:border-0">
                  <td className="py-1 font-mono text-xs">{field.name}</td>
                  <td className="py-1">
                    <span
                      className={`inline-block rounded px-1.5 py-0.5 text-xs font-medium ${
                        fieldTypeBadgeColors[field.type] || 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {field.type}
                    </span>
                  </td>
                  <td className="py-1 text-center">
                    {field.nullable === false ? (
                      <Check className="mx-auto h-3 w-3 text-green-600" />
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="py-1 text-center">
                    {field.unique ? (
                      <Check className="mx-auto h-3 w-3 text-green-600" />
                    ) : (
                      ''
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
