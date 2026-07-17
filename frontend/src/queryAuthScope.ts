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

export function sessionQueryKey(generation: number, queryKey: QueryKey) {
  return ['session', generation, ...queryKey] as const
}

export function isAuthScopedQuery(query: { queryKey: QueryKey, meta?: Record<string, unknown> }) {
  if (query.meta?.authScoped === true) return true
  const [root, second] = query.queryKey
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
