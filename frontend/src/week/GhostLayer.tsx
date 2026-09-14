import { useEffect, useRef, useState } from 'react'

import type { Ghost } from '../lib/board'
import { minutesOfDay, sameDate } from '../lib/time'

interface GhostLayerProps {
  ghosts: Ghost[]
  days: number[]
  weekStart: Date
  windowStart: number
  windowEnd: number
  /** Used to suppress a vacated-slot outline that something has already taken. */
  occupied: { start: Date; end: Date }[]
}

/**
 * The chinagraph marks: where a strip used to be, and an arrow to where it went.
 *
 * This is the board's answer to "what changed since I last looked", so it is drawn in the
 * annotation hand rather than shown as a notification. The layer measures itself and works
 * in pixel space: a normalised viewBox stretched to the board would shear every arrowhead.
 */
export function GhostLayer({
  ghosts,
  days,
  weekStart,
  windowStart,
  windowEnd,
  occupied,
}: GhostLayerProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })

  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) => {
      setSize({ width: entry.contentRect.width, height: entry.contentRect.height })
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const span = windowEnd - windowStart

  /** Board coordinates for a moment in time, or null when it falls outside the drawn week. */
  function locate(at: Date): { x: number; y: number } | null {
    if (size.width === 0) return null
    // Matched by calendar date, not just weekday, so a ghost from an adjacent week cannot
    // land on the column of the same weekday in the week being drawn.
    const dayIndex = days.findIndex((day) => {
      const date = new Date(weekStart)
      date.setDate(date.getDate() + (day - 1))
      return sameDate(date, at)
    })
    if (dayIndex < 0) return null

    const columnWidth = size.width / days.length
    const minutes = Math.min(Math.max(minutesOfDay(at) - windowStart, 0), span)
    return {
      x: dayIndex * columnWidth + columnWidth / 2,
      y: (minutes / span) * size.height,
    }
  }

  return (
    <div className="ghost-layer" ref={ref} aria-hidden="true">
      {size.width > 0 && (
        <svg width={size.width} height={size.height} className="ghost-svg">
          <defs>
            <marker
              id="ghost-arrow"
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M1 1 9 5 1 9" fill="none" stroke="currentColor" strokeWidth="1.6" />
            </marker>
          </defs>

          {ghosts.map((ghost) => {
            const from = locate(ghost.from)
            const to = ghost.to ? locate(ghost.to) : null
            if (!from) return null

            const columnWidth = size.width / days.length
            // The vacated position is always marked. Replanning compacts the schedule, so
            // the slot is usually taken again straight away - if an occupied slot went
            // unmarked, the signature mark would be absent in the normal case and the
            // arrows would appear to come from nowhere. Wax pencil goes over the strip.
            const occupiedNow = occupied.some(
              (block) => block.start <= ghost.from && block.end > ghost.from,
            )
            // Scored against the column's leading edge rather than blanketing it, so an
            // outline over an occupied slot annotates the strip instead of hiding it.
            const left = from.x - columnWidth / 2 + 3
            const height = Math.max(16, size.height * 0.028)

            return (
              <g key={ghost.key} className="ghost">
                <path
                  d={`M ${left + 10} ${from.y} H ${left} V ${from.y + height} H ${left + 10}`}
                  className="ghost-box"
                  data-over-strip={occupiedNow || undefined}
                />
                {to && (
                  <path
                    d={curve(from, to, left, columnWidth)}
                    className="ghost-arrow"
                    markerEnd="url(#ghost-arrow)"
                  />
                )}
              </g>
            )
          })}
        </svg>
      )}
    </div>
  )
}

/**
 * A hand-drawn arc rather than a straight line.
 *
 * Both ends sit on the column's leading edge beside the strips, never across them: routed
 * through the middle, the stroke draws itself straight through the strip's own times and
 * reads as crossed-out numerals.
 */
function curve(
  from: { x: number; y: number },
  to: { x: number; y: number },
  edgeX: number,
  columnWidth: number,
): string {
  const sameColumn = Math.abs(to.x - from.x) < columnWidth / 2
  if (sameColumn) {
    // A move within one day: bow out into the column's own margin rather than down
    // through every strip between the two ends.
    const gutter = edgeX - 7
    const midY = (from.y + to.y) / 2
    return `M ${edgeX} ${from.y + 4} Q ${gutter} ${midY} ${edgeX} ${to.y}`
  }

  // Across days: land on the destination column's leading edge, for the same reason.
  const endX = to.x - columnWidth / 2 + 3
  const midX = (edgeX + endX) / 2
  const midY = (from.y + to.y) / 2
  const bow = Math.min(50, Math.hypot(endX - edgeX, to.y - from.y) * 0.2)
  return `M ${edgeX} ${from.y + 4} Q ${midX} ${midY - bow} ${endX} ${to.y}`
}
