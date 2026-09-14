import type { Block } from '../api/types'
import { CheckIcon, PinIcon, RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { formatDuration, formatTime, minutesOfDay } from '../lib/time'

interface StripProps {
  block: Block
  windowStart: number
  /** Where this strip sat before the last replan, when it moved. */
  movedFrom: Date | null
  onTogglePin: (block: Block) => void
  onToggleDone: (block: Block) => void
  busy: boolean
}

/**
 * A laminated magnet strip seated in the board's grid.
 *
 * Its actions are labelled buttons that are always in the DOM rather than hover-revealed
 * icons: the board's controls are physical things you can see without touching it. They stay
 * visually quiet until focus or hover, but they are always reachable by keyboard and always
 * announced.
 */
export function Strip({
  block,
  windowStart,
  movedFrom,
  onTogglePin,
  onToggleDone,
  busy,
}: StripProps) {
  const start = new Date(block.startAt)
  const end = new Date(block.endAt)
  const offset = minutesOfDay(start) - windowStart
  const minutes = (end.getTime() - start.getTime()) / 60_000
  const done = block.status === 'DONE'

  // Below roughly half an hour there is no room for a second line; the strip keeps its
  // title and drops the times rather than clipping both.
  const compact = minutes < 35
  // The move note needs a third line. Clipping it half-off the strip edge would be worse
  // than leaving it to the arrow and the record, so it waits for a strip that can hold it.
  const roomForMoveNote = minutes >= 60

  return (
    <article
      className="strip"
      data-risk={block.atRisk || undefined}
      data-pinned={block.pinned || undefined}
      data-done={done || undefined}
      data-moved={movedFrom ? true : undefined}
      data-compact={compact || undefined}
      style={{
        top: `calc(${offset} * var(--px-per-min))`,
        height: `calc(${minutes} * var(--px-per-min))`,
      }}
    >
      <div className="strip-body">
        <h3 className="strip-title">{block.taskTitle}</h3>
        {!compact && (
          <p className="strip-meta">
            <time dateTime={block.startAt}>{formatTime(start)}</time>
            <span aria-hidden="true">–</span>
            <time dateTime={block.endAt}>{formatTime(end)}</time>
            <span className="strip-dot" aria-hidden="true" />
            {formatDuration(minutes)}
          </p>
        )}

        {/* Stated in words, not only drawn: the arrow can be missed, and a 480ms
            animation is gone by the time anyone looks up. */}
        {movedFrom && roomForMoveNote && (
          <p className="strip-moved">{t('state.movedFrom')} {formatTime(movedFrom)}</p>
        )}
      </div>

      <div className="strip-marks" aria-hidden="true">
        {block.pinned && <PinIcon className="mark mark-pin" />}
        {block.atRisk && <RiskIcon className="mark mark-risk" />}
        {done && <CheckIcon className="mark mark-done" />}
      </div>

      <div className="strip-actions">
        <button
          type="button"
          className="strip-action"
          onClick={() => onToggleDone(block)}
          disabled={busy}
        >
          {done ? t('action.reopen') : t('action.markDone')}
        </button>
        <button
          type="button"
          className="strip-action"
          onClick={() => onTogglePin(block)}
          disabled={busy}
        >
          {block.pinned ? t('action.unpin') : t('action.pin')}
        </button>
      </div>

      {block.atRisk && <span className="sr-only">{t('state.atRisk')}</span>}
    </article>
  )
}
