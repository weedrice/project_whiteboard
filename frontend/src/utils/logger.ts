import log, { type Logger } from 'loglevel'
import prefix from 'loglevel-plugin-prefix'

// Apply the prefix plugin to loglevel
prefix.reg(log)
prefix.apply(log, {
    template: '[%t] %l:',
    levelFormatter(level: string): string {
        return level.toUpperCase()
    },
    nameFormatter(name: string | undefined): string {
        return name || 'root'
    },
    timestampFormatter(date: Date): string {
        return date.toLocaleTimeString()
    },
})

// Set default level based on environment.
// Development keeps verbose diagnostics, while production only surfaces errors.
if (import.meta.env.DEV) {
    log.setLevel('trace')
} else {
    log.setLevel('error')
}

export default log as Logger
