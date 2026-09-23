import type { Block } from '../api/types'
import { t } from '../i18n/en'
import { blocksByDay, isToday } from '../lib/board'
import { DAY_NAMES, formatTime, monthGridDays, toLocalDateTime } from '../lib/time'

const MAX_VISIBLE = 3

interface MonthGridProps {
  monthStart: Date
  blocks: Block[]
  onPickDay: (day: Date) => void
}

/**
 * A read-only overview: what is planned on each day of the month. Editing and dragging stay in
 * the week view, so a day is one button that jumps there rather than a second, smaller board.
 */
export function MonthGrid({ monthStart, blocks, onPickDay }: MonthGridProps) {
  const byDay = blocksByDay(blocks)

  return (
    <div className="month">
      <div className="month-head" aria-hidden="true">
        {DAY_NAMES.map((name) => (
          <span key={name}>{name}</span>
        ))}
      </div>
      <div className="month-grid">
        {monthGridDays(monthStart).map((day) => {
          const dayBlocks = byDay.get(toLocalDateTime(day).slice(0, 10)) ?? []
          const hidden = dayBlocks.length - MAX_VISIBLE
          return (
            <button
              key={day.getTime()}
              type="button"
              className="month-day"
              data-outside={day.getMonth() !== monthStart.getMonth() || undefined}
              data-today={isToday(day) || undefined}
              onClick={() => onPickDay(day)}
              aria-label={`${day.toLocaleDateString('en', { weekday: 'long', day: 'numeric', month: 'long' })}, ${dayBlocks.length} ${t('month.tasks')}`}
            >
              <span className="month-date">{day.getDate()}</span>
              {dayBlocks.slice(0, MAX_VISIBLE).map((block) => (
                <span key={block.id} className="month-item" data-priority={block.priority} data-done={block.status === 'DONE' || undefined}>
                  <span className="month-time">{formatTime(new Date(block.startAt))}</span> {block.taskTitle}
                </span>
              ))}
              {hidden > 0 && (
                <span className="month-more">
                  +{hidden} {t('month.more')}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
