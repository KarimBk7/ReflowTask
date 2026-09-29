/**
 * Wall-clock time for the on-device scheduler: a local date-time's fields read as if they were
 * UTC, counted in minutes. A day is then always 1440 minutes and daylight saving never moves
 * 09:00, which is exactly how the server's LocalDateTime behaves.
 */
export type Wall = number

export const DAY = 1440

const pad = (value: number) => String(value).padStart(2, '0')

/** 'YYYY-MM-DDTHH:mm[:ss]' as the API writes it. */
export function wall(value: string): Wall {
  const [date, time = '00:00:00'] = value.split('T')
  const [year, month, day] = date.split('-').map(Number)
  const [hours, minutes, seconds = 0] = time.split(':').map(Number)
  return Date.UTC(year, month - 1, day, hours, minutes, seconds) / 60_000
}

/** Back to the API's zone-less form, to the second. */
export function text(value: Wall): string {
  const date = new Date(Math.round(value * 60) * 1000)
  return (
    `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}` +
    `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`
  )
}

/** The device's local time now, as wall-clock minutes. */
export function wallNow(now = new Date()): Wall {
  return (
    Date.UTC(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), now.getMinutes(), now.getSeconds()) /
    60_000
  )
}

/** ISO weekday, 1 = Monday. 1970-01-01 was a Thursday. */
export function isoDay(value: Wall): number {
  return ((Math.floor(value / DAY) + 3) % 7) + 1
}

export function startOfDay(value: Wall): Wall {
  return Math.floor(value / DAY) * DAY
}

/** 'HH:mm' or 'HH:mm:ss' to minutes after midnight. */
export function clock(value: string): number {
  const [hours, minutes] = value.split(':').map(Number)
  return hours * 60 + minutes
}

/** Minutes after midnight back to 'HH:mm:ss'. */
export function clockText(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}:00`
}

/** Calendar months ahead, clamped to the month's last day the way LocalDateTime.plusMonths is. */
export function plusMonths(value: Wall, months: number): Wall {
  const date = new Date(Math.round(value * 60) * 1000)
  const year = date.getUTCFullYear()
  const month = date.getUTCMonth() + months
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  return (
    Date.UTC(year, month, Math.min(date.getUTCDate(), lastDay), date.getUTCHours(), date.getUTCMinutes()) / 60_000
  )
}

/** A wall-clock time as a device-local Date, for APIs such as scheduled notifications. */
export function toLocalDate(value: Wall): Date {
  const utc = new Date(Math.round(value * 60) * 1000)
  return new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate(), utc.getUTCHours(), utc.getUTCMinutes())
}
