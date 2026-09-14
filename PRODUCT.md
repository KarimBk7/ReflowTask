# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Decided in PROJECT_SPEC.md, not open for redesign:

- Backend: Java + Spring Boot (REST API, `@Scheduled` jobs)
- Database: PostgreSQL in production, H2 in development
- Frontend: React, consuming the REST API as one client among future others
- Deployment: Docker Compose, ARM64-compatible for Raspberry Pi

API-first is a hard architectural commitment: the web frontend is a client of the API, never privileged over it, so a later React Native client needs no backend rewrite.

## Users

A single person planning their own working day — the owner of the self-hosted instance. They work at a desktop browser inside their home network, and the primary scene is a **sit-down planning session**: reviewing what the scheduler laid out, adjusting, and marking work done. The week calendar is the surface they open. Phone access works but is a courtesy layout, not a co-equal one.

A second audience reads this product without using it: **portfolio reviewers and recruiters.** They are not a user to design around, but they set one requirement — the rescheduling mechanism must be legible from the interface alone, without a walkthrough. Real daily use leads; comprehensibility rides along.

Multi-user operation is explicitly out of the MVP.

## Product Purpose

ReflowTask turns a task list into a schedule that repairs itself. Tasks carry an estimated duration as well as a deadline, so the system can place them as concrete time blocks inside the user's working hours. When a block passes without its task being completed, the task is treated as missed and the scheduler replans every remaining open task around it.

Success is that a missed morning does not require manual replanning of the afternoon — the user opens the week view and the plan is already correct.

## Positioning

The differentiator is automatic replanning on miss. Open-source task managers (Vikunja, Wekan, and their kind) are static lists or Kanban boards: a missed task becomes "overdue" and stays the user's problem to resolve. Commercial Focuster does automatic rescheduling but is hosted SaaS.

ReflowTask is the self-hosted, open-source intersection: scheduling intelligence that runs on your own hardware. A neighboring open-source list app cannot truthfully claim it, because it has no duration model and no server-side scheduler.

## Operating Context

- **Deployment scene:** self-hosted on the user's own Raspberry Pi home lab, reached over the local network from any device without installation.
- **Why a server, not a desktop app:** the rescheduling job must run on a clock that is independent of whether any given device is powered on. This forces client–server architecture.
- **Working hours are user-defined:** weekdays plus start/end times, configurable — not a fixed 9–18 assumption. Optional blocked periods (a lunch break) that the scheduler must leave empty.
- **Two rhythms:** the planning session (review and adjust the plan) and the in-day check-in (mark a block done). Both run in the same week view.
- **Replanning is an event with a history**, not a silent mutation. The user must be able to see that a replan happened and what moved.

## Capabilities and Constraints

Confirmed MVP scope:

- Task CRUD: title, optional description, estimated duration, deadline (date + optional time), priority (low/medium/high), status (open / in progress / done).
- Automatic placement: on task creation the system proposes a time block from free slots in the configured working window, avoiding overlap with existing blocks, ordered by deadline ascending then priority descending.
- MVP algorithm is a deliberate greedy pass, not an optimizer. Better algorithms are a later stage and explicitly not an MVP blocker.
- Rescheduling job on a clock (hourly or daily): a block whose end time has passed with its task not marked done counts as **missed** — the block is removed and all open plus missed tasks are replanned.
- Visible replan feedback in the UI: a log, history, or notification.
- Week calendar view of scheduled blocks, plus a list of tasks that are open, unscheduled, or missed. Completion is markable directly from the calendar.
- Working-hours configuration surface.

Explicitly out of MVP: multi-user accounts, external calendar integration (Google Calendar, CalDAV, Nextcloud), mobile app, push notifications, ML-based duration estimation, team collaboration.

**Authentication: none in the MVP, and possibly never.** No login screen, no user menu, no session. Nothing in the UI may imply an account exists.

The security model is the network boundary, and that boundary is a **private VPN (Tailscale or WireGuard)**, not a port-forward. Remote access — including the future mobile client — happens by joining the phone to the same private network as the Pi. The instance is never exposed to the public internet, so the device is authenticated at the network layer and app-level login is not required for sync to work. The README must state this as the deployment model, not as an afterthought.

Login therefore ranks as a **low-priority later stage**, wanted if the project gets that far, and never a reason to bend an early decision. The scheduler and the week view come first.

**Three forward-compatibility constraints, adopted now because they are free and make auth-later a day's work instead of surgery:**

1. **Flyway migrations from the first commit.** A later `user_id` column is then a routine migration, not schema surgery.
2. **All endpoints under `/api/v1/`.** A shipped mobile client pins to a version; adding versioning after clients exist is the expensive mistake.
3. **No "the single user" assumption leaks into the frontend or the API contract.** Absence of an owner is not the same as an owner who is implicit everywhere.

Undecided, not to be invented:

- Interface language is **English, kept i18n-ready**: strings live in a translatable structure so German can be added later without refactoring. The existing spec being in German does not make German the UI language.
- Exact scheduler heuristic beyond the greedy MVP pass.
- Whether the user can pin or lock a block against replanning.

## Brand Commitments

The name **ReflowTask** is binding, and it carries the product's central metaphor: the schedule *reflows*. The domain terms the product should use consistently are **task**, **time block**, **missed**, and **reschedule / replan**.

No logo, wordmark, palette, typeface, or visual reference exists yet. Nothing about the look has been committed.

## Evidence on Hand

- `PROJECT_SPEC.md` — the original briefing, in German. The authority for scope and stack.
- `LICENSE` — open source.
- Referenced context: the [Selfhosted Wish List](https://github.com/tony4212/Selfhosted-Wish-List) issue this idea came from, and [Focuster](https://www.focuster.com) as the commercial comparison.

No code, screenshots, or visual goldens exist yet — this is a greenfield build.

Nothing else may be fabricated. There are **no** users, testimonials, install counts, GitHub stars, benchmarks, uptime figures, screenshots, or press. No hosted demo instance exists.

## Product Principles

1. **The plan is always already correct.** The user's job is to review and adjust, never to repair. If the interface ever asks the user to manually clean up after a missed task, the product has failed its reason for existing.
2. **Replanning is visible, never silent.** A schedule that rearranges itself without evidence is untrustworthy. Every replan is an event the user can see and understand.
3. **Duration is first-class, alongside deadline.** Every task surface treats estimated duration as core data, not an optional extra field — it is what makes scheduling possible at all.
4. **The API is the product; the web UI is a client.** No capability may exist only in the frontend.
5. **Honest about the greedy MVP.** The scheduler is a simple heuristic and the interface should not imply optimization it does not perform.

## Accessibility & Inclusion

No product-specific requirement was established beyond ordinary standards. The week grid is a dense, time-based layout, so keyboard operation and non-color encoding of priority and status will need deliberate attention when it is designed.
