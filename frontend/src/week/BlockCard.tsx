import type { Block } from '../api/types'
import { CheckIcon, PinIcon, PriorityIcon, RiskIcon } from '../design/Icon'
import { t } from '../i18n/en'
import { formatClock, formatDayTime, formatDuration } from '../lib/time'

/** A block laid out on the grid: which column, and its minutes within that day. */
export interface Placed {
  key: string
  block: Block
  dayIndex: number
  start: number
  end: number
  dragging: boolean
}

interface BlockCardProps {
  item: Placed
  movedFrom: Date | null
  selected: boolean
  editable: boolean
  onOpen: (block: Block, anchor: DOMRect) => void
  onDragStart: (item: Placed, mode: 'move' | 'resize', event: React.PointerEvent) => void
  onKeyboardMove: (item: Placed, event: React.KeyboardEvent) => void
}

const LEVEL = { LOW: 1, MEDIUM: 2, HIGH: 3 } as const

/**
 * A time block on the calendar: title and time, and nothing to press but the block itself.
 * Opening it shows the details and actions; dragging it moves it; its bottom edge resizes it.
 */
export function BlockCard({ item, movedFrom, selected, editable, onOpen, onDragStart, onKeyboardMove }: BlockCardProps) {
  const { block, start, end } = item
  const minutes = end - start
  const visible = Math.min(end, 24 * 60) - start
  const done = block.status === 'DONE'
  const size = visible < 30 ? 'xs' : visible < 45 ? 'sm' : visible < 80 ? 'md' : 'lg'
  const range = `${formatClock(start)} – ${formatClock(end % (24 * 60))}`

  const label = [
    block.taskTitle,
    range,
    formatDuration(minutes),
    block.pinned && t('state.pinned'),
    block.atRisk && t('state.atRisk'),
    done && t('status.DONE'),
    movedFrom && `${t('state.movedFrom')} ${formatDayTime(movedFrom)}`,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <div
      className="block"
      data-key={item.key}
      data-size={size}
      data-priority={block.priority}
      data-risk={block.atRisk || undefined}
      data-done={done || undefined}
      data-pinned={block.pinned || undefined}
      data-moved={movedFrom ? true : undefined}
      data-dragging={item.dragging || undefined}
      data-selected={selected || undefined}
      data-editable={editable || undefined}
      style={{
        top: `calc(${start} * var(--px-per-min))`,
        height: `calc(${Math.max(visible, 15)} * var(--px-per-min) - 2px)`,
      }}
    >
      <button
        type="button"
        className="block-body"
        aria-label={label}
        aria-describedby={editable ? 'block-keys-hint' : undefined}
        onClick={(event) => onOpen(block, event.currentTarget.getBoundingClientRect())}
        onPointerDown={(event) => onDragStart(item, 'move', event)}
        onKeyDown={(event) => onKeyboardMove(item, event)}
      >
        <span className="block-line">
          <span className="block-title">{block.taskTitle}</span>
          {(size === 'xs' || size === 'sm') && <span className="block-start">{formatClock(start)}</span>}
          <span className="block-marks" aria-hidden="true">
            {block.atRisk && <RiskIcon size={12} className="mark-risk" />}
            {block.pinned && <PinIcon size={12} />}
            {done && <CheckIcon size={12} />}
            {size !== 'xs' && <PriorityIcon size={12} level={LEVEL[block.priority]} />}
          </span>
        </span>

        {(size === 'md' || size === 'lg') && (
          <span className="block-meta" aria-hidden="true">
            {range}
            {size === 'md' && movedFrom && (
              <span className="block-from">
                {' · '}
                {t('state.movedFromShort')} {formatDayTime(movedFrom)}
              </span>
            )}
          </span>
        )}

        {size === 'lg' && movedFrom && (
          <span className="block-chip" aria-hidden="true">
            {t('state.movedFrom')} {formatDayTime(movedFrom)}
          </span>
        )}
      </button>

      {editable && (
        <span
          className="block-resize"
          aria-hidden="true"
          onPointerDown={(event) => {
            event.stopPropagation()
            onDragStart(item, 'resize', event)
          }}
        />
      )}
    </div>
  )
}
