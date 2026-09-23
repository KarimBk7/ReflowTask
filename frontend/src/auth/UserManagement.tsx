import { useState } from 'react'

import { ApiError } from '../api/client'
import type { AuthUser } from '../api/types'
import { CloseIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { useCreateUser, useDeleteUser, useUsers } from '../lib/auth'

interface UserManagementProps {
  currentUser: AuthUser
  onClose: () => void
  headingId: string
}

/** Admin-only: see every household account, add a member, remove one. */
export function UserManagement({ currentUser, onClose, headingId }: UserManagementProps) {
  const users = useUsers()
  const createUser = useCreateUser()
  const deleteUser = useDeleteUser()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [failure, setFailure] = useState<string | null>(null)

  async function addMember(event: React.FormEvent) {
    event.preventDefault()
    setFailure(null)
    try {
      await createUser.mutateAsync({ username, password })
      setUsername('')
      setPassword('')
    } catch (error) {
      setFailure(error instanceof ApiError ? error.message : t('error.offline'))
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
                  onClick={() => deleteUser.mutate(user.id)}
                  disabled={deleteUser.isPending}
                >
                  {t('action.remove')}
                </button>
              </>
            )}
          </li>
        ))}
      </ul>

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
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder={t('auth.memberPassword')}
          aria-label={t('auth.memberPassword')}
          maxLength={100}
          required
        />
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
