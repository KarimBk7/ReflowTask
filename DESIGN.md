---
name: ReflowTask
description: A self-repairing week schedule, rendered as an enamel planning board.
colors:
  enamel: "#223f3c"
  enamel-sunk: "#1a312f"
  enamel-frame: "#142927"
  strip-bone: "#e7e1d3"
  strip-edge: "#c3bdaf"
  strip-ink: "#1b2422"
  strip-ink-quiet: "#55605c"
  strip-done: "#b9bdb2"
  legend: "#dbe6e1"
  legend-quiet: "#9fb3ad"
  chinagraph: "#f1eee4"
  signal-vermilion: "#e2583a"
  signal-ink: "#2a0d06"
  trim-aluminium: "#7f8a86"
  trim-lit: "#b6c0bb"
  trim-face: "#6d7874"
  trim-shadow: "#48514e"
  focus-amber: "#ffd166"
  enamel-daylight: "#8fa79f"
  strip-bone-daylight: "#f7f3e8"
  legend-daylight: "#0f1f1c"
  chinagraph-daylight: "#12201d"
  signal-vermilion-daylight: "#a32d12"
typography:
  wordmark:
    fontFamily: "Archivo Narrow, ui-sans-serif, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    letterSpacing: "0.22em"
  legend:
    fontFamily: "Archivo Narrow, ui-sans-serif, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    letterSpacing: "0.16em"
  label:
    fontFamily: "Archivo Narrow, ui-sans-serif, system-ui, sans-serif"
    fontSize: "10px"
    fontWeight: 400
    letterSpacing: "0.14em"
  title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.25
  body:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.45
  numeral:
    fontFamily: "Archivo Narrow, ui-sans-serif, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 400
    letterSpacing: "0.08em"
    fontFeature: "tnum"
rounded:
  none: "0px"
spacing:
  hair: "2px"
  tight: "6px"
  base: "12px"
  wide: "24px"
  room: "40px"
components:
  lever:
    backgroundColor: "{colors.trim-aluminium}"
    textColor: "{colors.strip-bone}"
    typography: "{typography.legend}"
    rounded: "{rounded.none}"
    padding: "9px 16px"
  lever-hover:
    backgroundColor: "{colors.trim-lit}"
  trim-button:
    backgroundColor: "transparent"
    textColor: "{colors.legend}"
    rounded: "{rounded.none}"
    padding: "6px 10px"
  strip:
    backgroundColor: "{colors.strip-bone}"
    textColor: "{colors.strip-ink}"
    typography: "{typography.title}"
    rounded: "{rounded.none}"
    padding: "5px 8px"
  strip-done:
    backgroundColor: "{colors.strip-done}"
    textColor: "{colors.strip-ink-quiet}"
  strip-action:
    backgroundColor: "transparent"
    textColor: "{colors.strip-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "2px 6px"
  field-input:
    backgroundColor: "{colors.enamel}"
    textColor: "{colors.legend}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "6px 8px"
  segment-selected:
    backgroundColor: "{colors.trim-aluminium}"
    textColor: "{colors.strip-bone}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "6px 4px"
---

# Design System: ReflowTask

## Overview

**Creative North Star: "The Theatre Board"**

ReflowTask is drawn as the vitreous-enamel planning board that still hangs in operating suites and dispatch offices: a steel sheet fired with a coloured enamel, ruled with permanent screen-printed lines, framed in aluminium extrusion, and carrying work as laminated magnet strips that people move by hand when the day goes wrong. That object was chosen because its defining ritual is the product's mechanism. The schedule is re-laid when something slips, and the board keeps a mark of where a strip used to be.

The material is flat, hard and matte from edge to edge. Nothing is glossy, nothing floats, and no corner is rounded. Depth comes from value steps between the enamel, the sunk enamel and the steel frame, plus single inset hairlines where a strip or plate is seated, never from cast shadows. Annotation is a separate hand, chinagraph wax pencil, used only for things the system wrote about itself: where a strip moved from, and the running record of replans.

The board is dense by design. It stays open all day in peripheral vision, so it has two complete material states that follow the OS: deep enamel under room light and the same enamel in daylight. The light theme is not a tint of the dark one.

**Key Characteristics:**
- Enamel ground, screen-printed rules, bone laminate strips, aluminium trim
- Square corners everywhere; circles only for screw heads and bullet dots
- One saturated colour, vermilion, and it means "at risk" and nothing else
- Every state carries a mark that survives without colour
- Two type voices: a condensed grotesque "printed" on the enamel, and a workhorse sans on the strips
- Tabular numerals wherever time appears

## Colors

A restrained, mid-saturated green-grey world with bone laminate on top, in which a single vermilion is the only colour allowed to shout.

### Primary
- **Theatre Enamel** (#223f3c): The board itself. It covers the whole working field under the strips and sets the identity. Its daylight counterpart **Daylight Enamel** (#8fa79f) is deliberately mid-value rather than pale, because a pale ground would sit at the same value as the bone strips and the board would disappear.
- **Sunk Enamel** (#1a312f): Bare, unruled enamel. Blocked periods such as lunch, hours outside the working window, and the rail's ground.
- **Steel Frame** (#142927): The darkest value, the steel the board is bolted into. Used for the page ground and the frame around the enamel.

### Secondary
- **Signal Vermilion** (#e2583a): Reserved for work scheduled past its deadline, in borders, the risk glyph, and validation errors. Daylight: **Daylight Vermilion** (#a32d12). Also the text-selection highlight. It is never decoration.

### Neutral
- **Strip Bone** (#e7e1d3): The laminate of every strip, in the week grid and the rail. Daylight: #f7f3e8.
- **Strip Edge** (#c3bdaf): The laminate's cut edge, as a strip border and the outline of strip-level controls.
- **Strip Ink** (#1b2422): Text printed on a strip.
- **Strip Ink Quiet** (#55605c): Secondary strip text: times, durations, move notes.
- **Spent Laminate** (#b9bdb2): The strip ground once its task is done.
- **Legend Ink** (#dbe6e1): Lettering fired onto the enamel: day names, headings, the wordmark. Daylight: #0f1f1c.
- **Legend Quiet** (#9fb3ad): Printed secondary lettering: hour numerals, dates, field labels.
- **Chinagraph** (#f1eee4): The wax-pencil annotation hand, for the now-line, move brackets, ghost marks and the record. Softened at 55% opacity for less important marks. Daylight: #12201d.
- **Aluminium** (#7f8a86), **Lit Edge** (#b6c0bb), **Brushed Face** (#6d7874), **Machined Shadow** (#48514e): The four values of the extrusion profile, top to bottom.
- **Focus Amber** (#ffd166): Keyboard focus rings only.

### Named Rules
**The One Signal Rule.** Vermilion marks work that cannot meet its deadline, plus errors. A vermilion element that isn't reporting a risk or an error is a bug.

**The Value-Separation Rule.** The enamel and the bone strips must sit far apart in value in both themes. The strip is an object on the board, never a tint of it.

## Typography

**Legend Font:** Archivo Narrow (with ui-sans-serif, system-ui)
**Strip Font:** Archivo, variable (with ui-sans-serif, system-ui)

**Character:** A condensed industrial grotesque set in widely tracked capitals reads as lettering screen-printed permanently into the enamel. A plain, sturdy sans on the strips reads as what someone typed onto a laminate.

### Hierarchy
- **Wordmark** (600, 15px, 0.22em, uppercase): The product name at the left end of the trim.
- **Legend** (600, 12px, 0.16em, uppercase): Day names, the week range, the replan lever.
- **Title** (600, 13px, line-height 1.25): Strip titles, truncated with an ellipsis rather than wrapped.
- **Body** (400, 14px, line-height 1.45): Default text, form input text, the record.
- **Numeral** (400, 11px, 0.08em, tabular): Hour gutter, times, durations.
- **Label** (400–600, 10–11px, 0.1–0.2em, uppercase): Section headings in the rail, field labels, strip controls, move notes.

### Named Rules
**The Printed-On Rule.** Anything that is a permanent part of the board (day names, hours, section headings, the wordmark) is set in Archivo Narrow capitals. Anything a person wrote (task titles, descriptions) is set in Archivo. Don't mix the two voices within one element.

**The Tabular Time Rule.** Every time, duration and count uses tabular numerals, so the board's columns of times line up and never jitter when values change.

**The Bundled Type Rule.** Faces are self-hosted and bundled into the build, never fetched from a CDN. The board runs on a home server that may have no internet connection.

## Layout

The page is a fixed stack: the aluminium trim across the top, then a two-column body with the strip rail on the left (264px) and the board filling the rest.

The board is a time grid: a 56px hour gutter, then one column per configured working day. Weekdays without working hours are not drawn at all, so a Monday–Friday setup shows five columns. The drawn time window comes from the configured working hours, rounded out to whole hours. Height is derived from that window at 1.05px per minute, never inherited from a flex parent, because every strip is absolutely positioned and the field has no height of its own.

One time ruler positions everything: strips, blocked periods, the now-line and ghost marks all use the same minutes-from-window-start calculation.

The spacing rhythm is 2 / 6 / 12 / 24 / 40px. Groups are tight at 6px, sections are separated at 24px, and 40px is used only as run-out below the board.

**Responsive (≤900px):** The body becomes one column with the board first and the rail after it. The trim wraps, with the week controls on their own full-width row. Day columns get a 148px minimum and the whole board scrolls sideways as one piece, so strips are never read against the wrong hour. The week is never squeezed into unreadable columns.

## Elevation & Depth

There are no cast shadows. Depth is tonal: the steel frame (darkest), the enamel field, then the sunk enamel for anything unruled. Seated objects show a single 1px inset lit hairline along their top edge: strips (`inset 0 1px 0 rgb(255 255 255 / 45%)`), and the lever plate and screw heads (`inset 0 1px 0` lit edge). The trim gets its profile from a four-stop vertical gradient, not a shadow.

### Named Rules
**The Seated-Not-Floating Rule.** A strip or plate sits on the board. It gets an inset edge, never a drop shadow, glow or blur beneath it.

## Shapes

Square corners throughout (0px). The only round forms are screw heads and 2px bullet dots, both at 50%. Borders are 1px: strip edge on strips, aluminium on trim fittings and fields. A pinned strip changes shape language, from a single edge to a double 3px border, so pinning is visible as a shape rather than a colour. An at-risk strip adds a 45° hatch of 2px lines at 6px spacing over its laminate.

## Components

### Lever (primary action)
The one control that changes the whole board, fitted as hardware.
- **Shape:** Square (0px), a 1px lit-edge border.
- **Default:** Aluminium ground, bone text in Legend capitals, 9px 16px padding. A small reflow glyph sits before the label.
- **Hover / Active:** The ground lightens to the lit edge; press drops it 1px. Disabled sits at 45% opacity.
- **Mounting:** Bolted to the right end of the trim through a plate with two screw heads.

### Trim Buttons (secondary)
- **Shape:** Square, 1px aluminium border, transparent ground, 6px 10px padding.
- **Content:** Previous and next week are chevron icons with screen-reader labels; "Today" is a text button in Legend capitals.
- **Hover:** The border lightens to the lit edge.

### Strip (signature component)
A laminated magnet strip seated in the time grid.
- **Shape:** Square, 1px strip-edge border, inset lit top hairline, 3px inset from the column sides.
- **Content:** Title, then start–end times and duration in tabular numerals, then labelled controls at the foot.
- **States (each carries a mark that works without colour):**
  - *At risk:* vermilion border plus the 45° hatch and a risk glyph.
  - *Pinned:* double 3px border plus a pin glyph.
  - *Done:* spent-laminate ground, title struck through, check glyph.
  - *Moved:* a dashed chinagraph bracket at the head of the leading edge, plus a written note ("Moved from 10:00"). Short strips abbreviate it ("from 10:00") but never show a bare time.
- **Controls:** "Mark done" and "Pin" are always labelled words in Label capitals, never icons alone.
- **Short strips (under 55 minutes):** drop the end time, keeping title, start, duration and the move note, because position on the ruler already shows when they are.
- **Motion:** A moved strip settles in once (translateY −6px → 0, opacity 0.35 → 1, 480ms, `cubic-bezier(0.16, 1, 0.3, 1)`). The bracket and note are the lasting record; the motion is a one-time flourish. Turned off under `prefers-reduced-motion`.

### Chinagraph Ghost
Where a strip used to be, drawn in wax pencil on top of everything. The latest replan's moves draw a bracket at the vacated position against the column's leading edge, plus a dashed arc to the new position. Moves within a day bow out into the column margin, and moves across days arc between column edges, so a stroke never crosses a strip's own text. Only the most recent replan is drawn; older history lives in the record.

### Strip Rail
Unscheduled and at-risk work, beside the board. The same bone laminate as a strip, 8px 10px padding, with the reason written in words ("No slot in the horizon" plus the shortfall, or "Past its deadline" in vermilion with the risk glyph). Delete is an icon button with a screen-reader label.

### Record
The board's own history of replans, in the chinagraph hand along a trim band at the bottom of the board. Each entry is running text: the time, then each task named with what happened to it (moved / placed / missed / no slot) in Label capitals, then the trigger. Only the three most recent replans are shown.

### Inputs / Fields
- **Style:** Square, 1px aluminium border, an enamel ground so fields read as part of the board, 6px 8px padding, tabular numerals.
- **Label:** Field labels sit above the input in Label capitals.
- **Error:** Vermilion message below the field, linked with `aria-describedby`, and `aria-invalid` on the input.
- **Disabled:** 40% opacity. The deadline-time field stays disabled until a date is set.

### Segmented Control
Priority picker. Joined square cells with 1px aluminium borders that overlap by 1px. The selected cell fills with aluminium and bone text at weight 600. The native radio inputs are visually hidden but keep keyboard focus, shown as an amber ring on the cell.

## Do's and Don'ts

### Do:
- **Do** keep every corner square (0px). Round forms are only for screw heads and bullet dots.
- **Do** give every state a mark that survives without colour: a hatch, a double border, a glyph or words.
- **Do** set every time, duration and count in tabular numerals.
- **Do** derive the board's height from the drawn time window, at 1.05px per minute.
- **Do** write annotation the system produces about itself in the chinagraph hand, not in legend ink.
- **Do** keep both themes as full material states, with enamel and bone far apart in value in each.
- **Do** bundle all faces into the build.

### Don't:
- **Don't** use vermilion for anything except at-risk work and errors.
- **Don't** give strips, plates or panels a drop shadow, glow or blur. Seat them with an inset hairline.
- **Don't** set board lettering in a system display face. Legends are Archivo Narrow.
- **Don't** show a time without a label where it could be mistaken for another time on the same strip.
- **Don't** hide a strip control behind a hover-only icon.
- **Don't** fetch fonts or other assets from a CDN at runtime.
