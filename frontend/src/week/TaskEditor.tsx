import { useId, useState } from 'react'

import { ApiError } from '../api/client'
import type { Priority, Task, TaskInput } from '../api/types'
import { t } from '../i18n/en'
import { DAY_NAMES, addDays, formatDayTime, formatDuration, isoDay, toLocalDateTime } from '../lib/time'

interface TaskEditorProps {
  /** The task being edited, or null for a new one. */
  task: Task | null
  /** The clicked time a new task was started from; null when it came from the New task button. */
  slot: Date | null
  onSubmit: (input: TaskInput) => Promise<unknown>
  onCancel: () => void
  /** Reports the chosen duration while the form is open, so the calendar can outline the slot. */
  onDraftChange?: (minutes: number, fixed: boolean) => void
  onDelete?: () => Promise<unknown>
  busy: boolean
  /** Id for the form heading, which labels the popover. */
  headingId: string
}

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH']
const DURATIONS = [15, 30, 45, 60, 90, 120]

type DeadlineChoice = 'none' | 'today' | 'tomorrow' | 'friday' | 'custom'

/** Date-only 'YYYY-MM-DD' for a local date, without a timezone shift. */
const dateOnly = (date: Date) => toLocalDateTime(date).slice(0, 10)

/** The next Friday strictly after today: "Today" already covers a Friday. */
function nextFriday(today: Date): Date {
  const ahead = (5 - isoDay(today) + 7) % 7 || 7
  return addDays(today, ahead)
}

function deadlineDates() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const friday = nextFriday(today)
  return {
    today: dateOnly(today),
    tomorrow: dateOnly(addDays(today, 1)),
    friday: dateOnly(friday),
    fridayLabel: `${DAY_NAMES[4]} ${friday.getDate()}`,
  }
}

/**
 * Title, duration, priority and deadline, chosen mostly with chips rather than native date and
 * time pickers. A task started from a clicked slot can be fixed at that time (an appointment) or
 * handed to the scheduler with the slot ignored.
 */
export function TaskEditor({ task, slot, onSubmit, onCancel, onDraftChange, onDelete, busy, headingId }: TaskEditorProps) {
  const id = useId()
  const dates = deadlineDates()
  const initialMinutes = task?.estimatedMinutes ?? 60

  const [title, setTitle] = useState(task?.title ?? '')
  const [minutes, setMinutes] = useState(initialMinutes)
  const [customMinutes, setCustomMinutes] = useState(!DURATIONS.includes(initialMinutes))
  const [priority, setPriority] = useState<Priority>(task?.priority ?? 'MEDIUM')
  // An update replaces every field, so the description must round-trip even when hidden.
  const [description, setDescription] = useState(task?.description ?? '')
  const [showDescription, setShowDescription] = useState(Boolean(task?.description))

  // A slot that has already passed cannot hold a fixed task; the server would refuse it.
  const [slotPassed] = useState(() => slot !== null && slot.getTime() < Date.now())
  const [fixed, setFixed] = useState(slot !== null && !slotPassed)

  // Sliced, not parsed: the API sends a zone-less local date-time. A date-only deadline comes
  // back as 23:59 with deadlineHasTime false, so its time must stay empty or a save would turn
  // "due Friday" into "due Friday at 23:59".
  const initialDate = task?.deadline?.slice(0, 10) ?? ''
  const [deadlineDate, setDeadlineDate] = useState(initialDate)
  const [deadlineTime, setDeadlineTime] = useState(
    task?.deadline && task.deadlineHasTime ? task.deadline.slice(11, 16) : '',
  )
  const [deadlineChoice, setDeadlineChoice] = useState<DeadlineChoice>(() => {
    if (!initialDate) return 'none'
    if (deadlineTime) return 'custom'
    if (initialDate === dates.today) return 'today'
    if (initialDate === dates.tomorrow) return 'tomorrow'
    if (initialDate === dates.friday) return 'friday'
    return 'custom'
  })

  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  function chooseMinutes(value: number) {
    setMinutes(value)
    onDraftChange?.(value, fixed)
  }

  function chooseFixed(value: boolean) {
    setFixed(value)
    onDraftChange?.(minutes, value)
  }

  function chooseDeadline(choice: DeadlineChoice) {
    setDeadlineChoice(choice)
    if (choice === 'none') setDeadlineDate('')
    if (choice === 'today') setDeadlineDate(dates.today)
    if (choice === 'tomorrow') setDeadlineDate(dates.tomorrow)
    if (choice === 'friday') setDeadlineDate(dates.friday)
    if (choice !== 'custom') setDeadlineTime('')
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setErrors({})
    setFailure(null)
    try {
      await onSubmit({
        title: title.trim(),
        description: description.trim() || null,
        estimatedMinutes: minutes,
        deadlineDate: deadlineDate || null,
        deadlineTime: deadlineDate && deadlineTime ? `${deadlineTime}:00` : null,
        priority,
        fixedStart: !task && slot && fixed ? toLocalDateTime(slot) : null,
      })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        if (Object.keys(error.fieldErrors).length === 0) setFailure(error.message)
      } else {
        setFailure(t('error.offline'))
      }
    }
  }

  const invalidMinutes = !Number.isInteger(minutes) || minutes < 1 || minutes > 43_200

  return (
    <form className="editor" onSubmit={submit} aria-labelledby={headingId} noValidate>
      <h2 className="sr-only" id={headingId}>
        {task ? t('action.editTask') : t('action.newTask')}
      </h2>

      <input
        className="editor-title"
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        placeholder={t('task.titlePlaceholder')}
        aria-label={t('task.title')}
        maxLength={200}
        data-autofocus
        aria-invalid={Boolean(errors.title) || undefined}
      />
      {errors.title && <p className="field-error">{errors.title}</p>}

      {!task && slot && (
        <fieldset className="editor-row">
          <legend className="editor-label">{t('task.when')}</legend>
          <div className="choice-list">
            <label className="choice" data-disabled={slotPassed || undefined}>
              <input
                type="radio"
                name={`${id}-when`}
                checked={fixed}
                disabled={slotPassed}
                onChange={() => chooseFixed(true)}
              />
              <span className="choice-text">
                <span className="choice-title">
                  {t('task.fixedAt')} {formatDayTime(slot)}
                </span>
                <span className="choice-hint">{slotPassed ? t('task.slotPassed') : t('task.fixedHint')}</span>
              </span>
            </label>
            <label className="choice">
              <input type="radio" name={`${id}-when`} checked={!fixed} onChange={() => chooseFixed(false)} />
              <span className="choice-text">
                <span className="choice-title">{t('task.letPlace')}</span>
                <span className="choice-hint">{t('task.letPlaceHint')}</span>
              </span>
            </label>
          </div>
        </fieldset>
      )}

      <fieldset className="editor-row">
        <legend className="editor-label">{t('task.estimate')}</legend>
        <div className="chips">
          {DURATIONS.map((value) => (
            <label key={value} className="chip" data-selected={(!customMinutes && minutes === value) || undefined}>
              <input
                type="radio"
                name={`${id}-duration`}
                checked={!customMinutes && minutes === value}
                onChange={() => {
                  setCustomMinutes(false)
                  chooseMinutes(value)
                }}
              />
              {formatDuration(value)}
            </label>
          ))}
          <label className="chip" data-selected={customMinutes || undefined}>
            <input
              type="radio"
              name={`${id}-duration`}
              checked={customMinutes}
              onChange={() => setCustomMinutes(true)}
            />
            {t('task.custom')}
          </label>
        </div>
        {customMinutes && (
          <div className="field-unit editor-custom">
            <input
              type="number"
              min={1}
              max={43200}
              step={1}
              value={Number.isNaN(minutes) ? '' : minutes}
              aria-label={t('task.estimateMinutes')}
              aria-invalid={invalidMinutes || Boolean(errors.estimatedMinutes) || undefined}
              onChange={(event) => chooseMinutes(event.target.valueAsNumber)}
            />
            <span aria-hidden="true">{t('unit.min')}</span>
          </div>
        )}
        {errors.estimatedMinutes && <p className="field-error">{errors.estimatedMinutes}</p>}
      </fieldset>

      <fieldset className="editor-row">
        <legend className="editor-label">{t('task.priority')}</legend>
        <div className="segmented">
          {PRIORITIES.map((value) => (
            <label key={value} className="segment" data-selected={priority === value || undefined}>
              <input
                type="radio"
                name={`${id}-priority`}
                checked={priority === value}
                onChange={() => setPriority(value)}
              />
              {t(`priority.${value}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="editor-row">
        <legend className="editor-label">{t('task.deadline')}</legend>
        <div className="chips">
          {(
            [
              ['none', t('task.noDeadline')],
              ['today', t('task.today')],
              ['tomorrow', t('task.tomorrow')],
              ['friday', dates.fridayLabel],
              ['custom', t('task.pickDate')],
            ] as [DeadlineChoice, string][]
          ).map(([choice, label]) => (
            <label key={choice} className="chip" data-selected={deadlineChoice === choice || undefined}>
              <input
                type="radio"
                name={`${id}-deadline`}
                checked={deadlineChoice === choice}
                onChange={() => chooseDeadline(choice)}
              />
              {label}
            </label>
          ))}
        </div>
        {deadlineChoice === 'custom' && (
          <div className="editor-deadline">
            <input
              type="date"
              value={deadlineDate}
              aria-label={t('task.deadlineDate')}
              onChange={(event) => {
                setDeadlineDate(event.target.value)
                if (!event.target.value) setDeadlineTime('')
              }}
            />
            <input
              type="time"
              value={deadlineTime}
              disabled={!deadlineDate}
              aria-label={t('task.deadlineTime')}
              onChange={(event) => setDeadlineTime(event.target.value)}
            />
          </div>
        )}
      </fieldset>

      {showDescription ? (
        <textarea
          className="editor-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder={t('task.description')}
          aria-label={t('task.description')}
          maxLength={2000}
          rows={3}
        />
      ) : (
        <button type="button" className="link-button" onClick={() => setShowDescription(true)}>
          {t('task.addDescription')}
        </button>
      )}

      {failure && (
        <p className="form-failure" role="alert">
          {failure}
        </p>
      )}

      <div className="editor-actions">
        {onDelete &&
          (confirmDelete ? (
            <button type="button" className="button button-danger" onClick={() => onDelete()} disabled={busy}>
              {t('action.confirmDelete')}
            </button>
          ) : (
            <button type="button" className="button button-quiet-danger" onClick={() => setConfirmDelete(true)}>
              {t('action.delete')}
            </button>
          ))}
        <span className="editor-actions-spacer" />
        <button type="button" className="button button-ghost" onClick={onCancel}>
          {t('action.cancel')}
        </button>
        <button
          type="submit"
          className="button button-primary"
          disabled={busy || title.trim() === '' || invalidMinutes}
        >
          {task ? t('action.save') : t('action.create')}
        </button>
      </div>
    </form>
  )
}
