import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { sanitizeClientLogArgument } from '@/utils/safeClientLog'

describe('sanitizeClientLogArgument', () => {
  it('removes Axios request headers and sensitive request/response bodies', () => {
    const error = new AxiosError(
      'Request failed',
      'ERR_BAD_REQUEST',
      {
        headers: new AxiosHeaders({ Authorization: 'Bearer secret-access' }),
        data: JSON.stringify({ password: 'secret-password', body: 'private message' }),
      },
      {},
      {
        data: { error: { code: 'A001' }, email: 'private@example.com' },
        status: 401,
        statusText: 'Unauthorized',
        headers: new AxiosHeaders({ 'x-correlation-id': 'request-123' }),
        config: { headers: new AxiosHeaders() },
      },
    )

    const sanitized = sanitizeClientLogArgument(error)
    const serialized = JSON.stringify(sanitized)

    expect(sanitized).toEqual({
      name: 'AxiosError',
      code: 'ERR_BAD_REQUEST',
      status: 401,
      errorCode: 'A001',
      correlationId: 'request-123',
    })
    expect(serialized).not.toContain('secret-access')
    expect(serialized).not.toContain('secret-password')
    expect(serialized).not.toContain('private message')
    expect(serialized).not.toContain('private@example.com')
  })

  it('sanitizes Axios errors nested in objects and arrays', () => {
    const error = new AxiosError(
      'Nested request failed',
      'ERR_BAD_RESPONSE',
      {
        headers: new AxiosHeaders({ Authorization: 'Bearer nested-secret' }),
        data: JSON.stringify({ password: 'nested-password' }),
      },
      {},
      {
        data: { error: { code: 'NESTED_ERROR' }, body: 'private response' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: new AxiosHeaders({ 'x-request-id': 'nested-request-123' }),
        config: { headers: new AxiosHeaders() },
      },
    )

    const sanitized = sanitizeClientLogArgument({ failures: [{ error }] })
    const serialized = JSON.stringify(sanitized)

    expect(sanitized).toEqual({
      failures: [{
        error: {
          name: 'AxiosError',
          code: 'ERR_BAD_RESPONSE',
          status: 500,
          errorCode: 'NESTED_ERROR',
          correlationId: 'nested-request-123',
        },
      }],
    })
    expect(serialized).not.toContain('nested-secret')
    expect(serialized).not.toContain('nested-password')
    expect(serialized).not.toContain('private response')
  })

  it('omits non-string server error codes and bounds recursive traversal', () => {
    const error = new AxiosError(
      'Invalid error code',
      'ERR_BAD_REQUEST',
      { headers: new AxiosHeaders() },
      {},
      {
        data: { error: { code: { secret: 'must-not-be-logged' } } },
        status: 400,
        statusText: 'Bad Request',
        headers: new AxiosHeaders(),
        config: { headers: new AxiosHeaders() },
      },
    )
    const circular: Record<string, unknown> = {}
    circular.self = circular
    const oversized = Array.from({ length: 100 }, (_, index) => index)

    const sanitizedError = sanitizeClientLogArgument(error) as { errorCode?: unknown }
    const sanitizedCircular = sanitizeClientLogArgument(circular)
    const sanitizedOversized = sanitizeClientLogArgument(oversized) as unknown[]

    expect(sanitizedError.errorCode).toBeUndefined()
    expect(JSON.stringify(sanitizedError)).not.toContain('must-not-be-logged')
    expect(sanitizedCircular).toEqual({ self: '[Circular]' })
    expect(sanitizedOversized).toHaveLength(51)
    expect(sanitizedOversized.at(-1)).toBe('[Truncated]')
  })
})
