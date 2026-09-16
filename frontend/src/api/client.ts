import type {
  Block,
  BoardConfig,
  LocalDateTime,
  ProblemDetail,
  RescheduleEvent,
  Task,
  TaskInput,
  TaskStatus,
} from './types'

const BASE = '/api/v1'

/**
 * An API failure carrying the server's problem detail, so a form can show per-field
 * messages instead of a generic "something went wrong".
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...init?.headers } : init?.headers,
  })

  if (!response.ok) {
    throw new ApiError(response.status, await messageFor(response), await fieldErrorsFor(response))
  }
  // 204 has no body; callers of delete get undefined.
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>)
}

// The body can only be read once, so both helpers work from one cached parse.
const parsed = new WeakMap<Response, Promise<ProblemDetail | null>>()

function problem(response: Response): Promise<ProblemDetail | null> {
  let existing = parsed.get(response)
  if (!existing) {
    existing = response
      .clone()
      .json()
      .then((body) => body as ProblemDetail)
      .catch(() => null)
    parsed.set(response, existing)
  }
  return existing
}

async function messageFor(response: Response): Promise<string> {
  const body = await problem(response)
  return body?.detail ?? body?.title ?? `Request failed with ${response.status}`
}

async function fieldErrorsFor(response: Response): Promise<Record<string, string>> {
  const body = await problem(response)
  return body?.errors ?? {}
}

export const api = {
  listTasks: () => request<Task[]>('/tasks'),

  createTask: (input: TaskInput) =>
    request<Task>('/tasks', { method: 'POST', body: JSON.stringify(input) }),

  updateTask: (id: number, input: TaskInput) =>
    request<Task>(`/tasks/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  changeTaskStatus: (id: number, status: TaskStatus) =>
    request<Task>(`/tasks/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),

  deleteTask: (id: number) => request<void>(`/tasks/${id}`, { method: 'DELETE' }),

  /** Blocks overlapping the range. Both bounds are local date-times without a zone. */
  schedule: (from: LocalDateTime, to: LocalDateTime) =>
    request<Block[]>(`/schedule?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),

  replan: () => request<RescheduleEvent[]>('/schedule/replan', { method: 'POST' }),

  pinBlock: (id: number) => request<Block>(`/schedule/blocks/${id}/pin`, { method: 'POST' }),

  unpinBlock: (id: number) => request<Block>(`/schedule/blocks/${id}/unpin`, { method: 'POST' }),

  rescheduleEvents: () => request<RescheduleEvent[]>('/reschedule-events'),

  config: () => request<BoardConfig>('/config'),

  /** Replaces the whole configuration and replans the schedule against it. */
  updateConfig: (config: BoardConfig) =>
    request<BoardConfig>('/config', { method: 'PUT', body: JSON.stringify(config) }),
}
