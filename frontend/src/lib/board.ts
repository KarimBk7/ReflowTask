import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../api/client'
import type { Block, RescheduleEvent, Task, TaskInput, TaskStatus } from '../api/types'
import { addDays, isoDay, parseClock, toLocalDateTime } from './time'

export interface ConfigWindow {
  day: string
  startTime: string
  endTime: string
  label: string | null
}

export interface BoardConfig {
  workingHours: ConfigWindow[]
  blockedPeriods: ConfigWindow[]
  horizonDays: number
  minChunkMinutes: number
}

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
 * The board draws only the hours the scheduler actually plans against, padded to whole
 * hours. Reading this from the server rather than assuming 09:00-18:00 is what stops the
 * board disagreeing with the plan it displays.
 */
export function boardWindow(config: BoardConfig | undefined): { start: number; end: number } {
  if (!config || config.workingHours.length === 0) return { start: 8 * 60, end: 18 * 60 }
  const start = Math.min(...config.workingHours.map((w) => parseClock(w.startTime)))
  const end = Math.max(...config.workingHours.map((w) => parseClock(w.endTime)))
  return { start: Math.floor(start / 60) * 60, end: Math.ceil(end / 60) * 60 }
}

/** Working days, in ISO order, so a week with no weekend work shows five columns not seven. */
export function boardDays(config: BoardConfig | undefined): number[] {
  if (!config || config.workingHours.length === 0) return [1, 2, 3, 4, 5]
  return [...new Set(config.workingHours.map((w) => dayNumber(w.day)))].sort((a, b) => a - b)
}

export function useConfig() {
  return useQuery({
    queryKey: ['config'],
    queryFn: () => fetch('/api/v1/config').then((r) => r.json() as Promise<BoardConfig>),
    staleTime: 5 * 60_000,
  })
}

export function useSchedule(weekStart: Date) {
  return useQuery({
    queryKey: ['schedule', toLocalDateTime(weekStart)],
    queryFn: () => api.schedule(toLocalDateTime(weekStart), toLocalDateTime(addDays(weekStart, 7))),
  })
}

export function useTasks() {
  return useQuery({ queryKey: ['tasks'], queryFn: api.listTasks })
}

export function useEvents() {
  return useQuery({ queryKey: ['events'], queryFn: api.rescheduleEvents })
}

/**
 * Anything that can change the schedule invalidates all three queries together. A replan
 * rewrites blocks, task state and history at once, so refreshing them piecemeal would show
 * the user a board that disagrees with its own record.
 */
function useBoardMutation<TArgs>(fn: (args: TArgs) => Promise<unknown>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['schedule'] })
      client.invalidateQueries({ queryKey: ['tasks'] })
      client.invalidateQueries({ queryKey: ['events'] })
    },
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

export function useDeleteTask() {
  return useBoardMutation((id: number) => api.deleteTask(id))
}

export function useCreateTask() {
  return useBoardMutation(api.createTask)
}

export function useUpdateTask() {
  return useBoardMutation(({ id, input }: { id: number; input: TaskInput }) => api.updateTask(id, input))
}

/**
 * Work the board cannot show: never placed, or placed beyond its deadline. The shortfall is
 * derived here rather than stored, so it cannot disagree with the schedule.
 */
export interface RailEntry {
  task: Task
  scheduledMinutes: number
  atRisk: boolean
}

export function unrackedWork(tasks: Task[], blocks: Block[]): RailEntry[] {
  const scheduled = new Map<number, number>()
  const risky = new Set<number>()
  for (const block of blocks) {
    const minutes = (new Date(block.endAt).getTime() - new Date(block.startAt).getTime()) / 60_000
    scheduled.set(block.taskId, (scheduled.get(block.taskId) ?? 0) + minutes)
    if (block.atRisk) risky.add(block.taskId)
  }

  return tasks
    .filter((task) => task.status !== 'DONE')
    .map((task) => ({
      task,
      scheduledMinutes: scheduled.get(task.id) ?? 0,
      atRisk: risky.has(task.id),
    }))
    .filter((entry) => entry.atRisk || entry.scheduledMinutes < entry.task.estimatedMinutes)
    .sort((a, b) => Number(b.atRisk) - Number(a.atRisk) || a.task.id - b.task.id)
}

/** Where a strip used to be, and where it went. Drawn as a chinagraph ghost on the enamel. */
export interface Ghost {
  key: string
  taskId: number | null
  taskTitle: string
  kind: 'MISSED' | 'MOVED'
  from: Date
  to: Date | null
}

/**
 * Ghosts come from the most recent event only. Older history stays in the margin record:
 * a board carrying every ghost it ever drew would be unreadable, and "what changed since I
 * last looked" means the last change.
 */
export function ghostsFrom(events: RescheduleEvent[] | undefined, weekStart: Date): Ghost[] {
  const latest = events?.[0]
  if (!latest) return []
  const weekEnd = addDays(weekStart, 7)

  return latest.items
    .filter((item) => (item.kind === 'MOVED' || item.kind === 'MISSED') && item.previousStartAt)
    .map((item, index) => ({
      key: `${latest.id}-${index}`,
      taskId: item.taskId,
      taskTitle: item.taskTitle,
      kind: item.kind as 'MISSED' | 'MOVED',
      from: new Date(item.previousStartAt as string),
      to: item.newStartAt ? new Date(item.newStartAt) : null,
    }))
    .filter((ghost) => ghost.from >= weekStart && ghost.from < weekEnd)
}

export function blockedOn(config: BoardConfig | undefined, day: number): ConfigWindow[] {
  return (config?.blockedPeriods ?? []).filter((period) => dayNumber(period.day) === day)
}

export function workingOn(config: BoardConfig | undefined, day: number): ConfigWindow | undefined {
  return (config?.workingHours ?? []).find((window) => dayNumber(window.day) === day)
}

export function isToday(date: Date): boolean {
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}

export { isoDay }
