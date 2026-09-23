import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { ApiError } from './api/client'
import App from './App'
import './index.css'

/**
 * Server state is the whole app: a replan rewrites the schedule underneath the UI, so
 * queries are invalidated after every mutation rather than patched optimistically. Nothing
 * here guesses what the scheduler decided - it asks.
 */
/**
 * A 401 from anywhere but the login itself means the session ended (expired, or the account was
 * removed or reset). Marking the user as gone sends the app back to the login screen instead of
 * leaving a board full of "cannot reach the server".
 */
function endSessionOn401(error: unknown) {
  if (error instanceof ApiError && error.status === 401) queryClient.setQueryData(['auth', 'me'], null)
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: endSessionOn401 }),
  mutationCache: new MutationCache({ onError: endSessionOn401 }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
