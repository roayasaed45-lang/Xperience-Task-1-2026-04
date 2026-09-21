import { useEffect, useState, type FormEvent } from 'react'
import { useRouter } from '../lib/router'
import { ApiError, cancelEvent, closeEvent, createInvitation, getDashboard } from '../services/eventRsvpApi'
import type { CreateInvitationResponse, HostDashboardResponse } from '../types'
import { loadLastEventSession, saveLastEventSession } from '../lib/storage'
import { Alert, Badge, Button, Card, Field, inputClass, outcomeTone, responseTone, statusTone } from '../components/ui'

export function HostDashboard() {
  const { query, navigate } = useRouter()
  const lastSession = loadLastEventSession()

  const [eventIdInput, setEventIdInput] = useState(
    query.get('eventId') ?? (lastSession ? String(lastSession.eventId) : ''),
  )
  const [tokenInput, setTokenInput] = useState(query.get('token') ?? lastSession?.hostManagementToken ?? '')
  const [session, setSession] = useState<{ eventId: number; token: string } | null>(null)
  const [dashboard, setDashboard] = useState<HostDashboardResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const [inviteEmail, setInviteEmail] = useState('')
  const [inviting, setInviting] = useState(false)
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [inviteResult, setInviteResult] = useState<CreateInvitationResponse | null>(null)

  const [lifecycleBusy, setLifecycleBusy] = useState(false)

  async function loadDashboard(eventId: number, token: string) {
    setLoading(true)
    setError(null)
    try {
      const data = await getDashboard(eventId, token)
      setDashboard(data)
      setSession({ eventId, token })
      saveLastEventSession({ eventId, hostManagementToken: token })
    } catch (err) {
      setDashboard(null)
      setError(err instanceof ApiError ? err.message : 'Unable to load the dashboard.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    // Run once on mount: if the page was opened with both an event id and a
    // host token already known (from the URL, or remembered from a prior
    // Create Event / dashboard visit), load immediately for convenience.
    const eventId = Number(eventIdInput)
    if (eventIdInput && tokenInput && Number.isFinite(eventId) && eventId > 0) {
      void loadDashboard(eventId, tokenInput)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleLoadSubmit(event: FormEvent) {
    event.preventDefault()
    const eventId = Number(eventIdInput)
    if (!eventIdInput || !tokenInput || !Number.isFinite(eventId) || eventId <= 0) {
      setError('Enter a valid Event ID and Host Management Token.')
      return
    }
    void loadDashboard(eventId, tokenInput)
  }

  async function handleInviteSubmit(event: FormEvent) {
    event.preventDefault()
    if (!session) {
      return
    }
    setInviteError(null)
    setInviteResult(null)
    setInviting(true)
    try {
      const invitation = await createInvitation(session.eventId, session.token, inviteEmail.trim())
      setInviteResult(invitation)
      setInviteEmail('')
      await loadDashboard(session.eventId, session.token)
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : 'Unable to send the invitation.')
    } finally {
      setInviting(false)
    }
  }

  async function handleClose() {
    if (!session) {
      return
    }
    if (!window.confirm('Close this event? No new RSVPs or changes to existing RSVPs will be accepted. This cannot be undone.')) {
      return
    }
    setLifecycleBusy(true)
    setError(null)
    setNotice(null)
    try {
      await closeEvent(session.eventId, session.token)
      setNotice('Event closed.')
      await loadDashboard(session.eventId, session.token)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to close the event.')
    } finally {
      setLifecycleBusy(false)
    }
  }

  async function handleCancel() {
    if (!session) {
      return
    }
    if (!window.confirm('Cancel this event? No new RSVPs or changes will be accepted. This cannot be undone.')) {
      return
    }
    setLifecycleBusy(true)
    setError(null)
    setNotice(null)
    try {
      await cancelEvent(session.eventId, session.token)
      setNotice('Event cancelled.')
      await loadDashboard(session.eventId, session.token)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to cancel the event.')
    } finally {
      setLifecycleBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <h2 className="text-xl font-semibold text-slate-900">Host Dashboard</h2>
        <form className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end" onSubmit={handleLoadSubmit}>
          <Field label="Event ID" htmlFor="eventId">
            <input id="eventId" value={eventIdInput} onChange={(e) => setEventIdInput(e.target.value)} className={inputClass} />
          </Field>
          <Field label="Host Management Token" htmlFor="hostToken">
            <input
              id="hostToken"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              className={`${inputClass} font-mono text-xs`}
            />
          </Field>
          <Button type="submit" loading={loading}>
            Load Dashboard
          </Button>
        </form>
        {error && (
          <div className="mt-4">
            <Alert kind="error">{error}</Alert>
          </div>
        )}
        {notice && (
          <div className="mt-4">
            <Alert kind="success">{notice}</Alert>
          </div>
        )}
      </Card>

      {dashboard && session && (
        <>
          <Card>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-lg font-semibold text-slate-900">{dashboard.title}</h3>
                {dashboard.description && <p className="mt-1 text-sm text-slate-600">{dashboard.description}</p>}
              </div>
              <Badge tone={statusTone(dashboard.status)}>{dashboard.status}</Badge>
            </div>
            <dl className="mt-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-slate-500">Date &amp; time</dt>
                <dd className="font-medium text-slate-800">{formatDateTime(dashboard.eventDateTime)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Location</dt>
                <dd className="font-medium text-slate-800">{dashboard.location}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Max capacity</dt>
                <dd className="font-medium text-slate-800">{dashboard.maxCapacity ?? 'Unlimited'}</dd>
              </div>
            </dl>
            <div className="mt-5 flex flex-wrap gap-3">
              <Button variant="secondary" onClick={() => loadDashboard(session.eventId, session.token)} loading={loading}>
                Refresh
              </Button>
              <Button variant="danger" onClick={handleClose} disabled={dashboard.status !== 'OPEN'} loading={lifecycleBusy}>
                Close Event
              </Button>
              <Button variant="danger" onClick={handleCancel} disabled={dashboard.status !== 'OPEN'} loading={lifecycleBusy}>
                Cancel Event
              </Button>
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-slate-900">Counts</h3>
            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <CountTile label="Invited" value={dashboard.counts.totalInvited} />
              <CountTile label="Yes" value={dashboard.counts.yesCount} />
              <CountTile label="No" value={dashboard.counts.noCount} />
              <CountTile label="Maybe" value={dashboard.counts.maybeCount} />
              <CountTile label="No response" value={dashboard.counts.noResponseCount} />
              <CountTile label="Confirmed" value={dashboard.counts.confirmedCount} />
              <CountTile label="Waitlisted" value={dashboard.counts.waitlistedCount} />
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-slate-900">Invitations</h3>
            <div className="mt-4 overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead>
                  <tr className="text-left text-slate-500">
                    <th className="py-2 pr-4 font-medium">Email</th>
                    <th className="py-2 pr-4 font-medium">RSVP Response</th>
                    <th className="py-2 pr-4 font-medium">Attendance Outcome</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {dashboard.invitations.length === 0 && (
                    <tr>
                      <td colSpan={3} className="py-4 text-center text-slate-500">
                        No invitations yet.
                      </td>
                    </tr>
                  )}
                  {dashboard.invitations.map((row) => (
                    <tr key={row.invitationId}>
                      <td className="py-2 pr-4 text-slate-800">{row.inviteeEmail}</td>
                      <td className="py-2 pr-4">
                        {row.rsvpResponse ? (
                          <Badge tone={responseTone(row.rsvpResponse)}>{row.rsvpResponse}</Badge>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                      <td className="py-2 pr-4">
                        {row.attendanceOutcome ? (
                          <Badge tone={outcomeTone(row.attendanceOutcome)}>{row.attendanceOutcome}</Badge>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-slate-900">Invite a Guest</h3>
            <p className="mt-1 text-sm text-slate-600">
              Email delivery is not implemented in this MVP — copy the invitation token below and share it yourself.
            </p>
            <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={handleInviteSubmit}>
              <Field label="Guest email" htmlFor="inviteEmail">
                <input
                  id="inviteEmail"
                  type="email"
                  required
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  className={inputClass}
                />
              </Field>
              <Button type="submit" loading={inviting}>
                Invite
              </Button>
            </form>
            {inviteError && (
              <div className="mt-3">
                <Alert kind="error">{inviteError}</Alert>
              </div>
            )}
            {inviteResult && (
              <div className="mt-4 space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
                <p>
                  <span className="font-medium text-slate-700">Invited:</span> {inviteResult.inviteeEmail}
                </p>
                <div>
                  <p className="font-medium text-slate-700">Invitation token</p>
                  <code className="mt-1 block break-all rounded-md border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800">
                    {inviteResult.invitationToken}
                  </code>
                </div>
                <Button
                  variant="secondary"
                  onClick={() => navigate(`/rsvp?token=${encodeURIComponent(inviteResult.invitationToken)}`)}
                >
                  Open RSVP link
                </Button>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}

function CountTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-center">
      <p className="text-2xl font-semibold text-slate-900">{value}</p>
      <p className="text-xs text-slate-500">{label}</p>
    </div>
  )
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}
