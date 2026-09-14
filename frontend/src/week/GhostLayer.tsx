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
            const boxWidth = columnWidth * 0.82
            // Replanning compacts the schedule, so a vacated slot is usually taken again
            // straight away. Outlining an occupied slot would just scribble over the strip
            // that now lives there; the arrow alone carries the move in that case.
            const slotFree = !occupied.some(
              (block) => block.start <= ghost.from && block.end > ghost.from,
            )

            return (
              <g key={ghost.key} className="ghost">
                {slotFree && (
                  <rect
                    x={from.x - boxWidth / 2}
                    y={from.y}
                    width={boxWidth}
                    height={Math.max(18, size.height * 0.03)}
                    rx="1"
                    className="ghost-box"
                  />
                )}
                {to && (
                  <path
                    d={curve(from, to)}
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
 * A hand-drawn arc rather than a straight line: the mark reads as pencil on enamel, and a
 * bow keeps the path clear of the strips between its ends.
 */
function curve(from: { x: number; y: number }, to: { x: number; y: number }): string {
  const midX = (from.x + to.x) / 2
  const midY = (from.y + to.y) / 2
  const bow = Math.min(60, Math.hypot(to.x - from.x, to.y - from.y) * 0.22)
  const normalX = -(to.y - from.y)
  const normalY = to.x - from.x
  const length = Math.hypot(normalX, normalY) || 1
  const controlX = midX + (normalX / length) * bow
  const controlY = midY + (normalY / length) * bow
  return `M ${from.x} ${from.y + 8} Q ${controlX} ${controlY} ${to.x} ${to.y}`
}
