import { useState } from 'react'
import { toast } from 'sonner'
import { AlertTriangle, Copy, Inbox, Plus, RotateCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { FieldLabel, StatusBadge } from '@/components/kelta'
import { LoadingSpinner, ErrorMessage } from '../../components'
import {
  useAutoReplyReport,
  useEscalationContacts,
  useMailboxAccess,
  useMailboxAdminActions,
  useMailboxes,
  type Mailbox,
} from '../../hooks/useMailboxAdmin'

/**
 * Administration for support mailboxes.
 *
 * Master-detail: mailboxes on the left, configuration on the right. Everything here requires
 * MANAGE_SUPPORT_MAILBOX, enforced server-side — the route wrapper is UX only, since
 * RequirePermission renders children while permissions load.
 *
 * The automation section is the only place in the product where a setting causes mail to be sent
 * without a person reading it, so it is deliberately the least convenient thing on the page:
 * confirmation before enabling, the shadow-mode report shown next to the switch, and the
 * consequence stated rather than implied.
 */

const ESCALATION_LEVELS = ['WARN', 'BREACH', 'BREACH_2', 'BREACH_3'] as const
const ROLES = ['VIEWER', 'AGENT', 'MANAGER'] as const

export function MailboxAdminPage() {
  const mailboxes = useMailboxes()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null)

  // Derived rather than synced in an effect: defaulting to the first mailbox is a render-time
  // question, and setState inside an effect is a cascading render for something that needs none.
  const list = mailboxes.data ?? []
  const selected = list.find((m) => m.id === selectedId) ?? list[0] ?? null

  if (mailboxes.isLoading) return <LoadingSpinner />
  if (mailboxes.error) return <ErrorMessage error="Could not load mailboxes." />

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-foreground">Support Mailboxes</h1>
          <p className="text-sm text-muted-foreground">
            Shared inboxes, membership, SLA policy and automation.
          </p>
        </div>
      </header>

      <div className="flex gap-4">
        <aside className="w-[260px] shrink-0 space-y-1">
          {list.length === 0 && (
            <p className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground">
              No mailboxes yet.
            </p>
          )}
          {list.map((m) => (
            <button
              key={m.id}
              onClick={() => {
                setSelectedId(m.id)
                // The plaintext secret belongs to the response that produced it. Carrying it
                // across a selection change would show one mailbox's secret under another's name.
                setRevealedSecret(null)
              }}
              className={`flex w-full flex-col rounded-md border border-border px-3 py-2 text-left transition-colors ${
                m.id === selected?.id ? 'bg-muted' : 'hover:bg-muted/50'
              }`}
              data-testid="mailbox-admin-row"
            >
              <span className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Inbox className="h-3.5 w-3.5" aria-hidden />
                {m.name}
              </span>
              <span className="truncate text-xs text-muted-foreground">{m.address}</span>
              {!m.active && <StatusBadge variant="inactive" label="Inactive" />}
            </button>
          ))}
        </aside>

        <div className="min-w-0 flex-1 space-y-4">
          {!selected && <p className="text-sm text-muted-foreground">Select a mailbox.</p>}
          {selected && (
            <>
              <IngestCard
                mailbox={selected}
                revealedSecret={revealedSecret}
                onRevealSecret={setRevealedSecret}
              />
              <SlaCard mailbox={selected} />
              <AutomationCard mailbox={selected} />
              <MembersCard mailbox={selected} />
              <EscalationCard mailbox={selected} />
            </>
          )}
        </div>
      </div>
    </div>
  )
}

/** Where mail comes in, and the material that authenticates it. */
function IngestCard({
  mailbox,
  revealedSecret,
  onRevealSecret,
}: {
  mailbox: Mailbox
  revealedSecret: string | null
  onRevealSecret: (secret: string | null) => void
}) {
  const actions = useMailboxAdminActions(mailbox.id)
  const webhookUrl = `${window.location.origin}/api/webhooks/mail/${mailbox.webhookKey}`

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Inbound</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div>
          <FieldLabel>Webhook URL</FieldLabel>
          <div className="flex items-center gap-2">
            <code className="min-w-0 flex-1 truncate rounded bg-muted px-2 py-1 text-xs">
              {webhookUrl}
            </code>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                void navigator.clipboard.writeText(webhookUrl)
                toast.success('Webhook URL copied')
              }}
            >
              <Copy className="h-3.5 w-3.5" aria-hidden />
            </Button>
          </div>
          {/* Said plainly because it looks like a secret and is not — it turns up in provider
              consoles and access logs, and treating it as a credential leads to the wrong
              decisions about where it may be pasted. */}
          <p className="mt-1 text-[11px] text-muted-foreground">
            This URL is an identifier, not a credential. Requests are authenticated separately by
            signature.
          </p>
        </div>

        <div>
          <FieldLabel>Provider</FieldLabel>
          <p className="text-sm text-foreground">{mailbox.inboundProvider}</p>
        </div>

        <div>
          <FieldLabel>Signing secret</FieldLabel>
          {revealedSecret ? (
            <div className="space-y-1 rounded-md border border-amber-500/40 bg-amber-500/10 p-2">
              <code className="block break-all text-xs">{revealedSecret}</code>
              <p className="text-[11px] text-muted-foreground">
                Copy this now — it cannot be shown again. The previous secret keeps working during
                the overlap window so deliveries in flight are not rejected.
              </p>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {mailbox.inboundSecretHint ? `Ends ${mailbox.inboundSecretHint}` : 'Not set'}
              {mailbox.inboundSecretRotatedAt &&
                ` · rotated ${new Date(mailbox.inboundSecretRotatedAt).toLocaleDateString()}`}
            </p>
          )}
          <Button
            variant="outline"
            size="sm"
            className="mt-2"
            disabled={actions.rotateSecret.isPending}
            onClick={() => {
              if (
                !window.confirm(
                  'Rotate the signing secret? The new one is shown once. The current secret keeps ' +
                    'working during the overlap window, so update your provider before it expires.'
                )
              ) {
                return
              }
              actions.rotateSecret.mutate(undefined, {
                onSuccess: (res) => {
                  onRevealSecret(res.data.attributes.inboundSecret ?? null)
                  toast.success('Secret rotated')
                },
                onError: () => toast.error('Could not rotate the secret'),
              })
            }}
          >
            <RotateCw className="mr-1 h-3.5 w-3.5" aria-hidden />
            Rotate secret
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function SlaCard({ mailbox }: { mailbox: Mailbox }) {
  const actions = useMailboxAdminActions(mailbox.id)
  const [first, setFirst] = useState(String(mailbox.slaFirstResponseMinutes ?? ''))
  const [resolution, setResolution] = useState(String(mailbox.slaResolutionMinutes ?? ''))

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">SLA policy</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex gap-4">
          <div>
            <FieldLabel>First response (minutes)</FieldLabel>
            <input
              value={first}
              onChange={(e) => setFirst(e.target.value)}
              className="w-32 rounded-md border border-border bg-background px-2 py-1 text-sm"
              data-testid="sla-first-response"
            />
          </div>
          <div>
            <FieldLabel>Resolution (minutes)</FieldLabel>
            <input
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              className="w-32 rounded-md border border-border bg-background px-2 py-1 text-sm"
              data-testid="sla-resolution"
            />
          </div>
        </div>
        {/* Frozen-at-creation is surprising unless stated: an admin who lowers this expecting
            every open thread to re-evaluate will otherwise think it did not save. */}
        <p className="text-[11px] text-muted-foreground">
          Applies to threads created from now on. Existing threads keep the deadline they were
          given, so past SLA reports stay accurate.
        </p>
        <Button
          size="sm"
          disabled={actions.update.isPending}
          onClick={() =>
            actions.update.mutate(
              {
                slaFirstResponseMinutes: first ? Number(first) : null,
                slaResolutionMinutes: resolution ? Number(resolution) : null,
              },
              {
                onSuccess: () => toast.success('SLA policy saved'),
                onError: () => toast.error('Could not save the SLA policy'),
              }
            )
          }
        >
          Save
        </Button>
      </CardContent>
    </Card>
  )
}

/** The only switch in the product that lets mail leave without a person reading it. */
function AutomationCard({ mailbox }: { mailbox: Mailbox }) {
  const actions = useMailboxAdminActions(mailbox.id)
  const report = useAutoReplyReport(mailbox.id)

  const followUpPct =
    report.data?.followUpRate == null ? null : Math.round(report.data.followUpRate * 100)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          Automation
          {report.data?.shadowMode && <StatusBadge variant="pending" label="Shadow mode" />}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {report.data?.shadowMode && (
          <div className="flex items-start gap-2 rounded-md border border-border bg-muted/40 p-2 text-xs text-muted-foreground">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
            <span>
              This deployment is recording what auto-reply <em>would</em> send without sending it.
              Turning the switch on below has no effect until shadow mode is disabled in
              configuration.
            </span>
          </div>
        )}

        <div className="flex items-center justify-between">
          <div>
            <FieldLabel>Auto-reply</FieldLabel>
            <p className="text-[11px] text-muted-foreground">
              Sends a matched template to the customer with no human review.
            </p>
          </div>
          <Switch
            checked={mailbox.autoReplyEnabled}
            data-testid="auto-reply-toggle"
            onCheckedChange={(next) => {
              if (
                next &&
                !window.confirm(
                  'Enable auto-reply?\n\nMatched templates will be emailed to customers without ' +
                    'anyone reading them first. Only templates explicitly marked auto-sendable are ' +
                    'used, and money, access and legal categories are never auto-answered.'
                )
              ) {
                return
              }
              actions.update.mutate(
                { autoReplyEnabled: next },
                {
                  onSuccess: () =>
                    toast.success(next ? 'Auto-reply enabled' : 'Auto-reply disabled'),
                  onError: () => toast.error('Could not update automation'),
                }
              )
            }}
          />
        </div>

        {report.data && (
          <div className="space-y-2 rounded-md border border-border p-2">
            <p className="text-xs font-medium text-foreground">Last {report.data.days} days</p>
            <div className="flex flex-wrap gap-2">
              {report.data.breakdown.slice(0, 8).map((row, i) => (
                <StatusBadge
                  key={i}
                  variant={row.outcome === 'SENT' ? 'active' : 'inactive'}
                  label={`${row.outcome}${row.veto_reason ? `: ${row.veto_reason}` : ''} · ${row.count}`}
                />
              ))}
              {report.data.breakdown.length === 0 && (
                <span className="text-xs text-muted-foreground">No decisions recorded yet.</span>
              )}
            </div>
            {/* Shown next to the SLA-facing numbers on purpose: auto-replying "thanks for
                writing!" to everything scores perfect first-response compliance while helping
                nobody, and this is the number that exposes it. */}
            <p className="text-[11px] text-muted-foreground">
              {report.data.autoReplied} auto-replied ·{' '}
              {followUpPct == null
                ? 'no follow-up data'
                : `${followUpPct}% written back within 72h`}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function MembersCard({ mailbox }: { mailbox: Mailbox }) {
  const access = useMailboxAccess(mailbox.id)
  const actions = useMailboxAdminActions(mailbox.id)
  const [principalId, setPrincipalId] = useState('')
  const [role, setRole] = useState<(typeof ROLES)[number]>('AGENT')

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Members</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-[11px] text-muted-foreground">
          Membership decides who can see this mailbox. MANAGER additionally approves replies and
          reassigns threads.
        </p>
        <ul className="space-y-1">
          {(access.data ?? []).map((g) => (
            <li
              key={g.id}
              className="flex items-center justify-between rounded-md border border-border px-2 py-1 text-sm"
            >
              <span>
                {g.principalType} · {g.principalId}
              </span>
              <span className="flex items-center gap-2">
                <StatusBadge variant="inactive" label={g.role} />
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    actions.revokeAccess.mutate(g.id, {
                      onSuccess: () => toast.success('Access revoked'),
                      onError: () => toast.error('Could not revoke access'),
                    })
                  }
                >
                  <Trash2 className="h-3.5 w-3.5" aria-hidden />
                </Button>
              </span>
            </li>
          ))}
          {(access.data ?? []).length === 0 && (
            <li className="text-sm text-muted-foreground">Nobody has access yet.</li>
          )}
        </ul>
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <FieldLabel>User ID</FieldLabel>
            <input
              value={principalId}
              onChange={(e) => setPrincipalId(e.target.value)}
              className="w-full rounded-md border border-border bg-background px-2 py-1 text-sm"
              data-testid="member-user-id"
            />
          </div>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as (typeof ROLES)[number])}
            className="rounded-md border border-border bg-background px-2 py-1 text-sm"
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
          <Button
            size="sm"
            disabled={!principalId.trim()}
            onClick={() =>
              actions.grantAccess.mutate(
                { principalType: 'USER', principalId: principalId.trim(), role },
                {
                  onSuccess: () => {
                    setPrincipalId('')
                    toast.success('Access granted')
                  },
                  onError: () => toast.error('Could not grant access'),
                }
              )
            }
          >
            <Plus className="mr-1 h-3.5 w-3.5" aria-hidden />
            Add
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function EscalationCard({ mailbox }: { mailbox: Mailbox }) {
  const contacts = useEscalationContacts(mailbox.id)
  const actions = useMailboxAdminActions(mailbox.id)
  const [userId, setUserId] = useState('')
  const [level, setLevel] = useState<(typeof ESCALATION_LEVELS)[number]>('BREACH')

  const byLevel = (l: string) => (contacts.data ?? []).filter((c) => c.level === l)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Escalation</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {ESCALATION_LEVELS.map((l) => {
          const people = byLevel(l)
          return (
            <div key={l} className="flex items-start justify-between gap-2">
              <span className="w-24 shrink-0 text-xs font-medium text-foreground">{l}</span>
              <div className="flex-1 space-y-1">
                {people.map((c) => (
                  <span key={c.id} className="flex items-center justify-between text-sm">
                    {c.userId}
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        actions.removeEscalationContact.mutate(c.id, {
                          onSuccess: () => toast.success('Contact removed'),
                          onError: () => toast.error('Could not remove the contact'),
                        })
                      }
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden />
                    </Button>
                  </span>
                ))}
                {people.length === 0 && (
                  /* Called out rather than left blank: a level with nobody on it looks like a
                     working system right up until something breaches. */
                  <span className="text-xs text-amber-600 dark:text-amber-500">
                    Nobody is notified at this level.
                  </span>
                )}
              </div>
            </div>
          )
        })}

        <div className="flex items-end gap-2 border-t border-border pt-3">
          <div className="flex-1">
            <FieldLabel>User ID</FieldLabel>
            <input
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full rounded-md border border-border bg-background px-2 py-1 text-sm"
              data-testid="escalation-user-id"
            />
          </div>
          <select
            value={level}
            onChange={(e) => setLevel(e.target.value as (typeof ESCALATION_LEVELS)[number])}
            className="rounded-md border border-border bg-background px-2 py-1 text-sm"
          >
            {ESCALATION_LEVELS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          <Button
            size="sm"
            disabled={!userId.trim()}
            onClick={() =>
              actions.addEscalationContact.mutate(
                { level, userId: userId.trim(), channels: ['email'] },
                {
                  onSuccess: () => {
                    setUserId('')
                    toast.success('Contact added')
                  },
                  onError: () => toast.error('Could not add the contact'),
                }
              )
            }
          >
            <Plus className="mr-1 h-3.5 w-3.5" aria-hidden />
            Add
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
