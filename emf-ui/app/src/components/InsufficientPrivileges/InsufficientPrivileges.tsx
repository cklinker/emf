/**
 * InsufficientPrivileges Component
 *
 * Displayed when a user navigates to a page they don't have permission
 * to access. Shows a clear message with a "Go Back" action.
 */

import React from 'react'
import { useNavigate } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

interface InsufficientPrivilegesProps {
  /** What action was denied (e.g., "view", "create", "edit", "delete") */
  action?: string
  /** What resource was being accessed (e.g., "Accounts", "this record") */
  resource?: string
  /** Custom message to display */
  message?: string
  /** Override the back navigation path */
  backPath?: string
}

export function InsufficientPrivileges({
  action = 'access',
  resource = 'this resource',
  message,
  backPath,
}: InsufficientPrivilegesProps): React.ReactElement {
  const navigate = useNavigate()

  const displayMessage =
    message ||
    `You don't have permission to ${action} ${resource}. Contact your administrator if you need access.`

  const handleGoBack = () => {
    if (backPath) {
      navigate(backPath)
    } else {
      navigate(-1)
    }
  }

  return (
    <div className="flex items-center justify-center p-12">
      <Card className="max-w-md">
        <CardContent className="pt-6">
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
              <ShieldAlert className="h-6 w-6 text-destructive" />
            </div>
            <div className="space-y-2">
              <h2 className="text-lg font-semibold text-foreground">Insufficient Privileges</h2>
              <p className="text-sm text-muted-foreground">{displayMessage}</p>
            </div>
            <Button variant="outline" onClick={handleGoBack}>
              Go Back
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
