import { RiskIcon, TrashIcon } from '../design/Icon'
import { t } from '../i18n/en'
import type { RailEntry } from '../lib/board'
import { formatDeadline, formatDuration } from '../lib/time'

interface StripRailProps {
  entries: RailEntry[]
  onDelete: (id: number) => void
  busy: boolean
}

/**
 * The rail of unracked strips beside the board: work with no place, or a place past its
 * deadline. Nothing here is hidden behind a filter - if the scheduler could not seat it, the
 * user sees it next to the board it failed to fit into.
 */
export function StripRail({ entries, onDelete, busy }: StripRailProps) {
  return (
    <aside className="rail" aria-labelledby="rail-heading">
      <h2 className="rail-heading" id="rail-heading">
        {t('rail.title')}
        <span className="rail-count">{entries.length}</span>
      </h2>

      {entries.length === 0 ? (
        <p className="rail-empty">{t('rail.empty')}</p>
      ) : (
        <ul className="rail-list">
          {entries.map(({ task, scheduledMinutes, atRisk }) => {
            const shortfall = task.estimatedMinutes - scheduledMinutes
            return (
              <li key={task.id} className="rail-strip" data-risk={atRisk || undefined}>
                <div className="rail-strip-body">
                  <h3 className="rail-strip-title">{task.title}</h3>
                  <p className="rail-strip-meta">
                    {formatDuration(task.estimatedMinutes)}
                    {task.deadline && (
                      <>
                        <span className="strip-dot" aria-hidden="true" />
                        {formatDeadline(task.deadline, task.deadlineHasTime)}
                      </>
                    )}
                  </p>
                  <p className="rail-strip-reason">
                    {atRisk ? (
                      <>
                        <RiskIcon />
                        {t('rail.atRisk')}
                      </>
                    ) : (
                      <>{t('rail.unscheduled')} · {formatDuration(shortfall)}</>
                    )}
                  </p>
                </div>
                <button
                  type="button"
                  className="rail-delete"
                  onClick={() => onDelete(task.id)}
                  disabled={busy}
                >
                  <TrashIcon />
                  <span className="sr-only">
                    {t('action.delete')}: {task.title}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </aside>
  )
}
