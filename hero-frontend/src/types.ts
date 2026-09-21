export type EventStatus = 'OPEN' | 'CLOSED' | 'CANCELLED'
export type RsvpResponseValue = 'YES' | 'NO' | 'MAYBE'
export type AttendanceOutcomeValue = 'CONFIRMED' | 'WAITLISTED' | 'NONE'

export interface CreateEventRequest {
  title: string
  description: string | null
  eventDateTime: string
  location: string
  maxCapacity: number | null
}

export interface CreateEventResponse {
  id: number
  title: string
  description: string | null
  eventDateTime: string
  location: string
  maxCapacity: number | null
  status: EventStatus
  hostManagementToken: string
}

export interface CreateInvitationResponse {
  id: number
  eventId: number
  inviteeEmail: string
  invitationToken: string
}

export interface InvitationDashboardRow {
  invitationId: number
  inviteeEmail: string
  rsvpResponse: RsvpResponseValue | null
  attendanceOutcome: AttendanceOutcomeValue | null
}

export interface DashboardCounts {
  totalInvited: number
  yesCount: number
  noCount: number
  maybeCount: number
  noResponseCount: number
  confirmedCount: number
  waitlistedCount: number
}

export interface HostDashboardResponse {
  eventId: number
  title: string
  description: string | null
  eventDateTime: string
  location: string
  maxCapacity: number | null
  status: EventStatus
  invitations: InvitationDashboardRow[]
  counts: DashboardCounts
}

export interface RsvpStateResponse {
  invitationId: number
  eventId: number
  response: RsvpResponseValue
  attendanceOutcome: AttendanceOutcomeValue
}

export interface EventLifecycleResponse {
  id: number
  status: EventStatus
  title: string
  eventDateTime: string
}
