// Mirrors the backend DTOs. Kept hand-written rather than generated: the API is small,
// and a generator would be another build step to maintain for six shapes.

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE'

/** How often a task comes back; DAILY means every working day. */
export type Recurrence = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'

/** Local date-times as the API sends them: 'YYYY-MM-DDTHH:mm:ss', no zone. */
export type LocalDateTime = string

export interface Task {
  id: number
  title: string
  description: string | null
  estimatedMinutes: number
  /** null when the task has no deadline. */
  deadline: LocalDateTime | null
  /** False when the user gave only a date, so render 'Fri' rather than 'Fri 23:59'. */
  deadlineHasTime: boolean
  priority: Priority
  status: TaskStatus
  createdAt: LocalDateTime
  /**
   * Minutes of the estimate that currently have a place, across all of the task's blocks.
   * Derived by the server from every block - never work this out from the blocks of one
   * displayed week, which reports anything placed outside that week as unscheduled.
   */
  scheduledMinutes: number
  /** Minutes of the estimate marked done, part by part. */
  doneMinutes: number
  /** Some of the task's work is placed after its deadline. */
  atRisk: boolean
  /** null for a one-off. When a repeating task is done, the next one appears, due one step later. */
  recurrence: Recurrence | null
  /** Work is not planned before this; set on the next occurrence of a repeating task. */
  notBefore: LocalDateTime | null
}

export interface TaskInput {
  title: string
  description?: string | null
  estimatedMinutes: number
  /** 'YYYY-MM-DD', or null for no deadline. */
  deadlineDate?: string | null
  /** 'HH:mm:ss'. Requires deadlineDate; the server rejects a time without a date. */
  deadlineTime?: string | null
  priority: Priority
  /** Create only: fix the task at this time as a pinned block instead of letting the scheduler place it. */
  fixedStart?: LocalDateTime | null
  /** Requires deadlineDate, and cannot be combined with fixedStart. */
  recurrence?: Recurrence | null
}

export interface Block {
  id: number
  taskId: number
  taskTitle: string
  startAt: LocalDateTime
  endAt: LocalDateTime
  pinned: boolean
  /** Derived server-side: the block ends after its task's deadline. */
  atRisk: boolean
  priority: Priority
  status: TaskStatus
  /** PLANNED can move; DONE and MISSED are history. A missed part can still be marked done. */
  state: BlockState
}

export type BlockState = 'PLANNED' | 'DONE' | 'MISSED'

export type RescheduleTrigger = 'TASK_CHANGED' | 'SCHEDULED_JOB' | 'MANUAL' | 'CONFIG_CHANGED' | 'CALENDAR_SYNCED'

export type RescheduleItemKind = 'MISSED' | 'MOVED' | 'PLACED' | 'UNPLACED'

export interface RescheduleItem {
  taskId: number | null
  taskTitle: string
  kind: RescheduleItemKind
  previousStartAt: LocalDateTime | null
  newStartAt: LocalDateTime | null
}

export interface RescheduleEvent {
  id: number
  occurredAt: LocalDateTime
  trigger: RescheduleTrigger
  summary: string | null
  items: RescheduleItem[]
}

export type Role = 'ADMIN' | 'MEMBER'

export interface AuthUser {
  id: number
  username: string
  role: Role
  /** True right after an admin creates the account; the app forces a password change first. */
  mustChangePassword: boolean
}

/** RFC 7807 problem detail, plus the per-field map the backend attaches on validation. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

/** A recurring weekly window. The label is only meaningful on a blocked period. */
export interface ConfigWindow {
  day: DayOfWeek
  /** 'HH:mm:ss' as the API sends it; the API also accepts 'HH:mm'. */
  startTime: string
  endTime: string
  label: string | null
}

/** The scheduling configuration. Read and written in the same shape. */
export interface BoardConfig {
  workingHours: ConfigWindow[]
  blockedPeriods: ConfigWindow[]
  horizonDays: number
  minChunkMinutes: number
  /** Minutes kept free between scheduled tasks and around fixed blocks. 0–120. */
  bufferMinutes: number
  /** Read-only: true once the configuration has been saved. The server ignores what is sent. */
  onboarded?: boolean
}

/** The subscribe link for your plan. path is null while the link is turned off. */
export interface FeedInfo {
  path: string | null
}

/** Another calendar whose appointments the scheduler plans around. */
export interface CalendarSource {
  id: number
  name: string
  url: string
  lastFetchedAt: LocalDateTime | null
  /** Why the last read failed; the busy times from the last good read are kept meanwhile. */
  lastError: string | null
}

/** One appointment from another calendar, in local time. */
export interface BusyPeriod {
  startAt: LocalDateTime
  endAt: LocalDateTime
  title: string | null
  sourceId: number
}
