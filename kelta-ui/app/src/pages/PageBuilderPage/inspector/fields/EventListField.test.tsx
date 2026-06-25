/**
 * EventListField authoring-shell tests. Every assertion checks that onChange receives the WHOLE
 * EventHandlers map (never a bare PageAction[]). The action runtime (executing runFlow/navigate/etc.) is
 * out of scope here — it lands in slice 2e; this only proves the authoring + value-write contract.
 */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../../context/I18nContext'
import { EventListField } from './EventListField'
import type { EventHandlers, EventName, PageAction, PageComponent } from '../../model/pageModel'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)
const node: PageComponent = { id: 'c1', type: 'button', props: {} }

function renderField(
  value: EventHandlers | undefined,
  supportedEvents: EventName[] = ['onClick'],
  onChange = vi.fn()
) {
  render(
    <EventListField
      supportedEvents={supportedEvents}
      value={value}
      onChange={onChange}
      node={node}
      fieldId="property-events"
    />,
    { wrapper }
  )
  return onChange
}

const a0: PageAction = { action: 'showToast', level: 'info', message: 'A' }
const a1: PageAction = { action: 'navigate', to: '/x' }

describe('EventListField', () => {
  it('renders one tab per supportedEvents entry and can switch tabs', async () => {
    renderField({ onChange: [a1] }, ['onClick', 'onChange'])
    expect(screen.getByTestId('event-tab-onClick')).toBeInTheDocument()
    expect(screen.getByTestId('event-tab-onChange')).toBeInTheDocument()
    // onClick is active first → no rows; switch to onChange → its row appears.
    expect(screen.queryByTestId('event-row-onChange-0')).not.toBeInTheDocument()
    await userEvent.click(screen.getByTestId('event-tab-onChange'))
    expect(screen.getByTestId('event-row-onChange-0')).toBeInTheDocument()
  })

  it('add: writes the whole EventHandlers map with the new action under the active event', async () => {
    const onChange = renderField(undefined)
    // default add type is runFlow
    await userEvent.click(screen.getByTestId('event-add-onClick'))
    expect(onChange).toHaveBeenCalledWith({
      onClick: [{ action: 'runFlow', flowId: '', input: {} }],
    })
  })

  it('remove: drops the action and writes the map', async () => {
    const onChange = renderField({ onClick: [a0, a1] })
    await userEvent.click(screen.getByTestId('event-remove-onClick-0'))
    expect(onChange).toHaveBeenCalledWith({ onClick: [a1] })
  })

  it('reorder: moving row 0 down swaps order and writes the map', async () => {
    const onChange = renderField({ onClick: [a0, a1] })
    await userEvent.click(screen.getByTestId('event-down-onClick-0'))
    expect(onChange).toHaveBeenCalledWith({ onClick: [a1, a0] })
  })

  it('edit a param: updates the action verbatim and writes the map', async () => {
    const onChange = renderField({ onClick: [a0] })
    await userEvent.type(screen.getByTestId('event-param-message'), '!')
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'showToast', level: 'info', message: 'A!' }],
    })
  })

  it('drops the event key entirely when its last action is removed (no empty [])', async () => {
    const onChange = renderField({ onClick: [a0] })
    await userEvent.click(screen.getByTestId('event-remove-onClick-0'))
    expect(onChange).toHaveBeenCalledWith({})
  })

  it('does not execute any action (runtime is 2e) — only authors the model', async () => {
    // Authoring a runFlow action must not trigger a network call; we assert no throw + the write only.
    const onChange = renderField(undefined)
    await userEvent.click(screen.getByTestId('event-add-onClick'))
    expect(onChange).toHaveBeenCalledTimes(1)
  })
})

describe('EventListField per-action sub-forms (2e)', () => {
  it('runFlow: toggling awaitResult sets the flag', async () => {
    const onChange = renderField({
      onClick: [{ action: 'runFlow', flowId: 'f1', input: {} }],
    })
    await userEvent.click(screen.getByTestId('event-param-awaitResult'))
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'runFlow', flowId: 'f1', input: {}, awaitResult: true }],
    })
  })

  it('runFlow: adding an input row writes an input map entry', async () => {
    const onChange = renderField({
      onClick: [{ action: 'runFlow', flowId: 'f1', input: {} }],
    })
    await userEvent.click(screen.getByTestId('event-param-input-add'))
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'runFlow', flowId: 'f1', input: { key1: '' } }],
    })
  })

  it('navigate: toggling newTab sets the flag', async () => {
    const onChange = renderField({ onClick: [{ action: 'navigate', to: '/x' }] })
    await userEvent.click(screen.getByTestId('event-param-newTab'))
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'navigate', to: '/x', newTab: true }],
    })
  })

  it('updateRecord: shows and edits a recordId field', async () => {
    const onChange = renderField({
      onClick: [{ action: 'updateRecord', collection: 'orders', attributes: {} }],
    })
    await userEvent.type(screen.getByTestId('event-param-recordId'), 'r')
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'updateRecord', collection: 'orders', attributes: {}, recordId: 'r' }],
    })
  })

  it('createRecord: adding an attribute row writes an attributes map entry', async () => {
    const onChange = renderField({
      onClick: [{ action: 'createRecord', collection: 'orders', attributes: {} }],
    })
    await userEvent.click(screen.getByTestId('event-param-attr-add'))
    expect(onChange).toHaveBeenLastCalledWith({
      onClick: [{ action: 'createRecord', collection: 'orders', attributes: { key1: '' } }],
    })
  })
})
