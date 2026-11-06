const dateFormat = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })
const yearFormat = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** `2026-12-05` → « Samedi 5 décembre » (the year only when it is not the current one). */
export function formatDate(iso: string, now: Date = new Date()): string {
  const date = new Date(`${iso}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return iso
  const text = (date.getUTCFullYear() === now.getFullYear() ? dateFormat : yearFormat).format(date)
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** Today in the viewer's time zone, as the `min` of a date input. */
export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
