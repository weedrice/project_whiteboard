import log from 'loglevel'
import prefix from 'loglevel-plugin-prefix'

// Apply the prefix plugin to loglevel
prefix.reg(log)
prefix.apply(log, {
    template: '[%t] %l:',
    levelFormatter(level) {
        return level.toUpperCase()
    },
    nameFormatter(name) {
        return name || 'root'
    },
    timestampFormatter(date) {
        return date.toLocaleTimeString()
    },
})

// Set default level based on environment
// In development, show everything (trace)
// In production, show only warnings and errors
if (import.meta.env.DEV) {
    log.setLevel('trace')
} else {
    log.setLevel('warn')
}

export default log
