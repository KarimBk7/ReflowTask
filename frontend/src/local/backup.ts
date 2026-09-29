import type { Block, BoardConfig, RescheduleEvent, Task } from '../api/types'
import { blockView, configView, taskView } from './api'
import { type Db, emptyDb, loadDb, saveDb } from './db'
import { wallNow, text } from './wall'

/**
 * Backups for a plan that lives only on the phone: the same JSON the server exports, so a file
 * moves either way, from a server into the app or from one phone to the next, plus the task list as
 * CSV for a spreadsheet.
 */

export const EXPORT_FORMAT = 'reflowtask-export/1'

export interface ExportFile {
  format: string
  exportedAt: string
  username: string
  settings: BoardConfig
  tasks: Task[]
  blocks: Block[]
  calendars: unknown[]
  history: RescheduleEvent[]
}

export async function exportJson(): Promise<string> {
  const db = await loadDb()
  const now = wallNow()
  const file: ExportFile = {
    format: EXPORT_FORMAT,
    exportedAt: text(now),
    username: 'me',
    settings: configView(db),
    tasks: db.tasks.map((task) => taskView(db, task, now)),
    blocks: db.blocks.map((block) => blockView(db, block)),
    calendars: [],
    history: db.events,
  }
  return JSON.stringify(file, null, 2)
}

const COLUMNS = [
  'id',
  'title',
  'status',
  'priority',
  'estimatedMinutes',
  'doneMinutes',
  'scheduledMinutes',
  'deadline',
  'notBefore',
  'recurrence',
  'profile',
  'afterTaskId',
  'nextStartAt',
  'createdAt',
  'notes',
] as const

/** As the server's: quoted only when needed, and cells a spreadsheet would run as a formula defused. */
export function cell(value: unknown): string {
  if (value === null || value === undefined) return ''
  let result = String(value)
  if (result && '=+-@\t\r'.includes(result[0]) && typeof value !== 'number') result = `'${result}`
  if (/[",\n\r]/.test(result)) result = `"${result.replaceAll('"', '""')}"`
  return result
}

export async function exportCsv(): Promise<string> {
  const db = await loadDb()
  const now = wallNow()
  const rows = db.tasks
    .map((task) => taskView(db, task, now))
    .map((task) =>
      [
        task.id,
        task.title,
        task.status,
        task.priority,
        task.estimatedMinutes,
        task.doneMinutes,
        task.scheduledMinutes,
        task.deadline,
        task.notBefore,
        task.recurrence,
        task.profile,
        task.afterTaskId,
        task.nextStartAt,
        task.createdAt,
        task.description,
      ]
        .map(cell)
        .join(','),
    )
  // A byte order mark, so Excel reads umlauts as UTF-8.
  return `﻿${[COLUMNS.join(','), ...rows].join('\r\n')}\r\n`
}

/** Replaces everything on the device with an export, from this app or from a ReflowTask server. */
export async function importJson(json: string): Promise<void> {
  let file: ExportFile
  try {
    file = JSON.parse(json)
  } catch {
    throw new Error('not-an-export')
  }
  if (file?.format !== EXPORT_FORMAT || !Array.isArray(file.tasks) || !Array.isArray(file.blocks)) {
    throw new Error('not-an-export')
  }
  const db: Db = emptyDb()
  const settings = file.settings
  db.settings = {
    workingHours: settings?.workingHours ?? [],
    personalHours: settings?.personalHours ?? [],
    blockedPeriods: settings?.blockedPeriods ?? [],
    horizonDays: settings?.horizonDays ?? 14,
    minChunkMinutes: settings?.minChunkMinutes ?? 30,
    bufferMinutes: settings?.bufferMinutes ?? 0,
    freezeMinutes: settings?.freezeMinutes ?? 0,
    onboarded: true,
  }
  db.tasks = file.tasks.map((task) => ({
    id: task.id,
    title: task.title,
    description: task.description ?? null,
    estimatedMinutes: task.estimatedMinutes,
    deadline: task.deadline ?? null,
    deadlineHasTime: Boolean(task.deadlineHasTime),
    priority: task.priority,
    status: task.status,
    createdAt: task.createdAt,
    recurrence: task.recurrence ?? null,
    notBefore: task.notBefore ?? null,
    profile: task.profile ?? 'WORK',
    afterTaskId: task.afterTaskId ?? null,
  }))
  const taskIds = new Set(db.tasks.map((task) => task.id))
  db.blocks = file.blocks
    .filter((block) => taskIds.has(block.taskId))
    .map((block) => ({
      id: block.id,
      taskId: block.taskId,
      startAt: block.startAt,
      endAt: block.endAt,
      pinned: block.pinned,
      state: block.state ?? 'PLANNED',
    }))
  db.events = (file.history ?? []).slice(0, 200)
  const ids = [...db.tasks, ...db.blocks, ...db.events].map((row) => row.id)
  db.nextId = Math.max(0, ...ids) + 1
  // Planned the next time it is read, against this device's clock.
  db.lastReplanAt = null
  await saveDb(db)
}
