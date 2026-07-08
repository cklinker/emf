import { useState } from 'react'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../ui/alert-dialog'
import { Button } from '../ui/button'
import { Textarea } from '../ui/textarea'
import { useI18n } from '../../context/I18nContext'

export interface ApprovalActionDialogProps {
  open: boolean
  mode: 'approve' | 'reject'
  isPending?: boolean
  onConfirm: (comment: string | undefined) => void
  onCancel: () => void
}

/**
 * Confirm-with-optional-comment dialog for approval actions. The comment is passed
 * verbatim to POST /api/approvals/{instanceId}/approve|reject as `comments`.
 */
export function ApprovalActionDialog({
  open,
  mode,
  isPending = false,
  onConfirm,
  onCancel,
}: ApprovalActionDialogProps) {
  const { t } = useI18n()
  const [comment, setComment] = useState('')

  const title =
    mode === 'approve'
      ? t('approvalsDialog.approveTitle', 'Approve record')
      : t('approvalsDialog.rejectTitle', 'Reject record')

  const handleConfirm = () => {
    const trimmed = comment.trim()
    onConfirm(trimmed.length > 0 ? trimmed : undefined)
  }

  const handleCancel = () => {
    setComment('')
    onCancel()
  }

  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('approvalsDialog.commentLabel', 'Comment (optional)')}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <Textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={3}
          data-testid="approval-comment"
          aria-label={t('approvalsDialog.commentLabel', 'Comment (optional)')}
        />
        <AlertDialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('common.cancel', 'Cancel')}
          </Button>
          <Button
            variant={mode === 'reject' ? 'destructive' : 'default'}
            onClick={handleConfirm}
            disabled={isPending}
            data-testid="approval-confirm"
          >
            {mode === 'approve'
              ? t('approvals.approve', 'Approve')
              : t('approvals.reject', 'Reject')}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
