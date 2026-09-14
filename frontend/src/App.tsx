import { useMemo, useState } from 'react'

import type { Block } from './api/types'
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
} from './lib/board'
import { DAY_NAMES, addDays, isoDay, startOfWeek } from './lib/time'
import { MarginRecord } from './week/MarginRecord'
import { NewStripForm } from './week/NewStripForm'
import { StripRail } from './week/StripRail'
import { WeekBoard } from './week/WeekBoard'
import './design/tokens.css'
import './design/board.css'

export default function App() {
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))

  const config = useConfig()
  const schedule = useSchedule(weekStart)
  const tasks = useTasks()
  const events = useEvents()

  const replan = useReplan()
  const setStatus = useSetStatus()
  const setPinned = useSetPinned()
  const deleteTask = useDeleteTask()
  const createTask = useCreateTask()

  const busy =
    replan.isPending || setStatus.isPending || setPinned.isPending || deleteTask.isPending

  const days = boardDays(config.data)
  const window = boardWindow(config.data)
  const blocks = schedule.data ?? []

  const rail = useMemo(() => unrackedWork(tasks.data ?? [], blocks), [tasks.data, blocks])
  const ghosts = useMemo(() => ghostsFrom(events.data, weekStart), [events.data, weekStart])

  const lastDay = addDays(weekStart, days[days.length - 1] - 1)
  const range = `${weekStart.getDate()} – ${lastDay.getDate()} ${lastDay.toLocaleDateString('en', {
    month: 'long',
  })}`

  function togglePin(block: Block) {
    setPinned.mutate({ id: block.id, pinned: !block.pinned })
  }

  function toggleDone(block: Block) {
    setStatus.mutate({ id: block.taskId, status: block.status === 'DONE' ? 'OPEN' : 'DONE' })
  }

  const unreachable = config.isError || schedule.isError || tasks.isError

  return (
    <div className="frame">
      <header className="trim">
        <h1 className="mark">{t('app.name')}</h1>

        <nav className="week-nav" aria-label={t('week.thisWeek')}>
          <button
            type="button"
            className="trim-button"
            onClick={() => setWeekStart(addDays(weekStart, -7))}
          >
            <ChevronIcon direction="left" />
            <span className="sr-only">{t('week.previous')}</span>
          </button>
          <p className="week-range" aria-live="polite">
            {range}
          </p>
          <button
            type="button"
            className="trim-button"
            onClick={() => setWeekStart(addDays(weekStart, 7))}
          >
            <ChevronIcon direction="right" />
            <span className="sr-only">{t('week.next')}</span>
          </button>
          <button
            type="button"
            className="trim-button trim-button-text"
            onClick={() => setWeekStart(startOfWeek(new Date()))}
          >
            {t('week.today')}
          </button>
        </nav>

        <button
          type="button"
          className="lever"
          onClick={() => replan.mutate(undefined)}
          disabled={busy}
        >
          <ReflowIcon />
          {t('action.replan')}
        </button>
      </header>

      {unreachable && (
        <p className="board-error" role="alert">
          {t('error.offline')}
        </p>
      )}

      <div className="frame-body">
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
            busy={busy}
          />
          <MarginRecord events={events.data} />
        </div>

        <div className="frame-rail">
          <StripRail entries={rail} onDelete={(id) => deleteTask.mutate(id)} busy={busy} />
          <NewStripForm onCreate={(input) => createTask.mutateAsync(input)} busy={busy} />
        </div>
      </div>
    </div>
  )
}

export { DAY_NAMES, isoDay }
