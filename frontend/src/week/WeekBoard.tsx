import { useEffect, useState } from 'react'

import type { Block } from '../api/types'
import { t } from '../i18n/en'
import type { BoardConfig, Ghost } from '../lib/board'
import { blockedOn, isToday, workingOn } from '../lib/board'
import { DAY_NAMES, addDays, formatClock, isoDay, minutesOfDay, parseClock, sameDate } from '../lib/time'
import { GhostLayer } from './GhostLayer'
import { Strip } from './Strip'

interface WeekBoardProps {
  weekStart: Date
  days: number[]
  windowStart: number
  windowEnd: number
  config: BoardConfig | undefined
  blocks: Block[]
  ghosts: Ghost[]
  onTogglePin: (block: Block) => void
  onToggleDone: (block: Block) => void
  onEdit: (block: Block) => void
  busy: boolean
}

const PX_PER_MIN = 1.05

export function WeekBoard({
  weekStart,
  days,
  windowStart,
  windowEnd,
  config,
  blocks,
  ghosts,
  onTogglePin,
  onToggleDone,
  onEdit,
  busy,
}: WeekBoardProps) {
  const hours: number[] = []
  for (let minute = windowStart; minute <= windowEnd; minute += 60) hours.push(minute)

  // Matched by id, not title: two tasks may share a name, and a rename between the replan
  // and the render would silently stop marking the strip that actually moved.
  const movedFrom = new Map(
    ghosts.filter((g) => g.taskId !== null && g.to).map((g) => [g.taskId as number, g.from]),
  )

  return (
    <div
      className="board"
      style={{
        ['--px-per-min' as string]: `${PX_PER_MIN}px`,
        // The board's height is the window it draws. Every strip is absolutely
        // positioned, so the field has no intrinsic height to inherit and would
        // otherwise collapse wherever it is not handed one by a flex parent.
        ['--window-minutes' as string]: windowEnd - windowStart,
      }}
    >
      <div className="board-gutter" aria-hidden="true">
        {hours.map((minute) => (
          <span
            key={minute}
            className="gutter-hour"
            style={{ top: `calc(${minute - windowStart} * var(--px-per-min))` }}
          >
            {formatClock(minute)}
          </span>
        ))}
      </div>

      <div className="board-columns" style={{ ['--day-count' as string]: days.length }}>
        {days.map((day) => {
          const date = addDays(weekStart, day - 1)
          const working = workingOn(config, day)
          return (
            <DayColumn
              key={day}
              date={date}
              day={day}
              working={working}
              blocked={blockedOn(config, day)}
              windowStart={windowStart}
              windowEnd={windowEnd}
              blocks={blocks.filter((block) => sameDate(new Date(block.startAt), date))}
              movedFrom={movedFrom}
              onTogglePin={onTogglePin}
              onToggleDone={onToggleDone}
              onEdit={onEdit}
              busy={busy}
            />
          )
        })}

        <GhostLayer
          ghosts={ghosts}
          days={days}
          weekStart={weekStart}
          windowStart={windowStart}
          windowEnd={windowEnd}
          occupied={blocks.map((block) => ({
            start: new Date(block.startAt),
            end: new Date(block.endAt),
          }))}
        />
      </div>
    </div>
  )
}

interface DayColumnProps {
  date: Date
  day: number
  working: ReturnType<typeof workingOn>
  blocked: ReturnType<typeof blockedOn>
  windowStart: number
  windowEnd: number
  blocks: Block[]
  movedFrom: Map<number, Date>
  onTogglePin: (block: Block) => void
  onToggleDone: (block: Block) => void
  onEdit: (block: Block) => void
  busy: boolean
}

function DayColumn({
  date,
  day,
  working,
  blocked,
  windowStart,
  windowEnd,
  blocks,
  movedFrom,
  onTogglePin,
  onToggleDone,
  onEdit,
  busy,
}: DayColumnProps) {
  const today = isToday(date)

  return (
    <section className="day" data-today={today || undefined} aria-label={`${DAY_NAMES[day - 1]} ${date.getDate()}`}>
      <header className="day-head">
        <span className="day-name">{DAY_NAMES[day - 1]}</span>
        <span className="day-date">{date.getDate()}</span>
      </header>

      <div className="day-field">
        {/* Outside the configured hours the enamel is bare: no rules are printed there. */}
        {working && parseClock(working.startTime) > windowStart && (
          <div
            className="off-hours"
            style={{
              top: 0,
              height: `calc(${parseClock(working.startTime) - windowStart} * var(--px-per-min))`,
            }}
          />
        )}
        {working && parseClock(working.endTime) < windowEnd && (
          <div
            className="off-hours"
            style={{
              top: `calc(${parseClock(working.endTime) - windowStart} * var(--px-per-min))`,
              bottom: 0,
            }}
          />
        )}
        {!working && <div className="off-hours" style={{ inset: 0 }} />}

        {blocked.map((period) => (
          <div
            key={`${period.day}-${period.startTime}`}
            className="blocked"
            style={{
              top: `calc(${parseClock(period.startTime) - windowStart} * var(--px-per-min))`,
              height: `calc(${parseClock(period.endTime) - parseClock(period.startTime)} * var(--px-per-min))`,
            }}
          >
            {period.label && <span className="blocked-label">{period.label}</span>}
          </div>
        ))}

        {today && <NowLine windowStart={windowStart} windowEnd={windowEnd} />}

        {blocks.map((block) => (
          <Strip
            key={block.id}
            block={block}
            windowStart={windowStart}
            movedFrom={originUnlessSeatedThere(movedFrom.get(block.taskId), block)}
            onTogglePin={onTogglePin}
            onToggleDone={onToggleDone}
            onEdit={onEdit}
            busy={busy}
          />
        ))}

        {!working && <p className="day-empty">{t('week.noWorkingHours')}</p>}
      </div>
    </section>
  )
}

/**
 * A move is recorded per task, but read per strip. When a task is split, the piece still sitting
 * where the task began did not move - labelling it "moved from 15:30" while it sits at 15:30
 * would be a false statement. Only a strip that is actually somewhere else carries the origin.
 */
function originUnlessSeatedThere(origin: Date | undefined, block: Block): Date | null {
  if (!origin) return null
  return origin.getTime() === new Date(block.startAt).getTime() ? null : origin
}

/** The present, drawn in the same hand as the ghosts and registered to the same ruler. */
function NowLine({ windowStart, windowEnd }: { windowStart: number; windowEnd: number }) {
  const [now, setNow] = useState(() => minutesOfDay(new Date()))

  useEffect(() => {
    const timer = window.setInterval(() => setNow(minutesOfDay(new Date())), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  if (now < windowStart || now > windowEnd) return null

  return (
    <div
      className="now-line"
      style={{ top: `calc(${now - windowStart} * var(--px-per-min))` }}
      aria-label={`Now, ${formatClock(now)}`}
    >
      <span className="now-time">{formatClock(now)}</span>
    </div>
  )
}

export { isoDay }
