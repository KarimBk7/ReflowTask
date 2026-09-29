import type { AuthUser } from '../api/types'
import { CalendarIcon, ClockIcon, HelpIcon, LogoutIcon, ReflowIcon, UserIcon, UsersIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { initial } from '../lib/auth'
import { DEVICE } from '../lib/mode'

export type MenuTarget = 'hours' | 'calendar' | 'users' | 'account' | 'help'

interface MainMenuProps {
  user: AuthUser
  /** Whether days off are shown; null when the switch does not apply (not the week view, or a phone). */
  daysOff: boolean | null
  busy: boolean
  onOpen: (target: MenuTarget) => void
  onReplan: () => void
  onToggleDaysOff: () => void
  onLogout: () => void
  headingId: string
}

/**
 * Everything the top bar does not need at hand: settings, sync, the household, help and logging out.
 * What planning needs every few minutes stays in the bar; what is set once a week lives here.
 */
export function MainMenu({ user, daysOff, busy, onOpen, onReplan, onToggleDaysOff, onLogout, headingId }: MainMenuProps) {
  return (
    <div className="menu">
      <div className="menu-head">
        {DEVICE ? (
          <span className="brand-mark" aria-hidden="true" />
        ) : (
          <span className="avatar" aria-hidden="true">
            {initial(user.username)}
          </span>
        )}
        <span className="menu-who">
          <strong id={headingId}>{DEVICE ? t('app.name') : user.username}</strong>
          <span>{DEVICE ? t('device.onThisPhone') : user.role === 'ADMIN' ? t('auth.admin') : t('auth.member')}</span>
        </span>
      </div>

      <ul className="menu-list">
        <li>
          <button type="button" className="menu-item" onClick={() => onOpen('hours')}>
            <ClockIcon />
            {t('hours.title')}
          </button>
        </li>
        {!DEVICE && (
        <li>
          <button type="button" className="menu-item" onClick={() => onOpen('calendar')}>
            <CalendarIcon />
            {t('calendar.title')}
          </button>
        </li>
        )}
        {!DEVICE && user.role === 'ADMIN' && (
          <li>
            <button type="button" className="menu-item" onClick={() => onOpen('users')}>
              <UsersIcon />
              {t('auth.usersTitle')}
            </button>
          </li>
        )}
        <li>
          <button type="button" className="menu-item" onClick={onReplan} disabled={busy}>
            <ReflowIcon />
            {t('menu.replan')}
          </button>
        </li>
        {daysOff !== null && (
          <li>
            <label className="menu-item menu-switch">
              <span>{t('menu.showDaysOff')}</span>
              <span className="switch">
                <input type="checkbox" checked={daysOff} onChange={onToggleDaysOff} />
                <span className="switch-track" aria-hidden="true" />
              </span>
            </label>
          </li>
        )}
      </ul>

      <ul className="menu-list">
        <li>
          <button type="button" className="menu-item" onClick={() => onOpen('account')}>
            <UserIcon />
            {DEVICE ? t('device.data') : t('menu.account')}
          </button>
        </li>
        <li>
          <button type="button" className="menu-item" onClick={() => onOpen('help')}>
            <HelpIcon />
            {t('action.help')}
          </button>
        </li>
        {!DEVICE && (
          <li>
            <button type="button" className="menu-item" onClick={onLogout}>
              <LogoutIcon />
              {t('auth.logout')}
            </button>
          </li>
        )}
      </ul>
    </div>
  )
}
