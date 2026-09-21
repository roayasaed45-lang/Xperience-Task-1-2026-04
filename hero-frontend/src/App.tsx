import { RouterProvider, useRouter } from './lib/router'
import { Home } from './pages/Home'
import { CreateEvent } from './pages/CreateEvent'
import { HostDashboard } from './pages/HostDashboard'
import { Rsvp } from './pages/Rsvp'

function NavBar() {
  const { path, navigate } = useRouter()

  function linkClass(target: string) {
    return `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
      path === target ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
    }`
  }

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <button onClick={() => navigate('/')} className="text-lg font-semibold text-slate-900">
          Event RSVP Manager
        </button>
        <nav className="flex flex-wrap gap-2">
          <button className={linkClass('/create')} onClick={() => navigate('/create')}>
            Create Event
          </button>
          <button className={linkClass('/host')} onClick={() => navigate('/host')}>
            Host Dashboard
          </button>
          <button className={linkClass('/rsvp')} onClick={() => navigate('/rsvp')}>
            RSVP
          </button>
        </nav>
      </div>
    </header>
  )
}

function Routes() {
  const { path } = useRouter()

  switch (path) {
    case '/create':
      return <CreateEvent />
    case '/host':
      return <HostDashboard />
    case '/rsvp':
      return <Rsvp />
    default:
      return <Home />
  }
}

function App() {
  return (
    <RouterProvider>
      <div className="min-h-screen bg-slate-50">
        <NavBar />
        <main className="mx-auto max-w-4xl px-4 py-8">
          <Routes />
        </main>
      </div>
    </RouterProvider>
  )
}

export default App
