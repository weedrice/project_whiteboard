/**
 * Formats a date string to a locale string.
 * Example: "2023-10-27T10:00:00" -> "10/27/2023, 10:00:00 AM" (depending on locale)
 */
export function formatDate(dateString: string | number[]): string {
    if (!dateString) return ''
    if (Array.isArray(dateString)) {
        const [year, month, day, hour, minute, second] = dateString
        return new Date(year, month - 1, day, hour, minute, second || 0).toLocaleString()
    }
    return new Date(dateString).toLocaleString()
}

/**
 * Formats a date string with special handling for "today".
 * If the date is today, returns the time (e.g., "14:30").
 * Otherwise, returns the date (e.g., "2023-10-27").
 */
export function formatRelativeDate(dateString: string): string {
    if (!dateString) return ''

    const date = new Date(dateString)
    const today = new Date()

    const isToday = date.getDate() === today.getDate() &&
        date.getMonth() === today.getMonth() &&
        date.getFullYear() === today.getFullYear()

    if (isToday) {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
    return date.toLocaleDateString()
}

export function formatDateOnly(dateString: string | number[]): string {
    if (!dateString) return ''
    if (Array.isArray(dateString)) {
        const [year, month, day] = dateString
        return new Date(year, month - 1, day).toLocaleDateString()
    }
    return new Date(dateString).toLocaleDateString()
}

export function formatTimeAgo(dateString: string | number[], t: (key: string, values?: any) => string): string {
    if (!dateString) return ''
    const date = Array.isArray(dateString)
        ? new Date(dateString[0], dateString[1] - 1, dateString[2], dateString[3], dateString[4], dateString[5] || 0)
        : new Date(dateString)

    const now = new Date()
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000)

    if (seconds < 60) return t('common.time.justNow')
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return t('common.time.minutesAgo', { count: minutes })
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return t('common.time.hoursAgo', { count: hours })
    const days = Math.floor(hours / 24)
    if (days < 7) return t('common.time.daysAgo', { count: days })

    return date.toLocaleDateString()
}
