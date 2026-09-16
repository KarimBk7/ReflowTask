import { useMemo, useState } from 'react'

import type { Block, Task } from './api/types'
import { ChevronIcon, ReflowIcon } from './design/Icon'
import { t } from './i18n/en'
import {
  boardDays,
  boardWindow,
  ghostsFrom,
  unrackedWork,
  useConfig,
  useCreateTask,
  useDeleteTask,
  useEvents,
  useReplan,
  useSchedule,
  useSetPinned,
  useSetStatus,
  useTasks,
  useUpdateConfig,
  useUpdateTask,
} from './lib/board'
import { DAY_NAMES, addDays, isoDay, startOfWeek } from './lib/time'
import { HoursForm } from './week/HoursForm'
import { MarginRecord } from './week/MarginRecord'
import { StripForm } from './week/StripForm'
import { StripRail } from './week/StripRail'
import { WeekBoard } from './week/WeekBoard'
import './design/tokens.css'
import './design/board.css'

export default function App() {
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  // The task open in the rail form, or null while the form writes a new one.
  const [editing, setEditing] = useState<Task | null>(null)
  // The rail shows either the strips and their form, or the working-hours editor in their place.
  const [hoursOpen, setHoursOpen] = useState(false)

  const config = useConfig()
  const schedule = useSchedule(weekStart)
  const tasks = useTasks()
  const events = useEvents()

  const replan = useReplan()
  const setStatus = useSetStatus()
  const setPinned = useSetPinned()
  const deleteTask = useDeleteTask()
  const createTask = useCreateTask()
  const updateTask = useUpdateTask()
  const updateConfig = useUpdateConfig()

  // Every mutation counts, including create and update: without them a quick second click
  // on Save sends the task twice and the board ends up with a duplicate.
  const busy =
    replan.isPending ||
    setStatus.isPending ||
    setPinned.isPending ||
    deleteTask.isPending ||
    createTask.isPending ||
    updateTask.isPending ||
    updateConfig.isPending

  const days = boardDays(config.data)
  const window = boardWindow(config.data)
  const blocks = schedule.data ?? []

  const rail = useMemo(() => unrackedWork(tasks.data ?? []), [tasks.data])
  const ghosts = useMemo(() => ghostsFrom(events.data, weekStart), [events.data, weekStart])

  const lastDay = addDays(weekStart, days[days.length - 1] - 1)
  const range = `${weekStart.getDate()} – ${lastDay.getDate()} ${lastDay.toLocaleDateString('en', {
    month: 'long',
  })}`

  function togglePin(block: Block) {
    setPinned.mutate({ id: block.id, pinned: !block.pinned })
  }

  /**
   * A block only carries its task id and title, so the full task is looked up before the
   * form opens. Editing always starts from the server's copy, never from what a strip shows.
   */
  function editBlock(block: Block) {
    const task = tasks.data?.find((candidate) => candidate.id === block.taskId)
    if (!task) return
    // The task form lives in the same rail as the hours editor; without closing it, the edit
    // would open behind the editor where nobody can see it.
    setHoursOpen(false)
    setEditing(task)
  }

  async function saveTask(input: Parameters<typeof createTask.mutateAsync>[0]) {
    if (editing) {
      await updateTask.mutateAsync({ id: editing.id, input })
      setEditing(null)
    } else {
      await createTask.mutateAsync(input)
    }
  }

  function toggleDone(block: Block) {
    setStatus.mutate({ id: block.taskId, status: block.status === 'DONE' ? 'OPEN' : 'DONE' })
  }

  const unreachable = config.isError || schedule.isError || tasks.isError

  return (
    <div className="frame">
      <header className="trim">
        <h1 className="mark">{t('app.name')}</h1>

        {/* The week's dates are fired into the extrusion itself; only the movement
            controls are fittings on it. */}
        <p className="week-range" aria-live="polite">
          {range}
        </p>

        <nav className="week-nav" aria-label={t('week.thisWeek')}>
          <button
            type="button"
            className="trim-button"
            onClick={() => setWeekStart(addDays(weekStart, -7))}
          >
            <ChevronIcon direction="left" />
            <span className="sr-only">{t('week.previous')}</span>
          </button>
          <button
            type="button"
            className="trim-button trim-button-text"
            onClick={() => setWeekStart(startOfWeek(new Date()))}
          >
            {t('week.today')}
          </button>
          <button
            type="button"
            className="trim-button"
            onClick={() => setWeekStart(addDays(weekStart, 7))}
          >
            <ChevronIcon direction="right" />
            <span className="sr-only">{t('week.next')}</span>
          </button>
        </nav>

        <button
          type="button"
          className="trim-button trim-button-text"
          aria-pressed={hoursOpen}
          onClick={() => {
            // Opening the hours editor abandons any task edit in progress in the same rail.
            setEditing(null)
            setHoursOpen((open) => !open)
          }}
        >
          {t('hours.open')}
        </button>

        {/* Bolted to the right end of the extrusion, through its two fixings. */}
        <div className="lever-plate">
          <span className="fixing" aria-hidden="true" />
          <button
            type="button"
            className="lever"
            onClick={() => replan.mutate(undefined)}
            disabled={busy}
          >
            <ReflowIcon />
            {t('action.replan')}
          </button>
          <span className="fixing" aria-hidden="true" />
        </div>
      </header>

      {unreachable && (
        <p className="board-error" role="alert">
          {t('error.offline')}
        </p>
      )}

      <div className="frame-body">
        {/* The rail runs down the left of the board: unracked strips sit beside the board
            they have not been seated into. */}
        <div className="frame-rail">
          {hoursOpen && config.data ? (
            <HoursForm
              config={config.data}
              onSave={(next) => updateConfig.mutateAsync(next)}
              onClose={() => setHoursOpen(false)}
              busy={busy}
            />
          ) : (
            <>
              <StripRail
                entries={rail}
                onDelete={(id) => {
                  // Deleting the task being edited would leave the form saving into nothing.
                  if (editing?.id === id) setEditing(null)
                  deleteTask.mutate(id)
                }}
                onEdit={setEditing}
                busy={busy}
              />
              {/* Keyed by task so switching which task is edited remounts the fields from it. */}
              <StripForm
                key={editing?.id ?? 'new'}
                editing={editing}
                onSave={saveTask}
                onCancel={() => setEditing(null)}
                busy={busy}
              />
            </>
          )}
        </div>

        <div className="frame-board">
          <WeekBoard
            weekStart={weekStart}
            days={days}
            windowStart={window.start}
            windowEnd={window.end}
            config={config.data}
            blocks={blocks}
            ghosts={ghosts}
            onTogglePin={togglePin}
            onToggleDone={toggleDone}
            onEdit={editBlock}
            busy={busy}
          />
          <MarginRecord events={events.data} />
        </div>
      </div>
    </div>
  )
}

export { DAY_NAMES, isoDay }
