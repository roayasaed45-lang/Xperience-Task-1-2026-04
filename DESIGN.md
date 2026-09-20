# Event RSVP Manager — Design

## 1. Problem Statement

Event hosts need a way to understand invitees' attendance intentions and manage limited event capacity, while invitees need a simple way to record and update that intention before the event happens.

**Success Conditions:**
- Hosts can reliably know who is attending, not attending, or undecided at any point before the event.
- Invitees can update their RSVP before the event starts while the event remains open to responses. Behavior while responses are closed or the event is cancelled remains governed by the unresolved lifecycle questions (see Section 4/15, Q11, Q14).
- Capacity limits (when set) are respected — confirmed attendees never exceed max-capacity.
- Invitees who cannot be confirmed due to capacity are placed on a waitlist rather than turned away.
- Waitlisted invitees are automatically confirmed when a confirmed attendee's status changes to "No."
- After the event's start time, no further RSVP changes are possible.

---

## 2. Goals and Non-Goals

### Goals
1. A user can create an event with title, description, date/time, location, and optional max-capacity; the creator becomes that event's host.
2. The host can invite people by email; each invitee can respond Yes / No / Maybe via a unique link.
3. An invitee can change their RSVP at any point before the event starts (subject to the event's open/closed/cancelled state — see Section 4, Q11 and Q14).
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
| A1 | Host is some identifiable entity with authorization rights over their event | Needed to reason about who may cancel/close | No basis for authorization checks |
| A2 | Only "Yes" counts toward max-capacity, not "Maybe" | Needed to define "reached" | Capacity/waitlist logic changes (Q1) |
| A3 | Unique link is the sole invitee identity mechanism | Needed to scope invitee workflows | Entire invitee trust model changes (Q2) |
| A4 | Each event has exactly one host | Needed for ownership model | Ownership/authorization model changes (Q4) |
| A5 | Close and Cancel are permanent, one-way transitions | Needed to define the event lifecycle | State machine needs extra transitions (Q6) |
| A6 | Waitlist ordering is FIFO | Needed to define promotion | Promotion selection logic changes (Q7) |
| A7 | One invitee email = at most one RSVP per event | Needed for capacity/attendee-count correctness | Counting semantics change (Q8) |
| A8 | The RSVP design will not depend on or reuse the unrelated bulk-messaging / `wasender` code unless repository intent is clarified | Needed to scope the feature without presuming what happens to unrelated code | N/A — this is a non-dependence stance, not a prediction; repository cleanup/removal itself remains unresolved |

**Note on A3 and A6:** both remain working assumptions only, not settled decisions. A3 is used only to let invitee workflows proceed; the final invitee identity model is unresolved (Q2). A6 is used only to let promotion logic proceed; the final waitlist ordering policy is unresolved (Q7).

### Open Questions

| ID | Question | Why it matters |
|---|---|---|
| Q1 | Does "Maybe" count toward capacity? | Defines when max-capacity is "reached" |
| Q2 | Do invitees need an account, or is the link sufficient? | Defines the entire invitee trust/security model |
| Q3 | Does the host require authentication? | Blocks enforcement of host-only actions |
| Q4 | Can an event have more than one host? | Affects ownership and authorization; blocks the final ownership/scope model |
| Q6 | Can a closed/cancelled event be reopened? | Affects the event lifecycle state machine |
| Q7 | What is the waitlist ordering policy? | Determines who is promoted first |
| Q8 | Are duplicate invitations to the same email allowed? | Affects capacity/attendee-count correctness |
| Q9 | Do invitation links expire? | Affects link security lifetime |
| Q11 | What exactly distinguishes "Cancelled" from "Closed"? | Needed to fully specify I5 and W13 |
| Q12 | What timezone rules apply to event start time? | Affects exactly when lock-after-start (I2) triggers |
| Q13 | Does promotion trigger only on confirmed→No, or any freed spot? | Affects scope of the promotion invariant (I3) |
| Q14 | Does "closed to further responses" block only new RSVPs (W3), or also changes to existing RSVPs (W4)? | Affects the enforcement scope of I4 |
| — | React 19 (README) vs. React 18.3.1 (`package.json`) — which is authoritative? | Affects frontend implementation target |
| — | What is the intent behind the existing unrelated bulk-messaging/`wasender` code? | Affects repository cleanup scope |
| — | Is invitation delivery synchronous or asynchronous? | Affects host-facing latency and delivery reliability design |
| — | Is editing event details after creation permitted? | Affects event-editing scope, if ever requested |

None of these are resolved in this document.

---

## 5. Actors and Workflows

### Actors

**Host** — creates and owns an event; wants to know who is coming and retain control over the response window. May invite people, view the attendance dashboard, close responses, and cancel the event. Whether the host must be authenticated is unresolved (Q3). Whether an event may have more than one host is unresolved (Q4).

**Invitee** — receives a unique link; wants to indicate attendance and change their mind before the event starts. May open the link and respond Yes/No/Maybe, and change that response before start. Access is via the link only (A3, unresolved per Q2).

No other human actors exist. Capacity evaluation, waitlist promotion, and RSVP locking are automatic **system** behavior, not actors — see the Actor/Initiator column below.

### Workflows

| Workflow | Actor/Initiator | Trigger | Preconditions | Major Steps | State Changes | Dependencies | Failure/Blocked Path | Related Open Items |
|---|---|---|---|---|---|---|---|---|
| Event creation | Host | Host decides to create an event | None stated | Provide fields → submit | Event exists; creator = host | None (entry point) | Missing/invalid fields not specified | A1, Q3, Q4 |
| Invitation | Host | Host invites by email | Event exists | Provide email → unique link created | New Invitation exists, unanswered | Depends on Event creation | Duplicate invite / invite-after-close not specified | A7, Q8 |
| RSVP submission | Invitee | Invitee opens link, responds | Valid link; event open; not started | Select Yes/No/Maybe → submit | Response set; if Yes, triggers Capacity Evaluation | Depends on Invitation | Blocked if locked/closed/cancelled | A2, A3, Q1 |
| RSVP change | Invitee | Invitee revisits link, changes response | Event not started | Select new response → submit | Response changes; may trigger Capacity Evaluation or Promotion | Depends on prior RSVP submission | Blocked after start (I2) | Q13, Q14 |
| Dashboard | Host | Host opens dashboard | Host recognized for event | View counts + attendee list | None (read-only) | Depends on Event creation; reflects RSVP/Promotion state | Not specified for cancelled events | "Live" definition unresolved |
| Close | Host | Host closes responses | Event exists | Issue close action | Event enters "closed" | Independent; feeds into RSVP-blocked path | Reversibility unresolved | A5, Q6, Q11, Q14 |
| Cancel | Host | Host cancels the event | Event exists | Issue cancel action | Event enters "cancelled" | Independent; feeds into RSVP-blocked path | Reversibility unresolved | A5, Q6, Q11 |
| Capacity Evaluation | **System** | Response becomes "Yes" (occurs inside RSVP submission/change processing, not a separate entry point) | Max-capacity set | Compare confirmed count to capacity | Confirmed or Waitlisted | Internal consequence of RSVP submission/change | None stated | A2, Q1 |
| Waitlist Promotion | **System** | Confirmed attendee changes to "No" (occurs inside RSVP change processing) | Waitlist non-empty | Select and promote one waitlisted invitee | One invitee becomes Confirmed | Internal consequence of RSVP change | None stated for the named case | A6, Q7, Q13 |
| Lock-after-start evaluation | **System** | Evaluated at the moment of each RSVP attempt (not a proactive background process) | Event has a start time | Compare current time to start time | Event treated as locked if past start (derived, not stored) | Internal consequence, evaluated within RSVP submission/change processing | Produces the RSVP-blocked path | Q12 |
| RSVP attempt after start | Invitee | Invitee attempts change post-start | Lock condition true | Attempt rejected | None | Depends on lock condition | Expected result: rejected | Q12 |
| RSVP attempt after close | Invitee | Invitee attempts response after close | Event closed | Attempt blocked | None | Depends on Close | Expected result: blocked; scope (W3 vs W4) open | Q14 |
| RSVP attempt on cancelled event | Invitee | Invitee attempts response after cancel | Event cancelled | Not specified by the task | Unknown | Depends on Cancel | Fully unresolved — no asserted control | Q11 |
| Invalid/unusable link | External requester / Invitee | Anyone opens a malformed/unrecognized link | N/A | Not specified | Not specified | N/A | Not fully specified | Q9 |

No new human actor is introduced by this table — "System" denotes automatic backend behavior triggered as a consequence of an existing request, not an independent actor.

---

## 6. Invariants

### Business Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I1 Capacity | Confirmed attendees never exceed max-capacity | New Yes accepted as confirmed while full | Concurrent/near-full Yes submissions | RSVP submission/change, Capacity Evaluation | Capacity check before confirming | Fact + Working Assumption A2 (Q1 open) |
| I2 Lock-After-Start | No RSVP may be created/changed after start time | RSVP accepted at/after start | Submission near the time boundary | RSVP submission/change, Lock-after-start evaluation | Server-side time check at submission | Fact + Open Question (Q12 timezone) |
| I3 Waitlist Promotion | Confirmed→No with available waitlist must promote one invitee | Spot frees up, nobody/multiple promoted | Confirmed attendee changes to No | RSVP change, Waitlist Promotion | Automatic follow-up action | Fact + Working Assumption A6 (Q7, Q13 open) |
| I4 Closed Responses | New RSVP submissions not accepted once closed | New RSVP accepted after close | Submission attempted after Close | Close, RSVP attempt after close | State check before accepting | Fact (scope re: existing RSVPs is Q14, open) |
| I5 Cancelled Event | Cancelled state must remain distinguishable from Closed | Both modeled as one state | Naive single-flag implementation | Cancel, Close, RSVP attempt on cancelled event | Not specified — depends on Q11 | Open Question (Q11) |

### Data Integrity Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I6 RSVP Value | Response must be exactly Yes/No/Maybe, distinct from Attendance Outcome | Value outside the set, or Response/Outcome conflated | Unrestricted input, or missing separation | RSVP submission/change, Capacity Evaluation | Input constraint + conceptual separation | Fact |
| I7 Invitation Scope | A link must correspond to exactly the correct invitation/event | Link resolves to wrong invitee/event | Weak binding | Invitation, RSVP submission/change, invalid link | Not specified — mechanism unresolved | Fact + Open Questions (Q8, Q9) |
| I8 Host/Event Ownership | Every event has its creator recorded as host | Event with no/incorrect host | Creation path skips assignment | Event creation | Assignment at creation time | Fact |

### Authorization Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I9 Host-Only Actions | Only the recognized host may invite, view dashboard, close, or cancel | Non-host performs a host-only action | No verification of requester vs. recorded host | Invitation, Dashboard, Close, Cancel | Authorization check pending host authentication | Fact + Open Question (Q3, unresolved) |
| I10 Invitee Scope | An invitee interaction must only affect their own invitation/RSVP/event | Link crosses into another invitee's or event's data | Weak link binding | RSVP submission/change, invalid link | Scoping check pending invitee identity model | Fact + Open Question (Q2, unresolved) |

### Concurrency Invariants

| Name | Statement | Break Scenario | Trigger | Workflows Affected | Protection Note | Source |
|---|---|---|---|---|---|---|
| I11 Last-Spot Concurrency | At most one of two simultaneous last-spot Yes's may be confirmed | Both read stale count, both confirm, exceeding capacity | Concurrent Yes submissions at capacity−1 | RSVP submission/change, Capacity Evaluation | Concurrency control (mechanism deferred to Section 10) | Fact, newly surfaced as a concurrency consequence of I1 |
| I12 Waitlist Promotion Concurrency | Same invitee not promoted twice; promotion stays within capacity and deterministic | Two near-simultaneous No's double-promote or lose a spot | Concurrent confirmed→No transitions | RSVP change, Waitlist Promotion | Concurrency-safe promotion (mechanism deferred) | Fact + A6 + Q7/Q13 |

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

**Ownership Boundaries:** Event/RSVP business rules, capacity/promotion decisions, and authorization decisions belong to Business Logic. Business Logic makes and coordinates these business decisions. **The Persistence Boundary and PostgreSQL participate in enforcing and durably preserving those decisions — including integrity and concurrency guarantees — but they do not independently decide business policy.** UI state belongs to the Frontend; delivery belongs to the Invitation Delivery Boundary. The one soft edge: the Request Boundary must only carry an identity claim forward — it must never itself decide authorization.

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

| Concept | Source of Truth | Mutated By | Read By | Derived State | Lifecycle Note |
|---|---|---|---|---|---|
| Event core details | PostgreSQL | Business Logic | Frontend, Business Logic | None | **Initially written at creation; post-creation mutation remains unresolved** (see Section 4/15) |
| Host ownership | PostgreSQL | Business Logic, at creation | Business Logic (I9) | None | Assigned once; whether an event may have more than one host is unresolved (Q4) |
| Event lifecycle/status | PostgreSQL | Business Logic, via Close/Cancel | Business Logic, Frontend | "Start reached" is derived from stored start time, not stored | Open→Closed/Cancelled known; reverse transitions unresolved (Q6, Q11) |
| Invitation | PostgreSQL | Business Logic, on invite | Business Logic, Delivery Boundary | None | Duplicate policy (Q8) and expiry (Q9) unresolved |
| RSVP Response (Yes/No/Maybe) | PostgreSQL | Business Logic, only on invitee request, subject to I2/I4/I5 | Business Logic, invitee's own view, host dashboard | None | See RSVP Lifecycle below |
| Attendance Outcome (Confirmed/Waitlisted/None) | PostgreSQL | Business Logic only (never invitee/frontend) | Business Logic, invitee's own view, host dashboard | Derived from Response + capacity state, then persisted | None→Confirmed/Waitlisted; Waitlisted→Confirmed on promotion |
| Waitlist ordering | Likely derivable from entry order (FIFO working assumption, A6 — unresolved, Q7) | Business Logic, on promotion | Business Logic | Likely derived, not separately stored | Ordering policy unresolved (Q7) |
| Confirmed attendance count | Derived (not stored) | N/A | Business Logic (I1) | Derived from Attendance Outcome | Avoids a duplicate source of truth for I1 |
| Dashboard counts | Derived (not stored) | N/A | Host | Derived from Response + Attendance Outcome | Subject to display staleness only — never feeds capacity decisions |
| Event start lock condition | Derived from stored start time vs. current time | N/A | Business Logic, on every RSVP attempt | Fully derived | No persisted `isLocked` flag; timezone rules unresolved (Q12) |
| Frontend state | None — not authoritative | Frontend itself | Frontend/user | N/A | Transient; can go stale relative to backend |

### Event Lifecycle
Created → Open (default) → Closed or Cancelled (host actions, stored). "Start reached" is a derived time condition, not a stored flag. Unresolved: reopening (Q6), Cancel-vs-Close distinction (Q11), whether Close also blocks changes to existing RSVPs (Q14), whether event details may be edited after creation.

### RSVP Lifecycle
**Response** {Yes, No, Maybe} and **Attendance Outcome** {Confirmed, Waitlisted, None} are kept strictly separate and must remain separate throughout this document.

**Response changes are allowed before the event start time while the event remains open. Behavior while responses are closed is governed by I4 and Q14; behavior while the event is cancelled remains unresolved under Q11.** This is intentionally conditional, not an unconditional freedom to change RSVPs at any time.

Outcome transitions: None→Confirmed/Waitlisted (on Yes); Waitlisted→Confirmed (on promotion, scope of trigger per Q13). Whether a Yes→Maybe transition (not just Yes→No) should free a confirmed spot is unresolved (Q13).

---

## 9. Trust Boundaries and Security Notes

### Trust Entry Points
1. **Host browser → backend** — event fields, invitee email, action requests, any claimed host identity. Frontend input is untrusted; the backend must not accept a host identity claim at face value.
2. **Invitee unique link → backend** — token, RSVP choice, identifiers. Possession of the link is security-sensitive under A3 (a working assumption, not a settled security decision); the client must never directly choose Confirmed/Waitlisted.
3. **Backend → Invitation Delivery Boundary** — email, event info, token. External boundary; must never become authoritative for Invitation validity; `wasender` is not assumed appropriate.

### Authentication vs. Authorization
**Authentication** (proving who is asking) is unresolved for both Host (Q3) and Invitee (Q2) — both remain genuine implementation blockers. **Authorization** (deciding if they may act) is specified independently of that: only the host may invite/view dashboard/close/cancel (I9); an invitee may only affect their own invitation/RSVP/event (I10); even an authorized caller is still blocked by business rules (event started, closed, cancelled, or an invalid transition) — authentication never bypasses invariants.

### Authorization Enforcement
Business Logic makes every allow/deny decision. The Request Boundary may carry an identity claim forward but must not decide authorization itself (the one flagged soft edge in the architecture).

### Sensitive Data / Privileged Operations
Unique invitation tokens function as a credential-equivalent under the A3 working assumption. Invitee email, host identity, event ownership, RSVP Response, and Attendance Outcome are all scoped to the relevant host/invitee only, never to unrelated parties. Privileged operations (invite, dashboard, close, cancel) require host authorization; system-controlled outcomes (Confirm/Waitlist, promotion) must never be directly triggerable by a client request.

### Event-Level Isolation
Tenant isolation is not applicable (Section 6). Event-level isolation is still required: one host cannot manage another host's event (I9); one invitee cannot access another invitee's invitation/RSVP (I10); one token cannot cross event boundaries (I7).

### Sensitive Data Paths
**Invitation path:** Host UI → Backend → Business Logic → PostgreSQL (stores invitation) → Invitation Delivery Boundary → Invitee. Exposure risk points: backend logs, the external delivery provider, and the recipient's inbox — all outside this system's control once handed off.
**RSVP path:** Invitee link → Backend → Business Logic (validates I10 scope, I2/I4/I5 rules, I1 capacity) → PostgreSQL → Host dashboard read (gated by I9).
**Host action path:** Host UI → Backend → host authorization check → Business Logic → PostgreSQL. If the backend trusted a frontend-supplied host/event relationship without verification, any caller could act as host of any event, violating I9.

An RSVP attempt against a cancelled event is left fully unresolved here — no control is asserted, pending Q11.

No authentication framework, session mechanism, or cryptographic implementation is designed here.

---

## 10. Concurrency and Correctness Notes

| Vulnerable Area | Workflow / State | Risk | What Can Go Wrong | Invariant(s) at Risk | Control Note |
|---|---|---|---|---|---|
| Last-Spot Capacity Race | RSVP submission/change → Capacity Evaluation | Check-then-write race | Two concurrent Yes's both read count=9, both confirm, exceeding capacity | I1, I11 | Serialized/atomic capacity decision, scoped per event |
| Waitlist Promotion Race | RSVP change → Waitlist Promotion | Concurrent free+select+promote | Double-promotion, lost freed spot, or wrong invitee selected | I1, I3, I12 | Same serialized boundary as capacity; ordering still open (Q7) |
| Duplicate RSVP | RSVP submission/change | Retry/double-click | Repeated side effects if implemented as append-not-set | I6, I10 | Idempotent update: trigger side effects only on actual value change |
| Conflicting RSVP Update | RSVP change (two requests, same invitation) | Concurrent conflicting writes | Lost update; promotion based on a superseded state | I6, I3 | Version check or per-invitation serialization; no invented "Last Write Wins" |
| Close vs RSVP Race | Close vs. RSVP submission/change | Stale status read | RSVP accepted just after close | I4 | Re-read authoritative status at write time; scope (Q14) unresolved |
| Start-Time Boundary | RSVP submission/change vs. Lock-after-start evaluation | Client/server clock mismatch, latency | RSVP accepted after start due to stale client state | I2 | Server-side time check at moment of processing; timezone unresolved (Q12) |
| Cancel vs RSVP Race | Cancel vs. RSVP submission/change | Stale lifecycle state | Inconsistent evaluation near cancellation | I5 | Re-read authoritative status at write time; outcome unresolved (Q11) |
| Partial Database Failure | Event creation, Invitation creation, RSVP+Outcome, Promotion | Multi-part write partially succeeds | Response without Outcome; Invitation without token; Event without host | I6, I7, I8, I3 | Each causally-linked pair in one transaction |
| External Invitation Delivery Side Effect | Invitation | External call not transactional with DB | Delivery failure or DB failure after send | I7 | Invitation validity determined solely by PostgreSQL, never delivery outcome |

### First-Pass Concurrency Decision
**Current choice:** a per-event pessimistic/serialized correctness boundary around the capacity decision. **This simplifies correctness reasoning only when every capacity-changing path uses the same protected boundary — locking alone does not automatically guarantee correctness if any path bypasses it.** Optimistic version-based concurrency remains a reasonable later alternative if contention becomes a measured concern; it is not chosen now, for simplicity and explainability.

### Transaction Boundaries
Five causally-linked write groups must succeed/fail together: (1) Response + capacity decision + Outcome, (2) Confirmed→No + promotion, (3) Event creation + host assignment, (4) Invitation creation + token relationship, (5) Close/Cancel state check-then-write.

### Correctness Guarantees
1. Confirmed attendance never exceeds max-capacity (depends on Q1 for full correctness).
2. At most one invitee can claim the final confirmed spot (mechanism-dependent only).
3. Waitlist promotion cannot promote the same attendee twice (depends on Q7).
4. RSVP changes after event start are rejected (depends on Q12).
5. One invitation must not create conflicting authoritative RSVP state (depends on Q8).
6. Partial database failures must not leave state inconsistent (mechanism-dependent only).
7. Client/frontend state is never authoritative for concurrency decisions (settled principle).
8. Display staleness must never become business-state staleness (settled principle).

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
| Waitlist promotion race | Double/lost/wrong promotion | Multi-step transition races | Shared transaction boundary; ordering unresolved (Q7) |
| Partial database update | Half-completed linked writes | No shared transaction | Transactional writes per Section 10 |
| Duplicate RSVP submission | Same action processed twice | No dedup on repeated requests | Single authoritative Response + idempotent side effects |
| Conflicting RSVP updates | Lost update / stale-based promotion | Concurrent writes to one Response | Version check or serialization; no invented Last-Write-Wins |
| **Host authentication unresolved** | Host-only actions unenforceable | Identity mechanism unresolved (Q3) | **No mitigation exists — hard blocker** |
| **Invitee-link trust risk** | Non-invitee acts as invitee | Link = identity under A3 | Scope limited to one invitation; Q2/Q9 unresolved |
| **Multiple-host ambiguity** | Ownership/authorization model may not match actual usage | Q4 unresolved | Kept explicit as an open question, not decided |
| Close vs RSVP race | RSVP accepted on stale status | No re-check at write boundary | Re-check status at write time; scope (Q14) unresolved |
| **Cancel vs RSVP ambiguity** | Inconsistent behavior near cancellation | Cancel semantics unresolved (Q11) | Kept explicit, not invented |
| **Event-start timing ambiguity** | Inconsistent lock enforcement | Timezone semantics unresolved (Q12) | Server-side authoritative time; policy open |
| Stale dashboard data | Host sees outdated counts | Derived read, another RSVP changes after | Acceptable if never reused for capacity decisions |
| **Invitation delivery failure** | Email not delivered | External dependency, not transactional with DB | Invitation validity stays PostgreSQL-determined |
| Wrong recipient / invitation pairing | Link sent to wrong person | Sensitive pairing error | Backend must construct pairing correctly before handoff |
| Duplicate invitation ambiguity | Duplicate invitation state | Q8 unresolved | Uniqueness rule deferred until Q8 decided |
| Email/token logging exposure | Token/email leaks via logs | Token = credential-equivalent under A3 | Avoid ordinary logging of raw tokens |
| PostgreSQL unavailable | All core workflows fail | Sole authoritative store, no fallback | Fail honestly; frontend never becomes truth |
| External invitation provider unavailable | Email delivery fails | Separate external system availability | Core RSVP/event state unaffected (decoupled) |
| **Repository mismatch risk** | Implementation built on wrong assumptions | React version mismatch, unrelated scaffold code | Kept visible as cleanup decisions, not resolved |

**Assumption Failure Risks:** if A2, A3, A4, A6, or A7 turn out false, the capacity logic, invitee trust model, ownership model, promotion logic, or capacity/attendee-counting semantics respectively would need to change (see Section 4 for each assumption's dependency).

None of the unresolved product questions above (Q1, Q3, Q4, Q6–Q9, Q11–Q14) are treated as bugs — they are explicit open decisions, not defects.

---

## 13. Alternatives and Tradeoffs

| Decision Area | Status | Position |
|---|---|---|
| Capacity concurrency strategy | **Chosen** | Per-event pessimistic/serialized control, over optimistic version-based or database-atomic alternatives, for simplicity and explainability |
| RSVP state model | **Chosen** | Separate Response and Attendance Outcome, over a combined enum, to keep intent and system decision distinct |
| Confirmed/dashboard counts | **Chosen** | Derived from authoritative state, over stored running counters, to avoid a duplicate source of truth |
| Event-start lock | **Chosen** | Derived from stored start time vs. current time, over a persisted flag, to avoid inventing background infrastructure |
| Invitation/RSVP data shape | **Deferred / first-pass** | Leaning toward separate Invitation and RSVP concepts, matching the natural lifecycle; not fully re-examined at a schema level |
| **Invitee identity** | **Working assumption; final model unresolved** | Link-only access (A3) is used to let the design proceed; whether this is sufficient or an account is required is **not decided** (Q2) |
| **Host identity** | **Unresolved, implementation-blocking** | Neither an authenticated account nor a possession-based secret has been chosen (Q3) |
| **Multiple hosts** | **Unresolved** | Whether an event may have more than one host is not decided (Q4); single-host is used only as a working assumption (A4) |
| **Waitlist ordering** | **Working assumption; ordering policy unresolved** | FIFO (A6) is used as the simplest interpretation of "automatically," but the actual policy is **not decided** (Q7) |
| **Duplicate invitation policy** | **Deferred / unresolved (Q8)** | The task does not specify whether the same email may receive more than one invitation for the same event. Disallowing duplicates simplifies RSVP/capacity identity and counting but requires an explicit uniqueness rule; allowing duplicates is more flexible but requires explicit semantics for capacity and attendee counting. **Not decided here.** |
| Invitation delivery execution | **Deferred / unresolved** | Neither synchronous nor asynchronous delivery is chosen |
| Event lifecycle representation | **Chosen (representation only)** | A single lifecycle status + derived start-lock, to make contradictory states harder to represent — this decides *how state is stored*, not *what Close/Cancel mean* (Q11 remains separately open) |
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
1. **Core domain/state** — the minimum persistent concepts: Event, Invitation, RSVP Response, Attendance Outcome.
2. **Core workflows** — event creation, invitation creation, RSVP submission/change, host dashboard.
3. **Correctness controls** — capacity, waitlist, promotion, lock-after-start, close/cancel rules.
4. **Identity/security** — the chosen host/invitee identity model, once Q2/Q3 are resolved.
5. **Invitation delivery** — the chosen email-delivery mechanism, once its sync/async model is decided (no provider introduced here).
6. **Validation and edge cases** — failure paths, concurrency scenarios, invalid token behavior, boundary-time behavior, and any open questions resolved along the way.

**"Implemented" does not mean "safe to expose."** Host-only workflows built in step 2 (invite, dashboard) and Close/Cancel in step 3 must not be exposed to real users before step 4 resolves Q3; invitee-facing RSVP workflows must not be exposed before Q2 is resolved; capacity-sensitive RSVP submission must not be exposed before step 3's concurrency controls are actually implemented, not just designed.

### Rollout Dependencies / Blockers
Q3 (host auth) blocks invite/dashboard/close/cancel. Q2 (invitee identity) blocks the final security design for RSVP submit/change. Q4 (multiple hosts) blocks the final ownership/scope model. Q7 blocks deterministic promotion. Q1 blocks the final capacity rule. Q11 blocks final lifecycle behavior. Q12 blocks final lock-after-start correctness. Q8 blocks the final uniqueness rule. The delivery execution model blocks final delivery integration.

### Existing Repository Coexistence
Leaving the unrelated bulk-messaging code in place risks confusion/accidental coupling; removing it risks deleting something intentionally retained. **Deferred until repository intent is clarified** — not silently deleted or reused.

### Frontend Version Mismatch
Implementing against the installed React 18.3.1 avoids repository churn but diverges from the README; upgrading to React 19 aligns with the README but introduces an unrelated repository change and a compatibility check against the existing bulk-messaging components. **Unresolved until clarified.**

### Backward Compatibility
No existing released RSVP API/data model requires compatibility. Existing unrelated repository behavior should not be broken accidentally.

### Rollback Considerations
Before real data exists, rollback is a low-stakes code/schema revert. Once real RSVP data exists, destructive rollback risks losing or invalidating it — the migration strategy would need more rigor if this becomes a real deployed system. No production backup infrastructure is designed.

### Feature Rollout Safety
Before considering the feature complete, verify: host authorization (I9), invitee scope (I10), the capacity invariant (I1), the last-spot concurrency scenario (I11), waitlist promotion (I3), safe duplicate-request behavior, lock-after-start (I2), close/cancel behavior matching whatever Q11 eventually resolves to, invitation-delivery failure not corrupting core state, and dashboard counts deriving from authoritative state.

---

## 15. Unresolved Issues

| ID | Open Question | Why It Matters | Blocks |
|---|---|---|---|
| Q1 | Does "Maybe" count toward capacity? | Defines when max-capacity is "reached" | Final capacity rule (I1) |
| Q2 | Invitee identity: link-only or authenticated account? | Defines the entire invitee trust/security model | Final RSVP submit/change security design |
| Q3 | Does the host require authentication? | Nothing enforces I9 without it | All host-only workflows (invite, dashboard, close, cancel) |
| Q4 | Can an event have more than one host? | Affects ownership and authorization | Final ownership/scope model |
| Q6 | Can a closed/cancelled event be reopened? | Affects the event lifecycle state machine | Final lifecycle transitions |
| Q7 | What is the waitlist ordering policy? | Determines who is promoted first | Deterministic promotion (I3, I12) |
| Q8 | Are duplicate invitations to the same email allowed? | Affects capacity/attendee-count correctness | Final uniqueness rule |
| Q9 | Do invitation links expire? | Affects link security lifetime | Invitee-link trust risk mitigation |
| Q11 | What exactly distinguishes Cancelled from Closed? | Needed to fully specify I5 and W13 | Final event lifecycle behavior |
| Q12 | What timezone rules apply to event start time? | Affects exactly when I2 (lock) triggers | Final lock-after-start correctness |
| Q13 | Does promotion trigger only on confirmed→No, or any freed spot? | Affects scope of I3 | Final promotion logic |
| Q14 | Does "closed to further responses" block only new RSVPs, or also changes to existing ones? | Affects enforcement scope of I4 | Final close-behavior implementation |
| — | React 19 (README) vs. React 18.3.1 (installed) — which is authoritative? | Affects frontend implementation target | Frontend rollout decision |
| — | Intent behind the existing unrelated bulk-messaging/`wasender` code | Affects repository cleanup scope | Repository coexistence decision |
| — | Invitation delivery: synchronous or asynchronous? | Affects host-facing latency and delivery reliability design | Delivery integration design |
| — | Is editing event details after creation permitted? | Affects event-editing scope, if ever requested | Event data ownership finalization |

Each question above appears exactly once in this table and is not contradicted by any other section of this document.
