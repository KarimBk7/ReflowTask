import type { AuthUser, BoardConfig } from '../api/types'
import { CloseIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { describeWorkingHours } from '../lib/board'

interface HowItWorksProps {
  config: BoardConfig | undefined
  user: AuthUser
  onClose: () => void
  headingId: string
}

/**
 * The planning rules in plain words, with the owner's own settings filled in. What "Let
 * ReflowTask place it" will do should not have to be discovered by trying it.
 */
export function HowItWorks({ config, user, onClose, headingId }: HowItWorksProps) {
  const hours = describeWorkingHours(config)

  return (
    <div className="help">
      <div className="details-head">
        <h2 className="details-title" id={headingId}>
          {t('help.title')}
        </h2>
        <button type="button" className="icon-button" onClick={onClose}>
          <CloseIcon />
          <span className="sr-only">{t('action.close')}</span>
        </button>
      </div>
      <ul className="help-list">
        <li>
          {t('help.place')}
          {hours && ` (${hours})`}. {t('help.order')}
        </li>
        <li>
          {t('help.whole')} {config?.minChunkMinutes ?? 30} {t('unit.min')}.
        </li>
        <li>{t('help.fixed')}</li>
        <li>{t('help.missed')}</li>
        <li>{t('help.parts')}</li>
        <li>{t('help.repeat')}</li>
        <li>{t('help.profiles')}</li>
        <li>{t('help.after')}</li>
        <li>{t('help.undo')}</li>
        {config && config.freezeMinutes > 0 && (
          <li>
            {t('help.freeze')} {config.freezeMinutes} {t('unit.min')}.
          </li>
        )}
        {config && config.bufferMinutes > 0 && (
          <li>
            {t('help.buffer')} {config.bufferMinutes} {t('unit.min')}.
          </li>
        )}
        <li>{t('help.activity')}</li>
        <li>{t('help.month')}</li>
        <li>{t('help.list')}</li>
        <li>{t('help.calendar')}</li>
        <li>{t('help.private')}</li>
        {user.role === 'ADMIN' && <li>{t('help.household')}</li>}
      </ul>
    </div>
  )
}
