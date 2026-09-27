import { useState } from 'react'

import type { Block, Task } from '../api/types'
import { CheckIcon, CloseIcon, EditIcon, PinIcon, PriorityIcon, ReflowIcon, RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import type { Ghost } from '../lib/board'
import { DAY_NAMES, dayOfMonth, formatDayTime, formatDeadline, formatDuration, formatTime, isoDay, shortMonth } from '../lib/time'

interface BlockDetailsProps {
  block: Block
  task: Task | undefined
  origin: Ghost | null
  /** The task's other blocks in the week on screen. */
  otherParts: Block[]
  onToggleDone: () => void
  /** Marks this one part done, or undoes that. */
  onTogglePartDone: () => void
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
export function BlockDetails({ block, task, origin, otherParts, onToggleDone, onTogglePartDone, onTogglePin, onEdit, onClose, busy, headingId }: BlockDetailsProps) {
  const start = new Date(block.startAt)
  const end = new Date(block.endAt)
  const minutes = (end.getTime() - start.getTime()) / 60_000
  const done = block.status === 'DONE'
  const missed = block.state === 'MISSED'
  const partDone = block.state === 'DONE'
  // A part that has started, of a larger task, or a part that is already history: then the part
  // itself gets an action. A part still ahead has no time of its own to record as done, and a task
  // that is a single block is simply marked done as a whole.
  // Read once when the popover opens, which is when the choice is being made.
  const [openedAt] = useState(() => Date.now())
  const started = start.getTime() <= openedAt
  const partAction =
    !done && (missed || partDone || (started && task !== undefined && minutes < task.estimatedMinutes))

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
        {DAY_NAMES[isoDay(start) - 1]} {dayOfMonth(start)} {shortMonth(start)}
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
        {missed && (
          <li className="fact fact-risk">
            <RiskIcon size={14} />
            {t('details.missedPart')}
          </li>
        )}
        {partDone && !done && (
          <li className="fact">
            <CheckIcon size={14} />
            {t('details.partDone')}
          </li>
        )}
        {task && task.doneMinutes > 0 && task.status !== 'DONE' && (
          <li className="fact">
            {formatDuration(task.doneMinutes)} {t('details.of')} {formatDuration(task.estimatedMinutes)} {t('details.doneSoFar')}
          </li>
        )}
        {task && minutes < task.estimatedMinutes && (
          <li className="fact fact-parts">
            <span>
              {t('details.partOf')} {formatDuration(task.estimatedMinutes)} {t('details.partTask')}{' '}
              {otherParts.length > 0
                ? `${t('details.otherParts')} ${otherParts.map((part) => formatDayTime(new Date(part.startAt))).join(', ')}.`
                : t('details.partsElsewhere')}
            </span>
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
          {t(`details.priority.${block.priority}`)}
        </li>
        {!block.atRisk && task?.deadline && (
          <li className="fact">
            {t('details.due')} {formatDeadline(task.deadline, task.deadlineHasTime)}
          </li>
        )}
        {task?.notBefore && new Date(task.notBefore) > new Date() && (
          <li className="fact">
            {t('details.notBefore')} {formatDeadline(task.notBefore, !task.notBefore.endsWith('T00:00:00'))}
          </li>
        )}
        {task?.profile === 'PERSONAL' && <li className="fact">{t('profile.PERSONAL')}</li>}
        {task?.recurrence && (
          <li className="fact">
            <ReflowIcon size={14} />
            {t('details.repeats')} {t(`repeat.${task.recurrence}`).toLowerCase()}
          </li>
        )}
      </ul>

      {task?.description && <p className="details-description">{task.description}</p>}

      <div className="details-actions">
        {partAction && (
          <button type="button" className="button button-primary" onClick={onTogglePartDone} disabled={busy}>
            <CheckIcon size={14} />
            {partDone ? t('action.partNotDone') : missed ? t('action.didIt') : t('action.partDone')}
          </button>
        )}
        <button
          type="button"
          className={partAction ? 'button button-secondary' : 'button button-primary'}
          onClick={onToggleDone}
          disabled={busy}
        >
          <CheckIcon size={14} />
          {done ? t('action.reopen') : partAction ? t('action.taskDone') : t('action.markDone')}
        </button>
        <button
          type="button"
          className="button button-secondary"
          onClick={onTogglePin}
          disabled={busy || block.state !== 'PLANNED'}
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
