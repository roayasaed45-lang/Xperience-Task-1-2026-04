import { useRouter } from '../lib/router'
import { Button, Card } from '../components/ui'

export function Home() {
  const { navigate } = useRouter()

  return (
    <div className="flex flex-col items-center gap-8 text-center">
      <div className="max-w-xl">
        <h1 className="text-3xl font-bold text-slate-900">Event RSVP Manager</h1>
        <p className="mt-3 text-slate-600">
          Create an event, invite guests by email, and let them RSVP Yes, No, or Maybe. Capacity and waitlisting are
          handled automatically.
        </p>
      </div>
      <Card className="w-full max-w-sm">
        <div className="flex flex-col gap-3">
          <Button onClick={() => navigate('/create')}>Create Event</Button>
          <Button variant="secondary" onClick={() => navigate('/host')}>
            Host Dashboard
          </Button>
          <Button variant="secondary" onClick={() => navigate('/rsvp')}>
            RSVP to an Invitation
          </Button>
        </div>
      </Card>
    </div>
  )
}
