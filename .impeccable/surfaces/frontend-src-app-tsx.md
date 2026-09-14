---
version: 1
slug: "frontend-src-app-tsx"
primary_target: "frontend/src/App.tsx"
related_targets: ["frontend/src/week","frontend/src/design"]
---

# Week board

Scope: the week view, its task rail, the reschedule record, and the task dialog. Visitor mode: Operate.

Audience: the instance owner, at a desktop, app left open all day beside their work. Task: review the plan the scheduler produced, adjust it, and mark work done. Constraint: a second audience (portfolio reviewers) must be able to see the rescheduling mechanism without a walkthrough. No authentication exists, so there is no login, account menu, or user identity anywhere.

Chosen direction: The Theatre Board (assigned by the roll; the round closed unanswered and the user then said to continue, so this was taken unattended and disclosed).

## Direction contract

THESIS: A schedule is a board of strips, and the strip that moved leaves a mark. This refuses the calendar grid's premise that a week is a passive container of coloured events; the board keeps the evidence of its own re-laying, so "what changed since I last looked" is answered before anything is read.

OWN-WORLD: Vitreous enamel on steel as the ground - deep slate-teal in dark, pale hospital-green in light - ruled with screen-printed white hour lines and hairline half-hours. Work is laminated magnet strips: flat, hard, matte, seated into the grid with a hard edge and no drop shadow or gloss. Chinagraph wax pencil is the annotation hand: ghosts, arrows, margin notes. Aluminium extrusion trims the frame and carries the labelled controls. One saturated vermilion, reserved for at risk. Condensed industrial grotesque in caps for anything screen-printed onto the enamel; tabular numerals everywhere time appears.

STORY: The visitor opens the board and sees, in order: that something moved and where it went, what is on now, and what could not be placed. They adjust by pinning a strip or completing it, and leave trusting that the plan is already correct.

FIRST VIEWPORT: The board fills the frame edge to edge. Aluminium extrusion across the top carries the week's dates screen-printed into the enamel, with the labelled REPLAN lever plate bolted at its right end. Below, five day columns ruled by printed hour lines from the configured working window; blocked periods are bare, unruled enamel. Strips sit at their hours. Where the last replan moved a strip, a chinagraph ghost outline stays at the old position with a drawn arrow to the new one. A narrow strip rail runs down the left holding unracked work - unscheduled and at-risk tasks waiting to be placed. The reschedule record is chinagraph margin text along the bottom trim.

FORM: The Theatre Board, candidate 5 of the ordered grounded list (ATC flight strips, railway train graph, broadcast continuity log, letterpress imposition, theatre board, dead-reckoning plot, streamline diagram). Seed key c0da7f88.

RAISE (from the cassette deck fascia): every action on a strip is a labelled, always-visible control in the board's own vocabulary; nothing hides behind a hover icon.
RAISE (from the phosphor terminal): the reschedule history prints itself in the board's own hand as chinagraph margin annotation, never as toasts or chrome notifications.
RAISE (from the cyclorama plot): every state carries a non-colour mark - a strip notch, a rule weight, a hatch - so the board reads fully without colour.
RAISE (from the dive profile): one time ruler governs every element; nothing floats off-grid, annotations included.

SIGNATURE INTERACTION: The re-seat. On a replan, moved strips lift off the enamel, travel, and seat into their new slot with a settle; the vacated position keeps its chinagraph ghost and arrow until acknowledged. Under prefers-reduced-motion the travel is dropped and the ghost is still drawn.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

- The direction was taken unattended; the user may still switch worlds, which would replace this contract.
- Working-hours and blocked-period editing is not in this surface yet; the board reads the configured values.
