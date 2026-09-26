import { vi } from 'vitest'

// Node reports the machine's language as the browser's; tests read English wherever they run.
vi.stubGlobal('navigator', { language: 'en-GB' })
