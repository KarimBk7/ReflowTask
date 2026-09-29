import type { Priority, TimeProfile } from '../api/types'
import { DAY, type Wall, isoDay, startOfDay } from './wall'

/**
 * The scheduling core on the device, ported line for line from the server's SchedulePlanner and
 * held to the same tests (planner.test.ts mirrors SchedulePlannerTests). A pure function: tasks,
 * obstacles, configuration and the current time in, blocks out.
 *
 * The algorithm is a greedy pass: sort by urgency, then fill the earliest free capacity.
 */

export interface Slot {
  start: Wall
  end: Wall
}

/** A weekly window: an ISO weekday and minutes after midnight. */
export interface DailyWindow {
  day: number
  start: number
  end: number
}

export interface PlanConfig {
  workingHours: DailyWindow[]
  /** Empty means personal tasks use working hours. */
  personalHours: DailyWindow[]
  blockedPeriods: DailyWindow[]
  horizonDays: number
  minChunkMinutes: number
  bufferMinutes: number
}

export interface PlanTask {
  id: number
  /** Effort still needing a slot, after kept blocks and parts marked done. */
  minutes: number
  deadline: Wall | null
  priority: Priority
  notBefore: Wall | null
  profile: TimeProfile
  /** A task planned in the same run that this one waits for. */
  after: number | null
}

export interface Planned {
  taskId: number
  start: Wall
  end: Wall
}

/** Free capacity starts on a clean quarter-hour, so a replan at 10:37 does not produce 10:37 starts. */
const SLOT_GRANULARITY = 15

const PRIORITY_RANK: Record<Priority, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }

const PROFILES: TimeProfile[] = ['WORK', 'PERSONAL']

export function plan(tasks: PlanTask[], obstacles: Slot[], config: PlanConfig, now: Wall): Planned[] {
  // One free list per profile, because each has its own hours. A person still does one thing at a
  // time, so whatever one profile takes is cut from the others as well.
  const free = new Map<TimeProfile, Slot[]>(
    PROFILES.map((profile) => [profile, freeCapacity(obstacles, config, now, profile)]),
  )
  const byId = new Map(tasks.map((task) => [task.id, task]))
  const handled = new Set<number>()
  const planned: Planned[] = []
  for (const task of inPlanningOrder(tasks)) {
    placeAfterItsPredecessor(task, byId, handled, free, config, planned)
  }
  return planned
}

/**
 * Places a task once whatever it waits for is placed. A task that waits for a less urgent one pulls
 * that one forward; if the one it waits for finds no time, neither does it.
 */
function placeAfterItsPredecessor(
  task: PlanTask,
  byId: Map<number, PlanTask>,
  handled: Set<number>,
  free: Map<TimeProfile, Slot[]>,
  config: PlanConfig,
  planned: Planned[],
) {
  // Marked before recursing, so a cycle ends instead of looping.
  if (handled.has(task.id)) return
  handled.add(task.id)
  let notBefore = task.notBefore
  const first = task.after === null ? undefined : byId.get(task.after)
  if (first) {
    placeAfterItsPredecessor(first, byId, handled, free, config, planned)
    const itsBlocks = planned.filter((block) => block.taskId === first.id)
    const placedMinutes = itsBlocks.reduce((sum, block) => sum + (block.end - block.start), 0)
    const itsEnd = placedMinutes < first.minutes ? Infinity : Math.max(...itsBlocks.map((block) => block.end))
    notBefore = notBefore === null || itsEnd > notBefore ? itsEnd : notBefore
  }
  const placed: Planned[] = []
  place({ ...task, notBefore }, free.get(task.profile)!, config.minChunkMinutes, config.bufferMinutes, placed)
  for (const other of PROFILES) {
    if (other === task.profile) continue
    let slots = free.get(other)!
    for (const block of placed) slots = subtract(slots, { start: block.start, end: block.end + config.bufferMinutes })
    free.set(other, slots)
  }
  planned.push(...placed)
}

/** Earliest deadline first (undated last), then highest priority, then oldest id. */
export function inPlanningOrder(tasks: PlanTask[]): PlanTask[] {
  return [...tasks].sort(
    (a, b) =>
      (a.deadline ?? Infinity) - (b.deadline ?? Infinity) ||
      PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority] ||
      a.id - b.id,
  )
}

function hoursFor(config: PlanConfig, profile: TimeProfile): DailyWindow[] {
  return profile === 'PERSONAL' && config.personalHours.length > 0 ? config.personalHours : config.workingHours
}

/** Working (or personal) hours across the horizon, minus breaks, obstacles and the past. */
export function freeCapacity(obstacles: Slot[], config: PlanConfig, now: Wall, profile: TimeProfile = 'WORK'): Slot[] {
  const earliest = ceilingTo(now, SLOT_GRANULARITY)
  const firstDay = startOfDay(earliest)
  const free: Slot[] = []
  for (let offset = 0; offset < config.horizonDays; offset++) {
    const date = firstDay + offset * DAY
    const day = isoDay(date)
    for (const window of hoursFor(config, profile).filter((candidate) => candidate.day === day)) {
      const end = date + window.end
      if (end <= earliest) continue
      let pieces: Slot[] = [{ start: Math.max(date + window.start, earliest), end }]
      for (const blocked of config.blockedPeriods.filter((candidate) => candidate.day === day)) {
        pieces = subtract(pieces, { start: date + blocked.start, end: date + blocked.end })
      }
      for (const obstacle of obstacles) {
        // Widened by the buffer on both sides, so a fixed appointment keeps room around it.
        pieces = subtract(pieces, {
          start: obstacle.start - config.bufferMinutes,
          end: obstacle.end + config.bufferMinutes,
        })
      }
      free.push(...pieces)
    }
  }
  return free.sort((a, b) => a.start - b.start)
}

/** Removes cut from every slot it overlaps, splitting slots where it lands inside one. */
export function subtract(slots: Slot[], cut: Slot): Slot[] {
  const result: Slot[] = []
  for (const slot of slots) {
    if (!(slot.start < cut.end && cut.start < slot.end)) {
      result.push(slot)
      continue
    }
    if (slot.start < cut.start) result.push({ start: slot.start, end: cut.start })
    if (cut.end < slot.end) result.push({ start: cut.end, end: slot.end })
  }
  return result
}

/**
 * Places the task in one block when that costs nothing, and splits it only when it must: kept whole
 * in the earliest gap that holds it, provided that gap starts no later than splitting would have
 * started its last piece and does not miss a deadline splitting would meet.
 */
function place(task: PlanTask, free: Slot[], minChunk: number, buffer: number, planned: Planned[]) {
  const remaining = task.minutes
  const first = firstUsable(free, task.notBefore)
  const split = fill(task.id, remaining, free.map((slot) => ({ ...slot })), first, minChunk, buffer)
  if (split.length > 1) {
    const last = split[split.length - 1]
    const splitMeetsDeadline = task.deadline === null || last.end <= task.deadline
    for (let index = first; index < free.length; index++) {
      const slot = free[index]
      if (slot.start > last.start) break
      const end = slot.start + remaining
      const wholeMeetsDeadline = task.deadline === null || end <= task.deadline
      if (slot.end - slot.start >= remaining && (wholeMeetsDeadline || !splitMeetsDeadline)) {
        planned.push({ taskId: task.id, start: slot.start, end })
        consume(free, index, remaining, buffer)
        return
      }
    }
  }
  planned.push(...fill(task.id, remaining, free, first, minChunk, buffer))
}

/**
 * The index of the first free slot a task may use when it must not start before notBefore. A slot
 * straddling that moment is cut in two there.
 */
// ponytail: the cut stays for the tasks planned after this one, as on the server.
export function firstUsable(free: Slot[], notBefore: Wall | null): number {
  if (notBefore === null) return 0
  for (let index = 0; index < free.length; index++) {
    const slot = free[index]
    if (slot.end <= notBefore) continue
    if (slot.start < notBefore) {
      free.splice(index, 1, { start: slot.start, end: notBefore }, { start: notBefore, end: slot.end })
      return index + 1
    }
    return index
  }
  return free.length
}

/** The greedy pass: fills minutes from the earliest free capacity, consuming what it takes. */
function fill(taskId: number, minutes: number, free: Slot[], firstIndex: number, minChunk: number, buffer: number) {
  const pieces: Planned[] = []
  let remaining = minutes
  let index = firstIndex
  while (remaining > 0 && index < free.length) {
    const slot = free[index]
    const chunk = Math.min(slot.end - slot.start, remaining)
    // Skip a slot too small to be worth splitting into, unless this is the task's last scrap.
    if (chunk <= 0 || (chunk < minChunk && remaining >= minChunk)) {
      index++
      continue
    }
    pieces.push({ taskId, start: slot.start, end: slot.start + chunk })
    remaining -= chunk
    if (!consume(free, index, chunk, buffer)) index++
  }
  return pieces
}

/** Takes minutes from the start of a slot; whatever comes next starts after the buffer. */
function consume(free: Slot[], index: number, minutes: number, buffer: number): boolean {
  const slot = free[index]
  const consumed = minutes + buffer
  if (consumed >= slot.end - slot.start) {
    free.splice(index, 1)
    return true
  }
  free[index] = { start: slot.start + consumed, end: slot.end }
  return false
}

/** Rounds up to the next step-minute boundary, leaving exact boundaries alone. */
export function ceilingTo(time: Wall, step: number): Wall {
  const floor = Math.floor(Math.floor(time) / step) * step
  return floor < time ? floor + step : floor
}
