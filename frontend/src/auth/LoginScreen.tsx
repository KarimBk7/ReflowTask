import { t } from '../i18n/en'
import { useLogin } from '../lib/auth'
import { AuthForm } from './AuthForm'

export function LoginScreen() {
  const login = useLogin()

  return (
    <AuthForm
      title={t('auth.loginTitle')}
      passwordLabel={t('auth.password')}
      submitLabel={t('auth.logIn')}
      footnote={t('auth.forgot')}
      onSubmit={(username, password) => login.mutateAsync({ username, password })}
    />
  )
}
