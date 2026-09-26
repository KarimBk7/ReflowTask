import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../api/client'
import { addDays, toLocalDateTime } from './time'

export function useFeed() {
  return useQuery({ queryKey: ['calendar', 'feed'], queryFn: api.calendarFeed })
}

export function useRenewFeed() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: api.renewCalendarFeed,
    onSuccess: (feed) => client.setQueryData(['calendar', 'feed'], feed),
  })
}

export function useDisableFeed() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: api.disableCalendarFeed,
    onSuccess: () => client.setQueryData(['calendar', 'feed'], { path: null }),
  })
}

export function useSources() {
  return useQuery({ queryKey: ['calendar', 'sources'], queryFn: api.calendarSources })
}

export function useBusy(weekStart: Date) {
  const from = toLocalDateTime(weekStart)
  return useQuery({
    queryKey: ['calendar', 'busy', from],
    queryFn: () => api.busy(from, toLocalDateTime(addDays(weekStart, 7))),
  })
}

/**
 * Adding, removing or reading another calendar again replans on the server, so the board refreshes
 * everything a replan touches as well as the calendar data itself.
 */
function useCalendarMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () =>
      Promise.all(
        ['calendar', 'schedule', 'tasks', 'events'].map((key) => client.invalidateQueries({ queryKey: [key] })),
      ),
  })
}

export function useAddSource() {
  return useCalendarMutation(({ name, url }: { name: string; url: string }) => api.addCalendarSource(name, url))
}

export function useRemoveSource() {
  return useCalendarMutation((id: number) => api.removeCalendarSource(id))
}

export function useRefreshSources() {
  return useCalendarMutation(() => api.refreshCalendarSources())
}

/** The feed as a full link: the server only knows its path, the browser knows the address it used. */
export function feedUrl(path: string): string {
  return new URL(path, window.location.origin).toString()
}

/** The same link as webcal://, which calendar apps on Apple devices open as a subscription. */
export function webcalUrl(path: string): string {
  return feedUrl(path).replace(/^https?:/, 'webcal:')
}
