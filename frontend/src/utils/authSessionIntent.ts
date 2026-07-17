export interface AuthSessionIntentSource {
  sessionGeneration: number
  user?: { userId?: string | number | null } | null
}

export interface AuthSessionIntent {
  sessionGeneration: number
  userId: string | number | null
}

export function captureAuthSessionIntent(source: AuthSessionIntentSource): AuthSessionIntent {
  return {
    sessionGeneration: source.sessionGeneration,
    userId: source.user?.userId ?? null,
  }
}

export function isAuthSessionIntentCurrent(
  source: AuthSessionIntentSource,
  intent: AuthSessionIntent,
): boolean {
  return source.sessionGeneration === intent.sessionGeneration
    && (source.user?.userId ?? null) === intent.userId
}

export function throwIfAuthSessionIntentChanged(
  source: AuthSessionIntentSource,
  intent: AuthSessionIntent,
  signal?: AbortSignal,
): void {
  if (signal?.aborted || !isAuthSessionIntentCurrent(source, intent)) {
    throw new DOMException('Authentication session changed', 'AbortError')
  }
}
