import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '@/context/I18nContext'
import { ApiError } from '@/services/apiClient'
import { VisitJoinCard } from './VisitJoinCard'

// Mock the API client behind the useVideo hooks.
const mockPost = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({ apiClient: { post: mockPost, get: vi.fn() } })),
}))

// Identity + presence are context-bound; stub them so the card renders standalone.
vi.mock('@/hooks/useMyIdentity', () => ({
  useMyIdentity: vi.fn(() => ({ identity: { userId: 'me', email: null, profileId: null } })),
}))
const mockPresence = vi.fn(() => [] as { id: string; email?: string }[])
vi.mock('@/realtime/usePresence', () => ({
  usePresence: (resource: string | null) => mockPresence(resource),
}))

// The lazy VideoRoom pulls in LiveKit — replace it with a marker so tests never
// touch the real bundle. Asserting this marker == "we reached the live room".
vi.mock('./LazyVideoRoom', () => ({
  LazyVideoRoom: (props: { serverUrl: string; token: string; recordingActive?: boolean }) => (
    <div
      data-testid="lazy-video-room"
      data-url={props.serverUrl}
      data-token={props.token}
      data-recording={String(props.recordingActive ?? false)}
    />
  ),
}))

const grant = {
  sessionId: 'sess-1',
  roomName: 'room-1',
  url: 'wss://livekit.example',
  token: 'jwt-token',
  expiresAt: '2026-07-10T11:00:00Z',
}

// An always-open window so the Join button is enabled without faking timers.
function openWindow() {
  const now = Date.now()
  return {
    scheduledStart: new Date(now - 60_000).toISOString(),
    scheduledEnd: new Date(now + 30 * 60_000).toISOString(),
  }
}

function renderCard(overrides: Partial<React.ComponentProps<typeof VisitJoinCard>> = {}) {
  const win = openWindow()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter>
          <VisitJoinCard
            target={{ kind: 'appointment', id: 'appt-1' }}
            scheduledStart={win.scheduledStart}
            scheduledEnd={win.scheduledEnd}
            {...overrides}
          />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>
  )
}

describe('VisitJoinCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPresence.mockReturnValue([])
  })

  it('enables Join inside the window and enters the live room on success', async () => {
    mockPost.mockResolvedValueOnce(grant)
    renderCard()

    const joinBtn = await screen.findByTestId('visit-join-join')
    fireEvent.click(joinBtn)

    await waitFor(() => expect(screen.getByTestId('lazy-video-room')).toBeInTheDocument())
    expect(screen.getByTestId('lazy-video-room')).toHaveAttribute('data-url', grant.url)
    // No recording enabled → no consent call, token posted with empty body.
    expect(mockPost).toHaveBeenCalledWith('/api/telehealth/appointments/appt-1/video-token')
  })

  it('shows a "countdown" (join disabled) before the window opens', () => {
    const now = Date.now()
    renderCard({
      scheduledStart: new Date(now + 60 * 60_000).toISOString(),
      scheduledEnd: new Date(now + 90 * 60_000).toISOString(),
    })
    expect(screen.getByTestId('visit-join-countdown')).toBeInTheDocument()
    expect(screen.queryByTestId('visit-join-join')).not.toBeInTheDocument()
  })

  it('maps a 403 feature-off error to a friendly message', async () => {
    mockPost.mockRejectedValueOnce(new ApiError(403, 'telehealth is not enabled for this tenant'))
    renderCard()
    fireEvent.click(await screen.findByTestId('visit-join-join'))

    await waitFor(() => expect(screen.getByTestId('visit-join-error')).toBeInTheDocument())
    expect(screen.getByTestId('visit-join-error')).toHaveTextContent(/not enabled/i)
  })

  it('maps a 409 outside-window error to a friendly message', async () => {
    mockPost.mockRejectedValueOnce(new ApiError(409, 'appointment is not CONFIRMED'))
    renderCard()
    fireEvent.click(await screen.findByTestId('visit-join-join'))

    await waitFor(() => expect(screen.getByTestId('visit-join-error')).toBeInTheDocument())
    expect(screen.getByTestId('visit-join-error')).toHaveTextContent(/scheduled time/i)
  })

  it('maps a 429 budget error to a friendly message', async () => {
    mockPost.mockRejectedValueOnce(new ApiError(429, 'video minute budget exhausted'))
    renderCard()
    fireEvent.click(await screen.findByTestId('visit-join-join'))

    await waitFor(() => expect(screen.getByTestId('visit-join-error')).toBeInTheDocument())
    expect(screen.getByTestId('visit-join-error')).toHaveTextContent(/run out/i)
  })

  it('shows the consent screen first when recording is enabled, then joins after Accept', async () => {
    mockPost.mockResolvedValue(grant) // token + consent both succeed
    renderCard({ recordingEnabled: true })

    // Consent gate blocks the join button.
    expect(screen.getByTestId('visit-join-consent')).toBeInTheDocument()
    expect(screen.queryByTestId('visit-join-join')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('visit-join-consent-accept'))

    const joinBtn = await screen.findByTestId('visit-join-join')
    fireEvent.click(joinBtn)

    await waitFor(() => expect(screen.getByTestId('lazy-video-room')).toBeInTheDocument())
    // Recorded consent → the live room flags recording active.
    expect(screen.getByTestId('lazy-video-room')).toHaveAttribute('data-recording', 'true')
    // Consent decision was posted for the session.
    expect(mockPost).toHaveBeenCalledWith('/api/telehealth/sessions/sess-1/consent', {
      accepted: true,
    })
  })

  it('lets the patient decline and still join, unrecorded', async () => {
    mockPost.mockResolvedValue(grant)
    renderCard({ recordingEnabled: true })

    fireEvent.click(screen.getByTestId('visit-join-consent-decline'))
    fireEvent.click(await screen.findByTestId('visit-join-join'))

    await waitFor(() => expect(screen.getByTestId('lazy-video-room')).toBeInTheDocument())
    expect(screen.getByTestId('lazy-video-room')).toHaveAttribute('data-recording', 'false')
    expect(mockPost).toHaveBeenCalledWith('/api/telehealth/sessions/sess-1/consent', {
      accepted: false,
    })
  })

  it('shows the waiting room when other participants are present', () => {
    mockPresence.mockReturnValue([{ id: 'other-user' }])
    renderCard()
    expect(screen.getByTestId('visit-join-waiting')).toBeInTheDocument()
  })
})
