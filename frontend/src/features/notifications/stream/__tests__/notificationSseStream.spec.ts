import { describe, expect, it, vi } from 'vitest'
import {
  consumeSseStream,
  SSE_STREAM_LIMITS,
} from '../notificationSseStream'

function streamFrom(...chunks: string[]) {
  const encoder = new TextEncoder()
  let index = 0
  return new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index >= chunks.length) {
        controller.close()
        return
      }
      controller.enqueue(encoder.encode(chunks[index++]))
    },
  })
}

describe('consumeSseStream protocol limits', () => {
  it('rejects and cancels a stream with an unbounded line buffer', async () => {
    const stream = streamFrom('x'.repeat(SSE_STREAM_LIMITS.bufferCharacters + 1))
    const cancel = vi.spyOn(ReadableStreamDefaultReader.prototype, 'cancel')

    await expect(consumeSseStream(stream, new AbortController().signal, vi.fn()))
      .rejects.toMatchObject({ name: 'SseProtocolLimitError', limit: 'buffer' })
    expect(cancel).toHaveBeenCalled()
    cancel.mockRestore()
  })

  it('rejects an event with too many data lines without invoking the handler', async () => {
    const handler = vi.fn()
    const payload = `${Array.from({ length: SSE_STREAM_LIMITS.dataLines + 1 }, () => 'data: x').join('\n')}\n\n`

    await expect(consumeSseStream(streamFrom(payload), new AbortController().signal, handler))
      .rejects.toMatchObject({ limit: 'data-lines' })
    expect(handler).not.toHaveBeenCalled()
  })

  it('accepts an event at the configured data line boundary', async () => {
    const handler = vi.fn()
    const payload = `${Array.from({ length: SSE_STREAM_LIMITS.dataLines }, () => 'data: x').join('\n')}\n\n`

    await consumeSseStream(streamFrom(payload), new AbortController().signal, handler)
    expect(handler).toHaveBeenCalledWith('message', Array.from({ length: SSE_STREAM_LIMITS.dataLines }, () => 'x').join('\n'))
  })
})
