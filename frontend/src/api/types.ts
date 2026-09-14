// Mirrors the backend DTOs. Kept hand-written rather than generated: the API is small,
// and a generator would be another build step to maintain for six shapes.

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE'

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
}

export type RescheduleTrigger = 'TASK_CHANGED' | 'SCHEDULED_JOB' | 'MANUAL'

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

/** RFC 7807 problem detail, plus the per-field map the backend attaches on validation. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}
