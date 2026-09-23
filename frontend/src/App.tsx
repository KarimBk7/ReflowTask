import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ApiError, api } from './api/client'
import type { AuthUser, Block, Task, TaskInput } from './api/types'
import { BootstrapScreen } from './auth/BootstrapScreen'
import { ChangePasswordScreen } from './auth/ChangePasswordScreen'
import { LoginScreen } from './auth/LoginScreen'
import { UserManagement } from './auth/UserManagement'
import { ChevronIcon, ClockIcon, HelpIcon, LogoutIcon, PlusIcon, ReflowIcon, UsersIcon } from './design/Icon'
import { t } from './i18n/en'
import {
  describeWorkingHours,
  ghostsFrom,
  originFor,
  needsAttention,
  useConfig,
  useCreateTask,
  useDeleteTask,
  useEvents,
  useMonthSchedule,
  useMoveBlock,
  useReplan,
  useSchedule,
  useSetPinned,
  useSetStatus,
  useTasks,
  useUpdateConfig,
  useUpdateTask,
} from './lib/board'
import { useAuthStatus, useLogout, useMe } from './lib/auth'
import { addDays, formatDayTime, startOfMonth, startOfWeek, toLocalDateTime } from './lib/time'
import { MonthGrid } from './month/MonthGrid'
import { BlockDetails } from './week/BlockDetails'
import { HoursPanel } from './week/HoursPanel'
import { HowItWorks } from './week/HowItWorks'
import { Popover } from './week/Popover'
import { Activity, NeedsAttention } from './week/Sidebar'
import { TaskEditor } from './week/TaskEditor'
import { type Draft, WeekGrid } from './week/WeekGrid'
import './design/tokens.css'
import './design/app.css'

type Open =
  | { kind: 'create'; anchor: DOMRect; slot: Date | null; placement: 'side' | 'below' }
  | { kind: 'block'; anchor: DOMRect; blockId: number }
  | { kind: 'edit'; anchor: DOMRect; taskId: number }
  | { kind: 'help'; anchor: DOMRect }
  | { kind: 'users'; anchor: DOMRect }

const POPOVER_HEADING = 'popover-heading'
const NOTICE_MS = 6000

/**
 * The gate in front of the board: no account yet, no session, a forced password change, or the
 * board itself. Each household member's own copy of the app starts here on every load.
 */
export default function Root() {
  const status = useAuthStatus()
  const me = useMe()

  if (status.isLoading || (status.data && !status.data.needsBootstrap && me.isLoading)) return null
  if (status.data?.needsBootstrap) return <BootstrapScreen />
  if (!me.data) return <LoginScreen />
  if (me.data.mustChangePassword) return <ChangePasswordScreen />
  return <Board user={me.data} />
}

function Board({ user }: { user: AuthUser }) {
  const logout = useLogout()
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const [view, setView] = useState<'week' | 'month'>('week')
  const [open, setOpen] = useState<Open | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  // null follows the server: the hours panel opens by itself until the owner has saved hours once.
  const [hoursChoice, setHoursChoice] = useState<boolean | null>(null)
  const [notice, setNotice] = useState<{ text: string; kind: 'error' | 'info' | 'changed' } | null>(null)
  const [flashBlockId, setFlashBlockId] = useState<number | null>(null)
  const helpButtonRef = useRef<HTMLButtonElement>(null)

  const config = useConfig()
  const schedule = useSchedule(weekStart)
  // The month is anchored on the week being looked at, so switching views never jumps elsewhere.
  const monthStart = startOfMonth(addDays(weekStart, 3))
  const monthSchedule = useMonthSchedule(monthStart)
  const tasks = useTasks()
  const events = useEvents()

  const replan = useReplan()
  const setStatus = useSetStatus()
  const setPinned = useSetPinned()
  const moveBlock = useMoveBlock()
  const deleteTask = useDeleteTask()
  const createTask = useCreateTask()
  const updateTask = useUpdateTask()
  const updateConfig = useUpdateConfig()

  // Every mutation counts: without create and update here, a quick second click sends the task twice.
  const busy =
    replan.isPending ||
    setStatus.isPending ||
    setPinned.isPending ||
    moveBlock.isPending ||
    deleteTask.isPending ||
    createTask.isPending ||
    updateTask.isPending ||
    updateConfig.isPending

  const welcome = config.data?.onboarded === false
  const hoursOpen = hoursChoice ?? welcome

  const blocks = schedule.data ?? []
  const taskMinutes = useMemo(
    () => new Map((tasks.data ?? []).map((task) => [task.id, task.estimatedMinutes])),
    [tasks.data],
  )
  const hoursSummary = describeWorkingHours(config.data)
  const attention = useMemo(() => needsAttention(tasks.data ?? []), [tasks.data])
  const ghosts = useMemo(() => ghostsFrom(events.data), [events.data])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS)
    return () => window.clearTimeout(timer)
  }, [notice])

  /*
   * Reflow's whole point - a missed deadline repairs itself - happens silently otherwise: the
   * calendar only shows a small "Missed" mark on the block, easy to never notice. The most
   * recent event is checked once per load and once per poll, and only a NEW one (an id not seen
   * before) can raise this, so it never re-announces the same miss on every re-render.
   */
  const lastAnnouncedEventId = useRef<number | null>(null)
  useEffect(() => {
    const latest = events.data?.[0]
    if (!latest || latest.id === lastAnnouncedEventId.current) return
    lastAnnouncedEventId.current = latest.id
    const missed = latest.items.filter((item) => item.kind === 'MISSED')
    if (missed.length === 0) return
    setNotice({
      kind: 'changed',
      text:
        missed.length === 1
          ? `"${missed[0].taskTitle}" ${t('missed.one')}`
          : `${missed.length} ${t('missed.many')}`,
    })
  }, [events.data])

  /** Opens the help popover once, ever, the first time the owner has something to look at. */
  useEffect(() => {
    if (welcome || hoursOpen || !config.data || open) return
    const seenKey = `reflowtask-seen-help-${user.id}`
    let seen = true
    try {
      seen = localStorage.getItem(seenKey) === 'true'
    } catch {
      /* Private browsing or a blocked store: treat as already seen rather than nag every load. */
    }
    if (seen || !helpButtonRef.current) return
    try {
      localStorage.setItem(seenKey, 'true')
    } catch {
      /* Nothing to persist to; the popover still opens this once. */
    }
    setOpen({ kind: 'help', anchor: helpButtonRef.current.getBoundingClientRect() })
  }, [welcome, hoursOpen, config.data, open, user.id])

  /*
   * A press outside an open popover closes it, and that same press must not also open a new task
   * at the spot it landed on: dismissing is the whole intent. The flag lives for one click.
   */
  const dismissing = useRef(false)
  const close = useCallback(() => {
    setOpen(null)
    setDraft(null)
    dismissing.current = true
    window.addEventListener('click', () => window.setTimeout(() => (dismissing.current = false), 0), {
      once: true,
      capture: true,
    })
    // A keyboard close produces no click, so the flag must not wait for one.
    window.setTimeout(() => (dismissing.current = false), 400)
  }, [])

  function fail(error: unknown) {
    setNotice({ kind: 'error', text: error instanceof ApiError ? error.message : t('error.offline') })
  }

  const lastDay = addDays(weekStart, 6)
  const range =
    view === 'month'
      ? monthStart.toLocaleDateString('en', { month: 'long', year: 'numeric' })
      : weekStart.getMonth() === lastDay.getMonth()
      ? `${weekStart.getDate()} – ${lastDay.getDate()} ${lastDay.toLocaleDateString('en', { month: 'long', year: 'numeric' })}`
      : `${weekStart.toLocaleDateString('en', { day: 'numeric', month: 'short' })} – ${lastDay.toLocaleDateString('en', { day: 'numeric', month: 'short', year: 'numeric' })}`

  // A replan recreates blocks, so the one a popover was showing can vanish; its popover then simply
  // does not render.
  const openBlock = open?.kind === 'block' ? blocks.find((block) => block.id === open.blockId) : undefined
  const openTask =
    open?.kind === 'edit' ? tasks.data?.find((task) => task.id === open.taskId) : undefined

  async function create(input: TaskInput) {
    const task = await createTask.mutateAsync(input)
    setOpen(null)
    setDraft(null)
    if (!input.fixedStart) await revealPlacement(task)
  }

  /**
   * Work handed to the scheduler can land out of sight, in a later week or a later hour. Say where it
   * went, go to that week and pulse the block, so placing a task never looks like nothing happened.
   */
  async function revealPlacement(task: Task) {
    try {
      const from = new Date()
      const horizon = (config.data?.horizonDays ?? 14) + 1
      const parts = (await api.schedule(toLocalDateTime(from), toLocalDateTime(addDays(from, horizon))))
        .filter((block) => block.taskId === task.id)
        .sort((a, b) => a.startAt.localeCompare(b.startAt))
      if (parts.length === 0) {
        setNotice({ kind: 'info', text: t('placed.none') })
        return
      }
      const first = new Date(parts[0].startAt)
      setWeekStart(startOfWeek(first))
      setFlashBlockId(parts[0].id)
      window.setTimeout(() => setFlashBlockId(null), 2400)
      setNotice({
        kind: 'info',
        text:
          parts.length === 1
            ? `${t('placed.single')} ${formatDayTime(first)}.`
            : `${t('placed.split')} ${parts.length} ${t('placed.partsFirst')} ${formatDayTime(first)}.`,
      })
    } catch (error) {
      fail(error)
    }
  }

  async function save(taskId: number, input: TaskInput) {
    await updateTask.mutateAsync({ id: taskId, input })
    setOpen(null)
  }

  async function remove(taskId: number) {
    try {
      await deleteTask.mutateAsync(taskId)
      setOpen(null)
    } catch (error) {
      fail(error)
    }
  }

  async function move(block: Block, start: Date, end: Date) {
    try {
      await moveBlock.mutateAsync({ id: block.id, start, end })
    } catch (error) {
      fail(error)
      throw error
    }
  }

  /** Month view moves whole months; week view moves a week. */
  function step(direction: -1 | 1) {
    if (view === 'week') {
      setWeekStart(addDays(weekStart, direction * 7))
      return
    }
    // The view derives its month from the week's Thursday, so land on a week whose Thursday is in the target month.
    const first = new Date(monthStart.getFullYear(), monthStart.getMonth() + direction, 1)
    setWeekStart(startOfWeek(addDays(first, 3)))
  }

  const unreachable = config.isError || schedule.isError || tasks.isError

  return (
    <div className="app">
      <header className="topbar">
        <h1 className="brand">
          <span className="brand-mark" aria-hidden="true" />
          {t('app.name')}
        </h1>

        <nav className="week-nav" aria-label={t('week.navigation')}>
          <button type="button" className="button button-secondary button-small" onClick={() => setWeekStart(startOfWeek(new Date()))}>
            {t('week.today')}
          </button>
          <button type="button" className="icon-button" onClick={() => step(-1)}>
            <ChevronIcon direction="left" />
            <span className="sr-only">{view === 'month' ? t('month.previous') : t('week.previous')}</span>
          </button>
          <button type="button" className="icon-button" onClick={() => step(1)}>
            <ChevronIcon direction="right" />
            <span className="sr-only">{view === 'month' ? t('month.next') : t('week.next')}</span>
          </button>
          <p className="week-range" aria-live="polite">
            {range}
          </p>
          <div className="segmented" role="radiogroup" aria-label={t('view.switch')}>
            {(['week', 'month'] as const).map((value) => (
              <label key={value} className="segment" data-selected={view === value || undefined}>
                <input type="radio" name="view" checked={view === value} onChange={() => setView(value)} />
                {t(`view.${value}`)}
              </label>
            ))}
          </div>
        </nav>

        <div className="topbar-actions">
          <button
            type="button"
            className="button button-ghost"
            onClick={() => replan.mutate(undefined, { onError: fail })}
            disabled={busy}
          >
            <ReflowIcon />
            <span className="label-wide">{t('action.replan')}</span>
          </button>
          <button
            ref={helpButtonRef}
            type="button"
            className="icon-button"
            onClick={(event) => setOpen({ kind: 'help', anchor: event.currentTarget.getBoundingClientRect() })}
          >
            <HelpIcon />
            <span className="sr-only">{t('action.help')}</span>
          </button>
          <button
            type="button"
            className="button button-ghost"
            aria-pressed={hoursOpen}
            onClick={() => setHoursChoice(!hoursOpen)}
          >
            <ClockIcon />
            <span className="label-wide">{t('hours.open')}</span>
          </button>
          {user.role === 'ADMIN' && (
            <button
              type="button"
              className="icon-button"
              onClick={(event) => setOpen({ kind: 'users', anchor: event.currentTarget.getBoundingClientRect() })}
            >
              <UsersIcon />
              <span className="sr-only">{t('auth.usersTitle')}</span>
            </button>
          )}
          <button type="button" className="icon-button" onClick={() => logout.mutate()}>
            <LogoutIcon />
            <span className="sr-only">{t('auth.logout')}</span>
          </button>
          <button
            type="button"
            className="button button-primary"
            onClick={(event) =>
              setOpen({ kind: 'create', anchor: event.currentTarget.getBoundingClientRect(), slot: null, placement: 'below' })
            }
          >
            <PlusIcon />
            {t('action.newTask')}
          </button>
        </div>
      </header>

      {(unreachable || notice) && (
        <p
          className="notice"
          data-kind={unreachable ? 'error' : notice?.kind}
          role={unreachable || notice?.kind === 'error' ? 'alert' : 'status'}
        >
          {unreachable ? t('error.offline') : notice?.text}
        </p>
      )}

      <div className="app-body">
        <aside className="sidebar" data-wide={hoursOpen || undefined}>
          {hoursOpen && config.data ? (
            <HoursPanel
              config={config.data}
              welcome={welcome}
              onSave={(next) => updateConfig.mutateAsync(next)}
              onClose={() => setHoursChoice(false)}
              busy={busy}
            />
          ) : (
            <>
              <NeedsAttention
                tasks={attention}
                onOpen={(task, anchor) => setOpen({ kind: 'edit', anchor, taskId: task.id })}
              />
              <Activity events={events.data} />
            </>
          )}
        </aside>

        <main className="calendar">
          {view === 'month' ? (
            <MonthGrid
              monthStart={monthStart}
              blocks={monthSchedule.data ?? []}
              onPickDay={(day) => {
                setWeekStart(startOfWeek(day))
                setView('week')
              }}
            />
          ) : (
          <WeekGrid
            weekStart={weekStart}
            config={config.data}
            blocks={blocks}
            ghosts={ghosts}
            draft={draft}
            selectedBlockId={open?.kind === 'block' ? open.blockId : null}
            taskMinutes={taskMinutes}
            flashBlockId={flashBlockId}
            busy={busy}
            onOpenBlock={(block, anchor) => setOpen({ kind: 'block', anchor, blockId: block.id })}
            onCreateAt={(slot, anchor) => {
              if (dismissing.current) return
              setOpen({ kind: 'create', anchor, slot, placement: 'side' })
              // The editor starts fixed at the slot unless that time has passed, and the outline follows.
              setDraft(slot.getTime() >= Date.now() ? { start: slot, minutes: 60 } : null)
            }}
            onMove={move}
          />
          )}
        </main>
      </div>

      {open?.kind === 'create' && (
        <Popover anchor={open.anchor} placement={open.placement} labelledBy={POPOVER_HEADING} onClose={close}>
          <TaskEditor
            headingId={POPOVER_HEADING}
            task={null}
            slot={open.slot}
            hoursSummary={hoursSummary}
            onSubmit={create}
            onCancel={close}
            onDraftChange={(minutes, fixed) =>
              setDraft(open.slot && fixed ? { start: open.slot, minutes: minutes || 15 } : null)
            }
            busy={busy}
          />
        </Popover>
      )}

      {open?.kind === 'block' && openBlock && (
        <Popover anchor={open.anchor} labelledBy={POPOVER_HEADING} onClose={close}>
          <BlockDetails
            headingId={POPOVER_HEADING}
            block={openBlock}
            task={tasks.data?.find((task) => task.id === openBlock.taskId)}
            origin={originFor(openBlock, ghosts)}
            otherParts={blocks.filter((block) => block.taskId === openBlock.taskId && block.id !== openBlock.id)}
            onToggleDone={() => {
              setStatus.mutate(
                { id: openBlock.taskId, status: openBlock.status === 'DONE' ? 'OPEN' : 'DONE' },
                { onError: fail },
              )
              setOpen(null)
            }}
            onTogglePin={() => setPinned.mutate({ id: openBlock.id, pinned: !openBlock.pinned }, { onError: fail })}
            onEdit={() => setOpen({ kind: 'edit', anchor: open.anchor, taskId: openBlock.taskId })}
            onClose={close}
            busy={busy}
          />
        </Popover>
      )}

      {open?.kind === 'help' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <HowItWorks config={config.data} user={user} onClose={close} headingId={POPOVER_HEADING} />
        </Popover>
      )}

      {open?.kind === 'users' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <UserManagement currentUser={user} onClose={close} headingId={POPOVER_HEADING} />
        </Popover>
      )}

      {open?.kind === 'edit' && openTask && (
        <Popover anchor={open.anchor} labelledBy={POPOVER_HEADING} onClose={close}>
          <TaskEditor
            key={openTask.id}
            headingId={POPOVER_HEADING}
            task={openTask}
            slot={null}
            hoursSummary={hoursSummary}
            onSubmit={(input) => save(openTask.id, input)}
            onCancel={close}
            onDelete={() => remove(openTask.id)}
            busy={busy}
          />
        </Popover>
      )}
    </div>
  )
}

