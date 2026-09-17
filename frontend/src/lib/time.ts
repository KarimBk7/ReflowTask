/**
 * Wall-clock helpers.
 *
 * The backend stores and sends LocalDateTime - 'YYYY-MM-DDTHH:mm:ss' with no zone - because
 * for a personal scheduler "09:00 means 09:00". A date-time string without a zone is parsed
 * as local time by every current browser, which is exactly the semantic we want, so nothing
 * here converts between zones.
 */

export const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const

/** ISO weekday, 1 = Monday, matching the backend's day_of_week column. */
export function isoDay(date: Date): number {
  return date.getDay() === 0 ? 7 : date.getDay()
}

export function startOfWeek(date: Date): Date {
  const start = new Date(date)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - (isoDay(start) - 1))
  return start
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

export function sameDate(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  )
}

export function minutesOfDay(date: Date): number {
  return date.getHours() * 60 + date.getMinutes()
}

/** 'HH:mm:ss' or 'HH:mm' to minutes since midnight. */
export function parseClock(clock: string): number {
  const [hours, minutes] = clock.split(':')
  return Number(hours) * 60 + Number(minutes)
}

/** Serializes for the API, which expects a zone-less local date-time. */
export function toLocalDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** The same calendar day at a number of minutes past its midnight. */
export function atMinutes(date: Date, minutes: number): Date {
  const at = new Date(date)
  at.setHours(0, minutes, 0, 0)
  return at
}

export function formatClock(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  return `${String(hours).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

export function formatTime(date: Date): string {
  return formatClock(minutesOfDay(date))
}

/** '2h 30m', '45m'. Durations read as effort, so hours lead and zero minutes are dropped. */
export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes}m`
  return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`
}

/** 'Tue 10:00': enough to place a time within the visible week. */
export function formatDayTime(date: Date): string {
  return `${DAY_NAMES[isoDay(date) - 1]} ${formatTime(date)}`
}

/** 'Fri 18 Sep', or 'Fri 18 Sep 14:00' when the user gave an explicit time. */
export function formatDeadline(value: string, hasTime: boolean): string {
  const date = new Date(value)
  const day = DAY_NAMES[isoDay(date) - 1]
  const stamp = `${day} ${date.getDate()} ${date.toLocaleDateString('en', { month: 'short' })}`
  return hasTime ? `${stamp} ${formatTime(date)}` : stamp
}
