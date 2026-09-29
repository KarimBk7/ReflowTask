import type {
  BlockState,
  ConfigWindow,
  Priority,
  Recurrence,
  RescheduleItem,
  RescheduleTrigger,
  TaskStatus,
  TimeProfile,
} from '../api/types'

/**
 * Everything the device keeps, as one document. A person's plan is a few hundred rows at most, so a
 * single JSON file is simpler than a database and just as fast; it is also one thing to back up,
 * export, or later hand to a sync.
 *
 * Rows mirror the server's tables and use the same zone-less 'YYYY-MM-DDTHH:mm:ss' date-times, so a
 * server export and this document speak the same language.
 */
// ponytail: whole-document writes. Fine for one person's plan; move to SQLite if a plan ever grows
// into tens of thousands of rows, and add per-row change stamps when sync with a server arrives.

export interface TaskRow {
  id: number
  title: string
  description: string | null
  estimatedMinutes: number
  deadline: string | null
  deadlineHasTime: boolean
  priority: Priority
  status: TaskStatus
  createdAt: string
  recurrence: Recurrence | null
  notBefore: string | null
  profile: TimeProfile
  afterTaskId: number | null
}

export interface BlockRow {
  id: number
  taskId: number
  startAt: string
  endAt: string
  pinned: boolean
  state: BlockState
}

export interface EventRow {
  id: number
  occurredAt: string
  trigger: RescheduleTrigger
  summary: string | null
  items: RescheduleItem[]
}

export interface SettingsRow {
  workingHours: ConfigWindow[]
  personalHours: ConfigWindow[]
  blockedPeriods: ConfigWindow[]
  horizonDays: number
  minChunkMinutes: number
  bufferMinutes: number
  freezeMinutes: number
  onboarded: boolean
}

export interface Db {
  version: 1
  /** One counter for every row, so ids never collide across tables. */
  nextId: number
  tasks: TaskRow[]
  blocks: BlockRow[]
  events: EventRow[]
  settings: SettingsRow
  /** When the plan was last rebuilt; the device replans on its own when this grows stale. */
  lastReplanAt: string | null
}

export function emptyDb(): Db {
  return {
    version: 1,
    nextId: 1,
    tasks: [],
    blocks: [],
    events: [],
    settings: {
      workingHours: [],
      personalHours: [],
      blockedPeriods: [],
      horizonDays: 14,
      minChunkMinutes: 30,
      bufferMinutes: 0,
      freezeMinutes: 0,
      onboarded: false,
    },
    lastReplanAt: null,
  }
}

export function nextId(db: Db): number {
  return db.nextId++
}

/** Where the document lives: the browser for development, the app's own files on a phone. */
export interface DocumentStore {
  read(): Promise<string | null>
  write(json: string): Promise<void>
}

const KEY = 'reflowtask-device-db'

export const browserStore: DocumentStore = {
  read: async () => localStorage.getItem(KEY),
  write: async (json) => localStorage.setItem(KEY, json),
}

let store: DocumentStore = browserStore
let cached: Db | null = null
let writing: Promise<void> = Promise.resolve()
let afterSave: (db: Db) => void = () => undefined

/** Something to run after every save, such as rescheduling the phone's reminders. */
export function setAfterSave(next: (db: Db) => void) {
  afterSave = next
}

/** The app shell swaps in durable file storage before the first read. */
export function setDocumentStore(next: DocumentStore) {
  store = next
  cached = null
}

export async function loadDb(): Promise<Db> {
  if (!cached) {
    const json = await store.read()
    cached = json ? (JSON.parse(json) as Db) : emptyDb()
  }
  return cached
}

/** Writes are queued, so two quick changes can never land in the wrong order. */
export function saveDb(db: Db): Promise<void> {
  cached = db
  const json = JSON.stringify(db)
  writing = writing.then(() => store.write(json)).then(() => afterSave(db))
  return writing
}
