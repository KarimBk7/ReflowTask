import { useState } from 'react'

import type { AuthUser, Role } from '../api/types'
import { CloseIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { useCreateUser, useDeleteUser, useResetPassword, useUsers } from '../lib/auth'
import { describe } from './describe'

interface UserManagementProps {
  currentUser: AuthUser
  onClose: () => void
  headingId: string
}

/** Admin-only: see every household account, add one, reset a forgotten password, remove one. */
export function UserManagement({ currentUser, onClose, headingId }: UserManagementProps) {
  const users = useUsers()
  const createUser = useCreateUser()
  const deleteUser = useDeleteUser()
  const resetPassword = useResetPassword()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('MEMBER')
  const [failure, setFailure] = useState<string | null>(null)
  const [resetting, setResetting] = useState<AuthUser | null>(null)
  const [temporary, setTemporary] = useState('')
  const [removing, setRemoving] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  async function addMember(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    try {
      await createUser.mutateAsync({ username, password, role })
      setUsername('')
      setPassword('')
    } catch (error) {
      setFailure(describe(error))
    }
  }

  async function submitReset(event: React.FormEvent) {
    event.preventDefault()
    if (!resetting) return
    setFailure(null)
    try {
      await resetPassword.mutateAsync({ id: resetting.id, password: temporary })
      setNotice(`${t('auth.resetDone')} (${resetting.username})`)
      setResetting(null)
      setTemporary('')
    } catch (error) {
      setFailure(describe(error))
    }
  }

  return (
    <div className="hours">
      <div className="hours-head">
        <h2 className="hours-title" id={headingId}>
          {t('auth.usersTitle')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>

      <ul className="help-list">
        {(users.data ?? []).map((user) => (
          <li key={user.id}>
            <strong>{user.username}</strong> — {user.role === 'ADMIN' ? t('auth.admin') : t('auth.member')}
            {user.id === currentUser.id && ` (${t('auth.you')})`}
            {user.mustChangePassword && ` · ${t('auth.mustChangePassword')}`}
            {user.id !== currentUser.id && (
              <>
                {' '}
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    setResetting(user)
                    setTemporary('')
                    setNotice(null)
                    setFailure(null)
                  }}
                >
                  {t('auth.resetPassword')}
                </button>{' '}
                {removing === user.id ? (
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => deleteUser.mutate(user.id, { onSettled: () => setRemoving(null) })}
                    disabled={deleteUser.isPending}
                  >
                    {t('auth.confirmRemove')}
                  </button>
                ) : (
                  <button type="button" className="link-button" onClick={() => setRemoving(user.id)}>
                    {t('action.remove')}
                  </button>
                )}
              </>
            )}
          </li>
        ))}
      </ul>
      {removing !== null && <p className="editor-note">{t('auth.removeHint')}</p>}
      {notice && (
        <p className="editor-note" role="status">
          {notice}
        </p>
      )}

      {resetting && (
        <form className="editor-row" onSubmit={submitReset}>
          <span className="editor-label">
            {t('auth.resetFor')} {resetting.username}
          </span>
          <input
            className="editor-description"
            type="text"
            value={temporary}
            onChange={(event) => setTemporary(event.target.value)}
            placeholder={t('auth.memberPassword')}
            aria-label={t('auth.memberPassword')}
            autoComplete="off"
            maxLength={100}
            data-autofocus
            required
          />
          <p className="editor-note">{t('auth.resetHint')}</p>
          <div className="editor-actions">
            <button type="button" className="button button-ghost" onClick={() => setResetting(null)}>
              {t('action.cancel')}
            </button>
            <span className="editor-actions-spacer" />
            <button type="submit" className="button button-primary" disabled={resetPassword.isPending}>
              {t('auth.resetPassword')}
            </button>
          </div>
        </form>
      )}

      <form className="editor-row" onSubmit={addMember}>
        <span className="editor-label">{t('auth.addMember')}</span>
        <input
          className="editor-description"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          placeholder={t('auth.memberUsername')}
          aria-label={t('auth.memberUsername')}
          maxLength={50}
          required
        />
        <input
          className="editor-description"
          type="text"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder={t('auth.memberPassword')}
          aria-label={t('auth.memberPassword')}
          autoComplete="off"
          maxLength={100}
          required
        />
        <select
          className="editor-description"
          value={role}
          onChange={(event) => setRole(event.target.value as Role)}
          aria-label={t('auth.role')}
        >
          <option value="MEMBER">{t('auth.roleMember')}</option>
          <option value="ADMIN">{t('auth.roleAdmin')}</option>
        </select>
        {failure && (
          <p className="form-failure" role="alert">
            {failure}
          </p>
        )}
        <button type="submit" className="button button-primary" disabled={createUser.isPending}>
          {t('auth.addMember')}
        </button>
      </form>
    </div>
  )
}
