import { describe, expect, it } from 'vitest'
import { QueryClient } from '@tanstack/vue-query'
import {
  AUTH_SCOPED_QUERY_META,
  clearAuthScopedQueries,
  sessionQueryKey,
} from '@/queryAuthScope'

describe('query auth scope', () => {
  it('adds the session generation to private query keys', () => {
    expect(sessionQueryKey(4, ['user', 'settings'])).toEqual([
      'session', 4, 'user', 'settings',
    ])
  })

  it('removes private session data while preserving public caches', () => {
    const client = new QueryClient()
    client.setQueryData(['board', 'all'], ['public-board'])
    client.setQueryData(['user', 42], { displayName: 'public-profile' })
    client.setQueryData(['user', 'settings'], { theme: 'dark' })
    client.setQueryDefaults(['private-custom'], { meta: AUTH_SCOPED_QUERY_META })
    client.setQueryData(['private-custom'], { secret: true })

    clearAuthScopedQueries(client)

    expect(client.getQueryData(['board', 'all'])).toEqual(['public-board'])
    expect(client.getQueryData(['user', 42])).toEqual({ displayName: 'public-profile' })
    expect(client.getQueryData(['user', 'settings'])).toBeUndefined()
    expect(client.getQueryData(['private-custom'])).toBeUndefined()
  })
})
