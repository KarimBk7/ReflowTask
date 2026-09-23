import { t } from '../i18n/en'
import { useChangePassword } from '../lib/auth'
import { AuthForm } from './AuthForm'

export function ChangePasswordScreen() {
  const changePassword = useChangePassword()

  return (
    <AuthForm
      title={t('auth.changePasswordTitle')}
      intro={t('auth.changePasswordIntro')}
      usernameField={false}
      passwordLabel={t('auth.newPassword')}
      submitLabel={t('auth.setPassword')}
      onSubmit={(_username, password) => changePassword.mutateAsync(password)}
    />
  )
}
