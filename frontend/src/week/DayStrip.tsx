import type { Block } from '../api/types'
import { ChevronIcon } from '../design/Icon'
import { LOCALE, t } from '../i18n/en'
import { isToday } from '../lib/board'
import { DAY_NAMES, addDays, sameDate } from '../lib/time'

interface DayStripProps {
  weekStart: Date
  /** ISO weekday shown in the grid, 1 = Monday. */
  selected: number
  blocks: Block[]
  onSelect: (day: number) => void
  onStep: (direction: -1 | 1) => void
}

/**
 * The phone's week: seven days in a row, one of them open in the grid below. A dot marks a day with
 * work on it, so the week still reads at a glance while only one day fits the screen.
 */
export function DayStrip({ weekStart, selected, blocks, onSelect, onStep }: DayStripProps) {
  return (
    <nav className="day-strip" aria-label={t('week.navigation')}>
      <button type="button" className="icon-button" onClick={() => onStep(-1)}>
        <ChevronIcon direction="left" />
        <span className="sr-only">{t('week.previous')}</span>
      </button>
      <div className="day-strip-days">
        {DAY_NAMES.map((name, index) => {
          const day = index + 1
          const date = addDays(weekStart, index)
          const work = blocks.filter((block) => block.state !== 'MISSED' && sameDate(new Date(block.startAt), date)).length
          return (
            <button
              key={day}
              type="button"
              className="day-chip"
              aria-pressed={selected === day}
              aria-label={`${date.toLocaleDateString(LOCALE, { weekday: 'long', day: 'numeric', month: 'long' })}, ${work} ${t('month.tasks')}`}
              data-today={isToday(date) || undefined}
              onClick={() => onSelect(day)}
            >
              <span className="day-chip-name">{name}</span>
              <span className="day-chip-date">{date.getDate()}</span>
              <span className="day-chip-dot" data-on={work > 0 || undefined} />
            </button>
          )
        })}
      </div>
      <button type="button" className="icon-button" onClick={() => onStep(1)}>
        <ChevronIcon direction="right" />
        <span className="sr-only">{t('week.next')}</span>
      </button>
    </nav>
  )
}
