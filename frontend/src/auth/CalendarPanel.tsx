import { useState } from 'react'

import { CloseIcon, TrashIcon } from '../design/Icon'
import { t } from '../i18n/en'
import {
  feedUrl,
  useAddSource,
  useDisableFeed,
  useFeed,
  useRefreshSources,
  useRemoveSource,
  useRenewFeed,
  useSources,
  webcalUrl,
} from '../lib/calendar'
import { formatDayTime } from '../lib/time'
import { describe } from './describe'

interface CalendarPanelProps {
  onClose: () => void
  headingId: string
}

/**
 * Calendar sync, both ways: a link to subscribe to your plan in any calendar app, and other
 * calendars whose appointments the scheduler plans around.
 */
export function CalendarPanel({ onClose, headingId }: CalendarPanelProps) {
  const feed = useFeed()
  const renew = useRenewFeed()
  const disable = useDisableFeed()
  const sources = useSources()
  const add = useAddSource()
  const remove = useRemoveSource()
  const refresh = useRefreshSources()

  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [failure, setFailure] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [confirmRenew, setConfirmRenew] = useState(false)

  const path = feed.data?.path ?? null

  async function copy() {
    if (!path) return
    try {
      await navigator.clipboard.writeText(feedUrl(path))
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      /* No clipboard access (plain http on some browsers): the link is selectable in the field. */
    }
  }

  async function addSource(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    try {
      await add.mutateAsync({ name, url })
      setName('')
      setUrl('')
    } catch (error) {
      setFailure(describe(error))
    }
  }

  return (
    <div className="hours calendar-panel">
      <div className="hours-head">
        <h2 className="hours-title" id={headingId}>
          {t('calendar.title')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>

      <section className="hours-section">
        <h3 className="section-label">{t('calendar.subscribeTitle')}</h3>
        <p className="section-hint">{t('calendar.subscribeHint')}</p>
        {path ? (
          <>
            <input
              className="editor-description"
              readOnly
              value={feedUrl(path)}
              aria-label={t('calendar.link')}
              onFocus={(event) => event.currentTarget.select()}
            />
            <div className="chips">
              <button type="button" className="button button-primary button-small" onClick={copy}>
                {copied ? t('calendar.copied') : t('calendar.copy')}
              </button>
              <a className="button button-secondary button-small" href={webcalUrl(path)}>
                {t('calendar.openInApp')}
              </a>
            </div>
            <p className="section-hint">{t('calendar.secretHint')}</p>
            <div className="chips">
              {confirmRenew ? (
                <button
                  type="button"
                  className="button button-ghost button-small"
                  onClick={() => renew.mutate(undefined, { onSettled: () => setConfirmRenew(false) })}
                >
                  {t('calendar.renewConfirm')}
                </button>
              ) : (
                <button type="button" className="button button-ghost button-small" onClick={() => setConfirmRenew(true)}>
                  {t('calendar.renew')}
                </button>
              )}
              <button type="button" className="button button-ghost button-small" onClick={() => disable.mutate()}>
                {t('calendar.turnOff')}
              </button>
            </div>
          </>
        ) : (
          <button
            type="button"
            className="button button-primary button-small"
            onClick={() => renew.mutate()}
            disabled={renew.isPending || feed.isLoading}
          >
            {t('calendar.createLink')}
          </button>
        )}
      </section>

      <section className="hours-section">
        <h3 className="section-label">{t('calendar.busyTitle')}</h3>
        <p className="section-hint">{t('calendar.busyHint')}</p>
        <ul className="calendar-sources">
          {(sources.data ?? []).map((source) => (
            <li key={source.id}>
              <strong>{source.name}</strong>{' '}
              {source.lastError ? (
                <span className="field-error">{source.lastError}</span>
              ) : (
                source.lastFetchedAt && (
                  <span className="editor-note">
                    {t('calendar.updated')} {formatDayTime(new Date(source.lastFetchedAt))}
                  </span>
                )
              )}{' '}
              <button
                type="button"
                className="icon-button"
                onClick={() => remove.mutate(source.id)}
                disabled={remove.isPending}
              >
                <TrashIcon />
                <span className="sr-only">
                  {t('action.remove')} {source.name}
                </span>
              </button>
            </li>
          ))}
        </ul>
        {(sources.data ?? []).length > 0 && (
          <button
            type="button"
            className="button button-ghost button-small"
            onClick={() => refresh.mutate()}
            disabled={refresh.isPending}
          >
            {t('calendar.refresh')}
          </button>
        )}
        <form className="editor-row" onSubmit={addSource}>
          <input
            className="editor-description"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder={t('calendar.sourceName')}
            aria-label={t('calendar.sourceName')}
            maxLength={100}
            required
          />
          <input
            className="editor-description"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder={t('calendar.sourceUrl')}
            aria-label={t('calendar.sourceUrl')}
            inputMode="url"
            maxLength={2000}
            required
          />
          {failure && (
            <p className="form-failure" role="alert">
              {failure}
            </p>
          )}
          <button type="submit" className="button button-primary button-small" disabled={add.isPending}>
            {add.isPending ? t('calendar.reading') : t('calendar.add')}
          </button>
        </form>
      </section>
    </div>
  )
}
