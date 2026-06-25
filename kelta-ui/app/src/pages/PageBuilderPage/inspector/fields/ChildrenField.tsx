/**
 * `kind:'children'` → read-only summary of `node.children`. Children are edited on the canvas (2c); this
 * just shows acceptsChildren widgets (card/container) something in the inspector. No write contract.
 */
import React from 'react'
import { useI18n } from '../../../../context/I18nContext'
import type { FieldEditorProps } from './types'
import type { PageComponent } from '../../model/pageModel'

export function ChildrenField({ node, fieldId }: FieldEditorProps): React.ReactElement {
  const { t } = useI18n()
  const count = Array.isArray((node as PageComponent).children)
    ? (node as PageComponent).children!.length
    : 0
  return (
    <p className="text-xs text-muted-foreground" data-testid={fieldId}>
      {t('builder.inspector.children.summary', { count })}
    </p>
  )
}
