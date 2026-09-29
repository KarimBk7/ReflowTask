import type { Api } from '../api/client'
import { ApiError } from '../api/errors'
import type { AuthUser, Block, BoardConfig, ConfigWindow, DayOfWeek, Task, TaskInput, TaskStatus } from '../api/types'
import { type BlockRow, type Db, type TaskRow, loadDb, nextId, saveDb } from './db'
import { addBlock, assertCanFix, doneMinutes, replan, seedDemoMiss, workingHoursOf } from './scheduler'
import { type Wall, text, wall, wallNow } from './wall'

/**
 * The API the web app talks to, answered on the device instead of by a server: same methods, same
 * shapes, same rules and error statuses. The app cannot tell the difference, which is what keeps one
 * user interface for both the self-hosted server and the phone app.
 */

/** Without a server job, the plan is rebuilt when it is read and has grown older than this. */
const STALE_MINUTES = 15

const MAX_ESTIMATE = 43_200

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** The device's one person. There is no login: whoever holds the phone owns the plan. */
export const DEVICE_USER: AuthUser = { id: 1, username: 'me', role: 'MEMBER', mustChangePassword: false }

async function read<T>(query: (db: Db, now: Wall) => T): Promise<T> {
  const db = await loadDb()
  const now = wallNow()
  if (!db.lastReplanAt || now - wall(db.lastReplanAt) >= STALE_MINUTES) {
    replan(db, 'SCHEDULED_JOB', now)
    await saveDb(db)
  }
  return query(db, now)
}

/** Runs a change on a copy and keeps it only if it succeeds, so a refused request changes nothing. */
async function change<T>(update: (db: Db, now: Wall) => T): Promise<T> {
  const draft = structuredClone(await loadDb())
  const result = update(draft, wallNow())
  await saveDb(draft)
  return result
}

const notFound = (what: string, id: number) => new ApiError(404, `${what} ${id} not found`)

function requireTask(db: Db, id: number): TaskRow {
  const task = db.tasks.find((candidate) => candidate.id === id)
  if (!task) throw notFound('Task', id)
  return task
}

function requireBlock(db: Db, id: number): BlockRow {
  const block = db.blocks.find((candidate) => candidate.id === id)
  if (!block) throw notFound('Time block', id)
  return block
}

export function taskView(db: Db, task: TaskRow, now: Wall): Task {
  let minutes = 0
  let done = 0
  let atRisk = false
  let next: Wall | null = null
  const deadline = task.deadline ? wall(task.deadline) : null
  for (const block of db.blocks.filter((candidate) => candidate.taskId === task.id)) {
    const start = wall(block.startAt)
    const end = wall(block.endAt)
    if (block.state === 'PLANNED' && end > now && (next === null || start < next)) next = start
    if (block.state === 'MISSED') continue
    minutes += end - start
    if (block.state === 'DONE') done += end - start
    else if (deadline !== null && end > deadline) atRisk = true
  }
  return {
    ...task,
    scheduledMinutes: minutes,
    doneMinutes: done,
    atRisk,
    nextStartAt: next === null ? null : text(next),
  }
}

export function blockView(db: Db, block: BlockRow): Block {
  const task = requireTask(db, block.taskId)
  const deadline = task.deadline ? wall(task.deadline) : null
  return {
    id: block.id,
    taskId: task.id,
    taskTitle: task.title,
    startAt: block.startAt,
    endAt: block.endAt,
    pinned: block.pinned,
    atRisk: block.state === 'PLANNED' && deadline !== null && wall(block.endAt) > deadline,
    priority: task.priority,
    status: task.status,
    state: block.state,
  }
}

const seconds = (clockValue: string) => (clockValue.length === 5 ? `${clockValue}:00` : clockValue)

/** The server's TaskRequest checks, with the same field names so the editor shows them in place. */
function validateTask(input: TaskInput) {
  const errors: Record<string, string> = {}
  if (!input.title || !input.title.trim()) errors.title = 'must not be blank'
  else if (input.title.length > 200) errors.title = 'size must be between 0 and 200'
  if (input.description && input.description.length > 2000) errors.description = 'size must be between 0 and 2000'
  if (!Number.isInteger(input.estimatedMinutes) || input.estimatedMinutes < 1 || input.estimatedMinutes > MAX_ESTIMATE) {
    errors.estimatedMinutes = `must be between 1 and ${MAX_ESTIMATE}`
  }
  if (input.deadlineTime && !input.deadlineDate) errors.deadlineConsistent = 'deadlineTime requires deadlineDate'
  if (input.recurrence && !input.deadlineDate) errors.recurrenceAnchored = 'a repeating task needs a deadline to count from'
  if (input.recurrence && input.fixedStart) {
    errors.recurrenceUnfixed = 'a task fixed at a time cannot repeat; add repeating appointments to a calendar instead'
  }
  const deadline = deadlineOf(input)
  if (input.notBefore && deadline && wall(input.notBefore) >= wall(deadline)) {
    errors.startBeforeDeadline = 'the earliest start must be before the deadline'
  }
  if (Object.keys(errors).length > 0) throw new ApiError(400, 'Validation failed', errors)
}

/** A date without a time means "any time that day", so it is due at the day's last minute. */
function deadlineOf(input: TaskInput): string | null {
  if (!input.deadlineDate) return null
  return `${input.deadlineDate}T${input.deadlineTime ? seconds(input.deadlineTime) : '23:59:00'}`
}

/** The task to wait for must exist, and waiting for it must not come back round to this task. */
function checkedPredecessor(db: Db, taskId: number | null, afterTaskId: number | null | undefined): number | null {
  if (afterTaskId === null || afterTaskId === undefined) return null
  if (!db.tasks.some((task) => task.id === afterTaskId)) throw new ApiError(400, 'The task to wait for does not exist.')
  const seen = new Set<number>()
  for (let step: TaskRow | undefined = requireTask(db, afterTaskId); step && !seen.has(step.id); ) {
    if (step.id === taskId) throw new ApiError(400, 'These tasks would wait for each other.')
    seen.add(step.id)
    const next: number | null = step.afterTaskId
    step = next === null ? undefined : db.tasks.find((task) => task.id === next)
  }
  return afterTaskId
}

function applyInput(db: Db, task: TaskRow, input: TaskInput) {
  task.title = input.title
  task.description = input.description ?? null
  task.estimatedMinutes = input.estimatedMinutes
  task.deadline = deadlineOf(input)
  task.deadlineHasTime = Boolean(task.deadline && input.deadlineTime)
  task.priority = input.priority
  task.recurrence = input.recurrence ?? null
  task.notBefore = input.notBefore ?? null
  task.profile = input.profile ?? 'WORK'
  task.afterTaskId = checkedPredecessor(db, task.id, input.afterTaskId)
}

const BY_DAY_THEN_START = (a: ConfigWindow, b: ConfigWindow) =>
  DAYS.indexOf(a.day) - DAYS.indexOf(b.day) || a.startTime.localeCompare(b.startTime)

export function configView(db: Db): BoardConfig {
  const settings = db.settings
  return {
    workingHours: [...workingHoursOf(db)].sort(BY_DAY_THEN_START),
    personalHours: [...settings.personalHours].sort(BY_DAY_THEN_START),
    blockedPeriods: [...settings.blockedPeriods].sort(BY_DAY_THEN_START),
    horizonDays: settings.horizonDays,
    minChunkMinutes: settings.minChunkMinutes,
    bufferMinutes: settings.bufferMinutes,
    freezeMinutes: settings.freezeMinutes,
    onboarded: settings.onboarded,
  }
}

function normalizedWindows(windows: ConfigWindow[] | undefined, withLabel: boolean): ConfigWindow[] {
  return (windows ?? []).map((window) => ({
    day: window.day,
    startTime: seconds(window.startTime),
    endTime: seconds(window.endTime),
    label: withLabel && window.label?.trim() ? window.label.trim() : null,
  }))
}

/** The server's configuration checks: ranges as guards on the planner, one window per weekday. */
function validateConfig(config: BoardConfig) {
  const inRange = (value: number, min: number, max: number) => Number.isInteger(value) && value >= min && value <= max
  const windows = [...config.workingHours, ...(config.personalHours ?? []), ...config.blockedPeriods]
  const onePerDay = (list: ConfigWindow[]) => new Set(list.map((window) => window.day)).size === list.length
  const valid =
    inRange(config.horizonDays, 1, 366) &&
    inRange(config.minChunkMinutes, 1, 1440) &&
    inRange(config.bufferMinutes, 0, 120) &&
    inRange(config.freezeMinutes ?? 0, 0, 480) &&
    windows.every((window) => seconds(window.endTime) > seconds(window.startTime)) &&
    onePerDay(config.workingHours) &&
    onePerDay(config.personalHours ?? [])
  if (!valid) throw new ApiError(400, 'These settings are not valid.')
}

const unavailable = () => Promise.reject(new ApiError(400, 'Not available in the app.'))

export const deviceApi: Api = {
  listTasks: () =>
    read((db, now) =>
      [...db.tasks]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
        .map((task) => taskView(db, task, now)),
    ),

  createTask: (input) =>
    change((db, now) => {
      validateTask(input)
      if (input.fixedStart) {
        const start = wall(input.fixedStart)
        assertCanFix(db, start, start + input.estimatedMinutes, null, now)
      }
      const task: TaskRow = {
        id: nextId(db),
        title: '',
        description: null,
        estimatedMinutes: 0,
        deadline: null,
        deadlineHasTime: false,
        priority: 'MEDIUM',
        status: 'OPEN',
        createdAt: text(now),
        recurrence: null,
        notBefore: null,
        profile: 'WORK',
        afterTaskId: null,
      }
      applyInput(db, task, input)
      db.tasks.push(task)
      if (input.fixedStart) {
        const start = wall(input.fixedStart)
        addBlock(db, task.id, start, start + task.estimatedMinutes, true)
      }
      replan(db, 'TASK_CHANGED', now)
      return taskView(db, task, now)
    }),

  updateTask: (id, input) =>
    change((db, now) => {
      const task = requireTask(db, id)
      validateTask({ ...input, fixedStart: null })
      applyInput(db, task, input)
      replan(db, 'TASK_CHANGED', now)
      return taskView(db, task, now)
    }),

  changeTaskStatus: (id, status: TaskStatus) =>
    change((db, now) => {
      const task = requireTask(db, id)
      task.status = status
      replan(db, 'TASK_CHANGED', now)
      return taskView(db, task, now)
    }),

  deleteTask: (id) =>
    change((db, now) => {
      requireTask(db, id)
      db.blocks = db.blocks.filter((block) => block.taskId !== id)
      db.tasks = db.tasks.filter((task) => task.id !== id)
      // As the server's ON DELETE SET NULL: deleting the task waited for lifts the wait.
      for (const task of db.tasks) if (task.afterTaskId === id) task.afterTaskId = null
      replan(db, 'TASK_CHANGED', now)
      return undefined
    }),

  schedule: (from, to) =>
    read((db) => {
      const start = wall(from)
      const end = wall(to)
      return db.blocks
        .filter((block) => wall(block.startAt) < end && wall(block.endAt) > start)
        .map((block) => blockView(db, block))
    }),

  replan: () =>
    change((db, now) => {
      const event = replan(db, 'MANUAL', now)
      return event ? [event] : []
    }),

  moveBlock: (id, startAt, endAt) =>
    change((db, now) => {
      const block = requireBlock(db, id)
      const task = requireTask(db, block.taskId)
      const start = wall(startAt)
      const end = wall(endAt)
      if (end <= start) throw new ApiError(400, 'Validation failed', { ordered: 'must end after it starts' })
      if (task.status === 'DONE') throw new ApiError(409, "A completed task's time cannot be moved.")
      if (wall(block.endAt) <= now) throw new ApiError(409, 'Time that has already passed cannot be moved.')
      if (block.state !== 'PLANNED') throw new ApiError(409, 'A part that is done or missed cannot be moved.')
      assertCanFix(db, start, end, block.id, now)
      // Resizing says the work takes longer or shorter; moving alone leaves the estimate.
      const change = end - start - (wall(block.endAt) - wall(block.startAt))
      task.estimatedMinutes = Math.min(Math.max(task.estimatedMinutes + change, 1), MAX_ESTIMATE)
      const previousStart = wall(block.startAt)
      block.startAt = text(start)
      block.endAt = text(end)
      block.pinned = true
      replan(db, 'MANUAL', now, new Map([[task.id, [previousStart]]]))
      return blockView(db, block)
    }),

  pinBlock: (id) =>
    change((db) => {
      const block = requireBlock(db, id)
      block.pinned = true
      return blockView(db, block)
    }),

  unpinBlock: (id) =>
    change((db) => {
      const block = requireBlock(db, id)
      block.pinned = false
      return blockView(db, block)
    }),

  completeBlock: (id) =>
    change((db, now) => {
      const block = requireBlock(db, id)
      if (block.state !== 'DONE') {
        if (wall(block.startAt) > now) {
          throw new ApiError(409, 'A part that has not started yet cannot be marked done. Mark the task done, or shorten it.')
        }
        block.state = 'DONE'
        const task = requireTask(db, block.taskId)
        if (task.status !== 'DONE' && doneMinutes(db, task.id) >= task.estimatedMinutes) task.status = 'DONE'
        replan(db, 'TASK_CHANGED', now)
      }
      return blockView(db, requireBlock(db, id))
    }),

  reopenBlock: (id) =>
    change((db, now) => {
      const block = requireBlock(db, id)
      if (block.state === 'DONE') {
        block.state = 'PLANNED'
        const task = requireTask(db, block.taskId)
        if (task.status === 'DONE' && doneMinutes(db, task.id) < task.estimatedMinutes) task.status = 'OPEN'
        replan(db, 'TASK_CHANGED', now)
      }
      return blockView(db, requireBlock(db, id))
    }),

  rescheduleEvents: () => read((db) => db.events.slice(0, 50)),

  config: () => read((db) => configView(db)),

  updateConfig: (config) =>
    change((db, now) => {
      validateConfig(config)
      const firstTime = !db.settings.onboarded
      db.settings = {
        workingHours: normalizedWindows(config.workingHours, false),
        personalHours: normalizedWindows(config.personalHours, false),
        blockedPeriods: normalizedWindows(config.blockedPeriods, true),
        horizonDays: config.horizonDays,
        minChunkMinutes: config.minChunkMinutes,
        bufferMinutes: config.bufferMinutes,
        freezeMinutes: config.freezeMinutes ?? 0,
        onboarded: true,
      }
      if (firstTime) seedDemoMiss(db, now)
      replan(db, 'CONFIG_CHANGED', now)
      return configView(db)
    }),

  me: () => Promise.resolve(DEVICE_USER),
  logout: () => Promise.resolve(undefined),
  login: unavailable,
  changePassword: unavailable,
  listUsers: () => Promise.resolve([DEVICE_USER]),
  createUser: unavailable,
  resetPassword: unavailable,
  deleteUser: unavailable,

  calendarFeed: () => Promise.resolve({ path: null }),
  renewCalendarFeed: unavailable,
  disableCalendarFeed: unavailable,
  calendarSources: () => Promise.resolve([]),
  addCalendarSource: unavailable,
  removeCalendarSource: unavailable,
  refreshCalendarSources: () => Promise.resolve([]),
  busy: () => Promise.resolve([]),
}
