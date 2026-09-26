import { useState } from 'react'

import type { AuthUser } from '../api/types'
import { CloseIcon } from '../design/Icon'
import { LANGUAGE, setLanguage, t } from '../i18n/en'
import { useChangePassword } from '../lib/auth'
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
