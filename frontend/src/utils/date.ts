// Reusable date formatters for better performance
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
})

const dateOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
})

const timeOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
})

/**
 * Formats a date string to a locale string.
 * Example: "2023-10-27T10:00:00" -> "2023. 10. 27. 10:00:00" (Korean locale)
 * Uses Intl.DateTimeFormat for better performance and consistency.
 */
export function formatDate(dateString: string | number[]): string {
    if (!dateString) return ''
    
    let date: Date
    if (Array.isArray(dateString)) {
        const [year, month, day, hour, minute, second] = dateString
        // Treat array as UTC components
        date = new Date(Date.UTC(year, month - 1, day, hour, minute, second || 0))
    } else {
        date = new Date(dateString)
    }
    
    return dateTimeFormatter.format(date)
}

/**
 * Formats a date string with special handling for "today".
 * If the date is today, returns the time (e.g., "14:30").
 * Otherwise, returns the date (e.g., "2023. 10. 27.").
 */
export function formatRelativeDate(dateString: string): string {
    if (!dateString) return ''

    const date = new Date(dateString)
    const today = new Date()

    const isToday = date.getDate() === today.getDate() &&
        date.getMonth() === today.getMonth() &&
        date.getFullYear() === today.getFullYear()

    if (isToday) {
        return timeOnlyFormatter.format(date)
    }
    return dateOnlyFormatter.format(date)
}

/**
 * Formats a date string to show only the date part.
 * Example: "2023-10-27T10:00:00" -> "2023. 10. 27."
 * Uses Intl.DateTimeFormat for better performance and consistency.
 */
export function formatDateOnly(dateString: string | number[]): string {
    if (!dateString) return ''
    
    let date: Date
    if (Array.isArray(dateString)) {
        const [year, month, day] = dateString
        // Treat array as UTC components
        date = new Date(Date.UTC(year, month - 1, day))
    } else {
        date = new Date(dateString)
    }
    
    return dateOnlyFormatter.format(date)
}

export function formatTimeAgo(dateString: string | number[], t: (key: string, values?: Record<string, unknown>) => string): string {
    if (!dateString) return ''
    const date = Array.isArray(dateString)
        ? new Date(Date.UTC(dateString[0], dateString[1] - 1, dateString[2], dateString[3], dateString[4], dateString[5] || 0))
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
