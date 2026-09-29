import type { ConfigWindow, DayOfWeek, RescheduleItem, RescheduleItemKind, RescheduleTrigger, TimeProfile } from '../api/types'
import { ApiError } from '../api/errors'
import { type BlockRow, type Db, type EventRow, type TaskRow, nextId } from './db'
import { type DailyWindow, type PlanConfig, type PlanTask, type Slot, plan } from './planner'
import { type Wall, clock, isoDay, plusMonths, text, wall } from './wall'

/**
 * Turns the planner's decisions into blocks and records what changed: the device's copy of the
 * server's SchedulerService, with the same rules about which blocks survive a replan.
 *
 * - Blocks of completed tasks that have started are history; future ones are removed.
 * - A block in flight is left alone. A planned block whose time passed unfinished is a miss: kept
 *   as MISSED, and the task is replanned.
 * - Parts marked done count against the estimate. Pinned blocks and the freeze window are obstacles.
 * - Everything else in the future is rebuilt.
 */

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** Monday to Friday, 09:00 to 18:00: what someone plans against before saving hours of their own. */
export const DEFAULT_HOURS: ConfigWindow[] = DAYS.slice(0, 5).map((day) => ({
  day,
  startTime: '09:00:00',
  endTime: '18:00:00',
  label: null,
}))

export function workingHoursOf(db: Db): ConfigWindow[] {
  const settings = db.settings
  return !settings.onboarded && settings.workingHours.length === 0 ? DEFAULT_HOURS : settings.workingHours
}

const toDaily = (window: ConfigWindow): DailyWindow => ({
  day: DAYS.indexOf(window.day) + 1,
  start: clock(window.startTime),
  end: clock(window.endTime),
})

export function planConfig(db: Db): PlanConfig {
  return {
    workingHours: workingHoursOf(db).map(toDaily),
    personalHours: db.settings.personalHours.map(toDaily),
    blockedPeriods: db.settings.blockedPeriods.map(toDaily),
    horizonDays: db.settings.horizonDays,
    minChunkMinutes: db.settings.minChunkMinutes,
    bufferMinutes: db.settings.bufferMinutes,
  }
}

const minutesOf = (block: BlockRow) => wall(block.endAt) - wall(block.startAt)
const hasEndedBy = (block: BlockRow, now: Wall) => wall(block.endAt) <= now

/** Refuses to fix work at a time the schedule cannot give it. */
export function assertCanFix(db: Db, start: Wall, end: Wall, ignoreBlockId: number | null, now: Wall) {
  if (end <= now) throw new ApiError(409, 'Work cannot be fixed at a time that has already passed.')
  const clash = db.blocks.some(
    (block) => block.pinned && block.id !== ignoreBlockId && wall(block.startAt) < end && wall(block.endAt) > start,
  )
  if (clash) throw new ApiError(409, 'That time overlaps something that is already fixed.')
}

export function addBlock(db: Db, taskId: number, start: Wall, end: Wall, pinned: boolean): BlockRow {
  const block: BlockRow = { id: nextId(db), taskId, startAt: text(start), endAt: text(end), pinned, state: 'PLANNED' }
  db.blocks.push(block)
  return block
}

/**
 * One example task with a block that has already ended, so the first replan after setup shows the
 * reflow itself: a missed mark and the work placed again.
 */
export function seedDemoMiss(db: Db, now: Wall) {
  const demo: TaskRow = {
    id: nextId(db),
    title: 'See how this works: I was missed',
    description: null,
    estimatedMinutes: 30,
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
  db.tasks.push(demo)
  addBlock(db, demo.id, now - 45, now - 15, false)
}

export function doneMinutes(db: Db, taskId: number): number {
  return db.blocks
    .filter((block) => block.taskId === taskId && block.state === 'DONE')
    .reduce((sum, block) => sum + minutesOf(block), 0)
}

function nextStep(rule: TaskRow['recurrence'], from: Wall): Wall {
  switch (rule) {
    case 'DAILY':
      return from + 1440
    case 'WEEKLY':
      return from + 7 * 1440
    case 'BIWEEKLY':
      return from + 14 * 1440
    default:
      return plusMonths(from, 1)
  }
}

/**
 * The next occurrence of every finished repeating task, due one step after the last. Steps already
 * over are skipped, not owed; a daily task also skips days without hours for its profile.
 */
function continueSeries(db: Db, config: PlanConfig, now: Wall) {
  for (const finished of db.tasks.filter((task) => task.status === 'DONE' && task.recurrence)) {
    const rule = finished.recurrence
    const hours = finished.profile === 'PERSONAL' && config.personalHours.length > 0 ? config.personalHours : config.workingHours
    const dayOff = (date: Wall) => hours.length > 0 && !hours.some((window) => window.day === isoDay(date))
    const previousDeadline = wall(finished.deadline!)
    let deadline = nextStep(rule, previousDeadline)
    while (deadline <= now || (rule === 'DAILY' && dayOff(deadline))) deadline = nextStep(rule, deadline)
    // The next one starts where the period before its deadline ended.
    let notBefore = previousDeadline
    for (let step = nextStep(rule, previousDeadline); step < deadline; step = nextStep(rule, step)) notBefore = step
    db.tasks.push({
      ...finished,
      id: nextId(db),
      status: 'OPEN',
      deadline: text(deadline),
      notBefore: text(notBefore),
      createdAt: text(now),
    })
    finished.recurrence = null
  }
}

interface Disposition {
  obstacles: BlockRow[]
  doneHistory: BlockRow[]
  toRemove: BlockRow[]
  stillPlanned: BlockRow[]
  missedStarts: Map<number, Wall>
  completed: Set<number>
  titles: Map<number, string>
}

function disposeOf(db: Db, now: Wall, freezeUntil: Wall): Disposition {
  const tasks = new Map(db.tasks.map((task) => [task.id, task]))
  const result: Disposition = {
    obstacles: [],
    doneHistory: [],
    toRemove: [],
    stillPlanned: [],
    missedStarts: new Map(),
    completed: new Set(),
    titles: new Map(),
  }
  const frozen: BlockRow[] = []
  for (const block of db.blocks) {
    const task = tasks.get(block.taskId)
    if (!task) continue
    result.titles.set(task.id, task.title)
    const ended = hasEndedBy(block, now)
    if (task.status === 'DONE') result.completed.add(task.id)
    if (block.state === 'MISSED') continue
    if (block.state === 'DONE') {
      ;(ended ? result.doneHistory : result.obstacles).push(block)
      continue
    }
    const entirelyFuture = wall(block.startAt) > now
    if (!ended) result.stillPlanned.push(block)
    if (task.status === 'DONE') {
      if (entirelyFuture) result.toRemove.push(block)
      else if (!ended) result.obstacles.push(block)
      continue
    }
    if (ended) {
      const start = wall(block.startAt)
      const earliest = result.missedStarts.get(task.id)
      result.missedStarts.set(task.id, earliest === undefined ? start : Math.min(earliest, start))
      block.state = 'MISSED'
    } else if (!entirelyFuture || block.pinned) {
      result.obstacles.push(block)
    } else if (wall(block.startAt) < freezeUntil) {
      frozen.push(block)
    } else {
      result.toRemove.push(block)
    }
  }
  // A frozen block keeps its place only while its task still needs all of that time.
  const used = minutesPerTask([...result.obstacles, ...result.doneHistory])
  const groups = new Map<number, BlockRow[]>()
  for (const block of frozen) groups.set(block.taskId, [...(groups.get(block.taskId) ?? []), block])
  for (const [taskId, group] of groups) {
    const needed = (used.get(taskId) ?? 0) + group.reduce((sum, block) => sum + minutesOf(block), 0)
    ;(needed <= tasks.get(taskId)!.estimatedMinutes ? result.obstacles : result.toRemove).push(...group)
  }
  return result
}

function minutesPerTask(blocks: BlockRow[]): Map<number, number> {
  const result = new Map<number, number>()
  for (const block of blocks) result.set(block.taskId, (result.get(block.taskId) ?? 0) + minutesOf(block))
  return result
}

function startsPerTask(blocks: { taskId: number; start: Wall }[]): Map<number, Wall[]> {
  const result = new Map<number, Wall[]>()
  for (const block of blocks) result.set(block.taskId, [...(result.get(block.taskId) ?? []), block.start])
  for (const starts of result.values()) starts.sort((a, b) => a - b)
  return result
}

/**
 * Rebuilds the plan and returns the recorded event, or null when nothing changed.
 *
 * @param manualBefore the true previous start of a block a caller already moved, so that move is
 * not compared against itself
 */
export function replan(
  db: Db,
  trigger: RescheduleTrigger,
  now: Wall,
  manualBefore: Map<number, Wall[]> = new Map(),
): EventRow | null {
  const config = planConfig(db)
  continueSeries(db, config, now)
  const disposition = disposeOf(db, now, now + db.settings.freezeMinutes)

  const open = new Map(db.tasks.filter((task) => task.status !== 'DONE').map((task) => [task.id, task]))
  const covered = minutesPerTask([...disposition.obstacles, ...disposition.doneHistory])
  const keptEnds = new Map<number, Wall>()
  for (const block of disposition.obstacles) {
    keptEnds.set(block.taskId, Math.max(keptEnds.get(block.taskId) ?? -Infinity, wall(block.endAt)))
  }
  const toPlan: PlanTask[] = []
  for (const task of open.values()) {
    const remaining = Math.max(0, task.estimatedMinutes - (covered.get(task.id) ?? 0))
    if (remaining <= 0) continue
    const after = task.afterTaskId !== null && open.has(task.afterTaskId) ? task.afterTaskId : null
    let notBefore = task.notBefore ? wall(task.notBefore) : null
    const keptEnd = after === null ? undefined : keptEnds.get(after)
    if (keptEnd !== undefined && (notBefore === null || keptEnd > notBefore)) notBefore = keptEnd
    toPlan.push({
      id: task.id,
      minutes: remaining,
      deadline: task.deadline ? wall(task.deadline) : null,
      priority: task.priority,
      notBefore,
      profile: task.profile as TimeProfile,
      after,
    })
  }

  const before = startsPerTask(disposition.stillPlanned.map((block) => ({ taskId: block.taskId, start: wall(block.startAt) })))
  for (const [taskId, starts] of manualBefore) before.set(taskId, starts)

  const taken: Slot[] = disposition.obstacles.map((block) => ({ start: wall(block.startAt), end: wall(block.endAt) }))
  const planned = plan(toPlan, taken, config, now)

  const removed = new Set(disposition.toRemove.map((block) => block.id))
  db.blocks = db.blocks.filter((block) => !removed.has(block.id))
  for (const block of planned) addBlock(db, block.taskId, block.start, block.end, false)
  db.lastReplanAt = text(now)

  const after = startsPerTask([
    ...disposition.obstacles
      .filter((block) => block.state === 'PLANNED')
      .map((block) => ({ taskId: block.taskId, start: wall(block.startAt) })),
    ...planned.map((block) => ({ taskId: block.taskId, start: block.start })),
  ])
  return record(db, trigger, now, before, after, disposition, open)
}

function record(
  db: Db,
  trigger: RescheduleTrigger,
  now: Wall,
  before: Map<number, Wall[]>,
  after: Map<number, Wall[]>,
  disposition: Disposition,
  open: Map<number, TaskRow>,
): EventRow | null {
  const touched = new Set([...disposition.missedStarts.keys(), ...before.keys(), ...after.keys()])
  // Finishing a task hands its future time back; that is not a scheduling event.
  for (const taskId of disposition.completed) touched.delete(taskId)

  const items: RescheduleItem[] = []
  for (const taskId of touched) {
    const was = before.get(taskId) ?? []
    const is = after.get(taskId) ?? []
    const missed = disposition.missedStarts.get(taskId)
    const kind = classify(missed !== undefined, was, is)
    if (!kind) continue
    items.push({
      taskId,
      taskTitle: disposition.titles.get(taskId) ?? open.get(taskId)?.title ?? '(deleted task)',
      kind,
      previousStartAt: missed !== undefined ? text(missed) : was.length ? text(was[0]) : null,
      newStartAt: is.length ? text(is[0]) : null,
    })
  }
  if (items.length === 0) return null

  const order: RescheduleItemKind[] = ['MISSED', 'MOVED', 'PLACED', 'UNPLACED']
  const summary = order
    .map((kind) => [kind, items.filter((item) => item.kind === kind).length] as const)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => `${count} ${kind.toLowerCase()}`)
    .join(', ')
  const event: EventRow = { id: nextId(db), occurredAt: text(now), trigger, summary, items }
  db.events.unshift(event)
  // ponytail: history capped on the device; the server keeps all of it.
  db.events.length = Math.min(db.events.length, 200)
  return event
}

/** A task that never had a place and still has none is not reported: it would repeat every run. */
function classify(missed: boolean, before: Wall[], after: Wall[]): RescheduleItemKind | null {
  if (missed) return 'MISSED'
  if (before.length > 0 && after.length === 0) return 'UNPLACED'
  if (before.length === 0 && after.length > 0) return 'PLACED'
  return before.length === after.length && before.every((start, index) => start === after[index]) ? null : 'MOVED'
}
