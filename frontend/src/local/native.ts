import type { QueryClient } from '@tanstack/react-query'
import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { Directory, Encoding, Filesystem } from '@capacitor/filesystem'
import { LocalNotifications } from '@capacitor/local-notifications'
import { Share } from '@capacitor/share'

import { t } from '../i18n/en'
import { type Db, setAfterSave, setDocumentStore } from './db'
import { setSaveFile } from './files'
import { toLocalDate, wall, wallNow } from './wall'

/**
 * What only a phone has: the app's own files, the share sheet, the back button, coming back to the
 * foreground, and reminders. Loaded only in the app build and only on a device; in a browser the
 * app keeps its development defaults (local storage, downloads).
 */

const FILE = 'reflowtask.json'
const SPARE = 'reflowtask.previous.json'

/** Minutes before a block that its reminder goes off, as the calendar feed on the server does. */
const REMIND_BEFORE = 10

/** Android keeps a limited number of scheduled alarms per app; the next few days are plenty. */
const MAX_REMINDERS = 40

async function readFile(path: string): Promise<string | null> {
  try {
    const { data } = await Filesystem.readFile({ path, directory: Directory.Data, encoding: Encoding.UTF8 })
    const text = typeof data === 'string' ? data : await data.text()
    JSON.parse(text)
    return text
  } catch {
    return null
  }
}

export async function setUpDevice(client: QueryClient) {
  if (!Capacitor.isNativePlatform()) return

  // Written twice, spare first: an app killed halfway through a write still finds a whole copy.
  setDocumentStore({
    read: async () => (await readFile(FILE)) ?? (await readFile(SPARE)),
    write: async (json) => {
      await Filesystem.writeFile({ path: SPARE, directory: Directory.Data, encoding: Encoding.UTF8, data: json })
      await Filesystem.writeFile({ path: FILE, directory: Directory.Data, encoding: Encoding.UTF8, data: json })
    },
  })

  // A web view cannot download; backups go out through the share sheet (Drive, mail, Files).
  setSaveFile(async (name, content) => {
    const { uri } = await Filesystem.writeFile({ path: name, directory: Directory.Cache, encoding: Encoding.UTF8, data: content })
    await Share.share({ title: name, files: [uri] })
  })

  // Back in the foreground: read afresh, which replans if the plan went stale meanwhile.
  await App.addListener('resume', () => void client.invalidateQueries())

  // The back button closes what is open, and otherwise leaves the app as Android expects.
  await App.addListener('backButton', () => {
    if (document.querySelector('.popover')) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    } else {
      void App.minimizeApp()
    }
  })

  const { display } = await LocalNotifications.checkPermissions()
  if (display === 'prompt' || display === 'prompt-with-rationale') await LocalNotifications.requestPermissions()
  let scheduling = Promise.resolve()
  setAfterSave((db) => {
    scheduling = scheduling.then(() => scheduleReminders(db)).catch(() => undefined)
  })
}

/** Replaces the pending reminders with one for each of the next planned blocks. */
async function scheduleReminders(db: Db) {
  const { display } = await LocalNotifications.checkPermissions()
  if (display !== 'granted') return
  const pending = await LocalNotifications.getPending()
  if (pending.notifications.length > 0) {
    await LocalNotifications.cancel({ notifications: pending.notifications.map((item) => ({ id: item.id })) })
  }
  const now = wallNow()
  const titles = new Map(db.tasks.map((task) => [task.id, task.title]))
  const upcoming = db.blocks
    .filter((block) => block.state === 'PLANNED' && wall(block.startAt) - REMIND_BEFORE > now)
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .slice(0, MAX_REMINDERS)
  if (upcoming.length === 0) return
  await LocalNotifications.schedule({
    notifications: upcoming.map((block) => ({
      id: block.id % 2_147_483_647,
      title: titles.get(block.taskId) ?? t('app.name'),
      body: `${t('reminder.startsAt')} ${block.startAt.slice(11, 16)}`,
      schedule: { at: toLocalDate(wall(block.startAt) - REMIND_BEFORE), allowWhileIdle: true },
    })),
  })
}
