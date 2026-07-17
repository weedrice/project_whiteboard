import { isAxiosError } from 'axios'

const MAX_SANITIZE_DEPTH = 5
const MAX_SANITIZE_ITEMS = 50
const CIRCULAR_MARKER = '[Circular]'
const MAX_DEPTH_MARKER = '[MaxDepth]'
const TRUNCATED_MARKER = '[Truncated]'

interface SanitizeState {
  remainingItems: number
  ancestors: WeakSet<object>
}

function readHeader(headers: unknown, name: string): string | undefined {
  if (!headers || typeof headers !== 'object') return undefined
  const headerSource = headers as { get?: (key: string) => unknown } & Record<string, unknown>
  const value = typeof headerSource.get === 'function'
    ? headerSource.get(name)
    : headerSource[name] ?? headerSource[name.toLowerCase()]
  return typeof value === 'string' && value ? value : undefined
}

function sanitizeAxiosError(value: Parameters<typeof isAxiosError>[0]) {
  if (!isAxiosError(value)) return undefined

  const responseData = value.response?.data
  const errorCodeCandidate = typeof responseData === 'object' && responseData
    ? (responseData as { error?: { code?: unknown }, code?: unknown }).error?.code
      ?? (responseData as { code?: unknown }).code
    : undefined

  return {
    name: value.name || 'AxiosError',
    code: value.code,
    status: value.response?.status,
    errorCode: typeof errorCodeCandidate === 'string' ? errorCodeCandidate : undefined,
    correlationId: readHeader(value.response?.headers, 'x-correlation-id')
      ?? readHeader(value.response?.headers, 'x-request-id'),
  }
}

function sanitizeValue(value: unknown, depth: number, state: SanitizeState): unknown {
  if (isAxiosError(value)) {
    return sanitizeAxiosError(value)
  }

  if (value instanceof Error) {
    return {
      name: value.name,
      message: value.message,
      stack: value.stack,
    }
  }

  if (value === null || typeof value !== 'object') return value
  if (depth >= MAX_SANITIZE_DEPTH) return MAX_DEPTH_MARKER
  if (state.ancestors.has(value)) return CIRCULAR_MARKER

  state.ancestors.add(value)
  try {
    if (Array.isArray(value)) {
      const sanitized: unknown[] = []
      for (const item of value) {
        if (state.remainingItems <= 0) {
          sanitized.push(TRUNCATED_MARKER)
          break
        }
        state.remainingItems -= 1
        sanitized.push(sanitizeValue(item, depth + 1, state))
      }
      return sanitized
    }

    const sanitized: Record<string, unknown> = {}
    const descriptors = Object.getOwnPropertyDescriptors(value)
    for (const [key, descriptor] of Object.entries(descriptors)) {
      if (state.remainingItems <= 0) {
        sanitized.__truncated__ = TRUNCATED_MARKER
        break
      }
      state.remainingItems -= 1
      sanitized[key] = 'value' in descriptor
        ? sanitizeValue(descriptor.value, depth + 1, state)
        : '[Accessor]'
    }
    return sanitized
  } catch {
    return '[Unserializable]'
  } finally {
    state.ancestors.delete(value)
  }
}

export function sanitizeClientLogArgument(value: unknown): unknown {
  return sanitizeValue(value, 0, {
    remainingItems: MAX_SANITIZE_ITEMS,
    ancestors: new WeakSet<object>(),
  })
}
