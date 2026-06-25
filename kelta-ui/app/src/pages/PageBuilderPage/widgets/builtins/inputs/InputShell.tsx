/**
 * Shared chrome for the standalone typed inputs (slice 2f): the field label (with an advisory required
 * marker), and the dashed "no field configured" empty box. All user-facing strings go through
 * `useI18n` (`builder.input.*`) — no hardcoded literals (§3.6).
 */
import React from 'react'
import { useI18n } from '@/context/I18nContext'

/** The dashed empty box shown when no collection/field is configured (mirrors the table/form idiom). */
export function InputEmpty({ testid }: { testid: string }): React.ReactElement {
  const { t } = useI18n()
  return (
    <div
      className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground"
      data-testid={testid}
    >
      {t('builder.input.noFieldConfigured')}
    </div>
  )
}

/** Field label with optional advisory required marker. */
export function InputLabel({
  htmlFor,
  label,
  required,
}: {
  htmlFor?: string
  label: string
  required?: boolean
}): React.ReactElement {
  const { t } = useI18n()
  return (
    <label className="text-xs font-medium text-muted-foreground" htmlFor={htmlFor}>
      {label}
      {required && (
        <span className="ml-0.5 text-destructive" aria-label={t('builder.input.required')}>
          *
        </span>
      )}
    </label>
  )
}

/** Vertical wrapper: label above control. */
export function InputField({
  testid,
  htmlFor,
  label,
  required,
  children,
}: {
  testid: string
  htmlFor?: string
  label: string
  required?: boolean
  children: React.ReactNode
}): React.ReactElement {
  return (
    <div className="flex flex-col gap-1" data-testid={testid}>
      <InputLabel htmlFor={htmlFor} label={label} required={required} />
      {children}
    </div>
  )
}
