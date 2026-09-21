import { useState } from 'react'
import { useRouter } from '../lib/router'
import { ApiError, submitRsvp } from '../services/eventRsvpApi'
import type { RsvpResponseValue, RsvpStateResponse } from '../types'
import { Alert, Badge, Button, Card, Field, inputClass, outcomeTone, responseTone } from '../components/ui'

const OUTCOME_MESSAGES: Record<RsvpStateResponse['attendanceOutcome'], string> = {
  CONFIRMED: "You're confirmed.",
  WAITLISTED: "You're on the waitlist.",
  NONE: 'Your response has been recorded.',
}

export function Rsvp() {
  const { query } = useRouter()
  const [token, setToken] = useState(query.get('token') ?? '')
  const [pending, setPending] = useState<RsvpResponseValue | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<RsvpStateResponse | null>(null)

  const busy = pending !== null

  async function respond(value: RsvpResponseValue) {
    if (!token.trim()) {
      setError('Paste your invitation token first.')
      return
    }
    setPending(value)
    setError(null)
    try {
      const state = await submitRsvp(token.trim(), value)
      setResult(state)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to submit your RSVP.')
    } finally {
      setPending(null)
    }
  }

  return (
    <Card className="mx-auto max-w-lg">
      <h2 className="text-xl font-semibold text-slate-900">RSVP</h2>
      <p className="mt-1 text-sm text-slate-600">Paste the invitation token your host shared with you.</p>

      <div className="mt-4">
        <Field label="Invitation token" htmlFor="invitationToken">
          <input
            id="invitationToken"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            className={`${inputClass} font-mono text-xs`}
            placeholder="Paste your invitation token"
          />
        </Field>
      </div>

      <div className="mt-5 grid grid-cols-3 gap-3">
        <Button variant="secondary" disabled={busy} loading={pending === 'YES'} onClick={() => respond('YES')}>
          Yes
        </Button>
        <Button variant="secondary" disabled={busy} loading={pending === 'MAYBE'} onClick={() => respond('MAYBE')}>
          Maybe
        </Button>
        <Button variant="secondary" disabled={busy} loading={pending === 'NO'} onClick={() => respond('NO')}>
          No
        </Button>
      </div>

      {error && (
        <div className="mt-4">
          <Alert kind="error">{error}</Alert>
        </div>
      )}

      {result && (
        <div className="mt-5 space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
          <div className="flex items-center gap-2">
            <span className="text-slate-600">Your response:</span>
            <Badge tone={responseTone(result.response)}>{result.response}</Badge>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-slate-600">Attendance:</span>
            <Badge tone={outcomeTone(result.attendanceOutcome)}>{result.attendanceOutcome}</Badge>
          </div>
          <p className="font-medium text-slate-800">{OUTCOME_MESSAGES[result.attendanceOutcome]}</p>
          <p className="text-xs text-slate-500">You can change your response above at any time before the event starts.</p>
        </div>
      )}
    </Card>
  )
}
