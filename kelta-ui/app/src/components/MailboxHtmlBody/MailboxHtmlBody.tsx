import { useMemo, useState } from 'react'
import { ImageOff } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Renders an inbound email body.
 *
 * This is the only place in the product that displays third-party HTML, and it is the highest-risk
 * render in the application: the author is an unauthenticated stranger who chose every byte.
 *
 * Two independent controls, either of which would be sufficient:
 *
 * 1. `sandbox=""` — an empty sandbox is the most restrictive value, not the least. It gives the
 *    frame an opaque origin with no scripts, no forms, no popups, no top-level navigation, and no
 *    access to this document. `srcDoc` still works under it.
 * 2. A `default-src 'none'` CSP inside the document.
 *
 * The server already sanitised this markup with jsoup at ingest. Both layers exist anyway, because
 * a sanitiser is a denylist-shaped problem however carefully it is written, and the browser
 * boundary does not depend on having got the parser right.
 */

/** Attributes the server writes when it parks a remote image. Must match MailboxHtmlSanitizer. */
const BLOCKED_ATTR = 'data-kelta-blocked'
const REMOTE_SRC_ATTR = 'data-kelta-remote-src'

export interface MailboxHtmlBodyProps {
  html: string
  /** Fixed frame height in pixels. See the note on auto-sizing below. */
  height?: number
  'data-testid'?: string
}

/**
 * Builds the framed document.
 *
 * `sandbox` and `frame-ancestors` are deliberately absent from the meta CSP: both are ignored in
 * meta form and only work as real headers. Putting them here would read as protection that cannot
 * fire. The iframe attribute is the actual enforcement.
 *
 * `style-src 'unsafe-inline'` looks alarming and grants the sender nothing: every author `style`
 * attribute and `<style>` element was stripped server-side, so the only inline CSS in the document
 * is ours, below.
 */
function buildSrcDoc(bodyHtml: string, allowRemoteImages: boolean): string {
  const imgSrc = allowRemoteImages ? 'img-src data: https:' : 'img-src data:'
  return `<!doctype html><html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="
  default-src 'none';
  ${imgSrc};
  style-src 'unsafe-inline';
  font-src 'none'; media-src 'none'; frame-src 'none'; child-src 'none';
  connect-src 'none'; script-src 'none'; object-src 'none';
  form-action 'none'; base-uri 'none'">
<meta name="referrer" content="no-referrer">
<style>
  :root { color-scheme: light; }
  html, body { background: #ffffff; }
  body { font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
         padding: 16px; margin: 0; color: #111; line-height: 1.5;
         word-break: break-word; overflow-wrap: anywhere; }
  body > *:first-child { margin-top: 0; }
  img { max-width: 100%; height: auto; }
  img[${BLOCKED_ATTR}] { display: inline-block; min-width: 24px; min-height: 24px;
        border: 1px dashed #cbd5e1; background: #f8fafc; }
  table { border-collapse: collapse; max-width: 100%; }
  td, th { border: 1px solid #e2e8f0; padding: 4px 8px; }
  blockquote { margin: 0 0 0.75em; padding-left: 12px; border-left: 3px solid #e2e8f0; color: #475569; }
  pre { white-space: pre-wrap; }
  a { color: #2563eb; }
</style></head><body>${bodyHtml}</body></html>`
}

/**
 * Promotes parked image URLs back onto `src`.
 *
 * A string transform over already-sanitised markup rather than a re-parse: parsing it again on the
 * client would be a second sanitiser in the wrong place, and the value being promoted is one this
 * document's own server-side pass already validated as http(s).
 */
function promoteRemoteImages(html: string): string {
  return html.replace(
    new RegExp(`${REMOTE_SRC_ATTR}="([^"]*)"`, 'g'),
    (_match, url: string) => `src="${url}"`
  )
}

export function MailboxHtmlBody({ html, height = 420, ...rest }: MailboxHtmlBodyProps) {
  const [loadRemote, setLoadRemote] = useState(false)

  const blockedCount = useMemo(
    () => (html.match(new RegExp(BLOCKED_ATTR, 'g')) ?? []).length,
    [html]
  )

  const srcDoc = useMemo(
    () => buildSrcDoc(loadRemote ? promoteRemoteImages(html) : html, loadRemote),
    [html, loadRemote]
  )

  return (
    <div className="flex flex-col gap-2">
      {blockedCount > 0 && !loadRemote && (
        <div
          className="flex items-center justify-between gap-3 rounded-md border border-border bg-muted/40 px-3 py-2"
          data-testid="mailbox-body-remote-images-notice"
        >
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <ImageOff className="h-3.5 w-3.5 shrink-0" aria-hidden />
            <span>
              {blockedCount} image{blockedCount === 1 ? '' : 's'} blocked. Loading them tells the
              sender you opened this message.
            </span>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setLoadRemote(true)}
            data-testid="mailbox-body-load-images"
          >
            Load images
          </Button>
        </div>
      )}
      <iframe
        title="Message body"
        // Empty string, not omitted. An absent sandbox attribute means NO sandboxing at all, which
        // is the exact opposite of what this value expresses. Never add a token here: in
        // particular `allow-scripts allow-same-origin` together lets the framed document reach
        // into this one and remove its own sandbox attribute.
        sandbox=""
        referrerPolicy="no-referrer"
        srcDoc={srcDoc}
        // Fixed height on purpose. Auto-sizing needs the frame to postMessage its content height,
        // which needs allow-scripts, which is the one thing that must not happen here. The full
        // message is available in the plain-text view and by scrolling this frame.
        style={{ height: `${height}px` }}
        className="w-full rounded-md border border-border bg-white"
        data-testid={rest['data-testid'] ?? 'mailbox-body-iframe'}
      />
    </div>
  )
}
