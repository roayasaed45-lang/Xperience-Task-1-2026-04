import type {
  CreateEventRequest,
  CreateEventResponse,
  CreateInvitationResponse,
  EventLifecycleResponse,
  HostDashboardResponse,
  RsvpResponseValue,
  RsvpStateResponse,
} from '../types'

// Requests go through Vite's dev/preview proxy at /api (see vite.config.ts),
// which forwards to the backend at http://localhost:8280. This keeps the
// frontend same-origin so no backend CORS configuration is needed.
const API_BASE = '/api'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
  } catch {
    throw new ApiError(0, 'Unable to reach the server. Is the backend running?')
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as { error?: string }
      if (body.error) {
        message = body.error
      }
    } catch {
      // Response body wasn't JSON (or was empty) - keep the generic message.
    }
    throw new ApiError(response.status, message)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function createEvent(payload: CreateEventRequest): Promise<CreateEventResponse> {
  return request<CreateEventResponse>('/events', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createInvitation(
  eventId: number,
  hostManagementToken: string,
  inviteeEmail: string,
): Promise<CreateInvitationResponse> {
  return request<CreateInvitationResponse>(`/events/${eventId}/invitations`, {
    method: 'POST',
    headers: { 'X-Host-Management-Token': hostManagementToken },
    body: JSON.stringify({ inviteeEmail }),
  })
}

export function getDashboard(eventId: number, hostManagementToken: string): Promise<HostDashboardResponse> {
  return request<HostDashboardResponse>(`/events/${eventId}/dashboard`, {
    headers: { 'X-Host-Management-Token': hostManagementToken },
  })
}

export function submitRsvp(invitationToken: string, response: RsvpResponseValue): Promise<RsvpStateResponse> {
  return request<RsvpStateResponse>('/invitations/rsvp', {
    method: 'PUT',
    headers: { 'X-Invitation-Token': invitationToken },
    body: JSON.stringify({ response }),
  })
}

export function closeEvent(eventId: number, hostManagementToken: string): Promise<EventLifecycleResponse> {
  return request<EventLifecycleResponse>(`/events/${eventId}/close`, {
    method: 'PUT',
    headers: { 'X-Host-Management-Token': hostManagementToken },
  })
}

export function cancelEvent(eventId: number, hostManagementToken: string): Promise<EventLifecycleResponse> {
  return request<EventLifecycleResponse>(`/events/${eventId}/cancel`, {
    method: 'PUT',
    headers: { 'X-Host-Management-Token': hostManagementToken },
  })
}
