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

  // Below this there is no room for the times as well as the title and the move note.
  // The move note wins: position on the ruler already says when the strip is.
  const compact = minutes < 55

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

        <p className="strip-meta">
          <time dateTime={block.startAt}>{formatTime(start)}</time>
          {!compact && (
            <>
              <span aria-hidden="true">–</span>
              <time dateTime={block.endAt}>{formatTime(end)}</time>
            </>
          )}
          <span className="strip-dot" aria-hidden="true" />
          {formatDuration(minutes)}
        </p>

        {/* Stated in words, not only drawn: the arrow can be missed, and a 480ms
            animation is gone by the time anyone looks up. A compacted schedule produces
            short strips, so this may never be suppressed for want of room - it shortens
            to the origin time instead. */}
        {movedFrom && (
          <p className="strip-moved">
            {compact ? formatTime(movedFrom) : `${t('state.movedFrom')} ${formatTime(movedFrom)}`}
          </p>
        )}
      </div>

      {/* The mark the THESIS promises: a chinagraph bracket scored down the strip's
          leading edge where it was re-seated. Permanent, and carries no colour. */}
      {movedFrom && <span className="strip-rescored" aria-hidden="true" />}

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
