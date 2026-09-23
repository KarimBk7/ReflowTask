import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'

import type { Block } from '../api/types'
import { t } from '../i18n/en'
import type { BoardConfig, Ghost } from '../lib/board'
import { blockedOn, boardDays, firstWorkingMinute, isToday, originFor, workingOn, workload } from '../lib/board'
import {
  DAY_NAMES,
  addDays,
  atMinutes,
  formatClock,
  formatDuration,
  isoDay,
  minutesOfDay,
  parseClock,
  sameDate,
  toLocalDateTime,
} from '../lib/time'
import { BlockCard, type Placed } from './BlockCard'

/** Where a new task is being written, outlined on the grid while its popover is open. */
export interface Draft {
  start: Date
  minutes: number
}

interface WeekGridProps {
  weekStart: Date
  /** Show all seven days, not only working days and days with something on them. */
  allDays: boolean
  config: BoardConfig | undefined
  blocks: Block[]
  ghosts: Ghost[]
  draft: Draft | null
  selectedBlockId: number | null
  /** Each task's estimate, so a block can say it is one part of a longer task. */
  taskMinutes: Map<number, number>
  /** A block to scroll to and pulse once, after the scheduler placed a new task. */
  flashBlockId: number | null
  busy: boolean
  onOpenBlock: (block: Block, anchor: DOMRect) => void
  onCreateAt: (start: Date, anchor: DOMRect) => void
  onMove: (block: Block, start: Date, end: Date) => Promise<unknown>
}

const PX_PER_MIN = 1.2
const SNAP = 15
const DAY_MINUTES = 24 * 60
const DRAG_THRESHOLD = 4
/**
 * The replan record keeps where a task started, not how long its old block was, so the outline
 * marks the old start at a fixed height instead of guessing a length from the task's current pieces.
 */
const GHOST_MINUTES = 30

const snap = (minutes: number) => Math.round(minutes / SNAP) * SNAP
const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

interface Drag {
  item: Placed
  mode: 'move' | 'resize'
  x: number
  y: number
  moved: boolean
  next: { dayIndex: number; start: number; end: number } | null
}

/**
 * The week as a scrolling day-by-hour grid. The whole day is drawn, not just working hours, so a
 * task can be fixed at 19:00 or dragged before the day starts; the grid opens scrolled to the
 * first working hour.
 */
export function WeekGrid({
  weekStart,
  allDays,
  config,
  blocks,
  ghosts,
  draft,
  selectedBlockId,
  taskMinutes,
  flashBlockId,
  busy,
  onOpenBlock,
  onCreateAt,
  onMove,
}: WeekGridProps) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const columnsRef = useRef<HTMLDivElement>(null)
  const suppressClick = useRef(false)
  const dropped = useRef<number | null>(null)
  const [preview, setPreview] = useState<{ blockId: number; dayIndex: number; start: number; end: number } | null>(
    null,
  )
  const [now, setNow] = useState(() => new Date())
  const [hoveredTask, setHoveredTask] = useState<number | null>(null)

  // A task placed out of sight is scrolled into view once its block has rendered.
  const revealed = useRef<number | null>(null)
  useEffect(() => {
    if (flashBlockId === null || revealed.current === flashBlockId) return
    const element = scrollRef.current?.querySelector<HTMLElement>(`[data-block-id="${flashBlockId}"]`)
    if (!element) return
    revealed.current = flashBlockId
    element.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' })
  })

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  const days = useMemo(() => boardDays(config, blocks, weekStart, allDays), [config, blocks, weekStart, allDays])
  const placed = useMemo(() => {
    const weekEnd = addDays(weekStart, 7)
    const perTask = new Map<number, number>()
    const result: Placed[] = []
    for (const block of [...blocks].sort((a, b) => a.startAt.localeCompare(b.startAt))) {
      const startDate = new Date(block.startAt)
      if (startDate < weekStart || startDate >= weekEnd) continue
      const nth = perTask.get(block.taskId) ?? 0
      perTask.set(block.taskId, nth + 1)
      const start = minutesOfDay(startDate)
      const duration = (new Date(block.endAt).getTime() - startDate.getTime()) / 60_000
      const dragging = preview?.blockId === block.id
      result.push({
        key: `${block.taskId}:${nth}`,
        block,
        dayIndex: dragging ? preview.dayIndex : days.indexOf(isoDay(startDate)),
        start: dragging ? preview.start : start,
        end: dragging ? preview.end : start + duration,
        dragging,
      })
    }
    return result
  }, [blocks, days, preview, weekStart])

  // Open on the working day rather than at midnight, once per week shown.
  const scrolledFor = useRef<string | null>(null)
  useLayoutEffect(() => {
    const key = toLocalDateTime(weekStart)
    if (!config || !scrollRef.current || scrolledFor.current === key) return
    scrolledFor.current = key
    scrollRef.current.scrollTop = Math.max(0, (firstWorkingMinute(config) - 45) * PX_PER_MIN)
  }, [config, weekStart])

  /*
   * The glide. After a replan, a block that now sits somewhere else animates from where it was,
   * so the owner can watch the week rearrange instead of finding it changed. Positions are
   * remembered from layout data, not measured from the DOM, so a glide still running can never
   * be mistaken for a position. Blocks are matched by task and order, because a replan
   * recreates blocks with new ids.
   */
  const positions = useRef<{ week: string; at: Map<string, { dayIndex: number; start: number }> } | null>(null)
  useLayoutEffect(() => {
    const week = toLocalDateTime(weekStart)
    const previous = positions.current?.week === week ? positions.current.at : null
    const next = new Map<string, { dayIndex: number; start: number }>()
    const root = columnsRef.current
    const columnWidth = root?.firstElementChild?.getBoundingClientRect().width ?? 0
    const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    for (const item of placed) {
      next.set(item.key, { dayIndex: item.dayIndex, start: item.start })
      const old = previous?.get(item.key)
      if (!old || still || !root || item.dragging || item.block.taskId === dropped.current) continue
      if (old.dayIndex === item.dayIndex && old.start === item.start) continue
      root.querySelector<HTMLElement>(`[data-key="${item.key}"]`)?.animate(
        [
          {
            transform: `translate(${(old.dayIndex - item.dayIndex) * columnWidth}px, ${(old.start - item.start) * PX_PER_MIN}px)`,
          },
          { transform: 'translate(0, 0)' },
        ],
        { duration: 520, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
      )
    }
    positions.current = { week, at: next }
    if (!preview) dropped.current = null
  }, [placed, preview, weekStart])

  function editable(block: Block) {
    return !busy && block.status !== 'DONE' && new Date(block.endAt) > now
  }

  function columnAt(clientX: number): number {
    const columns = [...(columnsRef.current?.children ?? [])].filter((child) => child.classList.contains('day'))
    for (let index = 0; index < columns.length; index++) {
      const rect = columns[index].getBoundingClientRect()
      if (clientX < rect.right || index === columns.length - 1) return index
    }
    return 0
  }

  function commit(item: Placed, dayIndex: number, start: number, end: number) {
    const date = addDays(weekStart, days[dayIndex] - 1)
    dropped.current = item.block.taskId
    return onMove(item.block, atMinutes(date, start), atMinutes(date, end))
  }

  function startDrag(item: Placed, mode: 'move' | 'resize', event: React.PointerEvent) {
    // Touch keeps its scroll: on a phone a block is tapped to open, not dragged.
    if (event.button !== 0 || event.pointerType === 'touch' || !editable(item.block)) return
    const drag: Drag = { item, mode, x: event.clientX, y: event.clientY, moved: false, next: null }

    function onMovePointer(move: PointerEvent) {
      const dy = move.clientY - drag.y
      if (!drag.moved && Math.hypot(move.clientX - drag.x, dy) < DRAG_THRESHOLD) return
      drag.moved = true
      const duration = drag.item.end - drag.item.start
      if (drag.mode === 'move') {
        const start = clamp(snap(drag.item.start + dy / PX_PER_MIN), 0, DAY_MINUTES - Math.min(duration, DAY_MINUTES))
        drag.next = { dayIndex: columnAt(move.clientX), start, end: start + duration }
      } else {
        const end = clamp(snap(drag.item.end + dy / PX_PER_MIN), drag.item.start + SNAP, DAY_MINUTES)
        drag.next = { dayIndex: drag.item.dayIndex, start: drag.item.start, end }
      }
      setPreview({ blockId: drag.item.block.id, ...drag.next })
    }

    function finish(cancelled: boolean) {
      window.removeEventListener('pointermove', onMovePointer)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onCancel)
      window.removeEventListener('keydown', onKey)
      if (!drag.moved) return
      // The press that ends a drag also produces a click; it must not open anything.
      suppressClick.current = true
      window.setTimeout(() => (suppressClick.current = false), 0)
      const next = drag.next
      const unchanged =
        !next ||
        (next.dayIndex === drag.item.dayIndex && next.start === drag.item.start && next.end === drag.item.end)
      if (cancelled || unchanged) {
        setPreview(null)
        return
      }
      commit(drag.item, next.dayIndex, next.start, next.end)
        .catch(() => undefined)
        .finally(() => setPreview(null))
    }
    const onUp = () => finish(false)
    const onCancel = () => finish(true)
    const onKey = (key: KeyboardEvent) => {
      if (key.key === 'Escape') finish(true)
    }

    window.addEventListener('pointermove', onMovePointer)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onCancel)
    window.addEventListener('keydown', onKey)
  }

  /** The keyboard's drag: Alt+arrows move by 15 minutes or a day, Alt+Shift+up/down resizes. */
  function keyboardMove(item: Placed, event: React.KeyboardEvent) {
    if (!event.altKey || !editable(item.block)) return
    const duration = item.end - item.start
    let { dayIndex, start, end } = item
    if (event.shiftKey && event.key === 'ArrowUp') end = Math.max(start + SNAP, end - SNAP)
    else if (event.shiftKey && event.key === 'ArrowDown') end = Math.min(DAY_MINUTES, end + SNAP)
    else if (event.key === 'ArrowUp') start = Math.max(0, start - SNAP)
    else if (event.key === 'ArrowDown') start = Math.min(DAY_MINUTES - duration, start + SNAP)
    else if (event.key === 'ArrowLeft') dayIndex = Math.max(0, dayIndex - 1)
    else if (event.key === 'ArrowRight') dayIndex = Math.min(days.length - 1, dayIndex + 1)
    else return
    event.preventDefault()
    if (!event.shiftKey) end = start + duration
    if (dayIndex === item.dayIndex && start === item.start && end === item.end) return
    commit(item, dayIndex, start, end).catch(() => undefined)
  }

  function openBlock(block: Block, anchor: DOMRect) {
    if (suppressClick.current) return
    onOpenBlock(block, anchor)
  }

  const hours = Array.from({ length: 23 }, (_, index) => (index + 1) * 60)

  return (
    <div
      className="grid"
      ref={scrollRef}
      style={{ ['--day-count' as string]: days.length, ['--px-per-min' as string]: `${PX_PER_MIN}px` }}
    >
      <p id="block-keys-hint" className="sr-only">
        {t('grid.keysHint')}
      </p>

      <div className="grid-head">
        <div className="grid-corner" />
        {days.map((day) => {
          const date = addDays(weekStart, day - 1)
          return <DayHead key={day} day={day} date={date} config={config} blocks={blocks} />
        })}
      </div>

      <div className="grid-body">
        <div className="grid-gutter" aria-hidden="true">
          {days.some((day) => isToday(addDays(weekStart, day - 1))) && (
            <span className="gutter-now" style={{ top: `calc(${minutesOfDay(now)} * var(--px-per-min))` }}>
              {formatClock(minutesOfDay(now))}
            </span>
          )}
          {hours.map((minute) => (
            <span key={minute} className="gutter-hour" style={{ top: `calc(${minute} * var(--px-per-min))` }}>
              {formatClock(minute)}
            </span>
          ))}
        </div>

        <div className="grid-days" ref={columnsRef}>
          {days.map((day, dayIndex) => {
            const date = addDays(weekStart, day - 1)
            return (
              <DayColumn
                key={day}
                day={day}
                date={date}
                config={config}
                now={now}
                items={placed.filter((item) => item.dayIndex === dayIndex)}
                ghosts={ghosts.filter((ghost) => sameDate(ghost.from, date))}
                draft={draft && sameDate(draft.start, date) ? draft : null}
                renderBlock={(item) => (
                  <BlockCard
                    key={item.key}
                    item={item}
                    origin={originFor(item.block, ghosts)}
                    selected={item.block.id === selectedBlockId}
                    taskMinutes={taskMinutes.get(item.block.taskId)}
                    related={hoveredTask === item.block.taskId && placed.filter((other) => other.block.taskId === item.block.taskId).length > 1}
                    flash={item.block.id === flashBlockId}
                    onHover={setHoveredTask}
                    editable={editable(item.block)}
                    onOpen={openBlock}
                    onDragStart={startDrag}
                    onKeyboardMove={keyboardMove}
                  />
                )}
                onCreateAt={(start, anchor) => {
                  if (!suppressClick.current) onCreateAt(start, anchor)
                }}
              />
            )
          })}
        </div>
      </div>
    </div>
  )
}

function DayHead({ day, date, config, blocks }: { day: number; date: Date; config: BoardConfig | undefined; blocks: Block[] }) {
  const { planned, available } = workload(config, day, date, blocks)
  const over = planned > available
  const today = isToday(date)
  const summary =
    available === 0
      ? planned > 0
        ? `${formatDuration(planned)} ${t('workload.planned')}`
        : t('workload.dayOff')
      : planned === 0
        ? `${formatDuration(available)} ${t('workload.free')}`
        : `${formatDuration(planned)} ${t('workload.of')} ${formatDuration(available)}`

  return (
    <div className="day-head" data-today={today || undefined}>
      <div className="day-label">
        <span className="day-name">{DAY_NAMES[day - 1]}</span>
        <span className="day-date" aria-current={today ? 'date' : undefined}>
          {date.getDate()}
        </span>
      </div>
      <div className="workload" data-over={over || undefined}>
        <span className="workload-text">{summary}</span>
        {available > 0 && (
          <span className="workload-bar" aria-hidden="true">
            <span className="workload-fill" style={{ transform: `scaleX(${Math.min(planned / available, 1)})` }} />
          </span>
        )}
      </div>
    </div>
  )
}

interface DayColumnProps {
  day: number
  date: Date
  config: BoardConfig | undefined
  now: Date
  items: Placed[]
  ghosts: Ghost[]
  draft: Draft | null
  renderBlock: (item: Placed) => React.ReactNode
  onCreateAt: (start: Date, anchor: DOMRect) => void
}

function DayColumn({ day, date, config, now, items, ghosts, draft, renderBlock, onCreateAt }: DayColumnProps) {
  const hoverRef = useRef<HTMLDivElement>(null)
  const working = workingOn(config, day)
  const today = sameDate(date, now)

  function minuteAt(event: React.PointerEvent | React.MouseEvent) {
    const rect = event.currentTarget.getBoundingClientRect()
    return clamp(Math.floor((event.clientY - rect.top) / PX_PER_MIN / SNAP) * SNAP, 0, DAY_MINUTES - SNAP)
  }

  function onEmpty(event: React.PointerEvent | React.MouseEvent) {
    return !(event.target as HTMLElement).closest('.block')
  }

  function hover(event: React.PointerEvent<HTMLDivElement>) {
    const marker = hoverRef.current
    if (!marker) return
    if (event.pointerType !== 'mouse' || event.buttons !== 0 || !onEmpty(event)) {
      marker.hidden = true
      return
    }
    const minute = minuteAt(event)
    marker.hidden = false
    marker.style.top = `${minute * PX_PER_MIN}px`
    marker.textContent = formatClock(minute)
  }

  function click(event: React.MouseEvent<HTMLDivElement>) {
    if (!onEmpty(event)) return
    const minute = minuteAt(event)
    const rect = event.currentTarget.getBoundingClientRect()
    if (hoverRef.current) hoverRef.current.hidden = true
    onCreateAt(atMinutes(date, minute), new DOMRect(rect.left, rect.top + minute * PX_PER_MIN, rect.width, 30))
  }

  const nowMinute = minutesOfDay(now)

  return (
    <div
      className="day"
      data-today={today || undefined}
      data-off={!working || undefined}
      onClick={click}
      onPointerMove={hover}
      onPointerLeave={() => hoverRef.current && (hoverRef.current.hidden = true)}
    >
      {working && (
        <>
          <div className="off-hours" style={{ top: 0, height: `calc(${parseClock(working.startTime)} * var(--px-per-min))` }} />
          <div
            className="off-hours"
            style={{ top: `calc(${parseClock(working.endTime)} * var(--px-per-min))`, bottom: 0 }}
          />
        </>
      )}

      {blockedOn(config, day).map((period) => (
        <div
          key={`${period.startTime}-${period.endTime}-${period.label}`}
          className="break"
          style={{
            top: `calc(${parseClock(period.startTime)} * var(--px-per-min))`,
            height: `calc(${parseClock(period.endTime) - parseClock(period.startTime)} * var(--px-per-min))`,
          }}
        >
          <span className="break-label">{period.label || t('grid.break')}</span>
        </div>
      ))}

      {ghosts.map((ghost) => (
        <div
          key={ghost.key}
          className="ghost"
          data-kind={ghost.kind}
          aria-hidden="true"
          style={{
            top: `calc(${minutesOfDay(ghost.from)} * var(--px-per-min))`,
            height: `calc(${GHOST_MINUTES} * var(--px-per-min) - 2px)`,
          }}
        >
          {/* Labelled only where no block covers it: a label peeking out under a block reads as its text. */}
          {!items.some((item) => item.start < minutesOfDay(ghost.from) + GHOST_MINUTES && item.end > minutesOfDay(ghost.from)) && (
            <span className="ghost-label">{ghost.kind === 'MISSED' ? t('grid.missedHere') : t('grid.wasHere')}</span>
          )}
        </div>
      ))}

      {draft && (
        <div
          className="draft"
          aria-hidden="true"
          style={{
            top: `calc(${minutesOfDay(draft.start)} * var(--px-per-min))`,
            height: `calc(${Math.min(draft.minutes, DAY_MINUTES - minutesOfDay(draft.start))} * var(--px-per-min) - 2px)`,
          }}
        >
          {formatClock(minutesOfDay(draft.start))}
        </div>
      )}

      <div className="slot-hover" ref={hoverRef} hidden aria-hidden="true" />

      {today && (
        <div className="now-line" style={{ top: `calc(${nowMinute} * var(--px-per-min))` }} aria-hidden="true" />
      )}

      {items.map(renderBlock)}
    </div>
  )
}


