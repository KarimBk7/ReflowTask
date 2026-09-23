import { t } from '../i18n/en'
import { useBootstrap } from '../lib/auth'
import { AuthForm } from './AuthForm'

export function BootstrapScreen() {
  const bootstrap = useBootstrap()

  return (
    <AuthForm
      title={t('auth.bootstrapTitle')}
      intro={t('auth.bootstrapIntro')}
      passwordLabel={t('auth.password')}
      submitLabel={t('auth.createAccount')}
      onSubmit={(username, password) => bootstrap.mutateAsync({ username, password })}
    />
  )
}
