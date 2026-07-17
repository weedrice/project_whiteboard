type SseEventHandler = (eventType: string, payload: string) => void

export const SSE_STREAM_LIMITS = {
    bufferCharacters: 64 * 1024,
    lineCharacters: 64 * 1024,
    eventCharacters: 256 * 1024,
    dataLines: 256,
    eventTypeCharacters: 128,
} as const

export type SseProtocolLimit = 'buffer' | 'line' | 'event' | 'data-lines' | 'event-type'

export class SseProtocolLimitError extends Error {
    constructor(readonly limit: SseProtocolLimit) {
        super(`SSE protocol ${limit} limit exceeded`)
        this.name = 'SseProtocolLimitError'
    }
}

export const consumeSseStream = async (
    stream: ReadableStream<Uint8Array>,
    signal: AbortSignal,
    handleEvent: SseEventHandler,
) => {
    const reader = stream.getReader()
    const decoder = new TextDecoder()

    let buffer = ''
    let currentEvent = 'message'
    let dataLines: string[] = []
    let eventCharacters = 0

    const flushEvent = () => {
        if (signal.aborted) {
            currentEvent = 'message'
            dataLines = []
            eventCharacters = 0
            return
        }
        const payload = dataLines.join('\n').trim()
        if (payload) {
            handleEvent(currentEvent, payload)
        }
        currentEvent = 'message'
        dataLines = []
        eventCharacters = 0
    }

    try {
        while (!signal.aborted) {
            const { done, value } = await reader.read()
            if (signal.aborted) break
            if (done) break

            buffer += decoder.decode(value, { stream: true })

            let newlineIndex = buffer.indexOf('\n')
            while (newlineIndex !== -1) {
                const rawLine = buffer.slice(0, newlineIndex)
                buffer = buffer.slice(newlineIndex + 1)
                if (rawLine.length > SSE_STREAM_LIMITS.lineCharacters) {
                    throw new SseProtocolLimitError('line')
                }
                const line = rawLine.replace(/\r$/, '')

                if (line === '') {
                    flushEvent()
                } else if (line.startsWith(':')) {
                    // Keep-alive comment; ignore.
                } else if (line.startsWith('event:')) {
                    currentEvent = line.slice(6).trim() || 'message'
                    if (currentEvent.length > SSE_STREAM_LIMITS.eventTypeCharacters) {
                        throw new SseProtocolLimitError('event-type')
                    }
                } else if (line.startsWith('data:')) {
                    const dataLine = line.slice(5).trimStart()
                    if (dataLines.length >= SSE_STREAM_LIMITS.dataLines) {
                        throw new SseProtocolLimitError('data-lines')
                    }
                    eventCharacters += dataLine.length + 1
                    if (eventCharacters > SSE_STREAM_LIMITS.eventCharacters) {
                        throw new SseProtocolLimitError('event')
                    }
                    dataLines.push(dataLine)
                }

                newlineIndex = buffer.indexOf('\n')
            }
            if (buffer.length > SSE_STREAM_LIMITS.bufferCharacters) {
                throw new SseProtocolLimitError('buffer')
            }
        }

        if (!signal.aborted && (buffer.trim() || dataLines.length > 0)) {
            if (buffer.startsWith('data:')) {
                if (buffer.length > SSE_STREAM_LIMITS.lineCharacters) {
                    throw new SseProtocolLimitError('line')
                }
                const dataLine = buffer.slice(5).trimStart()
                if (dataLines.length >= SSE_STREAM_LIMITS.dataLines) {
                    throw new SseProtocolLimitError('data-lines')
                }
                eventCharacters += dataLine.length + 1
                if (eventCharacters > SSE_STREAM_LIMITS.eventCharacters) {
                    throw new SseProtocolLimitError('event')
                }
                dataLines.push(dataLine)
            }
            flushEvent()
        }
    } finally {
        await reader.cancel().catch(() => undefined)
    }
}
