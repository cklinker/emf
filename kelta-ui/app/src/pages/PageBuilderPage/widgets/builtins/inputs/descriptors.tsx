/**
 * Descriptors for the eight standalone typed-input widgets (slice 2f). Each is a `category:'input'`
 * `WidgetDescriptor` whose `Render` maps the bound field's `FieldType` to the matching control. They
 * share a common props shape ({@link InputWidgetProps}); a descriptor only differs in `type`/`label`/
 * `icon`, the `field-picker`'s `fieldTypeFilter`, and the inner control. The descriptor's `Render`
 * receives ALREADY-resolved `node.props` (resolved-node invariant) — it never re-resolves a binding.
 */
import React from 'react'
import {
  TextCursorInput,
  Hash,
  CheckSquare,
  ChevronDownSquare,
  Calendar,
  Link2,
  ListChecks,
  FileText,
} from 'lucide-react'
import type { WidgetDescriptor, WidgetRenderProps, PropFieldSchema } from '../../types'
import type { FieldType } from '@/hooks/useCollectionSchema'
import { TextInput } from './TextInput'
import { NumberInput } from './NumberInput'
import { CheckboxInput } from './CheckboxInput'
import { DropdownInput } from './DropdownInput'
import { DatePickerInput } from './DatePickerInput'
import { LookupInput } from './LookupInput'
import { MultiPicklistInput } from './MultiPicklistInput'
import { RichTextInput } from './RichTextInput'
import type { InputControlProps, InputWidgetProps } from './types'

/** Shared `propSchema` for an input, parameterized by the field-picker's `fieldTypeFilter`. */
function inputPropSchema(fieldTypeFilter: FieldType[]): PropFieldSchema[] {
  return [
    { key: 'collection', label: 'Collection', kind: 'collection-picker', group: 'Data' },
    {
      key: 'field',
      label: 'Field',
      kind: 'field-picker',
      dependsOnCollection: true,
      fieldTypeFilter,
      group: 'Data',
    },
    {
      key: 'defaultValue',
      label: 'Default value',
      kind: 'expression',
      bindable: true,
      group: 'Data',
    },
    { key: 'required', label: 'Required', kind: 'boolean', group: 'Data' },
  ]
}

/** Build an input descriptor that renders `Control` with the resolved props + the render `mode`. */
function inputDescriptor(
  type: string,
  label: string,
  icon: WidgetDescriptor['icon'],
  fieldTypeFilter: FieldType[],
  Control: React.ComponentType<InputControlProps>
): WidgetDescriptor {
  function Render({ node, mode }: WidgetRenderProps): React.ReactElement {
    const props = (node.props ?? {}) as InputWidgetProps
    return <Control {...props} mode={mode} />
  }
  return {
    type,
    label,
    icon,
    category: 'input',
    acceptsChildren: false,
    defaultProps: { collection: '', field: '', required: false },
    propSchema: inputPropSchema(fieldTypeFilter),
    Render,
  }
}

export const inputDescriptors: WidgetDescriptor[] = [
  inputDescriptor(
    'text-input',
    'Text input',
    TextCursorInput,
    ['string', 'phone', 'email', 'url', 'external_id', 'auto_number'],
    TextInput
  ),
  inputDescriptor(
    'number-input',
    'Number input',
    Hash,
    ['number', 'currency', 'percent'],
    NumberInput
  ),
  inputDescriptor('checkbox', 'Checkbox', CheckSquare, ['boolean'], CheckboxInput),
  inputDescriptor('dropdown', 'Dropdown', ChevronDownSquare, ['picklist'], DropdownInput),
  inputDescriptor('datepicker', 'Date picker', Calendar, ['date', 'datetime'], DatePickerInput),
  inputDescriptor('lookup', 'Lookup', Link2, ['master_detail', 'lookup', 'reference'], LookupInput),
  inputDescriptor(
    'multi-picklist',
    'Multi-picklist',
    ListChecks,
    ['multi_picklist'],
    MultiPicklistInput
  ),
  inputDescriptor('rich-text', 'Rich text', FileText, ['rich_text'], RichTextInput),
]
