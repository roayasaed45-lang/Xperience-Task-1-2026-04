import { useState, type FormEvent } from 'react'
import { useRouter } from '../lib/router'
import { ApiError, createEvent } from '../services/eventRsvpApi'
import type { CreateEventResponse } from '../types'
import { saveLastEventSession } from '../lib/storage'
import { Alert, Button, Card, Field, inputClass } from '../components/ui'

export function CreateEvent() {
  const { navigate } = useRouter()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [eventDateTime, setEventDateTime] = useState('')
  const [location, setLocation] = useState('')
  const [maxCapacity, setMaxCapacity] = useState('')

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<CreateEventResponse | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const created = await createEvent({
        title: title.trim(),
        description: description.trim() || null,
        eventDateTime,
        location: location.trim(),
        maxCapacity: maxCapacity.trim() === '' ? null : Number(maxCapacity),
      })
      setResult(created)
      saveLastEventSession({ eventId: created.id, hostManagementToken: created.hostManagementToken })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong creating the event.')
    } finally {
      setSubmitting(false)
    }
  }

  async function copyToken() {
    if (!result) {
      return
    }
    try {
      await navigator.clipboard.writeText(result.hostManagementToken)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard API unavailable - the token is still visible to copy manually.
    }
  }

  if (result) {
    return (
      <Card className="mx-auto max-w-lg">
        <h2 className="text-xl font-semibold text-slate-900">Event created</h2>
        <div className="mt-4 space-y-4 text-sm">
          <p>
            <span className="font-medium text-slate-700">Event ID:</span> {result.id}
          </p>
          <div>
            <p className="font-medium text-slate-700">Host Management Token</p>
            <code className="mt-1 block break-all rounded-md bg-slate-100 px-3 py-2 text-xs text-slate-800">
              {result.hostManagementToken}
            </code>
          </div>
          <Alert kind="info">
            Save this token now — it is the only way to manage this event (invite guests, view the dashboard, close,
            or cancel it) and it will not be shown again.
          </Alert>
        </div>
        <div className="mt-5 flex flex-wrap gap-3">
          <Button variant="secondary" onClick={copyToken}>
            {copied ? 'Copied!' : 'Copy token'}
          </Button>
          <Button
            onClick={() =>
              navigate(`/host?eventId=${result.id}&token=${encodeURIComponent(result.hostManagementToken)}`)
            }
          >
            Continue to Host Dashboard
          </Button>
        </div>
      </Card>
    )
  }

  return (
    <Card className="mx-auto max-w-lg">
      <h2 className="text-xl font-semibold text-slate-900">Create Event</h2>
      <form className="mt-4 flex flex-col gap-4" onSubmit={handleSubmit}>
        <Field label="Title" htmlFor="title">
          <input id="title" required value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </Field>
        <Field label="Description" htmlFor="description">
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={inputClass}
            rows={3}
          />
        </Field>
        <Field label="Date & time" htmlFor="eventDateTime">
          <input
            id="eventDateTime"
            type="datetime-local"
            required
            value={eventDateTime}
            onChange={(e) => setEventDateTime(e.target.value)}
            className={inputClass}
          />
        </Field>
        <Field label="Location" htmlFor="location">
          <input
            id="location"
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            className={inputClass}
          />
        </Field>
        <Field label="Max capacity" htmlFor="maxCapacity" hint="Leave blank for unlimited capacity">
          <input
            id="maxCapacity"
            type="number"
            min={0}
            value={maxCapacity}
            onChange={(e) => setMaxCapacity(e.target.value)}
            className={inputClass}
          />
        </Field>
        {error && <Alert kind="error">{error}</Alert>}
        <Button type="submit" loading={submitting}>
          Create Event
        </Button>
      </form>
    </Card>
  )
}
