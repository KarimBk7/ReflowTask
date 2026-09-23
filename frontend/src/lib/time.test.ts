import { describe, expect, it } from 'vitest'

import {
  addDays,
  formatClock,
  formatDeadline,
  formatDuration,
  isoDay,
  monthGridDays,
  parseClock,
  sameDate,
  startOfMonth,
  startOfWeek,
  toLocalDateTime,
} from './time'

const day = (y: number, m: number, d: number) => new Date(y, m - 1, d)

describe('weeks', () => {
  it('numbers Monday 1 and Sunday 7', () => {
    expect(isoDay(day(2026, 9, 21))).toBe(1)
    expect(isoDay(day(2026, 9, 27))).toBe(7)
  })

  it('starts the week on the Monday, midnight, for any day in it', () => {
    for (let d = 21; d <= 27; d++) {
      const start = startOfWeek(new Date(2026, 8, d, 15, 30))
      expect(toLocalDateTime(start)).toBe('2026-09-21T00:00:00')
    }
  })

  it('adds days across month and year ends', () => {
    expect(toLocalDateTime(addDays(day(2026, 12, 30), 3))).toBe('2027-01-02T00:00:00')
    expect(toLocalDateTime(addDays(day(2026, 3, 1), -1))).toBe('2026-02-28T00:00:00')
  })
})

describe('month grid', () => {
  it('always starts on a Monday and ends on a Sunday', () => {
    for (let month = 1; month <= 12; month++) {
      const days = monthGridDays(day(2026, month, 1))
      expect(isoDay(days[0])).toBe(1)
      expect(isoDay(days[days.length - 1])).toBe(7)
      expect(days.length % 7).toBe(0)
    }
  })

  it('contains every day of the month exactly once, in order', () => {
    const days = monthGridDays(day(2026, 9, 1))
    const inMonth = days.filter((d) => d.getMonth() === 8)
    expect(inMonth.map((d) => d.getDate())).toEqual(Array.from({ length: 30 }, (_, i) => i + 1))
    for (let i = 1; i < days.length; i++) expect(days[i].getTime()).toBeGreaterThan(days[i - 1].getTime())
  })

  it('uses four weeks for a 28-day February that starts on a Monday', () => {
    // 1 Feb 2027 is a Monday.
    expect(monthGridDays(day(2027, 2, 1))).toHaveLength(28)
  })

  it('uses six weeks when a 31-day month starts late in the week', () => {
    // 1 Aug 2026 is a Saturday: 5 leading days + 31 = 36 -> 6 weeks.
    expect(monthGridDays(day(2026, 8, 1))).toHaveLength(42)
  })

  it('pads with the neighbouring months', () => {
    const days = monthGridDays(day(2026, 9, 1))
    expect(toLocalDateTime(days[0])).toBe('2026-08-31T00:00:00')
    expect(toLocalDateTime(days[days.length - 1])).toBe('2026-10-04T00:00:00')
  })

  it('handles a month that starts on a Sunday', () => {
    // 1 Mar 2026 is a Sunday: 6 leading days + 31 = 37 -> 6 weeks.
    const days = monthGridDays(day(2026, 3, 1))
    expect(days).toHaveLength(42)
    expect(sameDate(days[6], day(2026, 3, 1))).toBe(true)
  })

  it('finds the first of the month from any day', () => {
    expect(toLocalDateTime(startOfMonth(day(2026, 9, 23)))).toBe('2026-09-01T00:00:00')
  })
})

describe('formatting', () => {
  it('serializes a zone-less local date-time with padding', () => {
    expect(toLocalDateTime(new Date(2026, 0, 5, 7, 4, 9))).toBe('2026-01-05T07:04:09')
  })

  it('reads clocks and formats minutes back', () => {
    expect(parseClock('09:30:00')).toBe(570)
    expect(parseClock('18:00')).toBe(1080)
    expect(formatClock(570)).toBe('09:30')
  })

  it('shows durations as effort', () => {
    expect(formatDuration(45)).toBe('45m')
    expect(formatDuration(120)).toBe('2h')
    expect(formatDuration(150)).toBe('2h 30m')
  })

  it('shows a deadline with its time only when the user gave one', () => {
    expect(formatDeadline('2026-09-18T23:59:00', false)).toBe('Fri 18 Sep')
    expect(formatDeadline('2026-09-18T14:00:00', true)).toBe('Fri 18 Sep 14:00')
  })
})
