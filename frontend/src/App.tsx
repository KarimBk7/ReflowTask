import { useQuery } from '@tanstack/react-query'

import { api } from './api/client'
import { t } from './i18n/en'

/**
 * Placeholder. The week view replaces this once the visual direction is locked; it exists
 * now only so the project builds and the API wiring is provably live.
 */
export default function App() {
  const tasks = useQuery({ queryKey: ['tasks'], queryFn: api.listTasks })

  return (
    <main>
      <h1>{t('app.name')}</h1>
      {tasks.isPending && <p>Loading…</p>}
      {tasks.isError && <p>{t('error.offline')}</p>}
      {tasks.data && (
        <ul>
          {tasks.data.map((task) => (
            <li key={task.id}>
              {task.title} — {task.estimatedMinutes} min — {t(`priority.${task.priority}`)}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
