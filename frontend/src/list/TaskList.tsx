import { useState } from 'react'

import type { Task } from '../api/types'
import { ReflowIcon, RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { type TaskFilter, listTasks } from '../lib/board'
import { formatDayTime, formatDeadline, formatDuration } from '../lib/time'

interface TaskListProps {
  tasks: Task[]
  busy: boolean
  onOpen: (task: Task, anchor: DOMRect) => void
  onToggleDone: (task: Task) => void
  /** Go to the week where the task's next part is planned. */
  onShow: (task: Task) => void
}

const FILTERS: TaskFilter[] = ['open', 'done', 'all']

/**
 * Every task in one place, whether or not it has a time this week: find one by searching its title
 * or notes, tick it off, or jump to where it is planned.
 */
export function TaskList({ tasks, busy, onOpen, onToggleDone, onShow }: TaskListProps) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<TaskFilter>('open')
  const shown = listTasks(tasks, query, filter)

  return (
    <section className="task-list" aria-label={t('view.list')}>
      <div className="task-list-head">
        <input
          type="search"
          className="editor-description task-list-search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('list.search')}
          aria-label={t('list.search')}
          data-autofocus
        />
        <div className="segmented" role="radiogroup" aria-label={t('list.show')}>
          {FILTERS.map((value) => (
            <label key={value} className="segment" data-selected={filter === value || undefined}>
              <input type="radio" name="task-filter" checked={filter === value} onChange={() => setFilter(value)} />
              {t(`list.${value}`)}
            </label>
          ))}
        </div>
      </div>

      {shown.length === 0 ? (
        <p className="panel-empty task-list-empty">{query.trim() ? t('list.noMatch') : t('list.empty')}</p>
      ) : (
        <ul className="task-list-rows">
          {shown.map((task) => (
            <TaskRow
              key={task.id}
              task={task}
              waitsFor={tasks.find((other) => other.id === task.afterTaskId && other.status !== 'DONE')}
              busy={busy}
              onOpen={onOpen}
              onToggleDone={onToggleDone}
              onShow={onShow}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function TaskRow({
  task,
  waitsFor,
  busy,
  onOpen,
  onToggleDone,
  onShow,
}: { task: Task; waitsFor: Task | undefined } & Omit<TaskListProps, 'tasks'>) {
  const done = task.status === 'DONE'
  const unscheduled = done ? 0 : task.estimatedMinutes - task.scheduledMinutes
  const facts: React.ReactNode[] = [
    !done && task.doneMinutes > 0
      ? `${formatDuration(task.doneMinutes)} ${t('details.of')} ${formatDuration(task.estimatedMinutes)} ${t('details.doneSoFar')}`
      : formatDuration(task.estimatedMinutes),
    t(`priority.${task.priority}`),
  ]
  if (task.profile === 'PERSONAL') facts.push(t('profile.PERSONAL'))
  if (!done && waitsFor) facts.push(`${t('details.after')} ${waitsFor.title}`)
  if (task.deadline) {
    facts.push(
      !done && task.atRisk ? (
        <span key="risk" className="task-list-risk">
          <RiskIcon size={12} />
          {t('attention.atRisk')} {formatDeadline(task.deadline, task.deadlineHasTime)}
        </span>
      ) : (
        `${t('details.due')} ${formatDeadline(task.deadline, task.deadlineHasTime)}`
      ),
    )
  }
  if (!done && task.notBefore && new Date(task.notBefore) > new Date()) {
    facts.push(`${t('details.notBefore')} ${formatDeadline(task.notBefore, !task.notBefore.endsWith('T00:00:00'))}`)
  }
  if (unscheduled > 0) facts.push(`${formatDuration(unscheduled)} ${t('attention.unscheduled')}`)
  if (task.recurrence) facts.push(`${t('details.repeats')} ${t(`repeat.${task.recurrence}`).toLowerCase()}`)

  return (
    <li className="task-list-row" data-done={done || undefined}>
      <input
        type="checkbox"
        className="task-list-check"
        checked={done}
        disabled={busy}
        onChange={() => onToggleDone(task)}
        aria-label={`${done ? t('action.reopen') : t('action.taskDone')}: ${task.title}`}
      />
      <button
        type="button"
        className="task-list-main"
        onClick={(event) => onOpen(task, event.currentTarget.getBoundingClientRect())}
      >
        <span className="task-list-title">
          {task.title}
          {task.recurrence && <ReflowIcon size={12} />}
        </span>
        <span className="task-list-facts">
          {facts.map((fact, index) => (
            <span key={index}>{fact}</span>
          ))}
        </span>
      </button>
      {!done && task.nextStartAt && (
        <button type="button" className="button button-ghost button-small task-list-next" onClick={() => onShow(task)}>
          {formatDayTime(new Date(task.nextStartAt))}
        </button>
      )}
    </li>
  )
}
