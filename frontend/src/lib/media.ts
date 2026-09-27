import { useSyncExternalStore } from 'react'

/** Whether a media query matches now, re-rendering when that changes (a phone turning, a window resized). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => window.matchMedia(query).matches,
  )
}

/** Where the layout turns into the phone one; the popovers turn into bottom sheets at the same width. */
export const PHONE_QUERY = '(max-width: 640px)'
