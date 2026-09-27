import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ApiError, api } from './api/client'
import type { AuthUser, Block, Task, TaskInput } from './api/types'
import { ChangePasswordScreen } from './auth/ChangePasswordScreen'
import { LoginScreen } from './auth/LoginScreen'
import { AccountPanel } from './auth/AccountPanel'
import { CalendarPanel } from './auth/CalendarPanel'
import { UserManagement } from './auth/UserManagement'
import { MainMenu, type MenuTarget } from './auth/MainMenu'
import { ActivityIcon, ChevronIcon, DayIcon, ListIcon, MonthIcon, PlusIcon } from './design/Icon'
import { LOCALE, t } from './i18n/en'
import {
  boardDays,
  describePersonalHours,
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
  useSetBlockDone,
  useSetPinned,
  useSetStatus,
  useTasks,
  useUpdateConfig,
  useUpdateTask,
} from './lib/board'
import { useBusy } from './lib/calendar'
import { initial, useLogout, useMe } from './lib/auth'
import { PHONE_QUERY, useMediaQuery } from './lib/media'
import { addDays, formatDayTime, isoDay, startOfMonth, startOfWeek, toLocalDateTime } from './lib/time'
import { TaskList } from './list/TaskList'
import { MonthGrid } from './month/MonthGrid'
import { BlockDetails } from './week/BlockDetails'
import { DayStrip } from './week/DayStrip'
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
  | { kind: 'account'; anchor: DOMRect }
  | { kind: 'calendar'; anchor: DOMRect }
  | { kind: 'menu'; anchor: DOMRect }

const POPOVER_HEADING = 'popover-heading'
const NOTICE_MS = 6000
/** Long enough to notice a drag went wrong and reach for Undo. */
const UNDO_MS = 10000

/**
 * The gate in front of the board: no session, a forced password change, or the
 * board itself. Each household member's own copy of the app starts here on every load.
 */
export default function Root() {
  const me = useMe()

  if (me.isLoading) return null
  if (!me.data) return <LoginScreen />
  if (me.data.mustChangePassword) return <ChangePasswordScreen />
  return <Board user={me.data} />
}

function Board({ user }: { user: AuthUser }) {
  const logout = useLogout()
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const phone = useMediaQuery(PHONE_QUERY)
  // 'activity' is the phone's own tab for what the sidebar shows beside the calendar on a wider screen.
  const [chosenView, setView] = useState<'week' | 'month' | 'list' | 'activity'>('week')
  const view = !phone && chosenView === 'activity' ? 'week' : chosenView
  // The day a phone shows of the week, as an ISO weekday.
  const [day, setDay] = useState(() => isoDay(new Date()))
  // A display preference of this browser, per person, so it survives a reload.
  const weekendKey = `reflowtask-show-days-off-${user.id}`
  const [showWeekend, setShowWeekend] = useState(() => {
    try {
      return localStorage.getItem(weekendKey) === 'true'
    } catch {
      return false
    }
  })
  function toggleWeekend() {
    setShowWeekend(!showWeekend)
    try {
      localStorage.setItem(weekendKey, String(!showWeekend))
    } catch {
      /* Not remembered, still applied for this visit. */
    }
  }
  const [open, setOpen] = useState<Open | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  // null follows the server: the hours panel opens by itself until the owner has saved hours once.
  const [hoursChoice, setHoursChoice] = useState<boolean | null>(null)
  const [notice, setNotice] = useState<{
    text: string
    kind: 'error' | 'info' | 'changed'
    /** The opposite action, offered as Undo (and Ctrl+Z) while the notice shows. */
    undo?: () => Promise<unknown>
  } | null>(null)
  const [flashBlockId, setFlashBlockId] = useState<number | null>(null)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

  const config = useConfig()
  const schedule = useSchedule(weekStart)
  const busyTimes = useBusy(weekStart)
  // The month is anchored on the week being looked at, so switching views never jumps elsewhere.
  const monthStart = startOfMonth(addDays(weekStart, 3))
  const monthSchedule = useMonthSchedule(monthStart)
  const tasks = useTasks()
  const events = useEvents()

  const replan = useReplan()
  const setStatus = useSetStatus()
  const setPinned = useSetPinned()
  const setBlockDone = useSetBlockDone()
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
    setBlockDone.isPending ||
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
  const personalSummary = describePersonalHours(config.data)
  const attention = useMemo(() => needsAttention(tasks.data ?? []), [tasks.data])
  const ghosts = useMemo(() => ghostsFrom(events.data, tasks.data), [events.data, tasks.data])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(null), notice.undo ? UNDO_MS : NOTICE_MS)
    return () => window.clearTimeout(timer)
  }, [notice])

  const undo = notice?.undo
  async function runUndo() {
    if (!undo) return
    setNotice(null)
    try {
      await undo()
    } catch (error) {
      fail(error)
    }
  }

  // Ctrl+Z (Cmd+Z) while an Undo is on offer, unless the keys belong to a text field.
  useEffect(() => {
    if (!undo) return
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement
      if (target.closest('input, textarea, select, [contenteditable="true"]')) return
      if ((event.ctrlKey || event.metaKey) && !event.shiftKey && event.key.toLowerCase() === 'z') {
        event.preventDefault()
        void runUndo()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  /*
   * Reflow's whole point - a missed deadline repairs itself - happens silently otherwise: the
   * calendar only shows a small "Missed" mark on the block, easy to never notice. The most
   * recent event is checked once per load and once per poll, and only a NEW one (an id not seen
   * before, remembered per person across reloads) can raise this, so the same miss is announced once.
   *
   * A new event also means the schedule changed. When the hourly job did it, nothing on this page
   * asked for fresh blocks, so the calendar would keep drawing the missed block where it was while
   * the activity feed already says it moved; refetching keeps the two in step.
   */
  const client = useQueryClient()
  const seenEventKey = `reflowtask-seen-event-${user.id}`
  const lastSeenEventId = useRef<number | null>(null)
  useEffect(() => {
    const latest = events.data?.[0]
    if (!latest || latest.id === lastSeenEventId.current) return
    const firstLook = lastSeenEventId.current === null
    lastSeenEventId.current = latest.id
    if (!firstLook) {
      client.invalidateQueries({ queryKey: ['schedule'] })
      client.invalidateQueries({ queryKey: ['tasks'] })
    }
    let announced: string | null = null
    try {
      announced = localStorage.getItem(seenEventKey)
      localStorage.setItem(seenEventKey, String(latest.id))
    } catch {
      /* No storage: announce as before, once per load. */
    }
    if (announced === String(latest.id)) return
    const missed = latest.items.filter((item) => item.kind === 'MISSED')
    if (missed.length === 0) return
    setNotice({
      kind: 'changed',
      text:
        missed.length === 1
          ? `"${missed[0].taskTitle}" ${t('missed.one')}`
          : `${missed.length} ${t('missed.many')}`,
    })
  }, [events.data, client, seenEventKey])

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
    if (seen || !menuButtonRef.current) return
    try {
      localStorage.setItem(seenKey, 'true')
    } catch {
      /* Nothing to persist to; the popover still opens this once. */
    }
    setOpen({ kind: 'help', anchor: menuButtonRef.current.getBoundingClientRect() })
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
      ? monthStart.toLocaleDateString(LOCALE, { month: 'long', year: 'numeric' })
      : weekStart.getMonth() === lastDay.getMonth()
      ? new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'long', year: 'numeric' }).formatRange(weekStart, lastDay)
      : new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' }).formatRange(weekStart, lastDay)

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
      setDay(isoDay(first))
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

  /**
   * Undo of a move is the move back, then unpinning if it was not pinned before. The planner is
   * deterministic, so everything else settles where it was.
   */
  async function move(block: Block, start: Date, end: Date) {
    const before = { start: new Date(block.startAt), end: new Date(block.endAt), pinned: block.pinned }
    try {
      await moveBlock.mutateAsync({ id: block.id, start, end })
      setNotice({
        kind: 'info',
        text: t('undo.moved'),
        undo: async () => {
          await moveBlock.mutateAsync({ id: block.id, start: before.start, end: before.end })
          if (!before.pinned) await setPinned.mutateAsync({ id: block.id, pinned: false })
        },
      })
    } catch (error) {
      fail(error)
      throw error
    }
  }

  /**
   * Marks a task done or open again. Finishing one offers Undo, except for a repeating task, whose
   * next occurrence already exists once this one is done.
   */
  function toggleTaskDone(taskId: number, status: Task['status']) {
    const next = status === 'DONE' ? 'OPEN' : 'DONE'
    const repeating = tasks.data?.find((task) => task.id === taskId)?.recurrence
    setStatus.mutate(
      { id: taskId, status: next },
      {
        onError: fail,
        onSuccess: () => {
          if (next === 'DONE' && !repeating) {
            setNotice({ kind: 'info', text: t('undo.done'), undo: () => setStatus.mutateAsync({ id: taskId, status }) })
          }
        },
      },
    )
  }

  /** Jumps to a date: its week, and on a phone that day of it. */
  function showDate(date: Date) {
    setWeekStart(startOfWeek(date))
    setDay(isoDay(date))
  }

  function openFromMenu(target: MenuTarget) {
    const anchor = open?.kind === 'menu' ? open.anchor : new DOMRect()
    if (target === 'hours') {
      setOpen(null)
      setHoursChoice(true)
      return
    }
    setOpen({ kind: target, anchor })
  }

  function openCreate(anchor: DOMRect) {
    setOpen({ kind: 'create', anchor, slot: null, placement: 'below' })
  }

  const daysOffApplies = !phone && view === 'week' && (showWeekend || boardDays(config.data, blocks, weekStart).length < 7)

  // A phone names what it shows: the open day, the month, or the tab.
  const phoneTitle = hoursOpen
    ? t(welcome ? 'hours.welcomeTitle' : 'hours.title')
    : view === 'week'
      ? addDays(weekStart, day - 1).toLocaleDateString(LOCALE, { weekday: 'short', day: 'numeric', month: 'long' })
      : view === 'month'
        ? range
        : view === 'list'
          ? t('list.title')
          : t('activity.title')

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
    <div className="app" data-phone={phone || undefined}>
      <header className="topbar">
        <h1 className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">{t('app.name')}</span>
        </h1>

        {phone ? (
          <p className="topbar-title" aria-live="polite">
            {phoneTitle}
          </p>
        ) : view === 'list' ? (
          <p className="topbar-title">{t('list.title')}</p>
        ) : (
          <nav className="week-nav" aria-label={t('week.navigation')}>
            <button type="button" className="button button-secondary button-small" onClick={() => showDate(new Date())}>
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
          </nav>
        )}

        <div className="topbar-actions">
          {phone ? (
            (view === 'week' || view === 'month') && (
              <button type="button" className="button button-secondary button-small" onClick={() => showDate(new Date())}>
                {t('week.today')}
              </button>
            )
          ) : (
            <>
              <div className="segmented" role="radiogroup" aria-label={t('view.switch')}>
                {(['week', 'month', 'list'] as const).map((value) => (
                  <label key={value} className="segment" data-selected={view === value || undefined}>
                    <input type="radio" name="view" checked={view === value} onChange={() => setView(value)} />
                    {t(`view.${value}`)}
                  </label>
                ))}
              </div>
              <button
                type="button"
                className="button button-primary"
                aria-label={t('action.newTask')}
                onClick={(event) => openCreate(event.currentTarget.getBoundingClientRect())}
              >
                <PlusIcon />
                <span className="label-roomy" aria-hidden="true">
                  {t('action.newTask')}
                </span>
              </button>
            </>
          )}
          <button
            ref={menuButtonRef}
            type="button"
            className="avatar-button"
            aria-expanded={open?.kind === 'menu'}
            aria-haspopup="dialog"
            onClick={(event) => setOpen({ kind: 'menu', anchor: event.currentTarget.getBoundingClientRect() })}
          >
            <span className="avatar" aria-hidden="true">
              {initial(user.username)}
            </span>
            <span className="sr-only">{t('menu.open')}</span>
          </button>
        </div>
      </header>

      {phone && view === 'week' && !hoursOpen && (
        <DayStrip
          weekStart={weekStart}
          selected={day}
          blocks={blocks}
          onSelect={setDay}
          onStep={(direction) => setWeekStart(addDays(weekStart, direction * 7))}
        />
      )}

      {(unreachable || notice) && (
        <div
          className="notice"
          data-kind={unreachable ? 'error' : notice?.kind}
          role={unreachable || notice?.kind === 'error' ? 'alert' : 'status'}
        >
          {unreachable ? t('error.offline') : notice?.text}
          {!unreachable && undo && (
            <button type="button" className="notice-undo" onClick={runUndo} disabled={busy}>
              {t('undo.action')}
            </button>
          )}
        </div>
      )}

      <div className="app-body">
        {(!phone || hoursOpen || view === 'activity') && (
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
        )}

        {(!phone || (!hoursOpen && view !== 'activity')) && (
        <main className="calendar">
          {view === 'list' ? (
            <TaskList
              tasks={tasks.data ?? []}
              busy={busy}
              onOpen={(task, anchor) => setOpen({ kind: 'edit', anchor, taskId: task.id })}
              onToggleDone={(task) => toggleTaskDone(task.id, task.status)}
              onShow={(task) => {
                if (!task.nextStartAt) return
                showDate(new Date(task.nextStartAt))
                setView('week')
              }}
            />
          ) : view === 'month' ? (
            <MonthGrid
              monthStart={monthStart}
              blocks={monthSchedule.data ?? []}
              onPickDay={(picked) => {
                showDate(picked)
                setView('week')
              }}
            />
          ) : (
          <WeekGrid
            weekStart={weekStart}
            allDays={showWeekend}
            onlyDay={phone ? day : undefined}
            config={config.data}
            blocks={blocks}
            appointments={busyTimes.data ?? []}
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
        )}
      </div>

      {phone && !hoursOpen && (
        <>
          <nav className="tabbar" aria-label={t('view.switch')}>
            {(
              [
                ['week', <DayIcon key="icon" />, t('view.day')],
                ['month', <MonthIcon key="icon" />, t('view.month')],
                ['list', <ListIcon key="icon" />, t('view.list')],
                ['activity', <ActivityIcon key="icon" />, t('activity.title')],
              ] as const
            ).map(([value, icon, label]) => (
              <button
                key={value}
                type="button"
                className="tab"
                aria-current={view === value ? 'page' : undefined}
                onClick={() => setView(value)}
              >
                {icon}
                <span className="tab-label">{label}</span>
                {value === 'activity' && attention.length > 0 && (
                  <span className="count tab-count">{attention.length}</span>
                )}
              </button>
            ))}
          </nav>
          <button
            type="button"
            className="fab"
            onClick={(event) => openCreate(event.currentTarget.getBoundingClientRect())}
          >
            <PlusIcon size={22} />
            <span className="sr-only">{t('action.newTask')}</span>
          </button>
        </>
      )}

      {open?.kind === 'create' && (
        <Popover anchor={open.anchor} placement={open.placement} labelledBy={POPOVER_HEADING} onClose={close}>
          <TaskEditor
            headingId={POPOVER_HEADING}
            task={null}
            slot={open.slot}
            hoursSummary={hoursSummary}
            personalSummary={personalSummary}
            tasks={tasks.data ?? []}
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
            tasks={tasks.data ?? []}
            otherParts={blocks.filter((block) => block.taskId === openBlock.taskId && block.id !== openBlock.id)}
            onToggleDone={() => {
              toggleTaskDone(openBlock.taskId, openBlock.status)
              setOpen(null)
            }}
            onTogglePartDone={() => {
              const id = openBlock.id
              const done = openBlock.state !== 'DONE'
              const repeating = tasks.data?.find((task) => task.id === openBlock.taskId)?.recurrence
              setBlockDone.mutate(
                { id, done },
                {
                  onError: fail,
                  onSuccess: () => {
                    if (done && !repeating) {
                      setNotice({
                        kind: 'info',
                        text: t('undo.partDone'),
                        undo: () => setBlockDone.mutateAsync({ id, done: false }),
                      })
                    }
                  },
                },
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

      {open?.kind === 'menu' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <MainMenu
            user={user}
            daysOff={daysOffApplies ? showWeekend : null}
            busy={busy}
            onOpen={openFromMenu}
            onReplan={() => {
              setOpen(null)
              replan.mutate(undefined, { onError: fail })
            }}
            onToggleDaysOff={toggleWeekend}
            onLogout={() => logout.mutate()}
            headingId={POPOVER_HEADING}
          />
        </Popover>
      )}

      {open?.kind === 'help' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <HowItWorks config={config.data} user={user} onClose={close} headingId={POPOVER_HEADING} />
        </Popover>
      )}

      {open?.kind === 'account' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <AccountPanel user={user} onClose={close} headingId={POPOVER_HEADING} />
        </Popover>
      )}

      {open?.kind === 'calendar' && (
        <Popover anchor={open.anchor} placement="below" labelledBy={POPOVER_HEADING} onClose={close}>
          <CalendarPanel onClose={close} headingId={POPOVER_HEADING} />
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
            personalSummary={personalSummary}
            tasks={tasks.data ?? []}
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

