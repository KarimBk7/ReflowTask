import { useEffect, useRef, useState } from 'react'

import { ApiError } from '../api/client'
import type { BoardConfig, DayOfWeek } from '../api/types'
import { PlusIcon, TrashIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { DAY_NAMES, parseClock } from '../lib/time'

interface HoursFormProps {
  config: BoardConfig
  onSave: (config: BoardConfig) => Promise<unknown>
  onClose: () => void
  busy: boolean
}

const WEEK: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** What a newly enabled day starts with, before the user adjusts it. */
const DEFAULT_START = '09:00'
const DEFAULT_END = '18:00'

interface DayRow {
  day: DayOfWeek
  working: boolean
  start: string
  end: string
}

interface BlockedRow {
  /** A client-side key only: blocked periods are replaced wholesale on save. */
  key: number
  day: DayOfWeek
  start: string
  end: string
  label: string
}

/** The API sends 'HH:mm:ss'; a time input wants 'HH:mm'. */
const toInput = (clock: string) => clock.slice(0, 5)

const endsAfterStart = (start: string, end: string) =>
  start !== '' && end !== '' && parseClock(end) > parseClock(start)

/**
 * The hours the board is ruled for, written into the rail.
 *
 * Shown in place of the strip rail rather than in a modal: changing hours needs neither
 * interruption nor protected focus, and the board stays visible beside it. Saving replaces the
 * whole configuration and replans, so the board redraws against the new hours immediately.
 */
export function HoursForm({ config, onSave, onClose, busy }: HoursFormProps) {
  const [days, setDays] = useState<DayRow[]>(() =>
    WEEK.map((day) => {
      const window = config.workingHours.find((candidate) => candidate.day === day)
      return window
        ? { day, working: true, start: toInput(window.startTime), end: toInput(window.endTime) }
        : { day, working: false, start: DEFAULT_START, end: DEFAULT_END }
    }),
  )
  const nextKey = useRef(0)
  const [blocked, setBlocked] = useState<BlockedRow[]>(() =>
    config.blockedPeriods.map((period) => ({
      key: nextKey.current++,
      day: period.day,
      start: toInput(period.startTime),
      end: toInput(period.endTime),
      label: period.label ?? '',
    })),
  )
  const [horizon, setHorizon] = useState(String(config.horizonDays))
  const [minChunk, setMinChunk] = useState(String(config.minChunkMinutes))
  const [failure, setFailure] = useState<string | null>(null)

  // The form opens from a trim button across the page; move focus to it so a keyboard user
  // lands where the content just appeared instead of being left behind at the trim.
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
  }, [])

  const invalidDays = days.filter((row) => row.working && !endsAfterStart(row.start, row.end))
  const invalidBlocked = blocked.filter((row) => !endsAfterStart(row.start, row.end))
  const valid = invalidDays.length === 0 && invalidBlocked.length === 0

  function updateDay(day: DayOfWeek, change: Partial<DayRow>) {
    setDays((rows) => rows.map((row) => (row.day === day ? { ...row, ...change } : row)))
  }

  function updateBlocked(key: number, change: Partial<BlockedRow>) {
    setBlocked((rows) => rows.map((row) => (row.key === key ? { ...row, ...change } : row)))
  }

  function addBlocked() {
    setBlocked((rows) => [
      ...rows,
      { key: nextKey.current++, day: 'MONDAY', start: '12:00', end: '13:00', label: '' },
    ])
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    // Ranges on the number fields are left to the browser's own validation, which runs
    // before this handler. What it cannot express is "later than that other field", so
    // start-before-end is checked here - otherwise the save would only fail after a round
    // trip, with no way to point at the row that caused it.
    if (!valid) {
      setFailure(t('hours.invalid'))
      return
    }
    try {
      await onSave({
        workingHours: days
          .filter((row) => row.working)
          .map((row) => ({ day: row.day, startTime: row.start, endTime: row.end, label: null })),
        blockedPeriods: blocked.map((row) => ({
          day: row.day,
          startTime: row.start,
          endTime: row.end,
          label: row.label.trim() || null,
        })),
        horizonDays: Number(horizon),
        minChunkMinutes: Number(minChunk),
      })
      onClose()
    } catch (error) {
      setFailure(error instanceof ApiError ? t('hours.rejected') : t('error.offline'))
    }
  }

  return (
    <form className="hours" onSubmit={submit} aria-labelledby="hours-heading">
      <h2 className="new-strip-heading" id="hours-heading" ref={heading} tabIndex={-1}>
        {t('hours.title')}
      </h2>

      <fieldset className="hours-group">
        <legend className="sr-only">{t('hours.working')}</legend>
        {days.map((row) => {
          const name = DAY_NAMES[WEEK.indexOf(row.day)]
          const invalid = row.working && !endsAfterStart(row.start, row.end)
          return (
            <div key={row.day} className="hours-day" data-off={!row.working || undefined}>
              <label className="hours-toggle">
                <input
                  type="checkbox"
                  checked={row.working}
                  onChange={(event) => updateDay(row.day, { working: event.target.checked })}
                />
                <span className="hours-day-name">{name}</span>
              </label>
              {row.working ? (
                <span className="hours-range">
                  <input
                    type="time"
                    value={row.start}
                    aria-label={`${name} ${t('hours.from')}`}
                    aria-invalid={invalid || undefined}
                    onChange={(event) => updateDay(row.day, { start: event.target.value })}
                  />
                  <span aria-hidden="true">–</span>
                  <input
                    type="time"
                    value={row.end}
                    aria-label={`${name} ${t('hours.until')}`}
                    aria-invalid={invalid || undefined}
                    onChange={(event) => updateDay(row.day, { end: event.target.value })}
                  />
                </span>
              ) : (
                <span className="hours-off">{t('hours.offDay')}</span>
              )}
              {invalid && <p className="field-error hours-row-error">{t('hours.endsBeforeStart')}</p>}
            </div>
          )
        })}
      </fieldset>

      <fieldset className="hours-group">
        <legend className="hours-legend">{t('hours.blocked')}</legend>
        <p className="hours-hint">{t('hours.blockedHint')}</p>
        {blocked.map((row) => {
          const invalid = !endsAfterStart(row.start, row.end)
          return (
            <div key={row.key} className="hours-blocked">
              <div className="hours-blocked-line">
                <select
                  value={row.day}
                  aria-label={t('hours.day')}
                  onChange={(event) => updateBlocked(row.key, { day: event.target.value as DayOfWeek })}
                >
                  {WEEK.map((day, index) => (
                    <option key={day} value={day}>
                      {DAY_NAMES[index]}
                    </option>
                  ))}
                </select>
                <input
                  type="time"
                  value={row.start}
                  aria-label={t('hours.from')}
                  aria-invalid={invalid || undefined}
                  onChange={(event) => updateBlocked(row.key, { start: event.target.value })}
                />
                <span aria-hidden="true">–</span>
                <input
                  type="time"
                  value={row.end}
                  aria-label={t('hours.until')}
                  aria-invalid={invalid || undefined}
                  onChange={(event) => updateBlocked(row.key, { end: event.target.value })}
                />
                <button
                  type="button"
                  className="rail-delete"
                  onClick={() => setBlocked((rows) => rows.filter((candidate) => candidate.key !== row.key))}
                >
                  <TrashIcon />
                  <span className="sr-only">{t('action.remove')}</span>
                </button>
              </div>
              <input
                className="hours-label"
                value={row.label}
                maxLength={100}
                placeholder={t('hours.label')}
                aria-label={t('hours.label')}
                onChange={(event) => updateBlocked(row.key, { label: event.target.value })}
              />
              {invalid && <p className="field-error">{t('hours.endsBeforeStart')}</p>}
            </div>
          )
        })}
        <button type="button" className="trim-button trim-button-text hours-add" onClick={addBlocked}>
          <PlusIcon />
          {t('hours.addBlocked')}
        </button>
      </fieldset>

      <fieldset className="hours-group">
        <legend className="hours-legend">{t('hours.planning')}</legend>
        <div className="field">
          <label htmlFor="hours-horizon">{t('hours.horizon')}</label>
          <div className="field-unit">
            <input
              id="hours-horizon"
              type="number"
              min={1}
              max={366}
              step={1}
              value={horizon}
              onChange={(event) => setHorizon(event.target.value)}
            />
            <span aria-hidden="true">{t('hours.horizonUnit')}</span>
          </div>
        </div>
        <div className="field">
          <label htmlFor="hours-chunk">{t('hours.minChunk')}</label>
          <div className="field-unit">
            <input
              id="hours-chunk"
              type="number"
              min={1}
              max={1440}
              step={1}
              value={minChunk}
              onChange={(event) => setMinChunk(event.target.value)}
            />
            <span aria-hidden="true">{t('hours.minChunkUnit')}</span>
          </div>
        </div>
      </fieldset>

      <div className="form-actions">
        <button type="submit" className="lever lever-quiet" disabled={busy}>
          {t('action.save')}
        </button>
        <button type="button" className="trim-button trim-button-text" onClick={onClose}>
          {t('action.cancel')}
        </button>
      </div>

      {failure && (
        <p className="field-error field-error-form" role="alert">
          {failure}
        </p>
      )}
    </form>
  )
}
