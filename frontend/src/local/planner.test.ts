import { describe, expect, it } from 'vitest'

import type { Priority } from '../api/types'
import { type DailyWindow, type PlanConfig, type PlanTask, type Planned, type Slot, plan } from './planner'
import { DAY, type Wall, isoDay, wall } from './wall'

/**
 * The scheduler's contract, mirrored from the server's SchedulePlannerTests so the device and the
 * server plan alike. Same fixtures, same expectations; only the syntax differs.
 */

const MONDAY = wall('2026-09-07T00:00')
const at = (dayOffset: number, hour: number, minute = 0): Wall => MONDAY + dayOffset * DAY + hour * 60 + minute
const window = (day: number, start: number, end: number): DailyWindow => ({ day, start: start * 60, end: end * 60 })
const WEEKDAYS = [1, 2, 3, 4, 5]

/** Mon-Fri 09:00-18:00 with a 12:00-13:00 lunch: 480 usable minutes per working day. */
function config(horizonDays = 14, bufferMinutes = 0, lunch = true): PlanConfig {
  return {
    workingHours: WEEKDAYS.map((day) => window(day, 9, 18)),
    personalHours: [],
    blockedPeriods: lunch ? WEEKDAYS.map((day) => window(day, 12, 13)) : [],
    horizonDays,
    minChunkMinutes: 30,
    bufferMinutes,
  }
}

const task = (id: number, minutes: number, deadline: Wall | null, priority: Priority, extra: Partial<PlanTask> = {}): PlanTask => ({
  id,
  minutes,
  deadline,
  priority,
  notBefore: null,
  profile: 'WORK',
  after: null,
  ...extra,
})
const block = (taskId: number, start: Wall, end: Wall): Planned => ({ taskId, start, end })
const slot = (start: Wall, end: Wall): Slot => ({ start, end })
const total = (planned: Planned[]) => planned.reduce((sum, piece) => sum + piece.end - piece.start, 0)
const sorted = (planned: Planned[]) => [...planned].sort((a, b) => a.taskId - b.taskId || a.start - b.start)

describe('planner', () => {
  it('uses real weekdays in its fixture', () => {
    expect(isoDay(MONDAY)).toBe(1)
  })

  describe('placement basics', () => {
    it('places work in the earliest free slot', () => {
      expect(plan([task(1, 120, null, 'MEDIUM')], [], config(), at(0, 9))).toEqual([block(1, at(0, 9), at(0, 11))])
    })

    it('rounds the first start up to a quarter hour', () => {
      expect(plan([task(1, 60, null, 'MEDIUM')], [], config(), at(0, 10, 37))).toEqual([block(1, at(0, 10, 45), at(0, 11, 45))])
    })

    it('places nothing before a task\'s earliest start', () => {
      const nextWeeks = task(1, 60, at(4, 23, 59), 'HIGH', { notBefore: at(2, 14, 30) })
      expect(plan([nextWeeks, task(2, 60, null, 'LOW')], [], config(), at(0, 9))).toEqual([
        block(1, at(2, 14, 30), at(2, 15, 30)),
        block(2, at(0, 9), at(0, 10)),
      ])
    })

    it('places nothing when there is nothing to place', () => {
      expect(plan([], [], config(), at(0, 9))).toEqual([])
    })
  })

  describe('work and personal time', () => {
    const withPersonal = (...personal: DailyWindow[]): PlanConfig => ({ ...config(), personalHours: personal })
    const personal = (id: number, minutes: number) => task(id, minutes, null, 'MEDIUM', { profile: 'PERSONAL' })

    it('puts a personal task into personal time, not the free working day', () => {
      const planned = plan([personal(1, 60), task(2, 60, null, 'LOW')], [], withPersonal(window(1, 19, 21)), at(0, 9))
      expect(sorted(planned)).toEqual([block(1, at(0, 19), at(0, 20)), block(2, at(0, 9), at(0, 10))])
    })

    it('never uses overlapping hours twice', () => {
      const work = task(1, 60, at(0, 10), 'HIGH')
      const planned = plan([personal(2, 60), work], [], withPersonal(window(1, 9, 11)), at(0, 9))
      expect(sorted(planned)).toEqual([block(1, at(0, 9), at(0, 10)), block(2, at(0, 10), at(0, 11))])
    })

    it('puts a personal task into working hours when no personal time is set', () => {
      expect(plan([personal(1, 60)], [], config(), at(0, 9))).toEqual([block(1, at(0, 9), at(0, 10))])
    })
  })

  describe('waiting for another task', () => {
    it('lets an urgent task pull forward the task it waits for', () => {
      const groundwork = task(1, 60, null, 'LOW')
      const urgent = task(2, 60, at(0, 12), 'HIGH', { after: 1 })
      const other = task(3, 60, at(0, 17), 'MEDIUM')
      expect(sorted(plan([other, urgent, groundwork], [], config(), at(0, 9)))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(2, at(0, 10), at(0, 11)),
        block(3, at(0, 11), at(0, 12)),
      ])
    })

    it('lets a task wait when what it waits for finds no time', () => {
      const groundwork = task(1, 20 * 60, null, 'MEDIUM')
      const follow = task(2, 60, null, 'MEDIUM', { after: 1 })
      const planned = plan([follow, groundwork], [], config(2), at(0, 9))
      expect(planned.every((piece) => piece.taskId === 1)).toBe(true)
    })
  })

  describe('ordering', () => {
    it('gives the earlier deadline the earlier slot', () => {
      const planned = plan([task(1, 60, at(0, 17), 'MEDIUM'), task(2, 60, at(0, 11), 'MEDIUM')], [], config(), at(0, 9))
      expect(planned.map((piece) => piece.taskId)).toEqual([2, 1])
    })

    it('breaks a deadline tie by priority', () => {
      const planned = plan([task(1, 60, at(0, 17), 'LOW'), task(2, 60, at(0, 17), 'HIGH')], [], config(), at(0, 9))
      expect(planned[0].taskId).toBe(2)
    })

    it('plans a task without a deadline after dated work', () => {
      const planned = plan([task(1, 60, null, 'HIGH'), task(2, 60, at(0, 17), 'LOW')], [], config(), at(0, 9))
      expect(planned.map((piece) => piece.taskId)).toEqual([2, 1])
    })

    it('always plans identical tasks in the same order', () => {
      const first = task(7, 60, null, 'MEDIUM')
      const second = task(9, 60, null, 'MEDIUM')
      const forwards = plan([first, second], [], config(), at(0, 9))
      expect(plan([second, first], [], config(), at(0, 9))).toEqual(forwards)
      expect(forwards[0].taskId).toBe(7)
    })
  })

  describe('splitting', () => {
    it('splits work too long for one day across days', () => {
      const planned = plan([task(1, 600, null, 'MEDIUM')], [], config(), at(0, 9))
      expect(planned).toEqual([block(1, at(0, 9), at(0, 12)), block(1, at(0, 13), at(0, 18)), block(1, at(1, 9), at(1, 11))])
      expect(total(planned)).toBe(600)
    })

    it('plans only the unpinned remainder, kept whole in the afternoon', () => {
      const planned = plan([task(1, 180, null, 'MEDIUM')], [slot(at(0, 9), at(0, 11))], config(), at(0, 9))
      expect(planned).toEqual([block(1, at(0, 13), at(0, 16))])
    })
  })

  describe('obstacles', () => {
    it('never schedules over lunch', () => {
      expect(plan([task(1, 360, null, 'MEDIUM')], [], config(), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 12)),
        block(1, at(0, 13), at(0, 16)),
      ])
    })

    it('never overlaps a pinned block', () => {
      expect(plan([task(1, 120, null, 'MEDIUM')], [slot(at(0, 10), at(0, 11))], config(), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(1, at(0, 11), at(0, 12)),
      ])
    })

    it('never places anything outside working hours or on a weekend', () => {
      const planned = plan([task(1, 480 * 7, null, 'MEDIUM')], [], config(), at(0, 9))
      expect(planned.length).toBeGreaterThan(0)
      for (const piece of planned) {
        expect([6, 7]).not.toContain(isoDay(piece.start))
        const startOfPieceDay = Math.floor(piece.start / DAY) * DAY
        expect(piece.start - startOfPieceDay).toBeGreaterThanOrEqual(9 * 60)
        expect(piece.end - startOfPieceDay).toBeLessThanOrEqual(18 * 60)
        const straddlesLunch = piece.start - startOfPieceDay < 12 * 60 && piece.end - startOfPieceDay > 12 * 60
        expect(straddlesLunch).toBe(false)
      }
    })

    it('skips weekends entirely', () => {
      expect(isoDay(at(5, 0))).toBe(6)
      expect(plan([task(1, 60, null, 'MEDIUM')], [], config(4), at(5, 9))).toEqual([block(1, at(7, 9), at(7, 10))])
    })
  })

  describe('chunking', () => {
    it('skips a slot too small to be worth splitting into', () => {
      expect(plan([task(1, 60, null, 'MEDIUM')], [slot(at(0, 9, 20), at(0, 18))], config(), at(0, 9))).toEqual([
        block(1, at(1, 9), at(1, 10)),
      ])
    })

    it('keeps a task whole in a later gap instead of chopping it', () => {
      const pinned = [slot(at(0, 9, 30), at(0, 10)), slot(at(0, 11), at(0, 12))]
      expect(plan([task(1, 60, null, 'MEDIUM')], pinned, config(), at(0, 9))).toEqual([block(1, at(0, 10), at(0, 11))])
    })

    it('never starts a whole task later than splitting would', () => {
      expect(plan([task(1, 120, null, 'MEDIUM')], [slot(at(0, 10), at(0, 11))], config(), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(1, at(0, 11), at(0, 12)),
      ])
    })

    it('does not keep a task whole when that would miss a deadline splitting meets', () => {
      const pinned = [slot(at(0, 9, 30), at(0, 10)), slot(at(0, 11), at(0, 12))]
      expect(plan([task(1, 60, at(0, 10, 40), 'MEDIUM')], pinned, config(), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 9, 30)),
        block(1, at(0, 10), at(0, 10, 30)),
      ])
    })

    it('leaves a skipped gap for other work', () => {
      const pinned = [slot(at(0, 9, 30), at(0, 10)), slot(at(0, 11), at(0, 12))]
      expect(plan([task(1, 60, at(0, 17), 'MEDIUM'), task(2, 30, null, 'LOW')], pinned, config(), at(0, 9))).toEqual([
        block(1, at(0, 10), at(0, 11)),
        block(2, at(0, 9), at(0, 9, 30)),
      ])
    })

    it('lets a short final remainder use a small slot', () => {
      expect(plan([task(1, 20, null, 'MEDIUM')], [slot(at(0, 9, 20), at(0, 18))], config(), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 9, 20)),
      ])
    })
  })

  describe('deadlines and capacity', () => {
    it('still places a task whose deadline has passed', () => {
      expect(plan([task(1, 60, at(-3, 12), 'MEDIUM')], [], config(), at(0, 9))).toEqual([block(1, at(0, 9), at(0, 10))])
    })

    it('places what fits when the horizon runs out', () => {
      expect(total(plan([task(1, 600, null, 'MEDIUM')], [], config(1), at(0, 9)))).toBe(480)
    })

    it('places nothing, rather than failing, without working hours', () => {
      const none: PlanConfig = { ...config(), workingHours: [], blockedPeriods: [] }
      expect(plan([task(1, 60, null, 'MEDIUM')], [], none, at(0, 9))).toEqual([])
    })

    it('does not offer capacity already past', () => {
      expect(plan([task(1, 90, null, 'MEDIUM')], [], config(), at(0, 17, 30))).toEqual([block(1, at(1, 9), at(1, 10, 30))])
    })
  })

  describe('buffer time', () => {
    const buffered = (minutes: number) => config(14, minutes, false)

    it('separates consecutive tasks', () => {
      expect(plan([task(1, 60, null, 'HIGH'), task(2, 60, null, 'LOW')], [], buffered(15), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(2, at(0, 10, 15), at(0, 11, 15)),
      ])
    })

    it('places tasks back to back without one', () => {
      expect(plan([task(1, 60, null, 'HIGH'), task(2, 60, null, 'LOW')], [], buffered(0), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(2, at(0, 10), at(0, 11)),
      ])
    })

    it('is kept on both sides of a fixed block', () => {
      const fixed = [slot(at(0, 10), at(0, 11))]
      expect(plan([task(1, 45, null, 'HIGH'), task(2, 30, null, 'LOW')], fixed, buffered(15), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 9, 45)),
        block(2, at(0, 11, 15), at(0, 11, 45)),
      ])
    })

    it('pushes work onward when it leaves too little room', () => {
      const restOfMonday = [slot(at(0, 10, 30), at(0, 18))]
      expect(plan([task(1, 60, null, 'HIGH'), task(2, 60, null, 'LOW')], restOfMonday, buffered(15), at(0, 9))).toEqual([
        block(1, at(0, 9), at(0, 10)),
        block(2, at(1, 9), at(1, 10)),
      ])
    })

    it('never splits one task against itself', () => {
      expect(plan([task(1, 180, null, 'MEDIUM')], [], buffered(15), at(0, 9))).toEqual([block(1, at(0, 9), at(0, 12))])
    })
  })

  describe('wall-clock semantics', () => {
    it('does not shift working hours over a daylight-saving change', () => {
      const dstSunday = wall('2026-03-29T00:00')
      expect(isoDay(dstSunday)).toBe(7)
      const planned = plan([task(1, 480, null, 'MEDIUM')], [], config(2), dstSunday + 12 * 60)
      expect(planned[0].start).toBe(dstSunday + DAY + 9 * 60)
      expect(total(planned)).toBe(480)
    })
  })
})
