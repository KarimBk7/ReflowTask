import { ApiError } from '../api/client'
import { t } from '../i18n/en'

/** The most specific message the server gave: a field's own error first, then the general one. */
export function describe(error: unknown): string {
  if (!(error instanceof ApiError)) return t('error.offline')
  const field = Object.values(error.fieldErrors)[0]
  return field ? `${Object.keys(error.fieldErrors)[0]} ${field}` : error.message
}
