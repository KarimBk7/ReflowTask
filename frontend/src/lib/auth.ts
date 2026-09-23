import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, api } from '../api/client'

/**
 * Cookies ride automatically on `fetch`'s default same-origin credentials, so nothing here
 * touches the session token directly - it is only ever read and written by the server.
 */
export function useAuthStatus() {
  return useQuery({ queryKey: ['auth', 'status'], queryFn: api.authStatus })
}

/**
 * 401 while logged out is the expected steady state, so it resolves to null rather than an error:
 * a failed refetch would keep the previous user's data, and the app would stay on the board.
 */
export function useMe() {
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () =>
      api.me().catch((error) => {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }),
    retry: false,
  })
}

function useAuthMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => client.invalidateQueries({ queryKey: ['auth'] }),
  })
}

export function useBootstrap() {
  return useAuthMutation(({ username, password }: { username: string; password: string }) =>
    api.bootstrap(username, password),
  )
}

export function useLogin() {
  return useAuthMutation(({ username, password }: { username: string; password: string }) =>
    api.login(username, password),
  )
}

export function useLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: api.logout,
    onSuccess: () => {
      // A fresh login as someone else must never show the previous user's tasks for a frame.
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
      client.setQueryData(['auth', 'me'], null)
    },
  })
}

export function useChangePassword() {
  return useAuthMutation((password: string) => api.changePassword(password))
}

export function useUsers() {
  return useQuery({ queryKey: ['auth', 'users'], queryFn: api.listUsers })
}

export function useCreateUser() {
  return useAuthMutation(({ username, password }: { username: string; password: string }) =>
    api.createUser(username, password),
  )
}

export function useDeleteUser() {
  return useAuthMutation((id: number) => api.deleteUser(id))
}
