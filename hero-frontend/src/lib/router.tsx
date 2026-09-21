import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

interface RouteState {
  path: string
  query: URLSearchParams
}

interface RouterContextValue extends RouteState {
  navigate: (path: string) => void
}

const RouterContext = createContext<RouterContextValue | null>(null)

function readRoute(): RouteState {
  return {
    path: window.location.pathname,
    query: new URLSearchParams(window.location.search),
  }
}

export function RouterProvider({ children }: { children: ReactNode }) {
  const [route, setRoute] = useState<RouteState>(() => readRoute())

  useEffect(() => {
    const onPopState = () => setRoute(readRoute())
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  function navigate(path: string) {
    window.history.pushState({}, '', path)
    setRoute(readRoute())
  }

  return <RouterContext.Provider value={{ ...route, navigate }}>{children}</RouterContext.Provider>
}

export function useRouter(): RouterContextValue {
  const context = useContext(RouterContext)
  if (!context) {
    throw new Error('useRouter must be used within a RouterProvider')
  }
  return context
}
