import type { Block, Task } from '../api/types'
import { CheckIcon, CloseIcon, EditIcon, PinIcon, PriorityIcon, RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import type { Ghost } from '../lib/board'
import { DAY_NAMES, formatDayTime, formatDeadline, formatDuration, formatTime, isoDay } from '../lib/time'

interface BlockDetailsProps {
  block: Block
  task: Task | undefined
  origin: Ghost | null
  onToggleDone: () => void
  onTogglePin: () => void
  onEdit: () => void
  onClose: () => void
  busy: boolean
  /** Id for the title, which labels the popover. */
  headingId: string
}

const LEVEL = { LOW: 1, MEDIUM: 2, HIGH: 3 } as const

/**
 * What a block is and why it sits where it does, with the actions for it. The actions live
 * here rather than on the block itself, so a block on the calendar only ever carries its title
 * and time and never runs out of room for its controls.
 */
export function BlockDetails({ block, task, origin, onToggleDone, onTogglePin, onEdit, onClose, busy, headingId }: BlockDetailsProps) {
  const start = new Date(block.startAt)
  const end = new Date(block.endAt)
  const minutes = (end.getTime() - start.getTime()) / 60_000
  const done = block.status === 'DONE'

  return (
    <div className="details">
      <div className="details-head">
        <h2 className="details-title" id={headingId} data-done={done || undefined}>
          {block.taskTitle}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>

      <p className="details-when">
        {DAY_NAMES[isoDay(start) - 1]} {start.getDate()} {start.toLocaleDateString('en', { month: 'short' })}
        <span className="dot" aria-hidden="true" />
        <span className="numeric">
          {formatTime(start)} – {formatTime(end)}
        </span>
        <span className="dot" aria-hidden="true" />
        {formatDuration(minutes)}
      </p>

      <ul className="details-facts">
        {origin && (
          <li className="fact fact-changed">
            <span className="fact-mark" aria-hidden="true" />
            {t(origin.kind === 'MISSED' ? 'details.missedAt' : 'details.movedFrom')} {formatDayTime(origin.from)}
          </li>
        )}
        {block.atRisk && (
          <li className="fact fact-risk">
            <RiskIcon size={14} />
            {t('details.atRisk')}
            {task?.deadline && ` ${formatDeadline(task.deadline, task.deadlineHasTime)}`}
          </li>
        )}
        {block.pinned && (
          <li className="fact">
            <PinIcon size={14} />
            {t('details.pinned')}
          </li>
        )}
        {done && (
          <li className="fact">
            <CheckIcon size={14} />
            {t('details.done')}
          </li>
        )}
        <li className="fact">
          <PriorityIcon size={14} level={LEVEL[block.priority]} />
          {t(`priority.${block.priority}`)} {t('details.priority')}
        </li>
        {!block.atRisk && task?.deadline && (
          <li className="fact">
            {t('details.due')} {formatDeadline(task.deadline, task.deadlineHasTime)}
          </li>
        )}
      </ul>

      {task?.description && <p className="details-description">{task.description}</p>}

      <div className="details-actions">
        <button type="button" className="button button-primary" onClick={onToggleDone} disabled={busy}>
          <CheckIcon size={14} />
          {done ? t('action.reopen') : t('action.markDone')}
        </button>
        <button
          type="button"
          className="button button-secondary"
          onClick={onTogglePin}
          disabled={busy}
          aria-pressed={block.pinned}
        >
          <PinIcon size={14} />
          {block.pinned ? t('action.unpin') : t('action.pin')}
        </button>
        <button type="button" className="button button-secondary" onClick={onEdit} disabled={busy || !task}>
          <EditIcon size={14} />
          {t('action.edit')}
        </button>
      </div>
    </div>
  )
}
