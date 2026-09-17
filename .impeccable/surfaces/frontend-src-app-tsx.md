---
version: 1
slug: "frontend-src-app-tsx"
primary_target: "frontend/src/App.tsx"
related_targets: ["frontend/src/week","frontend/src/design"]
---

# Week board

Scope: the week view, its sidebar (needs attention, activity), the task and block popovers, drag editing, and the hours editor with first-run setup. Visitor mode: Operate.

Audience: the instance owner in a sit-down planning session at a desktop, app left open beside their work. Task: review the plan the scheduler produced, adjust it by dragging or clicking, add work by clicking free time, and mark work done. Constraint: portfolio reviewers must see the reschedule mechanism without a walkthrough. No authentication: no login, account menu or identity anywhere.

Chosen direction: Clean & calm, pinned by the user in words ("Notion Calendar / Amie"). This is the category standard taken by the user, so no direction roll ran; convention is the commitment, executed at the craft level of Notion Calendar and Amie. It replaces the Theatre Board world, which was taken unattended.

## Direction contract

THESIS: A calm week calendar that is honest about its own changes. It refuses the auto-scheduler default of silent reshuffling: every block the last replan moved says where it came from, its old slot keeps a faint outline, and blocks glide to their new place instead of jumping.

OWN-WORLD: Cool light neutral page, white board, hairline rules, generous whitespace; soft charcoal in dark. One workhorse grotesque (bundled Archivo) with tabular numerals. Blocks are 8px-radius soft tinted fills with a 1px tinted edge, title and time only, never buttons. Priority is tint plus a bar glyph; at risk is the only red; "changed" is a soft amber ring and chip; indigo is reserved for primary actions and today.

STORY: The owner sees what moved and why, what is full, and what needs attention; they drag a block to fix it, click free time to add work, and trust the plan.

FIRST VIEWPORT: Slim top bar: wordmark, week range, prev/today/next, then Replan, Hours, and the indigo New task button at the right. Left sidebar: Needs attention, then Activity. The week grid fills the rest: day headers carry date and a workload meter; blocks sit at their hours.

FORM: Category canon (Notion Calendar / Amie), the user's standing-exit choice; no seed key.

SIGNATURE INTERACTION: Direct manipulation with visible consequences. Click empty time opens a popover prefilled with that time (Fixed here, or Let ReflowTask place it); drag moves or resizes a block and pins it; the rest of the week glides into its new plan (FLIP), with moved chips and old-slot outlines. Under prefers-reduced-motion the glide is dropped, the marks remain.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

- Plain-language quick add was offered and not chosen.
