/**
 * The app's icon set: authored SVG on a 16px grid at one 1.5px stroke, so every mark shares a
 * weight with the text beside it.
 */

type IconProps = { className?: string; size?: number }

function Frame({ children, className, size = 16 }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      viewBox="0 0 16 16"
      width={size}
      height={size}
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

/** Pinned: replans leave this block where it is. */
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

export function EditIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M10.5 2.75 13.25 5.5 5.5 13.25H2.75V10.5z" />
      <path d="M9 4.25 11.75 7" />
    </Frame>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M4 4l8 8M12 4l-8 8" />
    </Frame>
  )
}

export function HelpIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <circle cx="8" cy="8" r="6.25" />
      <path d="M6.25 6.25a1.75 1.75 0 1 1 2.5 1.6c-.5.25-.75.6-.75 1.15v.25" />
      <path d="M8 11.5h.01" />
    </Frame>
  )
}

export function ClockIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <circle cx="8" cy="8" r="6.25" />
      <path d="M8 4.5V8l2.25 1.5" />
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

/** Replan: two blocks trading places. */
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

/** Household accounts, admin-only. */
export function UsersIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <circle cx="6" cy="6" r="2.25" />
      <path d="M1.75 13.25c0-2.35 1.9-3.75 4.25-3.75s4.25 1.4 4.25 3.75" />
      <path d="M10.75 5.75a2 2 0 1 1 1.65 3.1" />
      <path d="M12 9.75c1.85.2 2.75 1.45 2.75 3.5" />
    </Frame>
  )
}

/** Log out of the current session. */
export function LogoutIcon(props: IconProps) {
  return (
    <Frame {...props}>
      <path d="M6.5 2.75H3.75a1 1 0 0 0-1 1v8.5a1 1 0 0 0 1 1H6.5" />
      <path d="M10.5 5.25 13.25 8l-2.75 2.75" />
      <path d="M13.25 8H6" />
    </Frame>
  )
}

/** Priority as rising bars, so it reads without colour: one, two or three filled. */
export function PriorityIcon({ level, ...props }: IconProps & { level: 1 | 2 | 3 }) {
  return (
    <Frame {...props}>
      {[0, 1, 2].map((index) => (
        <path
          key={index}
          d={`M${4 + index * 4} ${12.5}V${9.5 - index * 3}`}
          opacity={index < level ? 1 : 0.28}
        />
      ))}
    </Frame>
  )
}
