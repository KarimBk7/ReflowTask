import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../api/client'
import type { Block, BoardConfig, BusyPeriod, ConfigWindow, RescheduleEvent, Task, TaskInput, TaskStatus } from '../api/types'
import { DAY_NAMES, addDays, isoDay, minutesOfDay, monthGridDays, parseClock, sameDate, toLocalDateTime } from './time'

export type { BoardConfig, ConfigWindow }

const DAY_NUMBER: Record<string, number> = {
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
  SUNDAY: 7,
}

export function dayNumber(day: string): number {
  return DAY_NUMBER[day] ?? 0
}

/**
 * Working days in ISO order, so a week with no weekend work shows five columns, not seven.
 * A day outside them still appears when something is fixed on it: a Saturday appointment must
 * not vanish because Saturday is not a working day. With allDays, every day of the week is shown,
 * so a weekend can be planned into by hand even with no working hours on it.
 */
export function boardDays(config: BoardConfig | undefined, blocks: Block[], weekStart: Date, allDays = false): number[] {
  if (allDays) return [1, 2, 3, 4, 5, 6, 7]
  const days = new Set(
    config && config.workingHours.length > 0
      ? config.workingHours.map((w) => dayNumber(w.day))
      : [1, 2, 3, 4, 5],
  )
  const weekEnd = addDays(weekStart, 7)
  for (const block of blocks) {
    const start = new Date(block.startAt)
    if (start >= weekStart && start < weekEnd) days.add(isoDay(start))
  }
  return [...days].sort((a, b) => a - b)
}

/**
 * Working hours in a phrase: "Mon–Fri 09:00–18:00", or just the days when their hours differ.
 * Null when there are no working hours at all.
 */
export function describeWorkingHours(config: BoardConfig | undefined): string | null {
  return describeHours(config?.workingHours ?? [])
}

/** The same phrase for personal time; null when none is set, so personal tasks use working hours. */
export function describePersonalHours(config: BoardConfig | undefined): string | null {
  return describeHours(config?.personalHours ?? [])
}

function describeHours(windows: ConfigWindow[]): string | null {
  const hours = [...windows].sort((a, b) => dayNumber(a.day) - dayNumber(b.day))
  if (hours.length === 0) return null
  const numbers = hours.map((w) => dayNumber(w.day))
  const consecutive = numbers.every((n, i) => i === 0 || n === numbers[i - 1] + 1)
  const days =
    consecutive && numbers.length > 2
      ? `${DAY_NAMES[numbers[0] - 1]}–${DAY_NAMES[numbers[numbers.length - 1] - 1]}`
      : numbers.map((n) => DAY_NAMES[n - 1]).join(', ')
  const same = hours.every((w) => w.startTime === hours[0].startTime && w.endTime === hours[0].endTime)
  return same ? `${days} ${hours[0].startTime.slice(0, 5)}–${hours[0].endTime.slice(0, 5)}` : days
}

/** The earliest working start, where the grid scrolls to on open. */
export function firstWorkingMinute(config: BoardConfig | undefined): number {
  if (!config || config.workingHours.length === 0) return 8 * 60
  return Math.min(...config.workingHours.map((w) => parseClock(w.startTime)))
}

export function useConfig() {
  return useQuery({
    queryKey: ['config'],
    queryFn: api.config,
    staleTime: 5 * 60_000,
  })
}

export function useSchedule(weekStart: Date) {
  return useQuery({
    queryKey: ['schedule', toLocalDateTime(weekStart)],
    queryFn: () => api.schedule(toLocalDateTime(weekStart), toLocalDateTime(addDays(weekStart, 7))),
  })
}

/** The whole month grid in one request, padded to full weeks. */
export function useMonthSchedule(monthStart: Date) {
  const days = monthGridDays(monthStart)
  const from = toLocalDateTime(days[0])
  const to = toLocalDateTime(addDays(days[days.length - 1], 1))
  return useQuery({ queryKey: ['schedule', 'month', from], queryFn: () => api.schedule(from, to) })
}

/** Blocks grouped by the local day they start on, each day's list in time order. */
export function blocksByDay(blocks: Block[]): Map<string, Block[]> {
  const byDay = new Map<string, Block[]>()
  for (const block of [...blocks].sort((a, b) => a.startAt.localeCompare(b.startAt))) {
    const day = block.startAt.slice(0, 10)
    byDay.set(day, [...(byDay.get(day) ?? []), block])
  }
  return byDay
}

export function useTasks() {
  return useQuery({ queryKey: ['tasks'], queryFn: api.listTasks })
}

export function useEvents() {
  return useQuery({
    queryKey: ['events'],
    queryFn: api.rescheduleEvents,
    // The automatic hourly check can miss a deadline while this tab just sits open with no
    // interaction to trigger a refetch. Polling is how a background miss ever gets noticed.
    refetchInterval: 5 * 60_000,
  })
}

/**
 * Anything that can change the schedule refreshes blocks, tasks and history together: a replan
 * rewrites all three at once, and refreshing them piecemeal would show a calendar that
 * disagrees with its own activity feed.
 *
 * The refresh is awaited, so `mutateAsync` resolves only once the new plan is on screen. A
 * dragged block relies on that: it holds its dropped position until the server's copy of that
 * position has arrived, instead of snapping back for a frame.
 */
function useBoardMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>, alsoConfig = false) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () =>
      Promise.all([
        alsoConfig && client.invalidateQueries({ queryKey: ['config'] }),
        client.invalidateQueries({ queryKey: ['schedule'] }),
        client.invalidateQueries({ queryKey: ['tasks'] }),
        client.invalidateQueries({ queryKey: ['events'] }),
      ]),
  })
}

export function useReplan() {
  return useBoardMutation(() => api.replan())
}

export function useSetStatus() {
  return useBoardMutation(({ id, status }: { id: number; status: TaskStatus }) =>
    api.changeTaskStatus(id, status),
  )
}

export function useSetPinned() {
  return useBoardMutation(({ id, pinned }: { id: number; pinned: boolean }) =>
    pinned ? api.pinBlock(id) : api.unpinBlock(id),
  )
}

export function useSetBlockDone() {
  return useBoardMutation(({ id, done }: { id: number; done: boolean }) =>
    done ? api.completeBlock(id) : api.reopenBlock(id),
  )
}

export function useMoveBlock() {
  return useBoardMutation(({ id, start, end }: { id: number; start: Date; end: Date }) =>
    api.moveBlock(id, toLocalDateTime(start), toLocalDateTime(end)),
  )
}

export function useDeleteTask() {
  return useBoardMutation((id: number) => api.deleteTask(id))
}

export function useCreateTask() {
  return useBoardMutation(api.createTask)
}

export function useUpdateTask() {
  return useBoardMutation(({ id, input }: { id: number; input: TaskInput }) => api.updateTask(id, input))
}

/** Saving hours replans on the server, and the grid redraws its working hours from the result. */
export function useUpdateConfig() {
  return useBoardMutation(api.updateConfig, true)
}

/**
 * Work the calendar cannot show well: never placed, or placed past its deadline. Both come from
 * the server, which derives them from every block rather than the week on screen.
 */
export function needsAttention(tasks: Task[]): Task[] {
  return tasks
    .filter((task) => task.status !== 'DONE')
    .filter((task) => task.atRisk || task.scheduledMinutes < task.estimatedMinutes)
    .sort((a, b) => Number(b.atRisk) - Number(a.atRisk) || a.id - b.id)
}

/** Where a block used to be before the last replan. */
export interface Ghost {
  key: string
  taskId: number | null
  kind: 'MISSED' | 'MOVED'
  from: Date
  to: Date | null
}

/**
 * Ghosts come from the most recent replan only. Older history stays in the activity feed: "what
 * changed since I last looked" means the last change, and a week carrying every outline it
 * ever drew would be unreadable.
 *
 * Deleting or finishing a task replans without recording anything when nothing else moves, so the
 * last event can outlive its tasks. Passing the current tasks drops marks for any that are gone
 * or done; without them (tests, first load) every mark is kept.
 */
export function ghostsFrom(events: RescheduleEvent[] | undefined, tasks?: Task[]): Ghost[] {
  const latest = events?.[0]
  if (!latest) return []
  const open = tasks && new Set(tasks.filter((task) => task.status !== 'DONE').map((task) => task.id))
  return latest.items
    .filter((item) => (item.kind === 'MOVED' || item.kind === 'MISSED') && item.previousStartAt)
    .filter((item) => !open || (item.taskId !== null && open.has(item.taskId)))
    .map((item, index) => ({
      key: `${latest.id}-${index}`,
      taskId: item.taskId,
      kind: item.kind as 'MISSED' | 'MOVED',
      from: new Date(item.previousStartAt as string),
      to: item.newStartAt ? new Date(item.newStartAt) : null,
    }))
    // A split task can be reported as moved while its first piece stayed put and only later pieces
    // shifted. Its start did not change, so there is no old position to outline.
    .filter((ghost) => ghost.to === null || ghost.to.getTime() !== ghost.from.getTime())
}

/**
 * What the last replan did to a block: moved it here, or replanned it here after a miss. A change
 * is recorded per task, as the first piece's old and new start, so only the block now sitting at
 * that new start carries it. Marking every piece of a split task would label pieces that never
 * moved, with a time they never had.
 */
export function originFor(block: Block, ghosts: Ghost[]): Ghost | null {
  const start = new Date(block.startAt).getTime()
  return ghosts.find((g) => g.taskId === block.taskId && g.to?.getTime() === start) ?? null
}

export function blockedOn(config: BoardConfig | undefined, day: number): ConfigWindow[] {
  return (config?.blockedPeriods ?? []).filter((period) => dayNumber(period.day) === day)
}

export function workingOn(config: BoardConfig | undefined, day: number): ConfigWindow | undefined {
  return (config?.workingHours ?? []).find((window) => dayNumber(window.day) === day)
}

export function personalOn(config: BoardConfig | undefined, day: number): ConfigWindow | undefined {
  return (config?.personalHours ?? []).find((window) => dayNumber(window.day) === day)
}

/** One appointment from another calendar, as minutes of one day: an overnight one is cut at midnight. */
export interface Appointment {
  from: number
  to: number
  title: string | null
}

export function appointmentsOn(busy: BusyPeriod[], date: Date): Appointment[] {
  const midnight = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const result: Appointment[] = []
  for (const period of busy) {
    const from = (new Date(period.startAt).getTime() - midnight) / 60_000
    const to = (new Date(period.endAt).getTime() - midnight) / 60_000
    if (to > 0 && from < 24 * 60) result.push({ from: Math.max(from, 0), to: Math.min(to, 24 * 60), title: period.title })
  }
  return result
}

/**
 * Planned against available time for one day. Available is the working window minus the breaks
 * and calendar appointments inside it (counted once where they overlap); planned is the part of
 * each block on that date that falls inside the working window, so an evening appointment does
 * not read as a fuller working day. The scheduler never overfills a day by itself, so "over" only
 * happens when fixed or dragged work exceeds the day.
 */
export function workload(
  config: BoardConfig | undefined,
  day: number,
  date: Date,
  blocks: Block[],
  appointments: Appointment[] = [],
) {
  const working = workingOn(config, day)
  if (!working) return { planned: 0, available: 0 }
  const start = parseClock(working.startTime)
  const end = parseClock(working.endTime)
  const overlap = (from: number, to: number) => Math.max(0, Math.min(end, to) - Math.max(start, from))

  const taken = [
    ...blockedOn(config, day).map((period) => [parseClock(period.startTime), parseClock(period.endTime)]),
    ...appointments.map((appointment) => [appointment.from, appointment.to]),
  ]
    .map(([from, to]) => [Math.max(from, start), Math.min(to, end)])
    .filter(([from, to]) => to > from)
    .sort((a, b) => a[0] - b[0])
  let available = end - start
  let reached = start
  for (const [from, to] of taken) {
    available -= Math.max(0, to - Math.max(from, reached))
    reached = Math.max(reached, to)
  }
  let planned = 0
  for (const block of blocks) {
    const blockStart = new Date(block.startAt)
    // A missed part used no time: the work was planned again elsewhere.
    if (block.state === 'MISSED' || !sameDate(blockStart, date)) continue
    const from = minutesOfDay(blockStart)
    planned += overlap(from, from + (new Date(block.endAt).getTime() - blockStart.getTime()) / 60_000)
  }
  return { planned, available: Math.max(available, 0) }
}

export type TaskFilter = 'open' | 'done' | 'all'

/** Lower case without accents, so "cafe" finds "Café" and "uber" finds "Über". */
const fold = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/**
 * The task list: tasks matching every word of the search in their title or notes. Open work comes
 * first, soonest deadline first and undated last; finished work follows, newest first.
 */
// ponytail: filters in the browser over every task the app already loaded; fine for one person's
// tasks, move it to the server if a list ever runs into the tens of thousands.
export function listTasks(tasks: Task[], query: string, filter: TaskFilter): Task[] {
  const words = fold(query).split(/\s+/).filter(Boolean)
  const open = (task: Task) => task.status !== 'DONE'
  return tasks
    .filter((task) => filter === 'all' || (filter === 'open') === open(task))
    .filter((task) => {
      const text = fold(`${task.title} ${task.description ?? ''}`)
      return words.every((word) => text.includes(word))
    })
    .sort((a, b) => {
      if (open(a) !== open(b)) return open(a) ? -1 : 1
      if (open(a) && a.deadline !== b.deadline) {
        if (!a.deadline) return 1
        if (!b.deadline) return -1
        return a.deadline.localeCompare(b.deadline)
      }
      return b.createdAt.localeCompare(a.createdAt)
    })
}

export function isToday(date: Date): boolean {
  return sameDate(date, new Date())
}
