import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
    NOTIFICATION_SSE_EVENTS,
    SSE_DEFAULT_EVENT_NAME,
} from '@/features/notifications/stream/notificationSseEvents'

/**
 * SSE 이벤트 이름은 백엔드와 맺은 계약이지만 REST envelope와 달리 직렬화 검증 장치가 없다.
 * 백엔드 상수 파일을 직접 읽어 양쪽 이름 집합이 어긋나지 않는지 확인한다.
 */
const BACKEND_EVENTS_SOURCE = resolve(
    __dirname,
    '../../../../../../backend/src/main/java/com/weedrice/whiteboard/domain/notification/web/NotificationSseEvents.java',
)

function readBackendEventNames(): string[] {
    const source = readFileSync(BACKEND_EVENTS_SOURCE, 'utf-8')
    return [...source.matchAll(/public static final String [A-Z_]+ = "([^"]+)";/g)].map((match) => match[1])
}

describe('알림 SSE 이벤트 이름 계약', () => {
    it('백엔드가 선언한 이벤트 이름과 정확히 일치한다', () => {
        expect([...readBackendEventNames()].sort())
            .toEqual([...Object.values(NOTIFICATION_SSE_EVENTS)].sort())
    })

    it('SSE 기본 이벤트 이름은 백엔드 이벤트에 포함되지 않는다', () => {
        // 'message'는 프로토콜 기본값이지 백엔드가 보내는 이름이 아니다.
        expect(readBackendEventNames()).not.toContain(SSE_DEFAULT_EVENT_NAME)
        expect(Object.values(NOTIFICATION_SSE_EVENTS)).not.toContain(SSE_DEFAULT_EVENT_NAME)
    })
})
