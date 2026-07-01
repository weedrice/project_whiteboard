import { describe, expect, it } from 'vitest'
import {
  getNotificationPageNumber,
  getNotificationReconnectDelay,
  isNotificationPage,
  shouldStopNotificationReconnectAfterRefresh,
} from '../notificationStreamStateModel'

describe('notificationStreamStateModel', () => {
  it('detects notification page payloads and resolves page numbers', () => {
    expect(isNotificationPage(null)).toBe(false)
    expect(isNotificationPage({ content: [] })).toBe(true)
    expect(getNotificationPageNumber({ number: 2, page: 5 })).toBe(2)
    expect(getNotificationPageNumber({ page: 5 })).toBe(5)
    expect(getNotificationPageNumber({})).toBe(0)
  })

  it('calculates capped reconnect backoff delays', () => {
    expect(getNotificationReconnectDelay(1000, 0, 60000)).toBe(1000)
    expect(getNotificationReconnectDelay(1000, 3, 60000)).toBe(8000)
    expect(getNotificationReconnectDelay(5000, 8, 60000)).toBe(60000)
  })

  it('stops reconnect attempts only for authentication refresh failures', () => {
    expect(shouldStopNotificationReconnectAfterRefresh(401)).toBe(true)
    expect(shouldStopNotificationReconnectAfterRefresh(403)).toBe(true)
    expect(shouldStopNotificationReconnectAfterRefresh(500)).toBe(false)
    expect(shouldStopNotificationReconnectAfterRefresh(null)).toBe(false)
  })
})
