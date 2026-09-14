import type { RescheduleEvent } from '../api/types'
import { t } from '../i18n/en'
import { formatTime } from '../lib/time'

/**
 * The board's own written record, set in the annotation hand along the bottom trim.
 *
 * PRODUCT.md forbids silent schedule mutation, and this is where that promise is kept. It is
 * deliberately not a toast: the history belongs to the board permanently, not to a moment
 * that scrolls away before the user looks up.
 */
export function MarginRecord({ events }: { events: RescheduleEvent[] | undefined }) {
  const recent = (events ?? []).slice(0, 4)

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
              </time>
              <span className="record-text">
                {event.items
                  .map((item) => `${item.taskTitle} ${t(`history.kind.${item.kind}`)}`)
                  .join(', ')}
              </span>
              <span className="record-trigger">{t(`history.trigger.${event.trigger}`)}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
