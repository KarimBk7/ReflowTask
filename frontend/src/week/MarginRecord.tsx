import type { RescheduleEvent } from '../api/types'
import { t } from '../i18n/en'
import { formatTime } from '../lib/time'

/**
 * The board's own written record, in the annotation hand along the bottom trim.
 *
 * PRODUCT.md forbids silent schedule mutation, and this is where that promise is kept. It
 * is deliberately not a toast and deliberately not a log table: the history is marginalia
 * someone wrote on the board in wax pencil, so it is set as running annotation in the
 * chinagraph hand rather than ruled into columns.
 */
export function MarginRecord({ events }: { events: RescheduleEvent[] | undefined }) {
  const recent = (events ?? []).slice(0, 3)

  return (
    <section className="record" aria-labelledby="record-heading">
      <h2 className="record-heading" id="record-heading">
        {t('history.title')}
      </h2>

      {recent.length === 0 ? (
        <p className="record-empty">{t('history.empty')}</p>
      ) : (
        <ol className="record-list">
          {recent.map((event) => (
            <li key={event.id} className="record-entry">
              <time className="record-time" dateTime={event.occurredAt}>
                {formatTime(new Date(event.occurredAt))}
              </time>{' '}
              {event.items.map((item, index) => (
                <span key={`${item.taskId}-${index}`}>
                  {index > 0 && <span className="record-sep">, </span>}
                  <span className="record-task">{item.taskTitle}</span>{' '}
                  <span className="record-kind">{t(`history.kind.${item.kind}`)}</span>
                </span>
              ))}
              <span className="record-trigger"> — {t(`history.trigger.${event.trigger}`)}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
