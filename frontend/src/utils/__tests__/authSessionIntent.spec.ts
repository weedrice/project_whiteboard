import { describe, expect, it } from 'vitest'
import {
  captureAuthSessionIntent,
  isAuthSessionIntentCurrent,
} from '@/utils/authSessionIntent'

describe('authSessionIntent', () => {
  it('stays current across a normal access-token rotation', () => {
    const source = {
      sessionGeneration: 3,
      accessToken: 'before',
      user: { userId: 7 },
    }
    const intent = captureAuthSessionIntent(source)

    source.accessToken = 'after'

    expect(isAuthSessionIntentCurrent(source, intent)).toBe(true)
  })

  it('becomes stale when the session generation or user changes', () => {
    const source = { sessionGeneration: 3, user: { userId: 7 } }
    const intent = captureAuthSessionIntent(source)

    source.sessionGeneration = 4
    expect(isAuthSessionIntentCurrent(source, intent)).toBe(false)

    source.sessionGeneration = 3
    source.user.userId = 8
    expect(isAuthSessionIntentCurrent(source, intent)).toBe(false)
  })
})
