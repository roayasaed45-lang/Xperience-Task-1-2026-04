// localStorage is used only for host convenience in this MVP (remembering the
// last created event's id/token so the Host Dashboard can prefill its form).
// It is never treated as a security boundary - the host management token is
// still required to actually authorize any request.

const LAST_EVENT_KEY = 'hero.rsvp.lastEvent'

export interface StoredEventSession {
  eventId: number
  hostManagementToken: string
}

export function saveLastEventSession(session: StoredEventSession): void {
  try {
    localStorage.setItem(LAST_EVENT_KEY, JSON.stringify(session))
  } catch {
    // localStorage unavailable (private browsing, etc.) - convenience only, safe to ignore.
  }
}

export function loadLastEventSession(): StoredEventSession | null {
  try {
    const raw = localStorage.getItem(LAST_EVENT_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<StoredEventSession>
    if (typeof parsed.eventId === 'number' && typeof parsed.hostManagementToken === 'string') {
      return { eventId: parsed.eventId, hostManagementToken: parsed.hostManagementToken }
    }
    return null
  } catch {
    return null
  }
}
