import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MailboxHtmlBody } from './MailboxHtmlBody'

/**
 * The browser-side half of the untrusted-HTML defence.
 *
 * The sandbox assertion below is the single most important test in this feature. It is the
 * regression guard against someone adding `allow-scripts` to fix the fixed-height annoyance —
 * which is a reasonable-sounding change that would hand script execution to whoever sent the
 * email.
 */
describe('MailboxHtmlBody', () => {
  const frame = () => screen.getByTestId('mailbox-body-iframe') as HTMLIFrameElement

  it('sandboxes the frame with an EMPTY sandbox attribute', () => {
    render(<MailboxHtmlBody html="<p>hello</p>" />)

    // Exactly '' — not absent, and not any token. An absent sandbox attribute means no sandboxing
    // at all, which is the opposite of what this value expresses.
    expect(frame().getAttribute('sandbox')).toBe('')
  })

  it('never grants allow-scripts or allow-same-origin', () => {
    render(<MailboxHtmlBody html="<p>hello</p>" />)
    const sandbox = frame().getAttribute('sandbox') ?? ''

    expect(sandbox).not.toContain('allow-scripts')
    expect(sandbox).not.toContain('allow-same-origin')
    expect(sandbox).not.toContain('allow-forms')
    expect(sandbox).not.toContain('allow-popups')
    expect(sandbox).not.toContain('allow-top-navigation')
  })

  it('locks the framed document down with a default-src none CSP', () => {
    render(<MailboxHtmlBody html="<p>hello</p>" />)
    const doc = frame().getAttribute('srcdoc') ?? ''

    expect(doc).toContain("default-src 'none'")
    expect(doc).toContain("script-src 'none'")
    expect(doc).toContain("form-action 'none'")
    expect(doc).toContain("base-uri 'none'")
    expect(doc).toContain("connect-src 'none'")
    expect(doc).toContain('<meta name="referrer" content="no-referrer">')
  })

  it('sends no referrer', () => {
    render(<MailboxHtmlBody html="<p>hi</p>" />)
    expect(frame().getAttribute('referrerpolicy')).toBe('no-referrer')
  })

  it('blocks remote images by default and does not emit a src for them', () => {
    render(
      <MailboxHtmlBody html='<img data-kelta-remote-src="https://tracker.example/p.gif" data-kelta-blocked="1">' />
    )
    const doc = frame().getAttribute('srcdoc') ?? ''

    // img-src is data: only, so even a src that slipped through could not load.
    expect(doc).toContain('img-src data:')
    expect(doc).not.toContain('img-src data: https:')
    expect(doc).not.toContain('<img src=')
  })

  it('explains what loading images costs, rather than just offering a button', () => {
    render(
      <MailboxHtmlBody html='<img data-kelta-remote-src="https://tracker.example/p.gif" data-kelta-blocked="1">' />
    )
    // The consequence is the point: a remote image tells the sender exactly when the message was
    // opened, and an agent cannot weigh that if the UI does not say so.
    expect(screen.getByTestId('mailbox-body-remote-images-notice').textContent).toMatch(
      /tells the sender you opened this message/i
    )
  })

  it('promotes parked URLs and widens img-src only after an explicit click', async () => {
    const user = userEvent.setup()
    render(
      <MailboxHtmlBody html='<img data-kelta-remote-src="https://tracker.example/p.gif" data-kelta-blocked="1">' />
    )

    await user.click(screen.getByTestId('mailbox-body-load-images'))

    const doc = frame().getAttribute('srcdoc') ?? ''
    expect(doc).toContain('img-src data: https:')
    expect(doc).toContain('src="https://tracker.example/p.gif"')
    // The affordance goes away once used; it is per-message and never remembered per sender.
    expect(screen.queryByTestId('mailbox-body-load-images')).toBeNull()
  })

  it('shows no image notice when there are no blocked images', () => {
    render(<MailboxHtmlBody html="<p>plain</p>" />)
    expect(screen.queryByTestId('mailbox-body-remote-images-notice')).toBeNull()
  })

  it('renders the supplied body inside the frame document', () => {
    render(<MailboxHtmlBody html="<p>hello there</p>" />)
    expect(frame().getAttribute('srcdoc')).toContain('<p>hello there</p>')
  })
})
