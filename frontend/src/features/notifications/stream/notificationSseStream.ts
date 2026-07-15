type SseEventHandler = (eventType: string, payload: string) => void

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

    const flushEvent = () => {
        const payload = dataLines.join('\n').trim()
        if (payload) {
            handleEvent(currentEvent, payload)
        }
        currentEvent = 'message'
        dataLines = []
    }

    try {
        while (!signal.aborted) {
            const { done, value } = await reader.read()
            if (done) break

            buffer += decoder.decode(value, { stream: true })

            let newlineIndex = buffer.indexOf('\n')
            while (newlineIndex !== -1) {
                const rawLine = buffer.slice(0, newlineIndex)
                buffer = buffer.slice(newlineIndex + 1)
                const line = rawLine.replace(/\r$/, '')

                if (line === '') {
                    flushEvent()
                } else if (line.startsWith(':')) {
                    // Keep-alive comment; ignore.
                } else if (line.startsWith('event:')) {
                    currentEvent = line.slice(6).trim() || 'message'
                } else if (line.startsWith('data:')) {
                    dataLines.push(line.slice(5).trimStart())
                }

                newlineIndex = buffer.indexOf('\n')
            }
        }

        if (buffer.trim() || dataLines.length > 0) {
            if (buffer.startsWith('data:')) {
                dataLines.push(buffer.slice(5).trimStart())
            }
            flushEvent()
        }
    } finally {
        await reader.cancel().catch(() => undefined)
    }
}
