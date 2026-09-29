import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import type { AuthUser } from '../api/types'
import { CloseIcon } from '../design/Icon'
import { LANGUAGE, setLanguage, t } from '../i18n/en'
import { useChangePassword } from '../lib/auth'
import { DEVICE } from '../lib/mode'
import { exportCsv, exportJson, importJson } from '../local/backup'
import { pickTextFile, saveFile } from '../local/files'
import { describe } from './describe'

interface AccountPanelProps {
  user: AuthUser
  onClose: () => void
  headingId: string
}

/** Your own account: who you are signed in as, the language, and changing your own password. */
export function AccountPanel({ user, onClose, headingId }: AccountPanelProps) {
  const changePassword = useChangePassword()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [failure, setFailure] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    setDone(false)
    try {
      await changePassword.mutateAsync({ password: next, currentPassword: current })
      setCurrent('')
      setNext('')
      setDone(true)
    } catch (error) {
      setFailure(describe(error))
    }
  }

  if (DEVICE) return <DevicePanel onClose={onClose} headingId={headingId} />

  return (
    <div className="hours">
      <div className="hours-head">
        <h2 className="hours-title" id={headingId}>
          {t('auth.account')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>
      <p className="editor-note">
        {t('auth.signedInAs')} <strong>{user.username}</strong> ({user.role === 'ADMIN' ? t('auth.admin') : t('auth.member')})
      </p>
      <LanguageChoice />
      <div className="editor-row">
        <span className="editor-label">{t('auth.exportTitle')}</span>
        <p className="editor-note">{t('auth.exportHint')}</p>
        <div className="chips">
          <a className="button button-secondary button-small" href="/api/v1/export" download>
            {t('auth.exportJson')}
          </a>
          <a className="button button-secondary button-small" href="/api/v1/export/tasks.csv" download>
            {t('auth.exportCsv')}
          </a>
        </div>
      </div>
      <form className="editor-row" onSubmit={submit}>
        <span className="editor-label">{t('auth.changePassword')}</span>
        <input
          className="editor-description"
          type="password"
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
          placeholder={t('auth.currentPassword')}
          aria-label={t('auth.currentPassword')}
          autoComplete="current-password"
          maxLength={100}
          required
        />
        <input
          className="editor-description"
          type="password"
          value={next}
          onChange={(event) => setNext(event.target.value)}
          placeholder={t('auth.newPassword')}
          aria-label={t('auth.newPassword')}
          autoComplete="new-password"
          maxLength={100}
          required
        />
        {failure && (
          <p className="form-failure" role="alert">
            {failure}
          </p>
        )}
        {done && <p className="editor-note" role="status">{t('auth.passwordChanged')}</p>}
        <button type="submit" className="button button-primary" disabled={changePassword.isPending || next === ''}>
          {t('auth.changePassword')}
        </button>
      </form>
    </div>
  )
}

function LanguageChoice() {
  return (
    <div className="editor-row">
      <span className="editor-label">{t('auth.language')}</span>
      <div className="segmented" role="radiogroup" aria-label={t('auth.language')}>
        {([['en', 'English'], ['de', 'Deutsch']] as const).map(([value, name]) => (
          <label key={value} className="segment" data-selected={LANGUAGE === value || undefined}>
            <input type="radio" name="language" checked={LANGUAGE === value} onChange={() => setLanguage(value)} />
            {name}
          </label>
        ))}
      </div>
    </div>
  )
}

/**
 * The phone app keeps everything on the device, so this is where it is backed up and brought back:
 * the same JSON a ReflowTask server exports, which also moves a plan from a server into the app.
 */
function DevicePanel({ onClose, headingId }: { onClose: () => void; headingId: string }) {
  const client = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState<{ text: string; failed: boolean } | null>(null)
  const stamp = new Date().toISOString().slice(0, 10)

  async function restore() {
    setConfirming(false)
    const json = await pickTextFile('application/json,.json')
    if (json === null) return
    try {
      await importJson(json)
      await client.invalidateQueries()
      setNotice({ text: t('device.restored'), failed: false })
    } catch {
      setNotice({ text: t('device.notAnExport'), failed: true })
    }
  }

  return (
    <div className="hours">
      <div className="hours-head">
        <h2 className="hours-title" id={headingId}>
          {t('device.data')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>
      <LanguageChoice />
      <div className="editor-row">
        <span className="editor-label">{t('device.backupTitle')}</span>
        <p className="editor-note">{t('device.backupHint')}</p>
        <div className="chips">
          <button
            type="button"
            className="button button-secondary button-small"
            onClick={async () => saveFile(`reflowtask-${stamp}.json`, await exportJson(), 'application/json')}
          >
            {t('auth.exportJson')}
          </button>
          <button
            type="button"
            className="button button-secondary button-small"
            onClick={async () => saveFile(`reflowtask-${stamp}.csv`, await exportCsv(), 'text/csv')}
          >
            {t('auth.exportCsv')}
          </button>
        </div>
      </div>
      <div className="editor-row">
        <span className="editor-label">{t('device.restoreTitle')}</span>
        <p className="editor-note">{t('device.restoreHint')}</p>
        <div className="chips">
          {confirming ? (
            <button type="button" className="button button-danger button-small" onClick={restore}>
              {t('device.restoreConfirm')}
            </button>
          ) : (
            <button type="button" className="button button-secondary button-small" onClick={() => setConfirming(true)}>
              {t('device.restore')}
            </button>
          )}
        </div>
        {notice && (
          <p className={notice.failed ? 'form-failure' : 'editor-note'} role={notice.failed ? 'alert' : 'status'}>
            {notice.text}
          </p>
        )}
      </div>
    </div>
  )
}
