import { useEffect, useLayoutEffect, useRef, useState } from 'react'

interface PopoverProps {
  /** Viewport rectangle the popover opens beside: a block, a clicked slot, or a button. */
  anchor: DOMRect
  /** 'side' opens left or right of the anchor (calendar items); 'below' opens under it (buttons). */
  placement?: 'side' | 'below'
  labelledBy: string
  onClose: () => void
  children: React.ReactNode
}

const GAP = 8
const EDGE = 12
const SHEET_QUERY = '(max-width: 640px)'

/**
 * A non-modal dialog anchored to what opened it. Neither creating nor inspecting a block needs
 * the calendar hidden, so there is no backdrop: the week stays visible and pressing anywhere
 * else closes the popover. Focus moves in on open and returns to where it came from on close.
 * On a phone it becomes a bottom sheet, where an anchored box would not fit.
 */
export function Popover({ anchor, placement = 'side', labelledBy, onClose, children }: PopoverProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState<{ left: number; top: number; origin: string } | null>(null)
  const sheet = window.matchMedia(SHEET_QUERY).matches

  // Held in a ref so a parent re-rendering with a new callback does not re-run the open effect,
  // which would pull focus back to the first field while the user is typing in another.
  const close = useRef(onClose)
  useLayoutEffect(() => {
    close.current = onClose
  })

  // Positioned from its own measured size before paint (so it is never hidden meanwhile: a hidden
  // element cannot take the focus the open effect gives it), and again whenever its content grows
  // (a custom duration field appearing, the block view turning into the edit form).
  useLayoutEffect(() => {
    const element = ref.current
    if (!element || sheet) return
    function place() {
      if (!element) return
      // Layout size, not the painted box: the entrance animation starts scaled down, and a size
      // measured mid-animation would let the finished popover run past the viewport edge.
      const width = element.offsetWidth
      const height = element.offsetHeight
      let left: number
      let origin: string
      if (placement === 'below') {
        left = anchor.right - width
        origin = 'top right'
      } else if (anchor.right + GAP + width <= window.innerWidth - EDGE) {
        left = anchor.right + GAP
        origin = 'top left'
      } else {
        left = anchor.left - GAP - width
        origin = 'top right'
      }
      const top = placement === 'below' ? anchor.bottom + GAP : anchor.top
      setPosition({
        left: Math.max(EDGE, Math.min(left, window.innerWidth - width - EDGE)),
        top: Math.max(EDGE, Math.min(top, window.innerHeight - height - EDGE)),
        origin,
      })
    }
    place()
    const observer = new ResizeObserver(place)
    observer.observe(element)
    return () => observer.disconnect()
  }, [anchor, placement, sheet])

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const element = ref.current
    const first =
      element?.querySelector<HTMLElement>('[data-autofocus]') ??
      element?.querySelector<HTMLElement>('input, select, textarea, button')
    first?.focus({ preventScroll: true })

    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        close.current()
      }
    }
    // Pointer-down rather than click: the same press that closes this may start a drag or open
    // the next popover, and must not wait for a click to complete first.
    function onPointerDown(event: PointerEvent) {
      if (element && !element.contains(event.target as Node)) close.current()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointerDown, true)
      if (opener && opener !== document.body && document.contains(opener)) opener.focus({ preventScroll: true })
    }
  }, [])

  return (
    <div
      ref={ref}
      className="popover"
      data-sheet={sheet || undefined}
      role="dialog"
      aria-labelledby={labelledBy}
      style={
        sheet
          ? undefined
          : {
              left: position?.left ?? 0,
              top: position?.top ?? 0,
              transformOrigin: position?.origin,
            }
      }
    >
      {children}
    </div>
  )
}
