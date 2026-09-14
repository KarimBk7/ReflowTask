import { useState } from 'react'

import { ApiError } from '../api/client'
import type { Priority, TaskInput } from '../api/types'
import { PlusIcon } from '../design/Icon'
import { t } from '../i18n/en'

interface NewStripFormProps {
  onCreate: (input: TaskInput) => Promise<unknown>
  busy: boolean
}

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH']

/**
 * Writing a new strip before racking it.
 *
 * Inline on the board rather than in a modal: adding work needs neither interruption nor
 * protected focus, and the user should see the board they are adding to. Duration is as
 * prominent as the deadline, because duration is what makes a task schedulable at all.
 */
export function NewStripForm({ onCreate, busy }: NewStripFormProps) {
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('60')
  const [deadlineDate, setDeadlineDate] = useState('')
  const [deadlineTime, setDeadlineTime] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setErrors({})
    setFailure(null)
    try {
      await onCreate({
        title: title.trim(),
        estimatedMinutes: Number(minutes),
        deadlineDate: deadlineDate || null,
        deadlineTime: deadlineDate && deadlineTime ? `${deadlineTime}:00` : null,
        priority,
      })
      setTitle('')
      setDeadlineDate('')
      setDeadlineTime('')
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
    <form className="new-strip" onSubmit={submit} aria-labelledby="new-strip-heading">
      <h2 className="new-strip-heading" id="new-strip-heading">
        {t('action.newTask')}
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

      <div className="field">
        <label htmlFor="strip-minutes">{t('task.estimate')}</label>
        <div className="field-unit">
          <input
            id="strip-minutes"
            type="number"
            min={1}
            max={43200}
            step={5}
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
          onChange={(event) => setDeadlineDate(event.target.value)}
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

      <button type="submit" className="lever lever-quiet" disabled={busy || title.trim() === ''}>
        <PlusIcon />
        {t('action.save')}
      </button>

      {failure && (
        <p className="field-error field-error-form" role="alert">
          {failure}
        </p>
      )}
    </form>
  )
}
