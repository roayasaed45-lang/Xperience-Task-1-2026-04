# Event RSVP Manager — Design

## 1. Problem Statement

Event hosts need a way to understand invitees' attendance intentions and manage limited event capacity, while invitees need a simple way to record and update that intention before the event happens.

**Success Conditions:**
- Hosts can reliably know who is attending, not attending, or undecided at any point before the event.
- Invitees can update their RSVP before the event starts while the event remains OPEN to responses. Once CLOSED or CANCELLED, no new RSVP or change to an existing RSVP is accepted (Q11/Q14, Cancel behavior resolved — see Section 4).
- Capacity limits (when set) are respected — confirmed attendees never exceed max-capacity.
- Invitees who cannot be confirmed due to capacity are placed on a waitlist rather than turned away.
- Waitlisted invitees are automatically confirmed when a confirmed attendee's status changes to "No."
- After the event's start time, no further RSVP changes are possible.

---

## 2. Goals and Non-Goals

### Goals
1. A user can create an event with title, description, date/time, location, and optional max-capacity; the creator becomes that event's host.
2. The host can invite people by email; each invitee can respond Yes / No / Maybe via a unique link.
3. An invitee can change their RSVP at any point before the event starts, provided the event is still OPEN — CLOSED and CANCELLED both block changes to an existing RSVP as well as new submissions (Q11/Q14, Cancel behavior resolved — see Section 4).
4. When max-capacity is reached, new "Yes" RSVPs go to a waitlist instead of being confirmed.
5. A waitlisted invitee is automatically promoted to confirmed when a confirmed attendee changes their RSVP to "No."
6. The host has visibility into attendance via counts and an attendee list.
7. The host can cancel the event or close it to further responses at any time; after the event's start time, all RSVPs are locked.

### Non-Goals
1. **Recurring or multi-occurrence events** — the brief only describes a single event with one date/time.
2. **Public/self-service RSVP by uninvited people** — the brief frames attendance as invite-by-email only.
3. **Notifications/reminders beyond the initial invite** — only "invite people by email" is stated.
4. **Payment/ticketing and advanced analytics/reporting** — entirely absent from the brief.

*Multiple hosts / co-host support is intentionally **not** listed as a Non-Goal here — see Q4 in Section 4/15. It was previously listed as a Non-Goal while Q4 remained open; that contradiction has been corrected by treating this as an open question until confirmed.*

*Editing event details after creation is also intentionally not listed as a Non-Goal — see Section 4/15.*

---

## 3. Context and Constraints

### Technical Constraints
- Backend: Java 17, Spring Boot 4.0.5, Spring MVC, Spring Data JPA, Maven (`mvnw`).
- PostgreSQL is the only configured datasource; Hibernate `ddl-auto: update`; database `hero`, schema `hero`.
- Backend server fixed to port `8280`; frontend Vite dev server fixed to port `5171`.
- **React version discrepancy:** the README states React 19, but `package.json` currently pins React 18.3.1 — unresolved (see Section 15).
- No authentication library is present in the backend.
- The existing scaffold already contains an **unrelated** bulk-messaging feature (frontend components `FileUpload`, `MessageComposer`, `RecipientTable`, `ResultsTable`, `bulkSendApi.ts`; backend `wasender` config in `application.yml`) — not RSVP-related; intent for its retention/removal is unresolved.
- No entities, repositories, or controllers exist yet for the RSVP feature.

### Product Constraints
- The event creator becomes the host. Invitations are sent by email. Invitees respond via a unique link. RSVP choices are Yes / No / Maybe. Max-capacity is optional. "Yes" beyond capacity is waitlisted. Waitlisted attendees may be automatically promoted. Hosts may close responses or cancel the event at any time. Invitees may change their RSVP only before the event starts. All RSVPs are locked after the event starts.

### Operational / Process Constraints
- Local PostgreSQL expected at `localhost:5432`; credentials hardcoded (`postgres`/`1234`) in `application.yml`.
- `start.ps1` is Windows/PowerShell-only; no macOS/Linux equivalent exists in the repo.
- This is Xperience Task 01; `DESIGN.md` is the required deliverable; the 18-step guide is followed in order; design and implementation are committed separately; implementation is a stretch goal after design.
- Security/compliance, tenant model, backward compatibility, rollout, and performance expectations are **not specified** by the task.

---

## 4. Facts, Assumptions, and Open Questions

### Facts
All ten behavioral facts from the README (event creation fields, host assignment, email invitation, unique link with Yes/No/Maybe, live dashboard, capacity/waitlist, automatic promotion on confirmed→No, host close/cancel, invitee change before start, lock after start) plus the repository facts listed in Section 3.

### Working Assumptions

| ID | Assumption | Why needed | If false |
|---|---|---|---|
| A4 | Each event has exactly one host | Needed for ownership model | Ownership/authorization model changes (Q4) |
| A8 | The RSVP design will not depend on or reuse the unrelated bulk-messaging / `wasender` code unless repository intent is clarified | Needed to scope the feature without presuming what happens to unrelated code | N/A — this is a non-dependence stance, not a prediction; repository cleanup/removal itself remains unresolved |

**Note on former A5:** the working assumption "Close and Cancel are permanent, one-way transitions" has been **superseded** by the resolved decision below (Q6 — Reopen) and is no longer a separate open assumption. It is now a settled rule: `OPEN → CLOSED` and `OPEN → CANCELLED` are the only transitions; there is no reverse transition in this first-pass MVP.

**Note on former A6:** the working assumption "Waitlist ordering is FIFO" has been **superseded** by the resolved decision below (Q7 — Waitlist Ordering) and is no longer a separate open assumption. It is now a settled rule, with a concrete persisted mechanism (`waitlistedAt`), not a bare assumption.

**Note on former A1:** the working assumption "Host is some identifiable entity with authorization rights over their event" has been **superseded** by the resolved decision below (Q3) and is no longer a separate open assumption.

**Note on former A2:** the working assumption "Only 'Yes' counts toward max-capacity, not 'Maybe'" has been **superseded** by the resolved decision below (Q1) and is no longer a separate open assumption — it is now a settled rule, not an assumption.

**Note on former A3:** the working assumption "Unique link is the sole invitee identity mechanism" has been **superseded** by the resolved decision below (Q2) and is no longer a separate open assumption. The resolved model is more precise than A3 was: specifically the raw Invitation token (not merely "the link") is the bearer credential, matched against a stored hash.

**Note on former A7:** the working assumption "One invitee email = at most one RSVP per event" has been **superseded** by the resolved decision below (Q8) and is no longer a separate open assumption. The resolved rule is more precise than A7 was: uniqueness is per Event and per *normalized* email, not a bare assumption about "one RSVP."

### Resolved Decisions

**Q3 — Host authentication/identity: Host Management Token**

- The event creator becomes the host (unchanged from the original brief).
- When an Event is created, the backend generates a cryptographically random, unguessable **host management token**, stored alongside the event.
- This token is the sole mechanism identifying/authorizing the host for that event: any request attempting a host-only operation (invite, dashboard, close, cancel) must present this token, and the backend verifies it against the stored value for that event before proceeding (I9 enforcement).
- Event creation and host management token generation are **one transactional operation** (I8) — an event is never created without a token, and a token is never generated without an event.
- The token must **never** be accepted as an arbitrary client-supplied or client-invented value — only a backend-generated token, matched against the stored value, is valid. A client cannot choose or invent its own token and have it accepted.
- No User entity, username/password login, JWT, or Spring Security setup is introduced by this decision. This is explicitly a **first-pass authorization mechanism**, not a full user-account system — it establishes "whoever holds this token may manage this event," not a verified real-world identity.
- The host management token is a **distinct concept** from the invitee's unique invitation token (I7): the host token authorizes management of an entire event; an invitation token scopes exactly one invitee's RSVP access to that event. The two must never be confused or interchangeable.
- **Not addressed by this decision** (open limitations of this first-pass model): token recovery if lost, token rotation/expiry, and whether an event may have more than one host holding a valid token (Q4, still separately unresolved).

**Attendance Outcome ownership: belongs to the Invitation**

- **Invitation → RSVP Response concept → Attendance Outcome concept.** Both RSVP Response and Attendance Outcome are scoped to, and persisted in association with, exactly one Invitation.
- **RSVP Response and Attendance Outcome are sibling state concepts under the same Invitation — not parent/child concepts.** Attendance Outcome is not owned by, attached to, or keyed off any specific RsvpResponse row; it does not reference RsvpResponse at all.
- Rationale: RSVP Response represents the invitee's stated choice (Yes/No/Maybe). Attendance Outcome represents the system-derived current attendance state for that invitee (Confirmed/Waitlisted/None). The Outcome is derived from the current Response plus capacity state, but that derivation is a computation relationship, not a storage/ownership relationship — Attendance Outcome is not conceptually attached to one specific RsvpResponse row.
- This keeps RSVP intent and system attendance state structurally separate (I6), while both remain anchored to the same Invitation as their common owner.
- The current Attendance Outcome belongs to the Invitation directly and can change independently as capacity/promotion decisions occur — it does not "follow" any particular RSVP Response value beyond being recomputed from whatever the current Response is.
- **Not resolved by this decision:** Q7 (waitlist ordering), Q13 (promotion trigger scope). (Q1 and Q2, referenced in this entry's original text, are resolved separately — see the end of this section.)

**RSVP Response / Attendance Outcome current-state persistence model: one row per Invitation, update-in-place**

- For this first-pass implementation, each Invitation has **exactly one current RSVP Response row** and **exactly one current Attendance Outcome row**.
- RsvpResponse is **current-state only, not history** — an RSVP change updates the existing row in place; no historical rows are appended.
- AttendanceOutcome is **current-state only, not history** — an Attendance Outcome change (e.g., via promotion) updates the existing row in place; no historical rows are appended.
- **Enforced at the database level:** `UNIQUE(invitation_id)` on `rsvp_responses`, and `UNIQUE(invitation_id)` on `attendance_outcomes` — mirroring how Invitation uniqueness (I13) is enforced by a database constraint rather than by application logic alone.
- **Rationale:** removes ambiguity for dashboard/current-state reads, makes RSVP changes deterministic, keeps counts and future capacity logic based on one authoritative current row per Invitation, and avoids inventing history/audit semantics the product does not require.
- **Historical/audit tracking** (e.g., a log of every RSVP change over time) is explicitly **out of scope** for this first pass. If needed later, it would be a separate, additive concept — not a retrofit of RsvpResponse/AttendanceOutcome into an event log.
- **Not resolved by this decision:** Q7, Q13. This decision settles the *persistence shape* (one current row, enforced uniquely) — not the *business rules* that decide what value that row should hold (ordering, promotion triggers). (Q1 and Q2 are resolved separately — see the end of this section.)

**Q8 — Duplicate invitations to the same email: one Invitation per Event per normalized email**

- There may be only one Invitation per Event for the same **normalized** invitee email.
- **Normalization for this first-pass implementation:** trim surrounding whitespace, then lowercase using a locale-independent approach (e.g., `" Guest@Example.com "` → `"guest@example.com"`).
- A host may not create a second Invitation for the same normalized email within the same Event — a duplicate attempt must be **rejected**, not silently merged, and must not create another Invitation.
- The existing Invitation's token must **not** be rotated, regenerated, or overwritten as a side effect of a rejected duplicate attempt — the original invitation/token relationship (I7) is preserved untouched.
- Invitations for the same email across **different** Events remain allowed — this uniqueness rule is scoped per Event, not global.
- **Rationale:** prevents duplicate RSVP identities for the same invitee within one event, avoids duplicate dashboard rows and capacity-counting ambiguity, preserves the existing invitation token relationship, and avoids introducing any token rotation/recovery behavior (which remains unaddressed, per Q9).
- **Not resolved by this decision:** Q7, Q9 (token expiry), Q13. Email format validation beyond trim+lowercase normalization is not addressed. (Q1 and Q2 are resolved separately — see the end of this section.)

**I13 concurrency-enforcement mechanism: database UNIQUE constraint**

- Invitation uniqueness (I13, Q8) is enforced **authoritatively by a database UNIQUE constraint** on `(event_id, invitee_email)`, where `invitee_email` is already normalized (trim + locale-independent lowercase) before persistence.
- The application service **may** perform a pre-check for an existing Invitation before attempting the insert, purely to provide a clearer, friendlier duplicate-invitation error to the caller — but this pre-check is **not** the authoritative enforcement; it is a best-effort convenience only.
- **The database constraint is what actually prevents the violation.** If two concurrent requests both pass the service pre-check (a real possibility, since the pre-check and the insert are not atomic with each other), only one INSERT may succeed; the constraint rejects the second.
- The losing request's constraint violation must be translated into a duplicate-invitation **application error** — not surfaced as a raw database exception, and not silently swallowed.
- **Explicitly not used:** relying on check-then-insert alone (insufficient under concurrency, as the pre-existing "Duplicate Invitation Race" analysis showed); a global lock; per-event pessimistic locking introduced specifically for this invariant (the per-event locking used elsewhere for capacity, per Section 10's First-Pass Concurrency Decision, is a separate mechanism for a separate invariant and is not extended to cover I13).
- **Not resolved by this decision:** Q7, Q9, Q13, Q14 — untouched. The exact application-error type/shape returned to the caller on a constraint violation is an implementation-time detail, not decided here. (Q1 and Q2 are resolved separately — see below.)

**Q1 — MAYBE and capacity: only YES participates in capacity evaluation**

- **MAYBE does not consume event capacity.**
- **MAYBE maps to Attendance Outcome NONE.**
- **NO maps to Attendance Outcome NONE.**
- **YES is the only RSVP value that participates in capacity evaluation.** A YES may become CONFIRMED or WAITLISTED depending on available capacity at the moment it is evaluated (I1).
- This directly settles I1's "reached" threshold: max-capacity is compared only against the count of CONFIRMED outcomes, which can only ever originate from a YES response.
- **Not resolved by this decision:** Q7 (waitlist ordering), Q13 (promotion trigger scope — e.g., whether a Yes→Maybe transition on a previously-Confirmed invitee should free a spot the same way Yes→No does remains open). This decision settles what a *fresh* Yes/No/Maybe evaluates to, not every transition's side effects.

**Q2 — Invitee identity/scoping: the raw Invitation token is the first-pass bearer credential**

- **The raw Invitation token acts as the first-pass bearer credential for the invitee.** Whoever presents the correct raw token for a given Invitation is treated as that invitee — a possession-based model, the same first-pass shape as the Host Management Token (Q3), not a verified real-world identity.
- **Possession of a valid Invitation token authorizes access only to that Invitation and its Event/RSVP state** (I10) — never to another Invitation, another invitee's RSVP, or another Event.
- **No invitee account, login, password, JWT, or Spring Security is introduced.** This mirrors the Host Management Token decision's explicit scope limits.
- **The raw token is never persisted; its hash is matched against the stored `invitationTokenHash`** — the same verification shape as `HostTokenService`, using `InvitationTokenService` (already implemented), which shares no code with the host-token mechanism.
- **Invitee operations must never affect another Invitation, Event, or RSVP** — this is I10, now enforceable: the presented token is verified against the specific Invitation being acted on, exactly as the host token is verified against the specific Event being acted on.
- **Not addressed by this decision** (first-pass limitations, consistent with the Host Management Token's own limitations): token recovery if lost, token rotation, and token expiry (Q9, still separately unresolved). Whether an invitee should ever be offered a stronger identity model (a real account) instead of link/token possession is not revisited — this decision only settles that, for the first pass, token possession is sufficient.

**I2 first-pass time rule: server clock vs. stored `eventDateTime`**

- **RSVP create/change is allowed only while the current server time is strictly before `Event.eventDateTime`.**
- **At or after `Event.eventDateTime`, RSVP is locked** — this matches I2's existing statement ("no RSVP may be created or changed after start time"), now made precise and implementable.
- **The server/application clock is used for this first-pass implementation** — no client-supplied time is ever trusted (consistent with Section 9's "frontend state is never authoritative" principle).
- **No timezone conversion logic is introduced beyond the existing `LocalDateTime` model** — the comparison is a direct comparison between the application's current `LocalDateTime` and the stored `eventDateTime`, using whatever zone the running server/JVM is in. This is a deliberately minimal first-pass rule, not a timezone-correct one.
- **Q12 remains open** as a broader timezone/product issue (e.g., what timezone a host intended when entering a date/time, whether invitees in different timezones should see a different lock moment) — but Q12 does **not** block this first-pass server-clock rule from being implemented and enforced now. Q12's eventual resolution may later refine *how* `eventDateTime` is interpreted, without necessarily changing the comparison mechanism itself.

**Q7 — Waitlist ordering: FIFO via a persisted `waitlistedAt` timestamp**

- **Waitlist ordering is FIFO:** the first Invitation to enter WAITLISTED state is the first eligible for promotion.
- **Mechanism (first-pass):** `AttendanceOutcome` gains a `waitlistedAt` timestamp field.
  - `waitlistedAt` is **set** only at the moment an Invitation's Attendance Outcome **transitions into WAITLISTED**.
  - `waitlistedAt` is **not reset** while the Invitation remains continuously WAITLISTED (e.g., an unrelated resubmission that keeps the outcome WAITLISTED must not refresh this timestamp — that would break FIFO fairness).
  - `waitlistedAt` is **cleared** (set to null) whenever the Invitation **leaves** WAITLISTED (i.e., becomes CONFIRMED via promotion, or becomes NONE via a response change away from Yes).
- **Promotion order:** ascending `waitlistedAt` (earliest first). **Tie-breaker:** ascending Invitation `id` if two `waitlistedAt` values are equal — deterministic, never arbitrary.
- **Rationale:** FIFO is the simplest, most defensible reading of the brief's "automatically promoted" language, and a persisted timestamp (rather than a derived "entry order" from row-insertion order) is necessary now that Attendance Outcome rows are update-in-place, not append-only (Section 4/8) — row-insertion order can no longer imply waitlist order once a row can flip between states repeatedly.
- **Not resolved by this decision alone:** the promotion *trigger* scope is Q13, resolved separately immediately below. This decision settles only *which* waitlisted Invitation is selected once a promotion is triggered, not *when* a promotion is triggered.
- This supersedes working assumption A6 (see Section 4 note above) — FIFO is no longer an assumption, and now has a concrete persisted enforcement mechanism.

**Q13 — Promotion trigger scope: automatic promotion whenever a CONFIRMED spot frees up before event start**

- **Trigger condition:** whenever an Invitation's Attendance Outcome **transitions away from CONFIRMED** while the event has not yet started, and the waitlist is non-empty, the system automatically promotes the first eligible WAITLISTED Invitation (per the Q7 FIFO ordering) to CONFIRMED.
- **This explicitly includes both:**
  - CONFIRMED → NONE (i.e., a Yes → No response change), and
  - CONFIRMED → NONE (i.e., a Yes → Maybe response change) — both a Yes→No and a Yes→Maybe transition leave the Attendance Outcome at NONE and free the same CONFIRMED spot, so both trigger promotion identically. There is no behavioral distinction between the two triggers; the invariant only cares that a CONFIRMED spot was vacated.
- **Promotion occurs inside the same serialized per-event transaction boundary already used for the capacity decision** (Section 10, First-Pass Concurrency Decision) — it is not a separate, independently-locked operation. The response change that frees the spot and the resulting promotion (if any) are part of one atomic unit of work under the same per-event pessimistic lock.
- **Promotion after event start is explicitly not implemented.** Once the event has started (I2's lock-after-start condition is met), no RSVP change can occur at all (I2 already blocks this), so no freed-spot trigger can occur post-start either — this is a direct consequence of I2, not a new rule, but is stated explicitly here to close the gap the original Q13 phrasing left open.
- **Rationale:** "whenever a CONFIRMED spot becomes available" is the most literal, defensible reading of the brief's "automatically confirmed when a confirmed attendee's status changes to No" — extended consistently to Yes→Maybe, since both produce the identical system-level effect (a CONFIRMED spot becoming NONE) and treating them differently would be an invented, unsupported distinction.
- **Not resolved by this decision:** any promotion behavior tied to Close/Cancel (see the Close/Cancel decisions below, which explicitly state no promotion occurs when RSVP changes are blocked); Q9 (token expiry); the broader Q12 timezone question.

**Q11/Q14 — Event Close behavior: RSVP activity fully closed**

- **CLOSED means RSVP activity is fully closed**, resolving both Q11 (Cancelled-vs-Closed distinction, as it pertains to Close) and Q14 (does "closed" block only new RSVPs or also changes to existing ones) for the Close case:
  - **No new RSVP may be created** on a CLOSED event (this was already I4's existing statement, now explicitly reconfirmed).
  - **No existing RSVP may be changed** on a CLOSED event (this resolves Q14: Close blocks both new submissions and changes to existing RSVPs — there is no narrower "new-only" scope).
  - **No waitlist promotion is triggered by invitee RSVP changes on a CLOSED event**, as a direct consequence of the above: since no RSVP change of any kind is permitted once CLOSED, the Q13 promotion trigger (which fires only as a consequence of a permitted RSVP change) simply cannot occur on a CLOSED event. This is not a separate rule — it falls out of blocking the RSVP change itself.
  - **Existing RSVP/Attendance Outcome data remains fully readable** — Close affects only write access to RSVP state, never read access (dashboard, invitee's own view of their existing response).
- **Rationale:** "closed to further responses" is read as closing off the entire RSVP write surface for that event, not merely gating new invitees — this is the more conservative, unambiguous reading, and avoids the previously-open inconsistency where a Closed event could still silently accept RSVP *changes* while rejecting new ones.

**Cancel behavior: RSVP activity fully closed, with existing state preserved for visibility**

- **CANCELLED means:**
  - **No RSVP create/change** — identical write-blocking scope to CLOSED, above.
  - **No waitlist promotion** — same reasoning as CLOSED: since no RSVP change is permitted, the Q13 promotion trigger cannot fire.
  - **The host dashboard remains readable** for a CANCELLED event — visibility is never removed by cancellation.
  - **Existing stored RSVP/Attendance Outcome state is preserved exactly as it was at the moment of cancellation** — cancelling an event does not clear, reset, or mutate any invitee's Response or Attendance Outcome; it only blocks further writes.
- **This resolves I5 (Cancelled state must remain distinguishable from Closed) at the write-behavior level: CLOSED and CANCELLED now enforce the identical RSVP-write-blocking behavior.** The two statuses remain **distinct stored values** (Section 6/8's single-status-enum representation is unchanged — CLOSED and CANCELLED are never collapsed into one flag), but as of this decision they do not yet differ in *behavioral effect* on RSVP writes. Whether they should ever diverge in effect (e.g., a CANCELLED event hiding itself from some future listing, or being treated differently in some notification the brief does not describe) is not addressed here and is not invented — this decision resolves only the RSVP-write/promotion/visibility behavior explicitly asked about.
- **Rationale:** the brief provides no basis for inventing a behavioral difference between Close and Cancel beyond "cancel the event" vs. "close it to further responses" — both, read conservatively, mean invitees can no longer act on their RSVP; preserving existing data for visibility avoids destructive behavior the brief never asked for.

**Q6 — Reopen: out of scope for the first-pass MVP; no reverse transition**

- **Reopening a CLOSED or CANCELLED event is out of scope for this first-pass MVP.**
- **The only valid Event lifecycle transitions are `OPEN → CLOSED` and `OPEN → CANCELLED`.** There is no `CLOSED → OPEN`, `CANCELLED → OPEN`, or any other reverse/lateral transition.
- This makes former working assumption A5 ("Close and Cancel are permanent, one-way transitions") a settled rule rather than an assumption (see Section 4 note above).
- **Rationale:** the brief never describes a reopen workflow; inventing one (and its interaction with already-promoted waitlist entries, already-locked RSVPs, etc.) would introduce substantial undescribed complexity for no stated requirement. If reopening is ever needed, it is a distinct, additive future decision — not assumed here.
- **Not resolved by this decision:** whether a *new* event could be created to replace a cancelled one (not the same as reopening); this is out of scope and not implied.

### Open Questions

| ID | Question | Why it matters |
|---|---|---|
| Q4 | Can an event have more than one host? | Affects ownership and authorization; blocks the final ownership/scope model |
| Q9 | Do invitation links expire? | Affects link security lifetime |
| Q12 | What timezone rules apply to event start time? | Affects exactly when lock-after-start (I2) triggers |
| — | React 19 (README) vs. React 18.3.1 (`package.json`) — which is authoritative? | Affects frontend implementation target |
| — | What is the intent behind the existing unrelated bulk-messaging/`wasender` code? | Affects repository cleanup scope |
| — | Is invitation delivery synchronous or asynchronous? | Affects host-facing latency and delivery reliability design |
| — | Is editing event details after creation permitted? | Affects event-editing scope, if ever requested |

None of these are resolved in this document. (Q3, Q1, Q2, Q8 were previously listed here and are resolved — see "Resolved Decisions" above. Q6, Q7, Q11, Q13, Q14 have also since been resolved — see "Resolved Decisions" above and Section 15 for traceability.)

---

## 5. Actors and Workflows

### Actors

**Host** — creates and owns an event; wants to know who is coming and retain control over the response window. May invite people, view the attendance dashboard, close responses, and cancel the event. Host authorization for these host-only operations is established via a backend-generated **host management token** (Q3 resolved — see Section 4); this is a first-pass mechanism, not a full account system. Whether an event may have more than one host is unresolved (Q4).

**Invitee** — receives a unique link; wants to indicate attendance and change their mind before the event starts. May open the link and respond Yes/No/Maybe, and change that response before start. Access is via the raw Invitation token embedded in the link, which acts as a first-pass bearer credential scoping the invitee to exactly that Invitation (Q2 resolved — see Section 4); no account/login is introduced.

No other human actors exist. Capacity evaluation, waitlist promotion, and RSVP locking are automatic **system** behavior, not actors — see the Actor/Initiator column below.

### Workflows

| Workflow | Actor/Initiator | Trigger | Preconditions | Major Steps | State Changes | Dependencies | Failure/Blocked Path | Related Open Items |
|---|---|---|---|---|---|---|---|---|
| Event creation | Host | Host decides to create an event | None stated | Provide fields → submit | Event exists; creator = host; a host management token is generated and stored in the same transaction (I8, Q3 resolved) | None (entry point) | Missing/invalid fields not specified | Q4 |
| Invitation | Host | Host invites by email | Event exists; valid host management token for this event presented; no existing Invitation for the same normalized email on this Event (Q8 resolved) | Normalize email → optional service pre-check → attempt insert → database UNIQUE constraint on (event_id, invitee_email) authoritatively rejects a duplicate → unique link created only on success | New Invitation exists, unanswered (on success); no state change on a rejected duplicate | Depends on Event creation | Invite-after-close not specified; duplicate invite is now specified: rejected via DB constraint, surfaced as a duplicate-invitation application error (Q8/I13 resolved) | — |
| RSVP submission | Invitee | Invitee presents Invitation token, responds | Valid Invitation token (Q2 resolved); event OPEN (not CLOSED/CANCELLED — Q11/Q14 resolved); current server time strictly before eventDateTime (I2, resolved first-pass rule) | Select Yes/No/Maybe → submit | Response set; if Yes, triggers Capacity Evaluation (Q1 resolved: only Yes participates); if No/Maybe, Attendance Outcome set to NONE, freeing a spot and triggering Waitlist Promotion if the invitee was previously Confirmed (Q13 resolved) | Depends on Invitation | Blocked if locked/closed/cancelled (Q11/Q14 resolved: CLOSED and CANCELLED both block new submissions) | — |
| RSVP change | Invitee | Invitee revisits link, changes response | Event OPEN (not CLOSED/CANCELLED — Q11/Q14 resolved); event not started | Select new response → submit | Response changes; may trigger Capacity Evaluation or Waitlist Promotion (Q13 resolved: any CONFIRMED→NONE transition, i.e. Yes→No or Yes→Maybe, triggers promotion) | Depends on prior RSVP submission | Blocked after start (I2); blocked on CLOSED/CANCELLED events (Q11/Q14 resolved — changes to existing RSVPs are blocked too, not just new submissions) | — |
| Dashboard | Host | Host opens dashboard | Valid host management token for this event presented | View counts + attendee list | None (read-only) | Depends on Event creation; reflects RSVP/Promotion state | Readable regardless of event status, including CANCELLED (Cancel decision resolved — dashboard remains readable, existing data preserved) | "Live" definition unresolved |
| Close | Host | Host closes responses | Event exists; valid host management token for this event presented; Event currently OPEN (Q6 resolved: no transition out of CLOSED) | Issue close action | Event enters CLOSED: no new RSVP, no change to existing RSVP, no waitlist promotion triggered by invitee changes (all blocked at the source) — read access unaffected (Q11/Q14 resolved) | Independent; feeds into RSVP-blocked path | Not reversible — no CLOSED→OPEN transition (Q6 resolved) | — |
| Cancel | Host | Host cancels the event | Event exists; valid host management token for this event presented; Event currently OPEN (Q6 resolved: no transition out of CANCELLED) | Issue cancel action | Event enters CANCELLED: no new RSVP, no change to existing RSVP, no waitlist promotion — existing RSVP/Attendance data preserved and dashboard remains readable (Cancel decision resolved) | Independent; feeds into RSVP-blocked path | Not reversible — no CANCELLED→OPEN transition (Q6 resolved) | — |
| Capacity Evaluation | **System** | Response becomes "Yes" (occurs inside RSVP submission/change processing, not a separate entry point) — **only Yes triggers this; No/Maybe never do (Q1 resolved)** | Max-capacity set; Event OPEN (CLOSED/CANCELLED already block the triggering RSVP change itself) | Compare confirmed count to capacity, inside the per-event serialized transaction boundary | Confirmed or Waitlisted (Attendance Outcome's `waitlistedAt` set on transition into WAITLISTED — Q7 resolved) | Internal consequence of RSVP submission/change | None stated | — |
| Waitlist Promotion | **System** | An Invitation's Attendance Outcome transitions away from CONFIRMED (Yes→No or Yes→Maybe) before event start (occurs inside RSVP change processing, same serialized per-event transaction boundary as Capacity Evaluation — Q13 resolved) | Waitlist non-empty; event not started; event OPEN (promotion never triggers on CLOSED/CANCELLED since the triggering RSVP change is itself blocked there) | Select the WAITLISTED Invitation with the earliest `waitlistedAt` (Invitation id as tie-breaker) and promote it to CONFIRMED; clear its `waitlistedAt` (Q7/Q13 resolved) | One invitee becomes Confirmed; its `waitlistedAt` cleared | Internal consequence of RSVP change | Never triggers after event start (I2 already blocks the triggering change); never triggers on CLOSED/CANCELLED events | — |
| Lock-after-start evaluation | **System** | Evaluated at the moment of each RSVP attempt (not a proactive background process) | Event has a start time | **Compare current server time to `eventDateTime`: allowed only if strictly before (I2 resolved first-pass rule)** | Event treated as locked if current server time is at or after `eventDateTime` (derived, not stored) | Internal consequence, evaluated within RSVP submission/change processing | Produces the RSVP-blocked path | Q12 (broader timezone question; does not block this first-pass rule) |
| RSVP attempt after start | Invitee | Invitee attempts change post-start | Current server time at or after `eventDateTime` | Attempt rejected | None | Depends on lock condition | Expected result: rejected | Q12 (broader timezone question; does not block this first-pass rule) |
| RSVP attempt after close | Invitee | Invitee attempts response (new or change) after close | Event CLOSED | Attempt blocked | None | Depends on Close | Expected result: blocked — resolved to cover both new submissions and changes to existing RSVPs (Q11/Q14 resolved) | — |
| RSVP attempt on cancelled event | Invitee | Invitee attempts response (new or change) after cancel | Event CANCELLED | Attempt blocked | None | Depends on Cancel | Expected result: blocked — identical write-blocking scope to Close; existing data remains readable (Cancel decision resolved) | — |
| Invalid/unusable link | External requester / Invitee | Anyone opens a malformed/unrecognized link | N/A | Not specified | Not specified | N/A | Not fully specified | Q9 |

No new human actor is introduced by this table — "System" denotes automatic backend behavior triggered as a consequence of an existing request, not an independent actor.

---

## 6. Invariants

### Business Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I1 Capacity | Confirmed attendees never exceed max-capacity | New Yes accepted as confirmed while full | Concurrent/near-full Yes submissions | RSVP submission/change, Capacity Evaluation | Capacity check before confirming; only Yes participates — No/Maybe map directly to Attendance Outcome NONE and never enter this check (Resolved Decision — Section 4) | Fact + Resolved Decision (Q1 resolved — Section 4) |
| I2 Lock-After-Start | No RSVP may be created/changed after start time | RSVP accepted at/after start | Submission near the time boundary | RSVP submission/change, Lock-after-start evaluation | First-pass rule (Resolved Decision — Section 4): allowed only while current server time is strictly before `eventDateTime`, using the server/application clock and the existing `LocalDateTime` model, no timezone conversion | Fact + Resolved Decision (first-pass server-clock rule — Section 4); broader timezone semantics remain Open Question (Q12) |
| I3 Waitlist Promotion | Any CONFIRMED→NONE transition (Yes→No or Yes→Maybe) with a non-empty waitlist must promote exactly one invitee, before event start | Spot frees up, nobody/multiple promoted, or wrong invitee selected | Confirmed attendee changes to No or Maybe | RSVP change, Waitlist Promotion | Automatic follow-up action inside the same serialized per-event transaction boundary as the capacity decision; selects the WAITLISTED invitee with the earliest `waitlistedAt` (Invitation id tie-breaker); promotion updates the promoted invitee's single current AttendanceOutcome row in place and clears its `waitlistedAt` (Resolved Decisions — Section 4), never appends a new row | Fact + Resolved Decisions (Q7 ordering, Q13 trigger scope — Section 4) |
| I4 Closed Responses | Once CLOSED, no RSVP may be created or changed, and no waitlist promotion is triggered by invitee action | New RSVP accepted, or an existing RSVP changed, after close | Submission or change attempted after Close | Close, RSVP attempt after close | State check before accepting any RSVP create/change; a blocked change also prevents its Q13 promotion trigger from ever firing | Fact + Resolved Decision (Q11/Q14 — Section 4) |
| I5 Cancelled Event | Cancelled state must remain a distinct stored value from Closed; both enforce identical RSVP-write/promotion blocking, but existing data stays readable and unmutated | Both modeled as one flag, or cancellation destructively alters stored RSVP/Outcome data | Naive single-flag implementation, or an implementation that clears data on cancel | Cancel, Close, RSVP attempt on cancelled event | CLOSED and CANCELLED remain separate enum values (Section 6/8); Cancel blocks RSVP create/change and promotion identically to Close, but never mutates existing Response/Outcome rows, and dashboard reads remain unaffected | Fact + Resolved Decision (Cancel behavior — Section 4) |

### Data Integrity Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I6 RSVP Value | Response must be exactly Yes/No/Maybe, distinct from Attendance Outcome | Value outside the set, or Response/Outcome conflated | Unrestricted input, or missing separation | RSVP submission/change, Capacity Evaluation | Input constraint + conceptual separation; both concepts are sibling state scoped to the same Invitation, not parent/child (Resolved Decision — Section 4). Mapping resolved (Q1): No/Maybe → Outcome NONE directly; only Yes enters Capacity Evaluation | Fact + Resolved Decision (Attendance Outcome ownership, and Q1 mapping — Section 4) |
| I7 Invitation Scope | A link must correspond to exactly the correct invitation/event | Link resolves to wrong invitee/event | Weak binding | Invitation, RSVP submission/change, invalid link | Not specified — mechanism unresolved | Fact + Open Question (Q9) |
| I8 Host/Event Ownership | Every event has its creator recorded as host | Event with no/incorrect host, or an event with no host management token | Creation path skips assignment or token generation | Event creation | Host assignment and host management token generation occur in the same transaction as event creation | Fact + Resolved Decision (Host Management Token — Section 4) |
| I13 Invitation Uniqueness | An Event must not have more than one Invitation for the same normalized invitee email | Two Invitations exist for the same Event with the same normalized email | Concurrent or repeated invite attempts for the same email on the same Event | Invitation | Authoritatively enforced by a database UNIQUE constraint on (event_id, invitee_email), with invitee_email normalized (trim + locale-independent lowercase) before persistence; a service-layer pre-check provides a clearer error but is not itself the enforcement mechanism; existing Invitation/token must not be rotated or overwritten | Fact + Resolved Decision (Q8 policy + concurrency mechanism resolved — Section 4/10) |

### Authorization Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I9 Host-Only Actions | Only the recognized host may invite, view dashboard, close, or cancel | Non-host performs a host-only action | Request omits or presents an incorrect/mismatched host management token | Invitation, Dashboard, Close, Cancel | Authorization check: verify the presented host management token matches the event's stored token | Fact + Resolved Decision (Host Management Token — Q3 resolved) |
| I10 Invitee Scope | An invitee interaction must only affect their own invitation/RSVP/event | Link crosses into another invitee's or event's data | Weak link binding, or an incorrect/missing Invitation token | RSVP submission/change, invalid link | Authorization check: verify the presented raw Invitation token's hash against the target Invitation's stored `invitationTokenHash` (Resolved Decision — Section 4), the same verification shape as I9's host token check | Fact + Resolved Decision (Q2 resolved — Section 4) |

### Concurrency Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I11 Last-Spot Concurrency | At most one of two simultaneous last-spot Yes's may be confirmed | Both read stale count, both confirm, exceeding capacity | Concurrent Yes submissions at capacity−1 | RSVP submission/change, Capacity Evaluation | Concurrency control (mechanism deferred to Section 10) | Fact, newly surfaced as a concurrency consequence of I1 |
| I12 Waitlist Promotion Concurrency | Same invitee not promoted twice; promotion stays within capacity and deterministic | Two near-simultaneous freed-spot transitions double-promote or lose a spot | Concurrent CONFIRMED→NONE transitions (Yes→No or Yes→Maybe) | RSVP change, Waitlist Promotion | Promotion executes inside the same per-event pessimistic lock used for the capacity decision (Section 10), serializing freed-spot evaluation and promotion selection; FIFO selection via `waitlistedAt` (Invitation id tie-breaker) makes the choice deterministic | Fact + Resolved Decisions (Q7 ordering, Q13 trigger scope — Section 4) |

### Tenant Isolation
**Not applicable to the current scope.** No tenant/organization model is defined by the task or repository. Existing ownership isolation (I7, I9, I10) fully covers what's required without any tenant concept.

---

## 7. Proposed Architecture

```
React Frontend
      ↓
Spring MVC Boundary (Request Boundary)
      ↓
Business / Application Logic  ──────→  Invitation Delivery Boundary (external, execution model unresolved)
      ↓
Persistence Boundary (Spring Data JPA)
      ↓
PostgreSQL
```

| Component | Responsibility | Inputs | Outputs | Ownership | Notes |
|---|---|---|---|---|---|
| React Frontend | Host/invitee interaction, input collection, display | User input, fetched state | Requests to Request Boundary; rendered UI | Temporary UI state only — **not** capacity, waitlist placement, lock, authorization, or outcome | Never authoritative for any invariant |
| Request Boundary (Spring MVC) | Accept requests, validate shape, delegate | Requests matching workflow shapes | Calls to Business Logic; translated responses | Request/response shape only | Must not decide authorization itself |
| Business/Application Logic | Coordinate and enforce all workflows/invariants | Validated actions | State changes to persist; delivery requests | All business decisions (I1–I12) | Absorbs every open question until resolved |
| Persistence Boundary (Spring Data JPA) | Read/write persistent state | Read/write requests from Business Logic | Retrieved state; write confirmation | Data access translation only — no business decisions | Concurrency mechanism deferred to Section 10 |
| PostgreSQL | Authoritative persistent storage | Writes via Persistence Boundary | Durable state via Persistence Boundary | All durable event/invitation/RSVP/outcome state | No schema decided here |
| Invitation Delivery Boundary | Deliver the invitation email | Delivery request (event + invitee + link) | Delivery result (mechanism unresolved) | Only the act of external delivery | Not assumed to be the existing `wasender` integration; sync/async unresolved |

**Ownership Boundaries:** Event/RSVP business rules, capacity/promotion decisions, and authorization decisions belong to Business Logic. Business Logic makes and coordinates these business decisions. **The Persistence Boundary and PostgreSQL participate in enforcing and durably preserving those decisions — including integrity and concurrency guarantees — but they do not independently decide business policy.** UI state belongs to the Frontend; delivery belongs to the Invitation Delivery Boundary. The one soft edge: the Request Boundary must only carry an identity claim forward — it must never itself decide authorization. For host-only operations, this identity claim is the host management token (Q3 resolved — Section 4): the Request Boundary forwards it, but only Business Logic verifies it against the event's stored token.

**Dependency Direction:** Frontend → Request Boundary → Business Logic → Persistence Boundary → PostgreSQL, with Business Logic also calling out to the Invitation Delivery Boundary. No reverse dependencies.

**Interaction Summary:** All authoritative decisions concentrate in Business Logic, backed by one durable store (PostgreSQL); the frontend and request boundary are deliberately non-authoritative. Invitation delivery is the one external boundary, explicitly not assumed to reuse the existing unrelated `wasender` integration.

**Workflow-to-Architecture Mapping** (corrected — not every workflow originates from the frontend):

- **User-facing requests** (Frontend → Request Boundary → Business Logic → Persistence Boundary → PostgreSQL): Event creation, Invitation, RSVP submission, RSVP change, Dashboard, Close, Cancel.
- **Internal consequences** (Business Logic → Persistence Boundary → PostgreSQL, triggered *within* processing of a user-facing request, not as independent entry points): Capacity Evaluation, Waitlist Promotion, Lock-after-start evaluation.
- **External delivery** (Business Logic → Invitation Delivery Boundary): Invitation creation additionally branches here.

No background/scheduled infrastructure is introduced by this clarification — Lock-after-start evaluation remains a reactive check performed at the moment of an RSVP attempt, not a proactive process.

**Invariant-to-Architecture Mapping:** All of I1–I12 are decided/enforced in Business Logic; authoritative state for all of them lives in PostgreSQL, durably preserved via the Persistence Boundary; I11/I12 additionally require a concurrency mechanism at the Persistence Boundary/PostgreSQL layer, deferred to Section 10. Business Logic decides; PostgreSQL and the Persistence Boundary do not decide — they store and enforce durability.

---

## 8. Data Ownership and State Model

### Invitation, RSVP Response, and Attendance Outcome Relationship (Resolved)

```
Invitation
  → RSVP Response concept    (invitee's stated choice: YES / NO / MAYBE)
  → Attendance Outcome concept (system-derived current state: CONFIRMED / WAITLISTED / NONE)
```

RSVP Response and Attendance Outcome are **sibling state concepts scoped to the same Invitation — not parent/child concepts**. Attendance Outcome belongs to the Invitation directly; it is not owned by, attached to, or keyed off any specific RsvpResponse row, even though its *value* is computed from the current Response plus capacity state.

**Resolved:** each Invitation has exactly one current RSVP Response row and exactly one current Attendance Outcome row — both are current-state only, not history. Changes update the existing row in place; no historical rows are appended. This is enforced at the database level by `UNIQUE(invitation_id)` on both `rsvp_responses` and `attendance_outcomes` (see Section 4, "Resolved Decisions"). Historical/audit tracking is explicitly out of scope for this first pass.

| Concept | Source of Truth | Mutated By | Read By | Derived State | Lifecycle Note |
|---|---|---|---|---|---|
| Event core details | PostgreSQL | Business Logic | Frontend, Business Logic | None | **Initially written at creation; post-creation mutation remains unresolved** (see Section 4/15) |
| Host ownership | PostgreSQL | Business Logic, at creation, in the same transaction as host management token generation | Business Logic (I9) | None | Assigned once; whether an event may have more than one host is unresolved (Q4) |
| Host management token | PostgreSQL | Business Logic, generated exactly once at event creation — never client-supplied, never regenerated in this first-pass model | Business Logic, to authorize host-only operations (I9) | None — a stored, backend-generated random value | Created in the same transaction as the event and host assignment (I8); distinct from the invitee's invitation token (I7) — the host token authorizes managing the whole event, the invitation token scopes one invitee's RSVP; recovery/rotation if lost is out of scope for this first-pass model (Q3) |
| Event lifecycle/status | PostgreSQL | Business Logic, via Close/Cancel | Business Logic, Frontend | "Start reached" is derived from stored start time, not stored | `OPEN→CLOSED`/`OPEN→CANCELLED` only; no reverse transition (Q6 resolved — Section 4) |
| Invitation | PostgreSQL | Business Logic, on invite | Business Logic, Delivery Boundary | None | At most one Invitation per Event per normalized (trimmed, lowercased) invitee email (I13, Q8 resolved — Section 4); duplicate attempts are rejected, not merged, and never rotate the existing token. Expiry (Q9) remains unresolved |
| RSVP Response (Yes/No/Maybe) | PostgreSQL | Business Logic, only on invitee request bearing a valid Invitation token (I10, Q2 resolved), subject to I2/I4/I5 | Business Logic, invitee's own view, host dashboard | None | Belongs to exactly one Invitation; sibling to Attendance Outcome, not its parent (see relationship note above). **Exactly one current row per Invitation, enforced by `UNIQUE(invitation_id)` (Resolved Decision — Section 4); a change updates this row in place, never appends a new one; not history.** See RSVP Lifecycle below |
| Attendance Outcome (Confirmed/Waitlisted/None) | PostgreSQL | Business Logic only (never invitee/frontend) | Business Logic, invitee's own view, host dashboard | Derived from Response + capacity state, then persisted. **Only a Yes Response is evaluated against capacity (Q1 resolved); No/Maybe map directly to NONE without evaluation.** | Belongs to exactly one Invitation (Resolved Decision — Section 4); sibling to RSVP Response, not owned by or keyed off any specific RsvpResponse row. None→Confirmed/Waitlisted; Waitlisted→Confirmed on promotion; Confirmed→None on Yes→No/Yes→Maybe (triggers promotion if waitlist non-empty, Q13 resolved). **Exactly one current row per Invitation, enforced by `UNIQUE(invitation_id)`; promotion updates this row in place, never appends a new one; not history (Resolved Decision — Section 4).** **Also carries `waitlistedAt` (Resolved Decision, Q7 — Section 4): set only on transition into WAITLISTED, left unchanged while continuously WAITLISTED, cleared on leaving WAITLISTED** |
| Waitlist ordering | Persisted FIFO via `AttendanceOutcome.waitlistedAt` (Resolved Decision, Q7 — Section 4) | Business Logic: set on entering WAITLISTED, cleared on leaving WAITLISTED | Business Logic, on promotion (ascending `waitlistedAt`, Invitation id tie-breaker) | Not derived — explicitly persisted, because current-state update-in-place rows (Section 4) no longer allow deriving order from row-insertion order | Ordering policy resolved (Q7); promotion trigger scope resolved separately (Q13) |
| Confirmed attendance count | Derived (not stored) | N/A | Business Logic (I1) | Derived from Attendance Outcome | Avoids a duplicate source of truth for I1 |
| Dashboard counts | Derived (not stored) | N/A | Host | Derived from Response + Attendance Outcome | Subject to display staleness only — never feeds capacity decisions |
| Event start lock condition | Derived from stored `eventDateTime` vs. current server time | N/A | Business Logic, on every RSVP attempt | Fully derived | No persisted `isLocked` flag. **First-pass rule resolved (Section 4): locked when current server time is at or after `eventDateTime`, using the server/application clock and the existing `LocalDateTime` model — no timezone conversion added.** Broader timezone rules remain Open Question (Q12), which does not block this first-pass rule |
| Frontend state | None — not authoritative | Frontend itself | Frontend/user | N/A | Transient; can go stale relative to backend |

### Event Lifecycle
Created → OPEN (default) → CLOSED or CANCELLED (host actions, stored). "Start reached" is a derived time condition, not a stored flag. **The only valid transitions are `OPEN → CLOSED` and `OPEN → CANCELLED`; there is no reverse transition — reopening a CLOSED or CANCELLED event is out of scope for this first-pass MVP (Q6 resolved — Section 4).** CLOSED and CANCELLED enforce identical RSVP-write/promotion-blocking behavior (Q11/Q14 and Cancel behavior resolved — Section 4), while remaining distinct stored status values; CANCELLED additionally guarantees existing data stays readable and unmutated. Unresolved: whether event details may be edited after creation.

### RSVP Lifecycle
**Response** {Yes, No, Maybe} and **Attendance Outcome** {Confirmed, Waitlisted, None} are kept strictly separate and must remain separate throughout this document. Both are sibling concepts scoped to the same Invitation (see relationship note above) — Attendance Outcome is not a child of RSVP Response. **Both are current-state only, one row per Invitation, updated in place — not history** (Resolved Decision — Section 4).

**Response changes are allowed only while the current server time is strictly before `eventDateTime` (I2, resolved first-pass rule — Section 4), and while the event remains OPEN. Once CLOSED or CANCELLED, both new submissions and changes to existing RSVPs are blocked (Q11/Q14 and Cancel behavior resolved — Section 4) — there is no narrower "new-submissions-only" scope for either status.** This is intentionally conditional, not an unconditional freedom to change RSVPs at any time. A "change" means updating the invitee's single current Response row in place — it never creates a second row for that Invitation. Access to submit or change a Response requires presenting the correct raw Invitation token for that Invitation (Q2 resolved — I10).

Outcome transitions: None→Confirmed/Waitlisted (on Yes, the only Response value that participates in capacity evaluation — Q1 resolved); No/Maybe map directly to None without capacity evaluation. Waitlisted→Confirmed (on promotion, selected by ascending `waitlistedAt` with Invitation id as tie-breaker — Q7 resolved). Confirmed→None (on Yes→No or Yes→Maybe) automatically triggers promotion of the earliest-waitlisted Invitation if the waitlist is non-empty and the event has not started (Q13 resolved) — Yes→No and Yes→Maybe are treated identically as promotion triggers, since both free the same CONFIRMED spot. Each transition updates the Invitation's single current Attendance Outcome row in place, including setting/clearing `waitlistedAt` as specified in Section 4. Promotion executes inside the same serialized per-event transaction boundary as the capacity decision (Section 10) and never triggers after event start, or on a CLOSED/CANCELLED event (since the triggering RSVP change itself is blocked there).

---

## 9. Trust Boundaries and Security Notes

### Trust Entry Points
1. **Host browser → backend** — event fields, invitee email, action requests, and (for host-only operations) the host management token. Frontend input is untrusted; the backend must not accept a bare identity claim at face value — for host-only operations, the caller must present the backend-generated host management token, verified against the event's stored value (Q3 resolved — Section 4).
2. **Invitee unique link → backend** — the raw Invitation token, RSVP choice, identifiers. Possession of the token is the resolved first-pass bearer credential (Q2 resolved — Section 4): the backend must verify the presented token's hash against the target Invitation's stored `invitationTokenHash` before accepting any action, exactly as the host token is verified against its Event; the client must never directly choose Confirmed/Waitlisted.
3. **Backend → Invitation Delivery Boundary** — email, event info, token. External boundary; must never become authoritative for Invitation validity; `wasender` is not assumed appropriate.

### Authentication vs. Authorization
**Authentication for the Host is resolved (Q3)** via the host management token: a backend-generated, cryptographically random, unguessable token created at event creation and required for host-only operations. This is a **first-pass mechanism** establishing "whoever holds this token may manage this event" — it is not a verified real-world identity and not a full account system. **Authentication for the Invitee is also resolved (Q2)**, in the identical first-pass shape: the raw Invitation token is the bearer credential, matched against the stored `invitationTokenHash`. Neither mechanism is a verified real-world identity or a full account system — both are possession-based. **Authorization** (deciding if they may act) is specified independently: only the host may invite/view dashboard/close/cancel (I9), enforced by verifying the presented host management token against the event's stored token; an invitee may only affect their own invitation/RSVP/event (I10), enforced by verifying the presented Invitation token against that Invitation's stored hash; even an authorized caller is still blocked by business rules (event started, closed, cancelled, or an invalid transition) — authentication never bypasses invariants.

### Authorization Enforcement
Business Logic makes every allow/deny decision. The Request Boundary may carry an identity claim forward but must not decide authorization itself (the one flagged soft edge in the architecture). For host-only actions, this means Business Logic — not the Request Boundary — compares the presented host management token against the event's stored token. For invitee-scoped actions (RSVP submit/change), the same pattern applies: Business Logic compares the presented Invitation token against the target Invitation's stored hash before accepting any RSVP Response change.

### Sensitive Data / Privileged Operations
Unique invitation tokens function as a credential-equivalent (Q2 resolved — Section 4); the host management token is likewise a credential-equivalent value, but for host-level control of an entire event rather than one invitee's RSVP — the two are distinct and must never be confused or interchangeable. Invitee email, host identity, event ownership, RSVP Response, and Attendance Outcome are all scoped to the relevant host/invitee only, never to unrelated parties. Privileged operations (invite, dashboard, close, cancel) require a valid host management token; invitee-scoped operations (RSVP submit/change) require a valid Invitation token for that specific Invitation; system-controlled outcomes (Confirm/Waitlist, promotion) must never be directly triggerable by a client request.

**Host management token exposure/loss:** since this token is the sole first-pass authorization mechanism for a host, its loss means loss of host control over the event, and its leak means an outsider gains full host control — there is no recovery or rotation mechanism in this first-pass model. This is a known, accepted limitation of the decision, not silently ignored (see Section 12).

### Event-Level Isolation
Tenant isolation is not applicable (Section 6). Event-level isolation is still required: one host cannot manage another host's event (I9, now concretely enforced via host management token verification); one invitee cannot access another invitee's invitation/RSVP (I10); one token cannot cross event boundaries (I7).

### Sensitive Data Paths
**Invitation path:** Host UI → Backend → Business Logic → PostgreSQL (stores invitation) → Invitation Delivery Boundary → Invitee. Exposure risk points: backend logs, the external delivery provider, and the recipient's inbox — all outside this system's control once handed off.
**RSVP path:** Invitee link → Backend → Business Logic (verifies the presented Invitation token against the target Invitation's stored hash — I10, Q2 resolved; validates I2/I4/I5 rules; evaluates I1 capacity only if the Response is Yes — Q1 resolved) → PostgreSQL → Host dashboard read (gated by I9). If the backend instead trusted a client-supplied invitation/event relationship without this token check, any caller could act as any invitee, violating I10 — the same class of risk already described for the host action path below.
**Host action path:** Host UI → Backend → host authorization check (verify the presented host management token against the event's stored token) → Business Logic → PostgreSQL. The token must never be accepted as an arbitrary client-defined value — only a backend-generated token matching the stored value is valid. If the backend instead trusted a client-supplied host/event relationship without this check, any caller could act as host of any event, violating I9.

An RSVP attempt against a cancelled event is now resolved (Cancel behavior — Section 4): it is blocked, identically to an attempt against a closed event — no new RSVP and no change to an existing RSVP is accepted, though existing stored RSVP/Attendance Outcome data remains readable via the dashboard.

No full authentication framework (e.g., Spring Security), session mechanism, User entity, or specific cryptographic algorithm/library is chosen here — only the conceptual host management token mechanism (Q3) and the conceptual Invitation token bearer-credential mechanism (Q2) are decided. Both mechanisms' exact generation/storage implementation remain implementation-time decisions consistent with "cryptographically random and unguessable," and both already exist as `HostTokenService`/`InvitationTokenService` (separate code, no shared logic).

---

## 10. Concurrency and Correctness Notes

| Vulnerable Area | Workflow / State | Risk | What Can Go Wrong | Invariant(s) at Risk | Control Note |
|---|---|---|---|---|---|
| Last-Spot Capacity Race | RSVP submission/change → Capacity Evaluation | Check-then-write race | Two concurrent Yes's both read count=9, both confirm, exceeding capacity. **Only Yes submissions are ever involved (Q1 resolved) — No/Maybe never enter this race.** | I1, I11 | Serialized/atomic capacity decision, scoped per event |
| Waitlist Promotion Race | RSVP change → Waitlist Promotion | Concurrent free+select+promote | Double-promotion, lost freed spot, or wrong invitee selected | I1, I3, I12 | Same serialized per-event boundary as capacity (Section 10); selection is deterministic FIFO via `waitlistedAt` with Invitation id tie-breaker (Q7/Q13 resolved — Section 4) |
| Duplicate RSVP | RSVP submission/change | Retry/double-click | Repeated side effects if side effects fire on every request rather than only on an actual value change | I6, I10 | Idempotent update: trigger side effects only on actual value change. "Append-not-set" is no longer possible at the schema level — `UNIQUE(invitation_id)` on `rsvp_responses` (Resolved Decision — Section 4) means a second insert attempt would violate the constraint, not silently create a duplicate row |
| Conflicting RSVP Update | RSVP change (two requests, same invitation) | Concurrent conflicting writes to the same single current row | Lost update; promotion based on a superseded state | I6, I3 | Version check or per-invitation serialization; no invented "Last Write Wins". The `UNIQUE(invitation_id)` constraint guarantees there is only ever one row to race over, but does not by itself resolve which concurrent update wins — that mechanism is still not chosen here |
| Close vs RSVP Race | Close vs. RSVP submission/change | Stale status read | RSVP (new or change) accepted just after close | I4 | Re-read authoritative status at write time; scope resolved (Q11/Q14) — both new submissions and changes to existing RSVPs must be blocked once CLOSED |
| Start-Time Boundary | RSVP submission/change vs. Lock-after-start evaluation | Client/server clock mismatch, latency | RSVP accepted after start due to stale client state | I2 | **Resolved first-pass rule:** server-side comparison of current server time against stored `eventDateTime` at the moment of processing (never the client's claimed time); broader timezone correctness remains Open Question (Q12), which does not block this rule |
| Cancel vs RSVP Race | Cancel vs. RSVP submission/change | Stale lifecycle state | RSVP (new or change) accepted just after cancel | I5 | Re-read authoritative status at write time; outcome resolved (Cancel behavior — Section 4) — identical write-blocking scope to Close, existing data left unmutated |
| Partial Database Failure | Event creation, Invitation creation, RSVP+Outcome, Promotion | Multi-part write partially succeeds | Response without Outcome; Invitation without token; Event without host or without a generated host management token | I6, I7, I8, I3 | Each causally-linked pair in one transaction |
| External Invitation Delivery Side Effect | Invitation | External call not transactional with DB | Delivery failure or DB failure after send | I7 | Invitation validity determined solely by PostgreSQL, never delivery outcome |
| Duplicate Invitation Race (Resolved) | Invitation | Check-then-insert race | Two concurrent invite requests for the same normalized email on the same Event both pass an optional service pre-check | I13 | **Resolved:** a database UNIQUE constraint on (event_id, invitee_email), not the pre-check, is the authoritative enforcement — only one of the two concurrent INSERTs succeeds; the losing request's constraint violation is translated into a duplicate-invitation application error. No global lock and no per-event pessimistic locking are used for this invariant |

### First-Pass Concurrency Decision
**Current choice:** a per-event pessimistic/serialized correctness boundary around the capacity decision. **This simplifies correctness reasoning only when every capacity-changing path uses the same protected boundary — locking alone does not automatically guarantee correctness if any path bypasses it.** Optimistic version-based concurrency remains a reasonable later alternative if contention becomes a measured concern; it is not chosen now, for simplicity and explainability.

This per-event locking mechanism is specific to the capacity/waitlist invariants (I1, I11, I12) and is **not** extended to Invitation uniqueness (I13), which uses a separate, unrelated mechanism — a database UNIQUE constraint (see the Duplicate Invitation Race row above and Section 4). The two are independent design decisions for two different invariants, not one general concurrency strategy.

### Transaction Boundaries
Five causally-linked write groups must succeed/fail together: (1) Response + capacity decision + Outcome — both Response and Outcome are updated in place on their own single current row for the same Invitation (Resolved Decision — Section 4), as sibling state, not a shared row, (2) Confirmed→None (Yes→No or Yes→Maybe) + FIFO promotion selection + promotion write — all inside the **same** serialized per-event transaction boundary as the capacity decision in (1), not a separate one (Q13 resolved — Section 4): the promoted invitee's existing AttendanceOutcome row is updated in place (outcome set to CONFIRMED, `waitlistedAt` cleared), (3) Event creation + host assignment + host management token generation (I8 — the token is generated in the same transaction as the event and its host assignment, never after the fact), (4) Invitation creation + token relationship, (5) Close/Cancel state check-then-write — the status transition and the subsequent blocking of all RSVP create/change/promotion for that event (Q11/Q14, Cancel behavior resolved — Section 4) must be consistent with a single authoritative status read at write time.

### Correctness Guarantees
1. Confirmed attendance never exceeds max-capacity (mechanism-dependent only; the capacity rule itself — only Yes participates — is resolved, Q1).
2. At most one invitee can claim the final confirmed spot (mechanism-dependent only).
3. Waitlist promotion cannot promote the same attendee twice, and selects deterministically by ascending `waitlistedAt` with Invitation id as tie-breaker (Q7, Q13 resolved — Section 4) — mechanism-dependent for the concurrency guarantee, but the selection policy itself is now fully specified.
4. RSVP changes after event start are rejected — the first-pass rule (server clock vs. `eventDateTime`) is resolved and enforceable now; only the broader timezone question (Q12) remains open, and does not block this guarantee's first-pass form.
5. One Event must not have more than one Invitation for the same normalized email (I13, Q8 resolved) — enforced authoritatively by a database UNIQUE constraint on (event_id, invitee_email); this guarantee holds regardless of application-level races, since the constraint (not the service pre-check) is what's authoritative.
6. Partial database failures must not leave state inconsistent (mechanism-dependent only).
7. Client/frontend state is never authoritative for concurrency decisions (settled principle).
8. Display staleness must never become business-state staleness (settled principle).
9. An Invitation must not have more than one current RSVP Response row or more than one current Attendance Outcome row (Resolved Decision — Section 4) — enforced authoritatively by `UNIQUE(invitation_id)` on both `rsvp_responses` and `attendance_outcomes`, the same class of guarantee as guarantee 5 above. This guarantee is independent of Q7/Q13, which govern what value the single row holds, not how many rows exist. (Q1 and Q2, previously listed here, are now resolved — see Section 4.)
10. Once an Event is CLOSED or CANCELLED, no RSVP may be created or changed, and no waitlist promotion may occur, for that event (Q11/Q14, Cancel behavior resolved — Section 4); neither status is ever reachable from the other, nor from itself in reverse — `OPEN → CLOSED` and `OPEN → CANCELLED` are the only transitions, with no reopen path in this first-pass MVP (Q6 resolved — Section 4).

---

## 11. Scalability and Multi-Tenancy Notes

### Growth Axes
Number of events, invitees per event, concurrent RSVP submissions (especially for one "hot" event), waitlist size, dashboard reads, and invitation delivery volume can each grow independently. Of these, concurrent submissions to a single hot event is the only axis creating real contention, since it's the only one where requests compete over the same limited resource rather than adding independent rows.

### Likely Bottlenecks
**Hot-event capacity contention** is the most important scale issue: the per-event serialized capacity decision (Section 10) means concurrent Yes submissions for the same event will wait on each other — a deliberate correctness-over-throughput tradeoff. Locking is scoped per event, not global — unrelated events must remain independent under this scheme. Dashboard aggregation, large attendee/waitlist reads, and invitation delivery are all secondary, lower-priority bottlenecks at this project's scale.

### Current Sufficiency
A single Spring Boot application with PostgreSQL as the shared authoritative store, per-event serialized concurrency control, and derived (not stored) dashboard/confirmed counts are all sufficient for this task. The task provides no numeric traffic, latency, availability, or data-volume targets, so no distributed infrastructure is justified. Invitation delivery is treated as an external, decoupled dependency — **its synchronous-vs-asynchronous execution model remains unresolved** and is not described as settled in either direction anywhere in this document.

### Future Redesign Triggers
High concurrent traffic for one event → revisit concurrency strategy. Slow dashboard aggregation → consider caching (would introduce duplicate-state risk requiring new correctness rules). Slow/rate-limited delivery → consider decoupling asynchronously (no queue added now). Measured PostgreSQL bottleneck → revisit database design. A future tenant/organization requirement → revisit ownership, authorization, and partitioning.

### Event-Level Isolation
Same as Section 9 — required regardless of tenancy: one host cannot manage another host's event; one invitee cannot access another's invitation/RSVP; one token cannot cross events.

### Multi-Tenancy
**Multi-tenancy is not part of the current scope.** No tenant, organization, tenant ID, or tenant-specific configuration is defined or invented.

---

## 12. Risks and Failure Notes

| Risk | What Fails | Why It Fails | Mitigation / Exposure Note |
|---|---|---|---|
| Last-spot capacity race | Confirmed attendance exceeds capacity | Concurrent Yes's race on the same count | Per-event serialized control (Section 10); must cover every capacity-changing path |
| Waitlist promotion race | Double/lost/wrong promotion | Multi-step transition races | Same serialized per-event transaction boundary as capacity (Section 10); ordering resolved — FIFO via `waitlistedAt`, Invitation id tie-breaker (Q7/Q13 resolved) |
| Partial database update | Half-completed linked writes | No shared transaction | Transactional writes per Section 10 |
| Duplicate RSVP submission | Same action processed twice | No dedup on repeated requests | Single authoritative Response + idempotent side effects |
| Conflicting RSVP updates | Lost update / stale-based promotion | Concurrent writes to one Response | Version check or serialization; no invented Last-Write-Wins |
| **Host management token loss/leak** | Host permanently loses control of their event, or an outsider gains full host control | The token is the sole first-pass authorization credential (Q3 resolved), with no recovery/rotation mechanism | Token is backend-generated and never client-invented; loss/leak risk is a known first-pass limitation, not silently ignored |
| **Invitee-link trust risk** | Non-invitee acts as invitee | Token possession = identity (Q2 resolved) — anyone who obtains the raw token can act as that invitee | Scope limited to exactly one Invitation (I10, resolved verification); this is an accepted first-pass limitation of a possession-based model, the same class as the Host Management Token's own risk (Section 12 above). Token expiry (Q9) remains unresolved |
| **Multiple-host ambiguity** | Ownership/authorization model may not match actual usage | Q4 unresolved | Kept explicit as an open question, not decided |
| Close vs RSVP race | RSVP accepted on stale status | No re-check at write boundary | Re-check status at write time; scope resolved (Q11/Q14) — blocks both new submissions and changes to existing RSVPs |
| **Cancel vs RSVP ambiguity** | Inconsistent behavior near cancellation | Cancel semantics previously unresolved | **Resolved:** Cancel blocks RSVP create/change and promotion identically to Close, while preserving existing data and dashboard readability (Cancel behavior — Section 4) |
| **Event-start timing ambiguity** | Inconsistent lock enforcement across timezones | Broader timezone semantics unresolved (Q12) | **First-pass rule resolved:** server-side authoritative time compared directly against stored `eventDateTime` (`LocalDateTime`, no timezone conversion); this is a deliberately minimal rule, not a timezone-correct one — full policy remains open (Q12) but does not block this first-pass enforcement |
| Stale dashboard data | Host sees outdated counts | Derived read, another RSVP changes after | Acceptable if never reused for capacity decisions |
| **Invitation delivery failure** | Email not delivered | External dependency, not transactional with DB | Invitation validity stays PostgreSQL-determined |
| Wrong recipient / invitation pairing | Link sent to wrong person | Sensitive pairing error | Backend must construct pairing correctly before handoff |
| **Duplicate invitation attempt** | A second Invitation for the same normalized email on the same Event | Concurrent or repeated invite requests; a service-only pre-check would not be safe under concurrency | **Resolved:** a database UNIQUE constraint on (event_id, invitee_email) is the authoritative guard (I13); the losing concurrent request's constraint violation is translated into a duplicate-invitation application error, never a raw DB exception and never silently ignored |
| **Ambiguous RSVP/Outcome current state** | An Invitation ends up with more than one RsvpResponse or AttendanceOutcome row, with no way to know which is "current" | Implementing RSVP change or promotion as an insert rather than an update-in-place | **Resolved:** `UNIQUE(invitation_id)` on both `rsvp_responses` and `attendance_outcomes` (Resolved Decision — Section 4) makes a second row for the same Invitation a constraint violation, not a silently-accepted ambiguous state; RSVP/Outcome changes must be implemented as updates to the existing row |
| Email/token logging exposure | Invitation token, host management token, or email leaks via logs | Both token types are credential-equivalent (Q2 resolved for invitation tokens; the Host Management Token decision, Q3, for host tokens) | Avoid ordinary logging of raw tokens of either kind |
| PostgreSQL unavailable | All core workflows fail | Sole authoritative store, no fallback | Fail honestly; frontend never becomes truth |
| External invitation provider unavailable | Email delivery fails | Separate external system availability | Core RSVP/event state unaffected (decoupled) |
| **Repository mismatch risk** | Implementation built on wrong assumptions | React version mismatch, unrelated scaffold code | Kept visible as cleanup decisions, not resolved |

**Assumption Failure Risks:** if A4 turns out false, the ownership model would need to change (see Section 4 for its dependency). (Former A1, A2, A3, A5, A6, and A7 are all resolved, not assumptions — see Section 4.)

None of the unresolved product questions above (Q4, Q9, Q12) are treated as bugs — they are explicit open decisions, not defects. (Q1, Q2, Q3, Q6, Q7, Q8, Q11, Q13, and Q14 are now resolved — see Section 4.)

---

## 13. Alternatives and Tradeoffs

| Decision Area | Status | Position |
|---|---|---|
| Capacity concurrency strategy | **Chosen** | Per-event pessimistic/serialized control, over optimistic version-based or database-atomic alternatives, for simplicity and explainability |
| RSVP state model | **Chosen** | Separate Response and Attendance Outcome, over a combined enum, to keep intent and system decision distinct |
| **Attendance Outcome ownership** | **Chosen** | Belongs to the Invitation, as a sibling state concept to RSVP Response — not owned by or keyed off any specific RsvpResponse row, over the alternative of attaching Outcome to RsvpResponse itself. Chosen because Outcome must be able to change independently of RSVP changes (e.g., via promotion) without needing to "follow" a particular Response value beyond being recomputed from it |
| **RSVP Response / Attendance Outcome current-state persistence model** | **Chosen** | Exactly one current row per Invitation for each, enforced by `UNIQUE(invitation_id)` on both `rsvp_responses` and `attendance_outcomes`; changes update the existing row in place. Chosen over an append-only history model because the product does not require historical/audit tracking, and a single authoritative current row removes ambiguity for dashboard reads and future capacity logic. Historical tracking remains explicitly out of scope; if ever needed, it would be a separate, additive concept |
| Confirmed/dashboard counts | **Chosen** | Derived from authoritative state, over stored running counters, to avoid a duplicate source of truth |
| Event-start lock | **Chosen** | Derived from stored start time vs. current time, over a persisted flag, to avoid inventing background infrastructure |
| Invitation/RSVP data shape | **Deferred / first-pass** | Leaning toward separate Invitation and RSVP concepts, matching the natural lifecycle; not fully re-examined at a schema level |
| **Invitee identity** | **Chosen (first-pass)** | The raw Invitation token is the bearer credential (Q2 resolved) — the same first-pass shape as the Host Management Token: possession-based, no account/login/password/JWT, hash-verified, never persisted in raw form. Chosen over building an invitee account system because the brief never asks for invitee sign-up; token recovery, rotation, and expiry (Q9) remain unaddressed first-pass limitations |
| **MAYBE and capacity** | **Chosen** | Only Yes participates in capacity evaluation (Q1 resolved); No and Maybe both map directly to Attendance Outcome NONE. Chosen over having Maybe consume capacity because the brief's capacity/waitlist language ("Yes RSVPs go to a waitlist") only ever mentions Yes; treating Maybe as capacity-consuming would have been an invented rule, not a supported reading |
| **I2 lock-after-start mechanism** | **Chosen (first-pass)** | A direct comparison of current server time against the stored `eventDateTime` (`LocalDateTime`, no timezone conversion), over waiting for a full timezone-aware policy to be decided first. Chosen because the alternative — leaving I2 entirely unenforceable until Q12 resolves — would block RSVP submission indefinitely for a question that mostly affects precision at the boundary, not the existence of the rule itself. Q12 remains open for later refinement |
| **Host identity** | **Chosen (first-pass)** | Host Management Token — the backend generates a cryptographically random, unguessable token at event creation, in the same transaction as host assignment; the client must present it for host-only operations. No User entity, login, password, JWT, or Spring Security setup. Explicitly a first-pass authorization mechanism, not a full account system; token recovery/rotation is out of scope, and multi-host support (Q4) remains separately unresolved |
| **Multiple hosts** | **Unresolved** | Whether an event may have more than one host is not decided (Q4); single-host is used only as a working assumption (A4) |
| **Waitlist ordering** | **Chosen** | FIFO, via a persisted `AttendanceOutcome.waitlistedAt` timestamp (Q7 resolved) — set on entering WAITLISTED, unchanged while continuously WAITLISTED, cleared on leaving. Chosen over deriving order from row-insertion order because current-state rows are update-in-place (Section 4), so insertion order can no longer imply waitlist order once a row can flip states repeatedly. Invitation id used as a deterministic tie-breaker |
| **Waitlist promotion trigger scope** | **Chosen** | Automatic promotion whenever a CONFIRMED spot frees up before event start — explicitly including both Yes→No and Yes→Maybe (Q13 resolved), since both produce the identical system-level effect. Chosen over a narrower "confirmed→No only" reading because treating Yes→Maybe differently would be an invented, unsupported distinction; promotion runs inside the same serialized per-event transaction boundary as the capacity decision, not a separately-locked step |
| **Close / Cancel RSVP-blocking scope** | **Chosen** | CLOSED and CANCELLED both fully block RSVP create/change and promotion (Q11/Q14 resolved), over a narrower reading that would let CLOSED or CANCELLED block only new submissions while allowing changes to existing RSVPs. Chosen for conservative, unambiguous enforcement; CANCELLED additionally preserves existing data and keeps the dashboard readable, and the two remain distinct stored status values even though their write-blocking effect is now identical |
| **Event lifecycle reopening** | **Chosen** | No reverse transition — `OPEN → CLOSED` and `OPEN → CANCELLED` are the only transitions, and reopening either is explicitly out of scope for this first-pass MVP (Q6 resolved). Chosen over inventing a reopen workflow because the brief never describes one, and reopening would raise substantial undescribed complexity (interaction with already-promoted waitlist entries, already-locked RSVPs) for no stated requirement |
| **Duplicate invitation policy** | **Chosen** | At most one Invitation per Event per normalized (trimmed, lowercased) email (I13, Q8 resolved). A duplicate attempt is rejected outright — never merged into the existing Invitation, and never causes the existing token to be rotated/regenerated/overwritten. Invitations for the same email across different Events remain allowed. Chosen over allowing duplicates because it avoids capacity/attendee-counting ambiguity and duplicate dashboard rows |
| **Invitation uniqueness concurrency mechanism** | **Chosen** | A database UNIQUE constraint on (event_id, invitee_email), over relying on check-then-insert alone, a global lock, or per-event pessimistic locking. Chosen because it is authoritative regardless of application-level races, requires no lock contention, and doesn't extend the capacity-specific per-event locking mechanism (I1/I11/I12) to an unrelated invariant. A service-layer pre-check is retained only as a best-effort convenience for a clearer error message, not as the enforcement itself |
| Invitation delivery execution | **Deferred / unresolved** | Neither synchronous nor asynchronous delivery is chosen |
| Event lifecycle representation | **Chosen** | A single lifecycle status + derived start-lock, to make contradictory states harder to represent. This decided *how state is stored*; *what Close/Cancel mean* is now also resolved (Q11/Q14, Cancel behavior — Section 4): both fully block RSVP create/change/promotion, and only differ in that CANCELLED is a separate stored value from CLOSED, not in current RSVP-write behavior |
| Single-service architecture | **Chosen** | One Spring Boot application, matching the existing stack and supporting single-transaction correctness; no distributed services justified |

**Rejected/Deferred (not needed for current scope):** microservices, Kafka/event bus, Redis/distributed lock, stored dashboard counters, distributed RSVP processing, tenant-specific databases, a separate analytics service — none are inherently bad, but none solve a demonstrated need in this task.

---

## 14. Rollout and Migration Notes

### Current Starting Point
A new feature with no prior RSVP system. Backend has almost no RSVP implementation. PostgreSQL/schema already exist. Hibernate `ddl-auto: update`, no migration tool configured. Unrelated bulk-messaging frontend code and `wasender` config exist. React version mismatch (README 19 vs. installed 18.3.1).

### Data Migration
**No existing RSVP production data requires migration.** No backfill, legacy conversion, dual-write period, or compatibility layer is required.

### Schema Introduction
**Data migration: No. Schema introduction: Yes** — Event, Invitation, RSVP Response, Attendance Outcome, host ownership, token relationship, and lifecycle/status concepts must all be newly introduced. `ddl-auto: update` is kept as the first-pass approach (matches the scaffold, no evidence yet of needing reviewable migration history); a migration tool (Flyway/Liquibase) is not added unless a concrete requirement emerges.

### Implementation Rollout Order
A safe incremental order, following design dependencies:
1. **Core domain/state** — the minimum persistent concepts: Event, Invitation, RSVP Response, Attendance Outcome. Attendance Outcome's ownership relationship (belongs to the Invitation, sibling to RSVP Response) and the current-state persistence model for both RSVP Response and Attendance Outcome (exactly one row per Invitation, `UNIQUE(invitation_id)`-enforced, update-in-place, no history) are both resolved; see Section 4/8.
2. **Core workflows** — event creation, invitation creation, RSVP submission/change, host dashboard.
3. **Correctness controls** — capacity (Q1 resolved: only Yes participates), lock-after-start (I2 first-pass rule resolved), waitlist ordering (Q7 resolved: FIFO via `waitlistedAt`), promotion trigger scope (Q13 resolved: any CONFIRMED→NONE transition before event start, same serialized boundary as capacity), close/cancel rules (Q11/Q14 and Cancel behavior resolved: both fully block RSVP create/change/promotion; no reopen path per Q6).
4. **Identity/security** — both host-side authorization (Host Management Token, Q3) and invitee-side authorization (Invitation token, Q2) are resolved and can now be implemented, in the identical first-pass bearer-credential shape.
5. **Invitation delivery** — the chosen email-delivery mechanism, once its sync/async model is decided (no provider introduced here).
6. **Validation and edge cases** — failure paths, concurrency scenarios, invalid token behavior, boundary-time behavior, and any open questions resolved along the way.

**"Implemented" does not mean "safe to expose."** Host-only workflows built in step 2 (invite, dashboard) and Close/Cancel in step 3 must not be exposed to real users until the host management token check is actually implemented and enforced in code (Q3 is resolved at the design level, but that is not the same as being enforced); the same applies to invitee-facing RSVP workflows and the Invitation token check (Q2 is resolved at the design level, but must actually be enforced in code before exposure); capacity-sensitive RSVP submission must not be exposed before step 3's concurrency controls are actually implemented, not just designed.

### Rollout Dependencies / Blockers
Host-only workflows (invite/dashboard/close/cancel) can now proceed once the host management token mechanism (Q3, resolved) is implemented and correctly enforced. The invitation uniqueness rule and its concurrency-safe enforcement mechanism (Q8/I13, both resolved) can now be implemented in full — a database UNIQUE constraint on (event_id, invitee_email), with the losing concurrent request translated into a duplicate-invitation application error. RSVP submission/change can now proceed once the Invitation token check (Q2, resolved), the capacity rule (Q1, resolved), and the I2 first-pass lock rule (resolved) are all implemented and correctly enforced. Waitlist promotion (Q7 ordering, Q13 trigger scope) and the Close/Cancel RSVP-blocking rules (Q11/Q14, Cancel behavior) are now all resolved and unblocked for implementation, along with the fixed no-reopen lifecycle (Q6). Q4 (multiple hosts) blocks the final ownership/scope model. Q12 remains open for the broader/final timezone policy but does not block the first-pass lock-after-start rule already resolved. The delivery execution model blocks final delivery integration.

### Existing Repository Coexistence
Leaving the unrelated bulk-messaging code in place risks confusion/accidental coupling; removing it risks deleting something intentionally retained. **Deferred until repository intent is clarified** — not silently deleted or reused.

### Frontend Version Mismatch
Implementing against the installed React 18.3.1 avoids repository churn but diverges from the README; upgrading to React 19 aligns with the README but introduces an unrelated repository change and a compatibility check against the existing bulk-messaging components. **Unresolved until clarified.**

### Backward Compatibility
No existing released RSVP API/data model requires compatibility. Existing unrelated repository behavior should not be broken accidentally.

### Rollback Considerations
Before real data exists, rollback is a low-stakes code/schema revert. Once real RSVP data exists, destructive rollback risks losing or invalidating it — the migration strategy would need more rigor if this becomes a real deployed system. No production backup infrastructure is designed.

### Feature Rollout Safety
Before considering the feature complete, verify: host authorization (I9), invitee scope (I10), the capacity invariant (I1), the last-spot concurrency scenario (I11), waitlist promotion including FIFO ordering and the CONFIRMED→NONE trigger scope (I3, I12, Q7/Q13 resolved), safe duplicate-request behavior, lock-after-start (I2), close/cancel behavior per the resolved Q11/Q14/Cancel decisions (both statuses fully block RSVP create/change/promotion; no reopen path per Q6), invitation-delivery failure not corrupting core state, and dashboard counts deriving from authoritative state.

---

## 15. Unresolved Issues

| ID | Open Question | Why It Matters | Blocks |
|---|---|---|---|
| Q4 | Can an event have more than one host? | Affects ownership and authorization | Final ownership/scope model |
| Q9 | Do invitation links expire? | Affects link security lifetime | Invitee-link trust risk mitigation |
| Q12 | What broader/final timezone rules should apply to event start time (beyond the resolved first-pass server-clock rule)? | Affects precision of I2 across timezones | Final, timezone-correct lock-after-start behavior — does NOT block the first-pass rule already in effect (Section 4/6) |
| — | React 19 (README) vs. React 18.3.1 (installed) — which is authoritative? | Affects frontend implementation target | Frontend rollout decision |
| — | Intent behind the existing unrelated bulk-messaging/`wasender` code | Affects repository cleanup scope | Repository coexistence decision |
| — | Invitation delivery: synchronous or asynchronous? | Affects host-facing latency and delivery reliability design | Delivery integration design |
| — | Is editing event details after creation permitted? | Affects event-editing scope, if ever requested | Event data ownership finalization |

Each question above appears exactly once in this table and is not contradicted by any other section of this document.

**Q3 (host authentication/identity) has been resolved** via the Host Management Token decision (see Section 4, "Resolved Decisions") and is intentionally no longer listed here. It is removed from this table rather than marked "resolved" in place, so that this table continues to represent only genuinely open questions.

**The Attendance Outcome ownership relationship (Invitation vs. RsvpResponse) has been resolved**: Attendance Outcome belongs to the Invitation, as a sibling state concept to RSVP Response (see Section 4, "Resolved Decisions," and Section 8). This question surfaced during implementation and was never listed as a numbered Q item, so no row is removed here — it is noted for traceability only.

**The RSVP Response / Attendance Outcome current-state persistence model has also been resolved**: each Invitation has exactly one current row of each, enforced by `UNIQUE(invitation_id)` on `rsvp_responses` and `attendance_outcomes`; changes update the existing row in place; historical/audit tracking is out of scope for this first pass (see Section 4, "Resolved Decisions," Section 8, and Section 13). This question also surfaced during implementation (specifically, while building the read-only host dashboard) and was never a numbered Q item, so no row is removed here either — noted for traceability only. Q7 and Q13 remain untouched by both of these resolutions and are still listed above; they govern what value the single current row holds, not how many rows exist. (Q1 and Q2, previously referenced here, are resolved separately below.)

**Q8 (duplicate invitations) has been resolved**: at most one Invitation per Event per normalized (trimmed, lowercased) email; duplicate attempts are rejected, never merged, and never rotate the existing token (see Section 4, "Resolved Decisions," I13, and Section 13). It is removed from this table rather than marked "resolved" in place, consistent with how Q3 was handled. **The concurrency-safe enforcement mechanism (I13) is now also resolved**: a database UNIQUE constraint on (event_id, invitee_email) is authoritative; a service-layer pre-check is a best-effort convenience only; the losing concurrent request is translated into a duplicate-invitation application error. No global lock and no per-event pessimistic locking are used for this invariant (see Section 4, Section 10's "Duplicate Invitation Race (Resolved)," and Section 13).

**Q1 (Maybe and capacity) has been resolved**: MAYBE does not consume capacity and maps to Attendance Outcome NONE, exactly as NO does; only YES participates in capacity evaluation and may become CONFIRMED or WAITLISTED (see Section 4, "Resolved Decisions," I1, I6, and Section 8). It is removed from this table rather than marked "resolved" in place.

**Q2 (invitee identity/scoping) has been resolved**: the raw Invitation token is the first-pass bearer credential — possession of a valid token authorizes access only to that Invitation and its Event/RSVP state; the raw token is never persisted, only its hash is matched (see Section 4, "Resolved Decisions," I10, and Section 9). This is the identical first-pass shape as the Host Management Token (Q3): no account, login, password, JWT, or Spring Security. It is removed from this table rather than marked "resolved" in place.

**The I2 lock-after-start first-pass rule has been resolved and is now explicitly recorded**, even though it is not itself a removal from this table: RSVP create/change is allowed only while the current server time is strictly before `Event.eventDateTime`; at or after that moment, RSVP is locked. This uses the server/application clock directly against the existing `LocalDateTime eventDateTime` field, with no timezone conversion logic introduced (see Section 4, "Resolved Decisions," I2, and Section 8). **Q12 remains open** in the table above as a broader timezone/product question — but Q12 does not block this first-pass rule, which is already fully specified and ready to implement.

**Q7 (waitlist ordering) has been resolved**: FIFO, via a persisted `AttendanceOutcome.waitlistedAt` timestamp — set only on transition into WAITLISTED, left unchanged while continuously WAITLISTED, cleared on leaving WAITLISTED; promotion selects ascending `waitlistedAt` with Invitation id as a deterministic tie-breaker (see Section 4, "Resolved Decisions," I3, I12, and Section 8). This supersedes former working assumption A6. It is removed from this table rather than marked "resolved" in place, consistent with how Q1/Q2/Q3/Q8 were handled.

**Q13 (promotion trigger scope) has been resolved**: automatic promotion is triggered whenever an Invitation's Attendance Outcome transitions away from CONFIRMED (Yes→No or Yes→Maybe, treated identically) while the event has not yet started and the waitlist is non-empty; promotion executes inside the same serialized per-event transaction boundary already used for the capacity decision, never a separately-locked step, and never after event start (see Section 4, "Resolved Decisions," I3, I12, and Section 10). It is removed from this table rather than marked "resolved" in place.

**Q11 (Cancelled-vs-Closed distinction) and Q14 (does Close block only new RSVPs or also changes) have both been resolved together**: CLOSED means RSVP activity is fully closed — no new RSVP may be created and no existing RSVP may be changed; no waitlist promotion is triggered by invitee action, since the triggering RSVP change is itself blocked. CANCELLED enforces the identical RSVP-write/promotion-blocking behavior, but additionally guarantees existing stored RSVP/Attendance Outcome data is preserved unmutated and the host dashboard remains readable. CLOSED and CANCELLED remain distinct stored status values (I5) even though their current RSVP-write behavior is now identical (see Section 4, "Resolved Decisions," I4, I5, and Section 8). Both are removed from this table rather than marked "resolved" in place.

**Q6 (reopening a closed/cancelled event) has been resolved**: reopening is out of scope for this first-pass MVP. The only valid Event lifecycle transitions are `OPEN → CLOSED` and `OPEN → CANCELLED`; there is no reverse transition (see Section 4, "Resolved Decisions," and Section 8's Event Lifecycle). This supersedes former working assumption A5, making it a settled rule rather than an assumption. It is removed from this table rather than marked "resolved" in place.
