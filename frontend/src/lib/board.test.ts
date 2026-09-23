import { describe, expect, it } from 'vitest'

import type { Block, BoardConfig, RescheduleEvent, Task } from '../api/types'
import {
  blocksByDay,
  boardDays,
  describeWorkingHours,
  firstWorkingMinute,
  ghostsFrom,
  needsAttention,
  originFor,
  workload,
} from './board'

const window = (day: string, startTime = '09:00:00', endTime = '18:00:00') => ({ day, startTime, endTime, label: null }) as never

const config = (days: string[], blocked: never[] = []): BoardConfig => ({
  workingHours: days.map((d) => window(d)),
  blockedPeriods: blocked,
  horizonDays: 14,
  minChunkMinutes: 30,
  bufferMinutes: 0,
  onboarded: true,
})

const block = (id: number, startAt: string, endAt: string, extra: Partial<Block> = {}): Block => ({
  id,
  taskId: id,
  taskTitle: `Task ${id}`,
  startAt,
  endAt,
  pinned: false,
  atRisk: false,
  priority: 'MEDIUM',
  status: 'OPEN',
  ...extra,
})

const task = (id: number, extra: Partial<Task> = {}): Task => ({
  id,
  title: `Task ${id}`,
  description: null,
  estimatedMinutes: 60,
  deadline: null,
  deadlineHasTime: false,
  priority: 'MEDIUM',
  status: 'OPEN',
  createdAt: '2026-09-21T08:00:00',
  scheduledMinutes: 60,
  atRisk: false,
  ...extra,
})

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']

describe('blocksByDay', () => {
  it('groups by the day a block starts and keeps each day in time order', () => {
    const grouped = blocksByDay([
      block(3, '2026-09-22T14:00:00', '2026-09-22T15:00:00'),
      block(1, '2026-09-21T09:00:00', '2026-09-21T10:00:00'),
      block(2, '2026-09-22T09:00:00', '2026-09-22T10:00:00'),
    ])

    expect([...grouped.keys()]).toEqual(['2026-09-21', '2026-09-22'])
    expect(grouped.get('2026-09-22')?.map((b) => b.id)).toEqual([2, 3])
  })

  it('is empty for no blocks and does not mutate its input', () => {
    expect(blocksByDay([]).size).toBe(0)
    const input = [block(2, '2026-09-22T09:00:00', '2026-09-22T10:00:00'), block(1, '2026-09-21T09:00:00', '2026-09-21T10:00:00')]
    blocksByDay(input)
    expect(input.map((b) => b.id)).toEqual([2, 1])
  })
})

describe('working hours', () => {
  it('shows the working days, defaulting to Monday to Friday', () => {
    expect(boardDays(undefined, [], new Date(2026, 8, 21))).toEqual([1, 2, 3, 4, 5])
    expect(boardDays(config(['MONDAY', 'WEDNESDAY']), [], new Date(2026, 8, 21))).toEqual([1, 3])
  })

  it('shows all seven days when asked, whatever the working hours', () => {
    expect(boardDays(config(['MONDAY']), [], new Date(2026, 8, 21), true)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('adds a day off when something is fixed on it', () => {
    const saturday = block(1, '2026-09-26T10:00:00', '2026-09-26T11:00:00')
    expect(boardDays(config(WEEKDAYS), [saturday], new Date(2026, 8, 21))).toEqual([1, 2, 3, 4, 5, 6])
  })

  it('describes them in a phrase', () => {
    expect(describeWorkingHours(config(WEEKDAYS))).toBe('Mon–Fri 09:00–18:00')
    expect(describeWorkingHours(config(['MONDAY', 'THURSDAY']))).toBe('Mon, Thu 09:00–18:00')
    expect(describeWorkingHours(config([]))).toBeNull()
  })

  it('scrolls to the earliest start', () => {
    expect(firstWorkingMinute(undefined)).toBe(480)
    expect(firstWorkingMinute({ ...config(WEEKDAYS), workingHours: [window('MONDAY', '07:30:00'), window('TUESDAY')] })).toBe(450)
  })
})

describe('workload', () => {
  it('counts only planned time inside the working window, minus breaks', () => {
    const withLunch = config(WEEKDAYS, [{ day: 'MONDAY', startTime: '12:00:00', endTime: '13:00:00', label: 'Lunch' }] as never)
    const blocks = [block(1, '2026-09-21T09:00:00', '2026-09-21T11:00:00'), block(2, '2026-09-21T19:00:00', '2026-09-21T20:00:00')]

    expect(workload(withLunch, 1, new Date(2026, 8, 21), blocks)).toEqual({ planned: 120, available: 480 })
  })

  it('is zero on a day off', () => {
    expect(workload(config(['MONDAY']), 2, new Date(2026, 8, 22), [])).toEqual({ planned: 0, available: 0 })
  })
})

describe('needs attention', () => {
  it('lists at-risk and unplaced open work, at-risk first, never finished tasks', () => {
    const tasks = [
      task(1),
      task(2, { scheduledMinutes: 0 }),
      task(3, { atRisk: true }),
      task(4, { atRisk: true, status: 'DONE' }),
    ]

    expect(needsAttention(tasks).map((t) => t.id)).toEqual([3, 2])
  })
})

describe('ghosts from the last replan', () => {
  const event: RescheduleEvent = {
    id: 9,
    occurredAt: '2026-09-22T10:00:00',
    trigger: 'MANUAL',
    summary: null,
    items: [
      { taskId: 1, taskTitle: 'A', kind: 'MOVED', previousStartAt: '2026-09-22T09:00:00', newStartAt: '2026-09-22T11:00:00' },
      { taskId: 2, taskTitle: 'B', kind: 'MISSED', previousStartAt: '2026-09-22T08:00:00', newStartAt: '2026-09-22T12:00:00' },
      { taskId: 3, taskTitle: 'C', kind: 'PLACED', previousStartAt: null, newStartAt: '2026-09-22T13:00:00' },
      { taskId: 4, taskTitle: 'D', kind: 'MOVED', previousStartAt: '2026-09-22T14:00:00', newStartAt: '2026-09-22T14:00:00' },
    ],
  }

  it('outlines moved and missed blocks only, not placements or unchanged starts', () => {
    expect(ghostsFrom([event]).map((g) => g.taskId)).toEqual([1, 2])
  })

  it('drops marks for tasks that were deleted or finished since that replan', () => {
    // A later delete or completion that moved nothing records no new event, so the last event
    // still names these tasks; the board must not keep pointing at them.
    const current = [task(1), task(2, { status: 'DONE' })]
    expect(ghostsFrom([event], current).map((g) => g.taskId)).toEqual([1])
    expect(ghostsFrom([event], []).map((g) => g.taskId)).toEqual([])
  })

  it('uses only the most recent replan and tolerates none', () => {
    expect(ghostsFrom(undefined)).toEqual([])
    expect(ghostsFrom([])).toEqual([])
  })

  it('marks only the block now sitting at the new start', () => {
    const ghosts = ghostsFrom([event])
    expect(originFor(block(1, '2026-09-22T11:00:00', '2026-09-22T12:00:00', { taskId: 1 }), ghosts)?.kind).toBe('MOVED')
    expect(originFor(block(1, '2026-09-22T15:00:00', '2026-09-22T16:00:00', { taskId: 1 }), ghosts)).toBeNull()
  })
})
