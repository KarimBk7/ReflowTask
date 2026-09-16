import { useState } from 'react'

import { ApiError } from '../api/client'
import type { Priority, Task, TaskInput } from '../api/types'
import { PlusIcon } from '../design/Icon'
import { t } from '../i18n/en'

interface StripFormProps {
  /** The task being edited, or null to write a new one. */
  editing: Task | null
  onSave: (input: TaskInput) => Promise<unknown>
  onCancel: () => void
  busy: boolean
}

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH']

/**
 * Writing a strip - a new one before racking it, or an existing one in place.
 *
 * Inline on the board rather than in a modal: neither task needs interruption or protected
 * focus, and the user should see the board they are changing. Duration is as prominent as
 * the deadline, because duration is what makes a task schedulable at all.
 *
 * The parent remounts this per task (keyed by id), so the fields always start from the task
 * being edited rather than whatever the previous one left behind.
 */
export function StripForm({ editing, onSave, onCancel, busy }: StripFormProps) {
  const [title, setTitle] = useState(editing?.title ?? '')
  // An update replaces every field, so the description must round-trip even though most
  // tasks never have one: leaving it out of the form would silently erase it on save.
  const [description, setDescription] = useState(editing?.description ?? '')
  const [minutes, setMinutes] = useState(String(editing?.estimatedMinutes ?? 60))
  // Sliced rather than parsed: the API sends a zone-less 'YYYY-MM-DDTHH:mm:ss', and slicing
  // cannot shift it across a timezone. A date-only deadline is stored as 23:59 with
  // deadlineHasTime false, so its time field must come back empty - prefilling '23:59'
  // would quietly turn "due Friday" into "due Friday at 23:59" on the next save.
  const [deadlineDate, setDeadlineDate] = useState(editing?.deadline?.slice(0, 10) ?? '')
  const [deadlineTime, setDeadlineTime] = useState(
    editing?.deadline && editing.deadlineHasTime ? editing.deadline.slice(11, 16) : '',
  )
  const [priority, setPriority] = useState<Priority>(editing?.priority ?? 'MEDIUM')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setErrors({})
    setFailure(null)
    try {
      await onSave({
        title: title.trim(),
        description: description.trim() || null,
        estimatedMinutes: Number(minutes),
        deadlineDate: deadlineDate || null,
        deadlineTime: deadlineDate && deadlineTime ? `${deadlineTime}:00` : null,
        priority,
      })
      if (!editing) {
        setTitle('')
        setDescription('')
        setDeadlineDate('')
        setDeadlineTime('')
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        if (Object.keys(error.fieldErrors).length === 0) setFailure(error.message)
      } else {
        setFailure(t('error.offline'))
      }
    }
  }

  return (
    <form
      className="new-strip"
      data-editing={editing ? true : undefined}
      onSubmit={submit}
      aria-labelledby="strip-form-heading"
    >
      <h2 className="new-strip-heading" id="strip-form-heading">
        {editing ? t('action.editTask') : t('action.newTask')}
      </h2>

      <div className="field field-wide">
        <label htmlFor="strip-title">{t('task.title')}</label>
        <input
          id="strip-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
          maxLength={200}
          aria-invalid={Boolean(errors.title) || undefined}
          aria-describedby={errors.title ? 'strip-title-error' : undefined}
        />
        {errors.title && (
          <p className="field-error" id="strip-title-error">
            {errors.title}
          </p>
        )}
      </div>

      <div className="field field-wide">
        <label htmlFor="strip-description">{t('task.description')}</label>
        <textarea
          id="strip-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={2000}
          rows={2}
          aria-invalid={Boolean(errors.description) || undefined}
        />
        {errors.description && <p className="field-error">{errors.description}</p>}
      </div>

      <div className="field">
        <label htmlFor="strip-minutes">{t('task.estimate')}</label>
        <div className="field-unit">
          {/* step is 1, matching the server's own rule, and must not be 5. A browser counts
              steps from min, so min=1 step=5 accepts only 1, 6, 11 ... 61, 91 - which
              rejected the form's own default of 60 and blocked every save without a request
              ever being sent. It would also make a 42-minute task created through the API
              impossible to save once opened for editing. */}
          <input
            id="strip-minutes"
            type="number"
            min={1}
            max={43200}
            step={1}
            value={minutes}
            onChange={(event) => setMinutes(event.target.value)}
            required
            aria-invalid={Boolean(errors.estimatedMinutes) || undefined}
          />
          <span aria-hidden="true">min</span>
        </div>
        {errors.estimatedMinutes && <p className="field-error">{errors.estimatedMinutes}</p>}
      </div>

      <div className="field">
        <label htmlFor="strip-deadline">{t('task.deadline')}</label>
        <input
          id="strip-deadline"
          type="date"
          value={deadlineDate}
          onChange={(event) => {
            setDeadlineDate(event.target.value)
            // Clearing the date clears its time too, rather than leaving a hidden time
            // that the server would reject as a time without a date.
            if (!event.target.value) setDeadlineTime('')
          }}
        />
      </div>

      <div className="field">
        <label htmlFor="strip-deadline-time">{t('task.deadlineTime')}</label>
        <input
          id="strip-deadline-time"
          type="time"
          value={deadlineTime}
          disabled={!deadlineDate}
          onChange={(event) => setDeadlineTime(event.target.value)}
        />
      </div>

      <fieldset className="field field-priority">
        <legend>{t('task.priority')}</legend>
        <div className="segmented">
          {PRIORITIES.map((value) => (
            <label key={value} className="segment" data-selected={priority === value || undefined}>
              <input
                type="radio"
                name="priority"
                value={value}
                checked={priority === value}
                onChange={() => setPriority(value)}
              />
              {t(`priority.${value}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <div className="form-actions">
        <button type="submit" className="lever lever-quiet" disabled={busy || title.trim() === ''}>
          {!editing && <PlusIcon />}
          {t('action.save')}
        </button>
        {editing && (
          <button type="button" className="trim-button trim-button-text" onClick={onCancel}>
            {t('action.cancel')}
          </button>
        )}
      </div>

      {failure && (
        <p className="field-error field-error-form" role="alert">
          {failure}
        </p>
      )}
    </form>
  )
}
