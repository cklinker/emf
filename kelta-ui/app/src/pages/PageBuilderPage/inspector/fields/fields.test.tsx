/**
 * Per-kind value-write contract tests for the inspector field editors. Each block mounts the field with
 * a `vi.fn()` onChange and asserts what it writes. Collection-dependent fields mount inside the shared
 * test wrapper (ApiProvider + QueryClient + MSW); the rest only need I18n.
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../../context/I18nContext'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
} from '../../../../test/testUtils'
import type { PageComponent, PropValue } from '../../model/pageModel'
import { TextField } from './TextField'
import { TextareaField } from './TextareaField'
import { NumberField } from './NumberField'
import { BooleanField } from './BooleanField'
import { SelectField } from './SelectField'
import { ColorField } from './ColorField'
import { CollectionPickerField } from './CollectionPickerField'
import { FieldPickerField } from './FieldPickerField'
import { ExpressionField } from './ExpressionField'
import { SpanField } from './SpanField'
import { ChildrenField } from './ChildrenField'
import type { FieldEditorProps } from './types'
import type { PropFieldSchema } from '../../widgets/types'

const i18nWrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

/**
 * Mounts a field editor controlled by local state (mimicking how Inspector feeds value back). Returns
 * the spy `onChange` so tests can assert the FINAL committed value after multi-keystroke input.
 */
function Controlled<V>({
  Editor,
  initial,
  spy,
  node: theNode,
  schema: theSchema,
  fieldId,
}: {
  Editor: React.ComponentType<FieldEditorProps<V>>
  initial: V | undefined
  spy: (v: V) => void
  node: PageComponent
  schema: PropFieldSchema
  fieldId: string
}): React.ReactElement {
  const [val, setVal] = React.useState<V | undefined>(initial)
  return (
    <Editor
      schema={theSchema}
      value={val}
      onChange={(v) => {
        setVal(v)
        spy(v)
      }}
      node={theNode}
      fieldId={fieldId}
    />
  )
}

const node = (over: Partial<PageComponent> = {}): PageComponent => ({
  id: 'c1',
  type: 'heading',
  props: {},
  ...over,
})

const schema = (over: Partial<PropFieldSchema> = {}): PropFieldSchema => ({
  key: 'text',
  label: 'Text',
  kind: 'text',
  ...over,
})

describe('inspector field editors', () => {
  describe('TextField (text)', () => {
    it('writes the typed string', async () => {
      const onChange = vi.fn()
      render(
        <TextField
          schema={schema()}
          value=""
          onChange={onChange}
          node={node()}
          fieldId="property-text"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.type(screen.getByTestId('property-text'), 'X')
      expect(onChange).toHaveBeenLastCalledWith('X')
    })
  })

  describe('TextareaField (textarea)', () => {
    it('writes the typed string', async () => {
      const onChange = vi.fn()
      render(
        <TextareaField
          schema={schema({ key: 'content', kind: 'textarea' })}
          value=""
          onChange={onChange}
          node={node({ type: 'text' })}
          fieldId="property-content"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.type(screen.getByTestId('property-content'), 'Y')
      expect(onChange).toHaveBeenLastCalledWith('Y')
    })
  })

  describe('NumberField (number)', () => {
    it('writes a number when typed', async () => {
      const onChange = vi.fn()
      render(
        <Controlled<number | undefined>
          Editor={NumberField}
          initial={undefined}
          spy={onChange}
          node={node()}
          schema={schema({ key: 'limit', kind: 'number' })}
          fieldId="property-limit"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.type(screen.getByTestId('property-limit'), '25')
      expect(onChange).toHaveBeenLastCalledWith(25)
    })

    it('writes undefined (NOT 0) when cleared', async () => {
      const onChange = vi.fn()
      render(
        <NumberField
          schema={schema({ key: 'limit', kind: 'number' })}
          value={25}
          onChange={onChange}
          node={node()}
          fieldId="property-limit"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.clear(screen.getByTestId('property-limit'))
      expect(onChange).toHaveBeenLastCalledWith(undefined)
    })
  })

  describe('BooleanField (boolean)', () => {
    it('writes true when checked and false when unchecked', async () => {
      const onChange = vi.fn()
      const { rerender } = render(
        <BooleanField
          schema={schema({ key: 'flag', kind: 'boolean' })}
          value={false}
          onChange={onChange}
          node={node()}
          fieldId="property-flag"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.click(screen.getByTestId('property-flag'))
      expect(onChange).toHaveBeenLastCalledWith(true)

      rerender(
        <BooleanField
          schema={schema({ key: 'flag', kind: 'boolean' })}
          value={true}
          onChange={onChange}
          node={node()}
          fieldId="property-flag"
        />
      )
      await userEvent.click(screen.getByTestId('property-flag'))
      expect(onChange).toHaveBeenLastCalledWith(false)
    })
  })

  describe('SelectField (select)', () => {
    it('writes the chosen option value', async () => {
      const onChange = vi.fn()
      render(
        <SelectField
          schema={schema({
            key: 'level',
            kind: 'select',
            options: [
              { label: 'H1', value: 'h1' },
              { label: 'H2', value: 'h2' },
            ],
          })}
          value="h1"
          onChange={onChange}
          node={node()}
          fieldId="property-level"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.selectOptions(screen.getByTestId('property-level'), 'h2')
      expect(onChange).toHaveBeenLastCalledWith('h2')
    })
  })

  describe('ColorField (color)', () => {
    it('writes the hex string from the text input', async () => {
      const onChange = vi.fn()
      render(
        <Controlled<PropValue>
          Editor={ColorField}
          initial=""
          spy={onChange}
          node={node()}
          schema={schema({ key: 'color', kind: 'color' })}
          fieldId="property-color"
        />,
        { wrapper: i18nWrapper }
      )
      await userEvent.type(screen.getByTestId('property-color-hex'), '#ff0000')
      expect(onChange).toHaveBeenLastCalledWith('#ff0000')
    })
  })

  describe('ExpressionField (expression)', () => {
    it('displays the bound token wrapped in {{…}} (the $bind value stays bare)', () => {
      const onChange = vi.fn()
      render(
        <ExpressionField
          schema={schema({ kind: 'expression', bindable: true })}
          value={{ $bind: 'record.name', mode: 'expr' }}
          onChange={onChange}
          node={node()}
          fieldId="property-text"
        />,
        { wrapper: i18nWrapper }
      )
      // {{…}} is display-only; the stored $bind is the bare token (asserted in BindableField.test).
      expect(screen.getByTestId('bindable-expr-property-text')).toHaveTextContent('{{record.name}}')
    })

    it('renders an em dash for an empty binding', () => {
      render(
        <ExpressionField
          schema={schema({ kind: 'expression', bindable: true })}
          value={{ $bind: '', mode: 'expr' }}
          onChange={vi.fn()}
          node={node()}
          fieldId="property-text"
        />,
        { wrapper: i18nWrapper }
      )
      expect(screen.getByTestId('bindable-expr-property-text')).toHaveTextContent('—')
    })
  })

  describe('SpanField (span)', () => {
    it('writes the ResponsiveSpan with base updated', async () => {
      const onChange = vi.fn()
      render(
        <Controlled<import('../../model/pageModel').ResponsiveSpan>
          Editor={SpanField}
          initial={{ base: 12 }}
          spy={onChange}
          node={node()}
          schema={schema({ key: 'span', kind: 'span' })}
          fieldId="property-span"
        />,
        { wrapper: i18nWrapper }
      )
      // Set the base span directly to 6 (a number input clamps multi-digit appends, so change in one shot).
      fireEvent.change(screen.getByTestId('property-span-base'), { target: { value: '6' } })
      expect(onChange).toHaveBeenLastCalledWith({ base: 6 })
    })
  })

  describe('ChildrenField (children)', () => {
    it('renders a read-only child-count summary and never calls onChange', () => {
      const onChange = vi.fn()
      render(
        <ChildrenField
          schema={schema({ key: 'children', kind: 'children' })}
          value={undefined}
          onChange={onChange}
          node={node({
            type: 'container',
            children: [{ id: 'x', type: 'text', props: {} }],
          })}
          fieldId="property-children"
        />,
        { wrapper: i18nWrapper }
      )
      expect(screen.getByTestId('property-children')).toHaveTextContent('1')
      expect(onChange).not.toHaveBeenCalled()
    })
  })

  describe('collection-dependent fields', () => {
    let cleanupAuthMocks: () => void
    beforeEach(() => {
      cleanupAuthMocks = setupAuthMocks()
      resetMockAxios()
    })
    afterEach(() => {
      cleanupAuthMocks()
      vi.clearAllMocks()
    })

    describe('CollectionPickerField (collection-picker / dataView editor)', () => {
      it('writes the dataView.collection name (parity with the legacy editor)', async () => {
        const onChange = vi.fn()
        render(
          <Controlled<PropValue>
            Editor={CollectionPickerField}
            initial={{}}
            spy={onChange}
            node={node({ type: 'table' })}
            schema={schema({ key: 'dataView', kind: 'collection-picker' })}
            fieldId="property-field-dataView"
          />,
          { wrapper: createTestWrapper() }
        )
        await userEvent.type(screen.getByTestId('property-collection'), 'orders')
        expect(onChange).toHaveBeenLastCalledWith({ collection: 'orders' })
      })
    })

    describe('FieldPickerField (field-picker, dependsOnCollection)', () => {
      it('renders the empty hint with no collection and does not crash', () => {
        const onChange = vi.fn()
        render(
          <FieldPickerField
            schema={schema({
              key: 'dataView.fields',
              kind: 'field-picker',
              dependsOnCollection: true,
            })}
            value={[]}
            onChange={onChange}
            node={node({ type: 'table', props: { dataView: {} } })}
            fieldId="property-field-dataView.fields"
          />,
          { wrapper: createTestWrapper() }
        )
        expect(screen.getByTestId('property-field-dataView.fields-empty')).toBeInTheDocument()
        expect(onChange).not.toHaveBeenCalled()
      })

      it('toggles a field name when a sibling collection is selected', async () => {
        mockAxios.get.mockResolvedValue({
          data: {
            data: {
              id: 'col1',
              type: 'collections',
              attributes: { name: 'orders', displayName: 'Orders' },
            },
            included: [
              {
                id: 'f1',
                type: 'fields',
                attributes: { name: 'status', displayName: 'Status', type: 'string', active: true },
                relationships: { collectionId: { data: { id: 'col1', type: 'collections' } } },
              },
            ],
          },
        })
        const onChange = vi.fn()
        render(
          <FieldPickerField
            schema={schema({
              key: 'dataView.fields',
              kind: 'field-picker',
              dependsOnCollection: true,
            })}
            value={[]}
            onChange={onChange}
            node={node({ type: 'table', props: { dataView: { collection: 'orders' } } })}
            fieldId="property-field-dataView.fields"
          />,
          { wrapper: createTestWrapper() }
        )
        const checkbox = await screen.findByTestId('property-field-dataView.fields-status')
        await userEvent.click(checkbox)
        expect(onChange).toHaveBeenLastCalledWith(['status'])
      })
    })
  })

  describe('CollectionPickerField row limit (table-only)', () => {
    let cleanupAuthMocks: () => void
    beforeEach(() => {
      cleanupAuthMocks = setupAuthMocks()
      resetMockAxios()
    })
    afterEach(() => {
      cleanupAuthMocks()
      vi.clearAllMocks()
    })

    it('omits the row-limit input for a form', async () => {
      const onChange = vi.fn()
      render(
        <CollectionPickerField
          schema={schema({ key: 'dataView', kind: 'collection-picker' })}
          value={{}}
          onChange={onChange}
          node={node({ type: 'form' })}
          fieldId="property-field-dataView"
        />,
        { wrapper: createTestWrapper() }
      )
      await waitFor(() => expect(screen.getByTestId('property-collection')).toBeInTheDocument())
      expect(screen.queryByTestId('property-limit')).not.toBeInTheDocument()
    })
  })
})
