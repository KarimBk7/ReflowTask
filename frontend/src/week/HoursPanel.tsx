import { useEffect, useRef, useState } from 'react'

import { ApiError } from '../api/client'
import type { BoardConfig, DayOfWeek } from '../api/types'
import { CloseIcon, PlusIcon, TrashIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { DAY_NAMES, parseClock } from '../lib/time'

interface HoursPanelProps {
  config: BoardConfig
  /** First run: the owner has never saved hours, so the panel introduces itself. */
  welcome: boolean
  onSave: (config: BoardConfig) => Promise<unknown>
  onClose: () => void
  busy: boolean
}

const WEEK: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const WEEKDAYS: DayOfWeek[] = WEEK.slice(0, 5)

/** Common weeks in one click. Anything else, like Tue to Sat, is the day switches below. */
const PRESETS: { key: 'hours.presetWeekdays' | 'hours.presetSix' | 'hours.presetAll'; days: DayOfWeek[] }[] = [
  { key: 'hours.presetWeekdays', days: WEEKDAYS },
  { key: 'hours.presetSix', days: WEEK.slice(0, 6) },
  { key: 'hours.presetAll', days: WEEK },
]

interface DayRow {
  day: DayOfWeek
  working: boolean
  start: string
  end: string
}

/**
 * One break across several days. The API stores one period per day; the panel groups periods
 * with the same time and label so "Lunch, Mon to Fri" is one row, and expands them again on save.
 */
interface BreakRow {
  key: number
  days: DayOfWeek[]
  start: string
  end: string
  label: string
}

/** The API sends 'HH:mm:ss'; a time input wants 'HH:mm'. */
const toInput = (clock: string) => clock.slice(0, 5)

/** Client-side keys only: breaks are replaced wholesale on save. */
let breakKeys = 0
const nextKey = () => breakKeys++

const endsAfterStart = (start: string, end: string) =>
  start !== '' && end !== '' && parseClock(end) > parseClock(start)

function groupBreaks(config: BoardConfig): BreakRow[] {
  const groups = new Map<string, BreakRow>()
  for (const period of config.blockedPeriods) {
    const start = toInput(period.startTime)
    const end = toInput(period.endTime)
    const label = period.label ?? ''
    const id = `${start}|${end}|${label}`
    const group = groups.get(id)
    if (group) group.days.push(period.day)
    else groups.set(id, { key: nextKey(), days: [period.day], start, end, label })
  }
  return [...groups.values()]
}

/**
 * Working hours, breaks and planning settings. Shown in the sidebar rather than a modal: the
 * week stays visible beside it, and saving replans it immediately.
 */
export function HoursPanel({ config, welcome, onSave, onClose, busy }: HoursPanelProps) {
  const [days, setDays] = useState<DayRow[]>(() =>
    WEEK.map((day) => {
      const window = config.workingHours.find((candidate) => candidate.day === day)
      return window
        ? { day, working: true, start: toInput(window.startTime), end: toInput(window.endTime) }
        : { day, working: false, start: '09:00', end: '17:00' }
    }),
  )
  const [breaks, setBreaks] = useState<BreakRow[]>(() => groupBreaks(config))
  const [buffer, setBuffer] = useState(String(config.bufferMinutes))
  const [horizon, setHorizon] = useState(String(config.horizonDays))
  const [minChunk, setMinChunk] = useState(String(config.minChunkMinutes))
  const [failure, setFailure] = useState<string | null>(null)

  // The panel opens from a button across the page; focus moves to it so a keyboard user lands
  // where the content appeared.
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
  }, [])

  const valid =
    days.every((row) => !row.working || endsAfterStart(row.start, row.end)) &&
    breaks.every((row) => endsAfterStart(row.start, row.end) && row.days.length > 0)

  function updateDay(day: DayOfWeek, change: Partial<DayRow>) {
    setDays((rows) => rows.map((row) => (row.day === day ? { ...row, ...change } : row)))
  }

  /**
   * A day switched on takes the hours of the first working day, so turning on Saturday for
   * someone who works 07:00-15:00 does not quietly give it 09:00-17:00.
   */
  function setWorking(rows: DayRow[], working: (day: DayOfWeek) => boolean): DayRow[] {
    const model = rows.find((row) => row.working)
    return rows.map((row) => {
      const on = working(row.day)
      if (on && !row.working && model) return { ...row, working: true, start: model.start, end: model.end }
      return { ...row, working: on }
    })
  }

  const workingDays = days.filter((row) => row.working).map((row) => row.day)

  function updateBreak(key: number, change: Partial<BreakRow>) {
    setBreaks((rows) => rows.map((row) => (row.key === key ? { ...row, ...change } : row)))
  }

  function toggleBreakDay(row: BreakRow, day: DayOfWeek) {
    const next = row.days.includes(day) ? row.days.filter((d) => d !== day) : [...row.days, day]
    updateBreak(row.key, { days: WEEK.filter((d) => next.includes(d)) })
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    // Number ranges are left to the browser's validation, which runs first. What it cannot
    // express is "later than that other field", so that is checked here, where the row is known.
    if (!valid) {
      setFailure(t('hours.invalid'))
      return
    }
    try {
      await onSave({
        workingHours: days
          .filter((row) => row.working)
          .map((row) => ({ day: row.day, startTime: row.start, endTime: row.end, label: null })),
        blockedPeriods: breaks.flatMap((row) =>
          row.days.map((day) => ({ day, startTime: row.start, endTime: row.end, label: row.label.trim() || null })),
        ),
        bufferMinutes: Number(buffer),
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
      <div className="hours-head">
        <h2 className="hours-title" id="hours-heading" ref={heading} tabIndex={-1}>
          {welcome ? t('hours.welcomeTitle') : t('hours.title')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{welcome ? t('hours.skip') : t('action.close')}</span>
        </button>
      </div>
      {welcome && <p className="hours-intro">{t('hours.welcomeText')}</p>}

      <fieldset className="hours-section">
        <legend className="section-label">{t('hours.working')}</legend>
        <p className="section-hint">{t('hours.workingHint')}</p>
        <div className="chips">
          {PRESETS.map((preset) => {
            const selected =
              workingDays.length === preset.days.length && preset.days.every((day) => workingDays.includes(day))
            return (
              <button
                key={preset.key}
                type="button"
                className="chip"
                data-selected={selected || undefined}
                aria-pressed={selected}
                onClick={() => setDays((rows) => setWorking(rows, (day) => preset.days.includes(day)))}
              >
                {t(preset.key)}
              </button>
            )
          })}
        </div>
        {days.map((row) => {
          const name = DAY_NAMES[WEEK.indexOf(row.day)]
          const invalid = row.working && !endsAfterStart(row.start, row.end)
          return (
            <div key={row.day} className="hours-day" data-off={!row.working || undefined}>
              <label className="switch">
                <input
                  type="checkbox"
                  checked={row.working}
                  onChange={(event) =>
                    setDays((rows) =>
                      setWorking(rows, (day) => (day === row.day ? event.target.checked : rows.some((r) => r.day === day && r.working))),
                    )
                  }
                />
                <span className="switch-track" aria-hidden="true" />
                <span className="hours-day-name">{name}</span>
              </label>
              {row.working ? (
                <span className="time-range">
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

      <fieldset className="hours-section">
        <legend className="section-label">{t('hours.breaks')}</legend>
        <p className="section-hint">{t('hours.breaksHint')}</p>
        {breaks.map((row) => {
          const invalid = !endsAfterStart(row.start, row.end)
          return (
            <div key={row.key} className="break-row">
              <div className="break-row-line">
                <input
                  className="break-name"
                  value={row.label}
                  maxLength={100}
                  placeholder={t('hours.breakName')}
                  aria-label={t('hours.breakName')}
                  onChange={(event) => updateBreak(row.key, { label: event.target.value })}
                />
                <button
                  type="button"
                  className="icon-button"
                  onClick={() => setBreaks((rows) => rows.filter((candidate) => candidate.key !== row.key))}
                >
                  <TrashIcon />
                  <span className="sr-only">
                    {t('action.remove')} {row.label}
                  </span>
                </button>
              </div>
              <div className="break-row-line">
                <span className="day-toggles" role="group" aria-label={t('hours.breakDays')}>
                  {WEEK.map((day, index) => (
                    <button
                      key={day}
                      type="button"
                      className="day-toggle"
                      aria-pressed={row.days.includes(day)}
                      aria-label={DAY_NAMES[index]}
                      onClick={() => toggleBreakDay(row, day)}
                    >
                      {DAY_NAMES[index].charAt(0)}
                    </button>
                  ))}
                </span>
                <span className="time-range">
                  <input
                    type="time"
                    value={row.start}
                    aria-label={t('hours.from')}
                    aria-invalid={invalid || undefined}
                    onChange={(event) => updateBreak(row.key, { start: event.target.value })}
                  />
                  <span aria-hidden="true">–</span>
                  <input
                    type="time"
                    value={row.end}
                    aria-label={t('hours.until')}
                    aria-invalid={invalid || undefined}
                    onChange={(event) => updateBreak(row.key, { end: event.target.value })}
                  />
                </span>
              </div>
              {invalid && <p className="field-error">{t('hours.endsBeforeStart')}</p>}
              {row.days.length === 0 && <p className="field-error">{t('hours.noDays')}</p>}
            </div>
          )
        })}
        <button
          type="button"
          className="button button-ghost button-small"
          onClick={() =>
            setBreaks((rows) => [...rows, { key: nextKey(), days: WEEKDAYS, start: '12:00', end: '13:00', label: '' }])
          }
        >
          <PlusIcon size={14} />
          {t('hours.addBreak')}
        </button>
      </fieldset>

      <fieldset className="hours-section">
        <legend className="section-label">{t('hours.planning')}</legend>
        <NumberField id="hours-buffer" label={t('hours.buffer')} hint={t('hours.bufferHint')} unit={t('unit.min')} min={0} max={120} value={buffer} onChange={setBuffer} />
        <NumberField id="hours-horizon" label={t('hours.horizon')} unit={t('hours.horizonUnit')} min={1} max={366} value={horizon} onChange={setHorizon} />
        <NumberField id="hours-chunk" label={t('hours.minChunk')} hint={t('hours.minChunkHint')} unit={t('unit.min')} min={1} max={1440} value={minChunk} onChange={setMinChunk} />
      </fieldset>

      {failure && (
        <p className="form-failure" role="alert">
          {failure}
        </p>
      )}

      <div className="hours-actions">
        <button type="button" className="button button-ghost" onClick={onClose}>
          {welcome ? t('hours.skip') : t('action.cancel')}
        </button>
        <button type="submit" className="button button-primary" disabled={busy}>
          {welcome ? t('hours.start') : t('action.save')}
        </button>
      </div>
    </form>
  )
}

interface NumberFieldProps {
  id: string
  label: string
  hint?: string
  unit: string
  min: number
  max: number
  value: string
  onChange: (value: string) => void
}

function NumberField({ id, label, hint, unit, min, max, value, onChange }: NumberFieldProps) {
  return (
    <div className="number-field">
      <label htmlFor={id} className="number-label">
        {label}
        {hint && <span className="number-hint">{hint}</span>}
      </label>
      <span className="field-unit">
        {/* step 1: a browser counts steps from min, so any larger step rejects valid values. */}
        <input id={id} type="number" min={min} max={max} step={1} value={value} required onChange={(event) => onChange(event.target.value)} />
        <span aria-hidden="true">{unit}</span>
      </span>
    </div>
  )
}
