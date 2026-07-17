import type { QueryClient, QueryKey } from '@tanstack/vue-query'
import type { Query } from '@tanstack/query-core'

const AUTH_SCOPED_ROOTS = new Set([
  'attendance',
  'comments',
  'notifications',
  'post',
  'reports',
  'session',
])

const PRIVATE_USER_SEGMENTS = new Set([
  'agents',
  'blocks',
  'drafts',
  'history',
  'keyword-subscriptions',
  'login-history',
  'me',
  'notification-settings',
  'points',
  'post-series',
  'scheduled-posts',
  'scrap-folders',
  'scraps',
  'sessions',
  'settings',
])

export const AUTH_SCOPED_QUERY_META = { authScoped: true } as const

let resolveSessionGeneration = () => 0

export function configureAuthQueryScope(resolver: () => number) {
  resolveSessionGeneration = resolver
}

export function getCurrentSessionGeneration() {
  return resolveSessionGeneration()
}

export interface SessionGenerationSource {
  sessionGeneration: number
}

export function sessionQueryKey(generation: number, queryKey: QueryKey) {
  if (queryKey[0] === 'session' && typeof queryKey[1] === 'number') {
    return ['session', generation, ...queryKey.slice(2)] as const
  }
  return ['session', generation, ...queryKey] as const
}

export function currentSessionQueryKey(source: SessionGenerationSource, queryKey: QueryKey) {
  return sessionQueryKey(source.sessionGeneration, queryKey)
}

export function captureSessionGeneration(source: SessionGenerationSource) {
  return source.sessionGeneration
}

export function isSessionGenerationCurrent(
  source: SessionGenerationSource,
  generation: number,
) {
  return source.sessionGeneration === generation
}

export function isAuthScopedQuery(query: { queryKey: QueryKey, meta?: Record<string, unknown> }) {
  if (query.meta?.authScoped === true) return true
  const [root, second] = query.queryKey
  if (root === 'session' && typeof second === 'number') return true
  if (root === 'user' && typeof second === 'string' && PRIVATE_USER_SEGMENTS.has(second)) return true
  if (root === 'shop' && second === 'purchases') return true
  if (root === 'board' && (second === 'subscriptions' || second === 'detail')) return true
  if (root === 'emoticon' && query.queryKey.includes('purchased')) return true
  return typeof root === 'string' && AUTH_SCOPED_ROOTS.has(root)
}

export function clearAuthScopedQueries(queryClient: QueryClient) {
  const predicate = (query: Query) => isAuthScopedQuery(query)
  void queryClient.cancelQueries({ predicate })
  queryClient.removeQueries({ predicate })
}
