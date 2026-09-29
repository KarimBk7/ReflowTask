/**
 * Saving and opening files for backups. In a browser that is a download and a file picker; the
 * phone shell replaces saveFile with the system's share sheet, since an Android web view does not
 * download.
 */

export type SaveFile = (name: string, content: string, type: string) => Promise<void>

let save: SaveFile = async (name, content, type) => {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function setSaveFile(next: SaveFile) {
  save = next
}

export function saveFile(name: string, content: string, type: string): Promise<void> {
  return save(name, content, type)
}

/** Lets the person pick a file and resolves with its text, or null if they cancel. */
export function pickTextFile(accept: string): Promise<string | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = accept
    input.addEventListener('change', () => {
      const file = input.files?.[0]
      if (file) file.text().then(resolve)
      else resolve(null)
    })
    input.addEventListener('cancel', () => resolve(null))
    input.click()
  })
}
