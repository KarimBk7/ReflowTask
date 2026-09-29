import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../api/errors'
import type { BoardConfig, TaskInput } from '../api/types'
import { deviceApi as api } from './api'
import { exportCsv, exportJson, importJson } from './backup'
import { type Db, setDocumentStore } from './db'

/**
 * The device API against the rules the server's integration tests pin down: the same behaviour,
 * checked through the same calls the app makes, with the clock set by hand.
 */

// Monday 2026-09-07, local time.
const setNow = (day: number, hour: number, minute = 0) => vi.setSystemTime(new Date(2026, 8, 7 + day, hour, minute))
const at = (day: number, hour: number, minute = 0) =>
  `2026-09-${String(7 + day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`

let saved: string | null = null

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const
const hours = (extra: Partial<BoardConfig> = {}): BoardConfig => ({
  workingHours: WEEKDAYS.map((day) => ({ day, startTime: '09:00', endTime: '18:00', label: null })),
  personalHours: [],
  blockedPeriods: [],
  horizonDays: 14,
  minChunkMinutes: 30,
  bufferMinutes: 0,
  freezeMinutes: 0,
  ...extra,
})
const input = (title: string, estimatedMinutes = 60, extra: Partial<TaskInput> = {}): TaskInput => ({
  title,
  estimatedMinutes,
  priority: 'MEDIUM',
  ...extra,
})

async function blocksOf(title: string) {
  const blocks = await api.schedule(at(-7, 0), at(28, 0))
  return blocks.filter((block) => block.taskTitle === title).sort((a, b) => a.startAt.localeCompare(b.startAt))
}

async function taskNamed(title: string) {
  return (await api.listTasks()).filter((task) => task.title === title && task.status !== 'DONE')
}

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ['Date'] })
  saved = null
  setDocumentStore({ read: async () => saved, write: async (json) => void (saved = json) })
  setNow(0, 8)
  await api.updateConfig(hours())
  // The first save seeds an example missed task, as on the server; these tests start without it.
  for (const task of await api.listTasks()) await api.deleteTask(task.id)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('the device API', () => {
  it('places a new task into the first free working time', async () => {
    const task = await api.createTask(input('Report', 120))
    expect(task.scheduledMinutes).toBe(120)
    expect(task.nextStartAt).toBe(at(0, 9))
  })

  it('keeps it in the stored document across a reload', async () => {
    await api.createTask(input('Report'))
    const document = JSON.parse(saved!) as Db
    expect(document.tasks.some((task) => task.title === 'Report')).toBe(true)
  })

  it('marks a block missed when its time passes, and plans the work again', async () => {
    await api.createTask(input('Report'))
    setNow(0, 11)
    await api.replan()
    const blocks = await blocksOf('Report')
    expect(blocks.map((block) => [block.startAt, block.state])).toEqual([
      [at(0, 9), 'MISSED'],
      [at(0, 11), 'PLANNED'],
    ])
    const events = await api.rescheduleEvents()
    expect(events[0].items.find((item) => item.taskTitle === 'Report')?.kind).toBe('MISSED')
  })

  it('replans on its own when read after a while, as the server job would', async () => {
    await api.createTask(input('Report'))
    setNow(0, 11)
    const blocks = await blocksOf('Report')
    expect(blocks[0].state).toBe('MISSED')
    expect((await api.rescheduleEvents())[0].trigger).toBe('SCHEDULED_JOB')
  })

  it('counts a part marked done, and refuses one that has not started', async () => {
    // Split around a fixed meeting by its 13:00 deadline: 09:00-10:00 and 11:00-13:00.
    await api.createTask(input('Meeting', 60, { fixedStart: at(0, 10) }))
    await api.createTask(input('Write', 180, { deadlineDate: '2026-09-07', deadlineTime: '13:00' }))
    const [first, second] = await blocksOf('Write')
    expect([first.startAt, second.startAt]).toEqual([at(0, 9), at(0, 11)])
    await expect(api.completeBlock(second.id)).rejects.toMatchObject({ status: 409 })

    setNow(0, 10)
    await api.completeBlock(first.id)
    const [write] = await taskNamed('Write')
    expect(write.doneMinutes).toBe(60)
    expect(write.scheduledMinutes).toBe(180)
  })

  it('brings the next occurrence of a weekly task, not planned before this one was due', async () => {
    const weekly = await api.createTask(input('Review', 60, { deadlineDate: '2026-09-11', recurrence: 'WEEKLY' }))
    await api.changeTaskStatus(weekly.id, 'DONE')
    const [next] = await taskNamed('Review')
    expect(next.deadline).toBe(at(11, 23, 59))
    expect(next.notBefore).toBe(at(4, 23, 59))
    expect(next.nextStartAt).toBe(at(7, 9))
  })

  it('plans a task only after the one it waits for', async () => {
    const buy = await api.createTask(input('Buy paint', 120))
    const paint = await api.createTask(input('Paint', 60, { afterTaskId: buy.id }))
    expect(paint.nextStartAt).toBe(at(0, 11))
    await expect(api.updateTask(buy.id, input('Buy paint', 120, { afterTaskId: paint.id }))).rejects.toMatchObject({
      status: 400,
    })
  })

  it('keeps what is about to start in place inside the freeze window', async () => {
    await api.updateConfig(hours({ freezeMinutes: 120 }))
    setNow(0, 8, 30)
    await api.createTask(input('Report'))
    await api.createTask(input('Urgent', 60, { deadlineDate: '2026-09-07', deadlineTime: '10:00', priority: 'HIGH' }))
    expect((await blocksOf('Report'))[0].startAt).toBe(at(0, 9))
    expect((await blocksOf('Urgent'))[0].startAt).toBe(at(0, 10))
  })

  it('pins a moved block, follows a resize with the estimate, and records where it came from', async () => {
    await api.createTask(input('Report'))
    const [block] = await blocksOf('Report')
    await api.moveBlock(block.id, at(0, 14), at(0, 15, 30))
    const [moved] = await blocksOf('Report')
    expect(moved).toMatchObject({ startAt: at(0, 14), pinned: true })
    expect((await taskNamed('Report'))[0].estimatedMinutes).toBe(90)
    const item = (await api.rescheduleEvents())[0].items.find((candidate) => candidate.taskTitle === 'Report')
    expect(item).toMatchObject({ kind: 'MOVED', previousStartAt: at(0, 9), newStartAt: at(0, 14) })
  })

  it('reports validation failures by field, like the server', async () => {
    const blank = api.createTask(input('  '))
    await expect(blank).rejects.toBeInstanceOf(ApiError)
    await expect(blank).rejects.toMatchObject({ status: 400, fieldErrors: { title: expect.any(String) } })
    await expect(api.createTask(input('Daily', 30, { recurrence: 'DAILY' }))).rejects.toMatchObject({
      fieldErrors: { recurrenceAnchored: expect.any(String) },
    })
  })

  it('leaves nothing behind when a change is refused', async () => {
    await api.createTask(input('Meeting', 60, { fixedStart: at(0, 10) }))
    const before = saved
    await expect(api.createTask(input('Clash', 60, { fixedStart: at(0, 10, 30) }))).rejects.toMatchObject({ status: 409 })
    expect(saved).toBe(before)
    expect(await taskNamed('Clash')).toEqual([])
  })

  it('lifts a wait when the task waited for is deleted', async () => {
    const buy = await api.createTask(input('Buy paint', 120))
    await api.createTask(input('Paint', 60, { afterTaskId: buy.id }))
    await api.deleteTask(buy.id)
    const [paint] = await taskNamed('Paint')
    expect(paint.afterTaskId).toBeNull()
    expect(paint.nextStartAt).toBe(at(0, 9))
  })
})

describe('backups on the device', () => {
  it('restores exactly what it exported', async () => {
    const buy = await api.createTask(input('Buy paint', 120, { deadlineDate: '2026-09-09' }))
    await api.createTask(input('Paint', 60, { afterTaskId: buy.id, profile: 'PERSONAL' }))
    const before = await api.listTasks()
    const json = await exportJson()

    for (const task of before) await api.deleteTask(task.id)
    await importJson(json)

    const after = await api.listTasks()
    expect(after.map((task) => [task.title, task.afterTaskId, task.profile, task.deadline])).toEqual(
      before.map((task) => [task.title, task.afterTaskId, task.profile, task.deadline]),
    )
    expect((await api.config()).workingHours).toHaveLength(5)
  })

  it('refuses a file that is not a ReflowTask export and keeps the plan', async () => {
    await api.createTask(input('Keep me'))
    await expect(importJson('{"hello":"world"}')).rejects.toThrow('not-an-export')
    await expect(importJson('not json')).rejects.toThrow('not-an-export')
    expect(await taskNamed('Keep me')).toHaveLength(1)
  })

  it('writes CSV a spreadsheet opens safely', async () => {
    await api.createTask(input('=HYPERLINK(1)', 30, { description: 'a, "quoted" note' }))
    const csv = await exportCsv()
    expect(csv.startsWith('﻿id,title,status')).toBe(true)
    expect(csv).toContain(",'=HYPERLINK(1),")
    expect(csv).toContain(',"a, ""quoted"" note"\r\n')
  })
})
