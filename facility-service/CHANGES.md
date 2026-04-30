# Facility Service — Post-Review Changes

This document records issues identified during the architecture review
and the fixes applied. Each entry describes what was wrong, what was
changed, and where.

---

## R2 — FacilityStatus State Machine Was Too Permissive

**What was wrong**

`canTransitionTo()` in `FacilityStatus.java` only blocked transitions
out of RETIRED. Every other transition was silently permitted regardless
of business rules — OCCUPIED could transition to MAINTENANCE, RESTRICTED
could transition to OCCUPIED, and a status could even transition to
itself. The dead branch `if (this == MAINTENANCE && target == AVAILABLE)
return true` suggested stricter rules were originally intended but never
completed.

**What was changed**

An explicit transition matrix was added to `canTransitionTo()`. AVAILABLE
is the only hub state. All other states can only return to AVAILABLE or
go directly to RETIRED.

| From | Permitted |
|---|---|
| AVAILABLE | OCCUPIED, MAINTENANCE, RESTRICTED, RETIRED |
| OCCUPIED | AVAILABLE, RETIRED |
| MAINTENANCE | AVAILABLE, RETIRED |
| RESTRICTED | AVAILABLE, RETIRED |
| RETIRED | None — terminal |

Two new unit tests added to verify every legal and illegal pair.

**Files changed**

- `src/main/java/com/plassey/facilityservice/domain/model/FacilityStatus.java`
- `src/test/java/com/plassey/facilityservice/domain/FacilityAggregateTest.java`

---

## R4 — No Optimistic Locking on Facility Entity

**What was wrong**

`FacilityJpaEntity` had no `@Version` field. Two admins updating the same
facility simultaneously would produce a silent lost-update — the second
write would overwrite the first with no error and no indication anything
went wrong.

**What was changed**

`@Version private Long version` added to `FacilityJpaEntity`. Spring Data
JPA now enforces optimistic locking on every update. A concurrent write
that arrives with a stale version is rejected with a 409 Conflict.

`FacilityRepositoryImpl.save()` was also updated to carry the existing
version forward from the database before each write. This was necessary
because the domain-to-JPA mapper does not map the version field (version
is a persistence concern, not a domain concern) — without this fix the
version would be null on every save, breaking the locking mechanism.

**Files changed**

- `src/main/java/com/plassey/facilityservice/infrastructure/persistence/entity/FacilityJpaEntity.java`
- `src/main/java/com/plassey/facilityservice/infrastructure/persistence/repository/FacilityRepositoryImpl.java`

---

## R9 — Invariant IDs Used Wrong Prefix in Facility Context

**What was wrong**

The Facility context used `INV-L4` and `INV-L5` in Javadoc comments,
exception messages, and test assertions. The L-prefix belongs to the
Notification and Logging context (LogEntry invariants). These were
copied incorrectly from the LogEntry section during authoring and
propagated into the runtime exception messages, meaning a failing
invariant would report the wrong ID.

**What was changed**

All occurrences of `INV-L4` renamed to `INV-F4` and `INV-L5` renamed
to `INV-F5` within Facility context files. The genuine `INV-L5`
reference in the Notification and Logging context is unchanged.

**Files changed**

- `src/main/java/com/plassey/facilityservice/domain/model/FacilityStatus.java` — line 12
- `src/main/java/com/plassey/facilityservice/domain/model/Facility.java` — lines 18, 19, 131, 137, 147
- `src/test/java/com/plassey/facilityservice/domain/FacilityAggregateTest.java` — lines 58, 71, 97

---

## R1 — Idempotency-Key Strategy Documented Incorrectly

**What was wrong**

The design document (A4.2) claimed the `Idempotency-Key` header was
cached for 24 hours and that duplicate POST requests would return the
cached response. The controller accepts the header but the service
ignores it — there is no cache, no store, and no TTL. A retried POST
with a different facility name would still create a duplicate.

**What was changed**

Document updated to describe the as-built behaviour accurately:
duplicate creation is currently prevented by the name-uniqueness
constraint (INV-F8) which returns 409 Conflict on retried requests
with the same name. Full idempotency-key caching is noted as deferred.

**Files changed**

- Document only — no code change

---

## R6 — Inbound ACL Described as a Separate Class but Implemented Inline

**What was wrong**

The design document (A4.2) described a standalone
`FacilityRequestTranslator` class responsible for the inbound ACL —
name normalisation, enum parsing, status transition validation, and
malformed payload rejection. This class does not exist. The behaviour
is implemented correctly but lives as private static methods on
`FacilityApplicationService`.

**What was changed**

Document updated to reflect the as-built structure. The behaviour is
correct and fully tested — only the description was inaccurate.

**Files changed**

- Document only — no code change

---

## R7 — Notification Service Listed as Consumer but Has No Listener

**What was wrong**

The design document (A4.2) listed the Notification Service as a
consumer of all four Facility domain events (FacilityCreated,
FacilityUpdated, FacilityStatusChanged, MaintenanceScheduled). The
Notification Service has no `@RabbitListener` bound to
`facility.events` — its only listener subscribes to `booking-events`.

The Facility Service publishes correctly to the `facility.events`
Topic exchange with the correct routing keys. This is a cross-team
contract gap on the consumer side, not a defect in the Facility Service.

**What was changed**

Document updated to acknowledge the gap. Flagged to the Notification
Service owner for resolution.

**Files changed**

- Document only — no code change

---

## R8 — RETIRED Exclusion from Default Search

**What was wrong**

The review flagged that RETIRED facilities might surface in default
search results, which would show decommissioned rooms in any UI that
does not know to filter them out.

**Finding**

Already correctly implemented. `FacilityJpaRepository` contains
`AND status <> 'RETIRED'` in both the `search` and `countSearch`
queries. No change was needed.

---

## Test Results After All Fixes
<img width="1103" height="268" alt="Screenshot 2026-04-30 201336" src="https://github.com/user-attachments/assets/66ab5a4c-5131-4349-824f-3707cbfd3cdd" />
