/**
 * The board's icon set: authored SVG at one stroke weight, sized to the legend's cap height.
 * Small enough to read as punched or stencilled marks on the strip rather than as UI chrome.
 */

type IconProps = { className?: string }

function Frame({ children, className }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      viewBox="0 0 16 16"
      width="14"
      height="14"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      {children}
    </svg>
  )
}

/** Pinned: a magnet strip the replan must not move. */
export function PinIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M6 1.75h4l-.6 3.4 2.1 2.1H4.5l2.1-2.1z" />
      <path d="M8 7.25v7" />
    </Frame>
  )
}

export function CheckIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M2.75 8.5 6.25 12l7-8" />
    </Frame>
  )
}

/** At risk: scheduled past its deadline. */
export function RiskIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M8 2.25 15 14H1z" />
      <path d="M8 6.5v3.25" />
      <path d="M8 11.75h.01" />
    </Frame>
  )
}

export function PlusIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M8 3v10M3 8h10" />
    </Frame>
  )
}

export function TrashIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M2.75 4.25h10.5" />
      <path d="M6 4.25V2.75h4v1.5" />
      <path d="M4.25 4.25 5 13.25h6l.75-9" />
    </Frame>
  )
}

export function ChevronIcon({ direction = 'left', ...props }: IconProps & { direction?: 'left' | 'right' }) {
  return (
    <Frame {...props}>
      <path d={direction === 'left' ? 'M10 3 5 8l5 5' : 'M6 3l5 5-5 5'} />
    </Frame>
  )
}

/** The replan lever's mark: a strip lifted and re-seated. */
export function ReflowIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M2 4.5h7.5" />
      <path d="M7.5 2.25 9.75 4.5 7.5 6.75" />
      <path d="M14 11.5H6.5" />
      <path d="M8.5 9.25 6.25 11.5l2.25 2.25" />
    </Frame>
  )
}
