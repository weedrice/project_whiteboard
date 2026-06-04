interface CancellationErrorOptions {
  names?: readonly string[]
  codes?: readonly string[]
  requireDomException?: boolean
}

export function isCancellationError(
  error: unknown,
  {
    names = ['AbortError'],
    codes = [],
    requireDomException = false,
  }: CancellationErrorOptions = {},
): boolean {
  if (requireDomException && !(error instanceof DOMException)) {
    return false
  }

  if (typeof error !== 'object' || error === null) {
    return false
  }

  const maybeError = error as { name?: unknown; code?: unknown }
  return (
    (typeof maybeError.name === 'string' && names.includes(maybeError.name))
    || (typeof maybeError.code === 'string' && codes.includes(maybeError.code))
  )
}
