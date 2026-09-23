import { useState } from 'react'

import { t } from '../i18n/en'
import { describe } from './describe'

interface AuthFormProps {
  title: string
  intro?: string
  /** True on the change-password screen, which asks for one field instead of two. */
  usernameField?: boolean
  passwordLabel: string
  submitLabel: string
  onSubmit: (username: string, password: string) => Promise<unknown>
  /** Small print under the form, like where to turn after forgetting a password. */
  footnote?: string
}

/**
 * The shared shell for bootstrap, login and change-password: a centered card, since none of
 * these has a board behind it yet to anchor a popover to.
 */
export function AuthForm({ title, intro, usernameField = true, passwordLabel, submitLabel, onSubmit, footnote }: AuthFormProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [failure, setFailure] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    setBusy(true)
    try {
      await onSubmit(username, password)
    } catch (error) {
      setFailure(describe(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-screen">
      <form className="auth-card editor" onSubmit={submit} noValidate>
        <h1 className="hours-title">{title}</h1>
        {intro && <p className="editor-note">{intro}</p>}

        {usernameField && (
          <fieldset className="editor-row">
            <label className="editor-label" htmlFor="auth-username">
              {t('auth.username')}
            </label>
            <input
              id="auth-username"
              className="editor-description"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              maxLength={50}
              data-autofocus
              required
            />
          </fieldset>
        )}

        <fieldset className="editor-row">
          <label className="editor-label" htmlFor="auth-password">
            {passwordLabel}
          </label>
          <input
            id="auth-password"
            className="editor-description"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={usernameField ? 'current-password' : 'new-password'}
            maxLength={100}
            data-autofocus={!usernameField || undefined}
            required
          />
        </fieldset>

        {failure && (
          <p className="form-failure" role="alert">
            {failure}
          </p>
        )}

        <div className="editor-actions">
          <span className="editor-actions-spacer" />
          <button type="submit" className="button button-primary" disabled={busy || password === ''}>
            {submitLabel}
          </button>
        </div>
        {footnote && <p className="editor-note">{footnote}</p>}
      </form>
    </div>
  )
}
