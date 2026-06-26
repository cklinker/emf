/**
 * PageSettingsDrawer + Variables/Data-sources sections (slice 2d — the drawer is created here). Verifies
 * both sections render, add/remove/edit flow through, and the MAX_PAGE_DATA_SOURCES cap disables the add
 * button with an inline message.
 */
import { useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { I18nProvider } from '@/context/I18nContext'
import { PageSettingsDrawer } from './PageSettingsDrawer'
import { MAX_PAGE_DATA_SOURCES } from '../../model/limits'
import type { PageDataSource, PageVariable } from '../../pageConfig'

function Harness({
  initialVariables = [],
  initialDataSources = [],
  onVariablesChange,
  onDataSourcesChange,
  onIsHomePageChange,
}: {
  initialVariables?: PageVariable[]
  initialDataSources?: PageDataSource[]
  onVariablesChange?: (v: PageVariable[]) => void
  onDataSourcesChange?: (d: PageDataSource[]) => void
  onIsHomePageChange?: (v: boolean) => void
}) {
  const [variables, setVariables] = useState<PageVariable[]>(initialVariables)
  const [dataSources, setDataSources] = useState<PageDataSource[]>(initialDataSources)
  const [isHomePage, setIsHomePage] = useState(false)
  return (
    <I18nProvider>
      <PageSettingsDrawer
        open
        onOpenChange={() => {}}
        variables={variables}
        dataSources={dataSources}
        onVariablesChange={(v) => {
          setVariables(v)
          onVariablesChange?.(v)
        }}
        onDataSourcesChange={(d) => {
          setDataSources(d)
          onDataSourcesChange?.(d)
        }}
        onRequiredPermissionChange={() => {}}
        isHomePage={isHomePage}
        onIsHomePageChange={(v) => {
          setIsHomePage(v)
          onIsHomePageChange?.(v)
        }}
      />
    </I18nProvider>
  )
}

describe('PageSettingsDrawer', () => {
  it('renders the Variables and Data sources sections', async () => {
    render(<Harness />)
    await waitFor(() => expect(screen.getByTestId('page-settings-drawer')).toBeInTheDocument())
    expect(screen.getByTestId('page-settings-variables')).toBeInTheDocument()
    expect(screen.getByTestId('page-settings-data-sources')).toBeInTheDocument()
  })

  it('toggles "set as app home" and emits the change', async () => {
    const user = userEvent.setup()
    const onIsHomePageChange = vi.fn()
    render(<Harness onIsHomePageChange={onIsHomePageChange} />)
    const checkbox = await screen.findByTestId('page-home-checkbox')
    expect(checkbox).not.toBeChecked()
    await user.click(checkbox)
    expect(onIsHomePageChange).toHaveBeenCalledWith(true)
    expect(checkbox).toBeChecked()
  })

  it('adds, edits and removes a variable', async () => {
    const user = userEvent.setup()
    const onVariablesChange = vi.fn()
    render(<Harness onVariablesChange={onVariablesChange} />)
    await waitFor(() => expect(screen.getByTestId('page-settings-drawer')).toBeInTheDocument())

    await user.click(screen.getByTestId('add-variable-button'))
    await user.type(screen.getByTestId('variable-name-0'), 'count')
    await user.selectOptions(screen.getByTestId('variable-type-0'), 'number')

    // Last call reflects the edited variable.
    const last = onVariablesChange.mock.calls.at(-1)![0] as PageVariable[]
    expect(last[0]).toMatchObject({ type: 'number' })

    await user.click(screen.getByTestId('variable-remove-0'))
    expect(screen.queryByTestId('variable-row')).not.toBeInTheDocument()
  })

  it('adds and removes a data source', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await waitFor(() => expect(screen.getByTestId('page-settings-drawer')).toBeInTheDocument())

    await user.click(screen.getByTestId('add-data-source-button'))
    expect(screen.getByTestId('data-source-row')).toBeInTheDocument()
    await user.type(screen.getByTestId('data-source-collection-0'), 'accounts')

    await user.click(screen.getByTestId('data-source-remove-0'))
    expect(screen.queryByTestId('data-source-row')).not.toBeInTheDocument()
  })

  it('disables Add source and shows the cap message at MAX_PAGE_DATA_SOURCES', async () => {
    const full: PageDataSource[] = Array.from({ length: MAX_PAGE_DATA_SOURCES }, (_, i) => ({
      name: `s${i}`,
      collection: `c${i}`,
      mode: 'list',
    }))
    render(<Harness initialDataSources={full} />)
    await waitFor(() => expect(screen.getByTestId('page-settings-drawer')).toBeInTheDocument())
    expect(screen.getByTestId('add-data-source-button')).toBeDisabled()
    expect(screen.getByTestId('data-source-max-message')).toBeInTheDocument()
  })
})
