import type { RescheduleEvent, RescheduleItem, Task } from '../api/types'
import { RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { formatDayTime, formatDeadline, formatDuration, formatTime, sameDate } from '../lib/time'

/**
 * Work the calendar cannot show well: tasks with no time found, or placed past their deadline.
 * Each opens its editor, where a shorter estimate, a later deadline or a higher priority is the fix.
 */
export function NeedsAttention({ tasks, onOpen }: { tasks: Task[]; onOpen: (task: Task, anchor: DOMRect) => void }) {
  return (
    <section className="panel" aria-labelledby="attention-heading">
      <h2 className="panel-heading" id="attention-heading">
        {t('attention.title')}
        {tasks.length > 0 && <span className="count">{tasks.length}</span>}
      </h2>

      {tasks.length === 0 ? (
        <p className="panel-empty">{t('attention.empty')}</p>
      ) : (
        <ul className="attention-list">
          {tasks.map((task) => (
            <li key={task.id}>
              <button
                type="button"
                className="attention-item"
                data-risk={task.atRisk || undefined}
                onClick={(event) => onOpen(task, event.currentTarget.getBoundingClientRect())}
              >
                <span className="attention-title">{task.title}</span>
                <span className="attention-reason">
                  {task.atRisk ? (
                    <>
                      <RiskIcon size={12} />
                      {t('attention.atRisk')}
                      {task.deadline && ` ${formatDeadline(task.deadline, task.deadlineHasTime)}`}
                    </>
                  ) : (
                    <>
                      {formatDuration(task.estimatedMinutes - task.scheduledMinutes)} {t('attention.unscheduled')}
                    </>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

const RECENT = 6

/**
 * Every replan, written out. The calendar's outlines only show the latest one; this is where a
 * change from this morning can still be explained.
 */
export function Activity({ events }: { events: RescheduleEvent[] | undefined }) {
  const recent = (events ?? []).slice(0, RECENT)

  return (
    <section className="panel" aria-labelledby="activity-heading">
      <h2 className="panel-heading" id="activity-heading">
        {t('activity.title')}
      </h2>

      {recent.length === 0 ? (
        <p className="panel-empty">{t('activity.empty')}</p>
      ) : (
        <ol className="activity-list">
          {recent.map((event) => {
            const at = new Date(event.occurredAt)
            return (
              <li key={event.id} className="activity-entry">
                <p className="activity-when">
                  <time dateTime={event.occurredAt}>
                    {sameDate(at, new Date()) ? `${t('activity.today')} ${formatTime(at)}` : formatDayTime(at)}
                  </time>
                  <span className="dot" aria-hidden="true" />
                  {t(`activity.trigger.${event.trigger}`)}
                </p>
                <ul className="activity-items">
                  {event.items.map((item, index) => (
                    <li key={`${item.taskId}-${index}`} className="activity-item" data-kind={item.kind}>
                      <span className="activity-task">{item.taskTitle}</span> {describe(item)}
                    </li>
                  ))}
                </ul>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}

function describe(item: RescheduleItem): string {
  const fromDate = item.previousStartAt ? new Date(item.previousStartAt) : null
  const toDate = item.newStartAt ? new Date(item.newStartAt) : null
  const from = fromDate ? formatDayTime(fromDate) : null
  // A move within one day names the day once: "Fri 18, 14:30 → 15:00".
  const to = toDate ? (fromDate && sameDate(fromDate, toDate) ? formatTime(toDate) : formatDayTime(toDate)) : null
  switch (item.kind) {
    case 'MOVED':
      // Same first start: a split task whose later pieces shifted.
      if (fromDate && toDate && fromDate.getTime() === toDate.getTime()) return t('activity.reshuffled')
      return from && to ? `${t('activity.moved')} ${from} → ${to}` : t('activity.moved')
    case 'MISSED':
      return to ? `${t('activity.missed')} ${from ?? ''}, ${t('activity.nowAt')} ${to}` : `${t('activity.missed')} ${from ?? ''}`
    case 'PLACED':
      return to ? `${t('activity.placed')} ${to}` : t('activity.placed')
    case 'UNPLACED':
      return t('activity.unplaced')
  }
}
