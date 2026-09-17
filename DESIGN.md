---
name: ReflowTask
description: A calm week calendar that is honest about its own changes.
colors:
  accent: "#4253d4"
  accent-hover: "#3544bd"
  accent-soft: "#eceffd"
  accent-ink: "#ffffff"
  risk: "#c2332d"
  risk-soft: "#fdeeed"
  risk-edge: "#f0bdb9"
  risk-ink: "#8c1d18"
  changed: "#e3a019"
  changed-soft: "#fff4dc"
  changed-ink: "#74490a"
  low-fill: "#eff1f4"
  low-edge: "#d9dde4"
  low-ink: "#2a2f38"
  medium-fill: "#e7effd"
  medium-edge: "#c2d5f6"
  medium-ink: "#173a73"
  high-fill: "#efe9fc"
  high-edge: "#d6c7f5"
  high-ink: "#42267b"
  page: "#f3f4f7"
  surface: "#ffffff"
  surface-sunk: "#f7f8fa"
  surface-hover: "#f1f3f6"
  line: "#e6e8ed"
  line-strong: "#d4d8e0"
  line-faint: "#f0f1f4"
  ink: "#171a20"
  ink-2: "#4a505c"
  ink-3: "#626977"
typography:
  headline:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "19px"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  body:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.45
  block-title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 580
    lineHeight: 1.3
  label:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.45
  meta:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "11.5px"
    fontWeight: 400
    lineHeight: 1
    fontFeature: "tnum"
rounded:
  radius-s: "6px"
  radius: "8px"
  radius-l: "12px"
  pill: "15px"
spacing:
  space-1: "4px"
  space-2: "8px"
  space-3: "12px"
  space-4: "16px"
  space-5: "24px"
  space-6: "32px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.accent-ink}"
    rounded: "{rounded.radius}"
    padding: "0 14px"
    height: "34px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.radius}"
    padding: "0 14px"
    height: "34px"
  button-secondary-hover:
    backgroundColor: "{colors.surface-hover}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.radius}"
    padding: "0 14px"
    height: "34px"
  button-ghost-hover:
    backgroundColor: "{colors.surface-hover}"
    textColor: "{colors.ink}"
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.pill}"
    padding: "0 11px"
    height: "30px"
  chip-selected:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.radius-s}"
    padding: "0 9px"
    height: "34px"
  block-medium:
    backgroundColor: "{colors.medium-fill}"
    textColor: "{colors.medium-ink}"
    typography: "{typography.block-title}"
    rounded: "{rounded.radius-s}"
    padding: "6px 8px"
  block-low:
    backgroundColor: "{colors.low-fill}"
    textColor: "{colors.low-ink}"
  block-high:
    backgroundColor: "{colors.high-fill}"
    textColor: "{colors.high-ink}"
  block-at-risk:
    backgroundColor: "{colors.risk-soft}"
    textColor: "{colors.risk-ink}"
  changed-chip:
    backgroundColor: "{colors.changed-soft}"
    textColor: "{colors.changed-ink}"
    rounded: "10px"
    padding: "2px 7px"
  popover:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.radius-l}"
    padding: "16px"
    width: "364px"
---

# Design System: ReflowTask

## Overview

**Creative North Star: "The Honest Calendar"**

A clean, calm week calendar in the category of Notion Calendar and Amie: a cool light-grey page, one white calendar board, hairline hour rules and soft tinted blocks. It stays open all day beside other work, so it is quiet by default and follows the operating system between light and dark (every token has a dark override; dark is soft charcoal, never pure black).

Calm here does not mean silent. The world's one distinctive move is that the schedule is honest about its own changes: whatever the last replan moved carries an amber ring and a "from" chip, its old slot keeps a dashed amber outline, and blocks glide to their new places instead of jumping. Colour carries meaning and nothing else: indigo for action and the present, red for risk, amber for change, three soft tints for priority.

Density is desktop-calendar density: 14px body, 13px block titles, generous whitespace around a grid that fills the viewport. On phones the calendar moves above the sidebar and popovers become bottom sheets.

**Key Characteristics:**
- Cool neutral page, one white bordered board, hairline rules.
- One bundled variable grotesque with tabular numerals for every time.
- Soft tinted blocks with a 1px tinted edge; priority also drawn as a bar glyph.
- Three reserved signal colours: indigo (action, today, now), red (at risk, errors), amber (last replan's changes).
- Flat at rest; shadows only for things that float or are being dragged.
- Light and dark from the same token names via `prefers-color-scheme`.

## Colors

A cool, low-chroma neutral scale with three reserved signal hues and three soft priority tints.

### Primary
- **Calm Indigo** (accent): primary buttons (New task), today's date pill and day-name, today's column wash (accent-soft at 35%), the now-line, its dot and the gutter time pill, selected chips and choices, switches, focus rings, caret and workload fill. Indigo Hover deepens it on hover; Indigo Wash (accent-soft) is its tinted background.

### Secondary
- **Alarm Red** (risk, risk-soft, risk-edge, risk-ink): at-risk blocks (fill, edge and ink swap to the risk set), the risk glyph, over-capacity workload meters, the needs-attention count badge, missed ghosts, field errors, form failures and the floating error notice. Nothing else.

### Tertiary
- **Replan Amber** (changed, changed-soft, changed-ink): the 2px outline ring on a block the last replan moved, the "from" chip and text on that block, the dashed old-slot outlines and the half of the brand mark that reflowed.

### Neutral
- **Cool Page** (page): app background, top bar, sticky hours action bar.
- **Board White** (surface): the calendar board, popovers, secondary buttons, chips, inputs, done blocks.
- **Sunk Grey** (surface-sunk): days off inside the grid.
- **Hover Grey** (surface-hover): hover wash for ghost buttons, list rows, icon buttons; the segmented-control track.
- **Hairline / Strong Hairline / Faint Hairline** (line, line-strong, line-faint): hour rules and borders / control borders, done-block edge, scrollbar thumb / half-hour rules, day dividers, empty meter track.
- **Ink / Ink 2 / Ink 3** (ink, ink-2, ink-3): primary text / secondary text and labels / hints, gutter hours, day names, placeholders.
- **Priority tints** (low-*, medium-*, high-*): grey, soft blue and soft violet fill + edge + ink sets for LOW, MEDIUM and HIGH blocks.
- **Off-hours veil** (`rgb(40 50 70 / 3.5%)` light, `rgb(0 0 0 / 24%)` dark): laid over hours outside working time so the rules still show through.

### Named Rules
**The Reserved Signals Rule.** Indigo means act or now, red means at risk or failed, amber means the last replan changed this. No signal hue is ever used as decoration, and no two meanings share a hue.

**The Never-Colour-Alone Rule.** Every colour signal has a second carrier: priority is also the three-bar glyph, risk is also the triangle icon, change is also the "from" text and the dashed outline.

## Typography

**Display Font:** none; the system has no display role.
**Body Font:** Archivo Variable (with ui-sans-serif, system-ui, sans-serif), bundled via @fontsource-variable/archivo on the wght axis, never fetched.

**Character:** One workhorse grotesque doing everything, differentiated by size and fine variable weights (520 to 650) rather than by a second family. Times are always tabular.

### Hierarchy
- **Headline** (600, 19px, line-height 1, -0.02em): day-header dates; today's date sits in a 30px indigo pill at 16px.
- **Title** (600 to 620, 16 to 17px, -0.01em): week range in the top bar, popover titles (task editor, block details, hours).
- **Body** (400, 14px, 1.45): base text; buttons at 13.5px / 540, list titles at 560.
- **Block Title** (580, 13px, 1.3): block titles, single-line with ellipsis; two lines on lg blocks; 11px / 14px line on xs blocks.
- **Label** (600, 12px, ink-2): form and section labels, sentence case, no tracking; panel headings at 13px / 620.
- **Meta** (400, 11.5 to 12.5px, ink-3): gutter hours, block times, workload text, hints.

### Named Rules
**The Tabular Time Rule.** Every clock time, date range, duration and count uses `font-variant-numeric: tabular-nums` so columns of times never shimmer.

**The One Face Rule.** Archivo Variable is the only family. Hierarchy comes from size and variable weight steps, never from a second face, uppercase or letter-spacing.

## Layout

A fixed application frame at `100dvh`: a 60px top bar (brand, prev/today/next and week range, then Replan, Hours and the indigo New task button at the right), a 292px left sidebar (widens to 372px when the hours panel is open) holding Needs attention then Activity, and the calendar board filling the rest.

The board is a scrollable grid: a 56px sticky time gutter plus one column per day (minimum 120px), with a sticky header carrying each day's name, date and a 4px workload meter. Time maps linearly at **1.2px per minute** (72px per hour); hour rules use line, half-hour rules line-faint. Dragging and click-to-create snap to 15 minutes. On load the grid scrolls to 45 minutes before the first working hour.

Blocks are sized by visible minutes: **xs** under 30 (one line, 11px, start time inline), **sm** under 45 (one line, title + start), **md** under 80 (title plus meta line), **lg** otherwise (two-line title plus a change chip). Minimum drawn height is 15 minutes; every block is inset 4px from its column edges and 2px short of its slot.

Spacing follows a 4px base (4, 8, 12, 16, 24, 32). Sidebar sections sit 32px apart; popover content 16px.

Responsive: at 1100px wide labels drop to icons. At 900px the frame unlocks: the top bar wraps, the calendar moves above the sidebar at 72dvh, day columns widen to 132px minimum, the gutter narrows to 46px, and xs/sm blocks drop their start time. At 640px popovers become bottom sheets.

## Elevation & Depth

Flat at rest. Depth comes from tonal layering (page, white board, sunk days off) and hairline borders. Shadows appear only on elements that float above the grid or are in the user's hand.

### Shadow Vocabulary
- **Pop** (`box-shadow: 0 16px 40px -12px rgb(20 26 40 / 22%), 0 2px 6px rgb(20 26 40 / 6%)`): popovers, bottom sheets and the floating error notice.
- **Lift** (`box-shadow: 0 12px 28px -8px rgb(20 26 40 / 28%), 0 2px 4px rgb(20 26 40 / 8%)`): a block while it is being dragged.

Both deepen in dark mode (black at 60 to 65%).

### Named Rules
**The Only-What-Floats Rule.** Nothing resting on the page or grid carries a shadow. A shadow means "this is above the calendar right now".

**The Beneath-the-Blocks Rule.** The now-line and old-slot outlines render beneath blocks; drawn over them, a line strikes through a title and reads as done.

## Shapes

Softly rounded rectangles throughout, in three steps: 6px for blocks, inputs, ghosts and drafts; 8px for buttons, choices and list rows; 12px for the calendar board and popovers (16px top corners on bottom sheets). Chips, count badges and today's date are full pills. Borders are 1px hairlines; the only dashed strokes are 1.5px, reserved for old-slot outlines (amber, or red for missed) and the create draft (indigo), plus the break hatch. Icons are authored SVG on a 16px grid at a single 1.5px round stroke, drawn in currentColor.

## Components

### Buttons
Quiet and compact; one indigo button per view.
- **Shape:** gently rounded (8px), 34px tall, 30px in the small variant.
- **Primary:** indigo fill, white ink, 0 14px padding, 13.5px / 540.
- **Hover / Focus / Active:** fill shifts to Indigo Hover over 140ms; 2px indigo focus outline offset 2px; press scales to 0.97.
- **Secondary:** white with a strong hairline border, hover-grey on hover.
- **Ghost:** transparent, ink-2, hover-grey wash; pressed state (`aria-pressed`) holds the wash.
- **Quiet danger:** transparent red text with a red-soft hover wash, for destructive actions inside popovers.
- **Icon button:** 32px square, transparent, ink-2.

### Chips
- **Style:** 30px pill, white, strong-hairline border, ink-2, 13px / 520, tabular.
- **State:** selected turns indigo border, Indigo Wash fill, indigo text at 600. Used for duration, priority and deadline choices.
- **Segmented control:** a hover-grey track with 3px padding; the selected segment is white with a 1px hairline ring and a faint 1px shadow.

### Cards / Containers
- **Calendar board:** white, 1px hairline, 12px radius, no shadow.
- **Popover:** white, 1px hairline, 12px radius, Pop shadow, 364px wide, 16px padding; opens with a 180ms scale-from-0.96. Below 640px it is a bottom sheet with a 240ms rise.
- **Choice card:** 1px hairline, 8px radius, 9px 11px padding; checked turns indigo border on Indigo Wash.

### Inputs / Fields
- **Style:** 34px tall, white, strong-hairline border, 6px radius, 0 9px padding. The task title is a borderless 17px field with only a bottom hairline.
- **Focus:** border turns indigo with a 3px Indigo Wash halo (the title field's underline turns indigo instead).
- **Error / Disabled:** invalid fields take a red border with a 12.5px red message beneath; disabled choices fade to 60%.

### Navigation
Top bar on the page tone with a bottom hairline: brand (two offset bars, indigo over amber, beside a 15px / 650 wordmark), prev/next icon buttons with a small secondary Today button, the week range at 17px / 600, and ghost actions ending in the primary New task button.

### Week Grid Block (signature)
- **Body:** the priority tint set as fill, 1px edge and ink; 6px radius; 6px 8px padding; title plus start time or meta; icons at 13px (risk, pin, done, priority bars).
- **Hover:** a brightness 0.97 / saturate 1.08 filter; editable blocks show a grab cursor and a faint resize handle on the bottom edge.
- **Moved:** 2px amber outline offset 1px outside the block, plus a "from" line (md) or amber chip (lg).
- **At risk:** swaps to the red set with a red triangle.
- **Done:** white fill, strong-hairline edge, ink-3, struck-through title.
- **Selected:** 2px indigo ring. **Dragging:** Lift shadow, grabbing cursor.
- **Glide:** after a replan, moved blocks animate from their old position over 520ms on `cubic-bezier(0.22, 1, 0.36, 1)`; skipped under reduced motion, where the marks remain.

### Old-Slot Outline
A fixed 30-minute, 1.5px dashed amber (70%) outline with a 6px radius, drawn beneath blocks where a moved block used to start. Labelled "was here" in changed-ink only when no block overlaps it; missed-work outlines use red at 55%.

### Now Marker
A 2px indigo line across today with a 10px indigo dot at its left, running beneath blocks, and an indigo pill in the gutter with the current time at 11px / 600 tabular.

## Do's and Don'ts

### Do:
- **Do** keep indigo (accent) for primary actions, today and the present time only.
- **Do** keep red (risk) for at-risk blocks, over-capacity meters and error notices only.
- **Do** mark anything the last replan changed with amber: a 2px outline ring, a "from" chip or line, and a dashed 30-minute old-slot outline.
- **Do** pair every colour signal with a glyph or text (priority bars, risk triangle, "from" text).
- **Do** place blocks at 1.2px per minute, snap interactions to 15 minutes, and pick xs/sm/md/lg by visible minutes (<30, <45, <80, else).
- **Do** use tabular numerals for every time, date and count.
- **Do** define every colour as a token with both light and dark values in tokens.css.
- **Do** draw icons as 16px-grid SVG at one 1.5px stroke in currentColor.

### Don't:
- **Don't** put buttons inside blocks; a block is title and time, and actions live in its popover.
- **Don't** draw the now-line or old-slot outlines over blocks.
- **Don't** give resting surfaces a shadow; only popovers, the notice and dragged blocks lift.
- **Don't** introduce a second typeface, uppercase labels or letter-spaced small caps.
- **Don't** use amber for warnings or red for emphasis; each hue has exactly one meaning.
- **Don't** fetch fonts at runtime; the app must work on a home server with no internet.
