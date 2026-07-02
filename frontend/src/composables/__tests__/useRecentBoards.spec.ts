import { beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizeRecentBoards, useRecentBoards } from '../useRecentBoards'

describe('useRecentBoards', () => {
    beforeEach(() => {
        vi.useRealTimers()
        localStorage.clear()
        useRecentBoards().clearRecentBoards()
    })

    it('normalizes stored recent boards defensively', () => {
        const validBoards = Array.from({ length: 12 }, (_, index) => ({
            boardUrl: `board-${index}`,
            boardName: `Board ${index}`,
            iconUrl: index === 0 ? '  /icon.png  ' : '',
            visitedAt: `2026-05-${String(index + 1).padStart(2, '0')}T00:00:00.000Z`,
        }))

        const normalized = normalizeRecentBoards([
            validBoards[0],
            { boardUrl: 'board-0', boardName: 'Duplicate', visitedAt: '2026-05-20T00:00:00.000Z' },
            { boardUrl: '', boardName: 'Missing URL', visitedAt: '2026-05-20T00:00:00.000Z' },
            { boardUrl: 'broken-date', boardName: 'Broken Date', visitedAt: 'not-a-date' },
            null,
            ...validBoards.slice(1),
        ])

        expect(normalized).toHaveLength(10)
        expect(normalized[0]).toEqual({
            boardUrl: 'board-0',
            boardName: 'Board 0',
            iconUrl: '/icon.png',
            visitedAt: '2026-05-01T00:00:00.000Z',
        })
        expect(normalized.map((board) => board.boardUrl)).not.toContain('broken-date')
    })

    it('cleans corrupted localStorage entries on refresh', () => {
        localStorage.setItem('recentBoards', JSON.stringify([
            { boardUrl: 'free', boardName: 'Free', visitedAt: '2026-05-23T00:00:00.000Z' },
            { boardUrl: 'bad', boardName: '', visitedAt: '2026-05-23T00:00:00.000Z' },
        ]))

        const { recentBoards, refresh } = useRecentBoards()
        refresh()

        expect(recentBoards.value).toEqual([
            { boardUrl: 'free', boardName: 'Free', visitedAt: '2026-05-23T00:00:00.000Z' },
        ])
        expect(JSON.parse(localStorage.getItem('recentBoards') ?? '[]')).toEqual(recentBoards.value)
    })

    it('trims and deduplicates added boards before saving', () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-05-24T00:00:00.000Z'))
        const { addRecentBoard, recentBoards } = useRecentBoards()

        addRecentBoard({ boardUrl: ' free ', boardName: ' Free ', iconUrl: ' /free.png ' })
        addRecentBoard({ boardUrl: 'free', boardName: 'Free Again' })
        addRecentBoard({ boardUrl: '', boardName: 'Ignored' })

        expect(recentBoards.value).toEqual([
            {
                boardUrl: 'free',
                boardName: 'Free Again',
                visitedAt: '2026-05-24T00:00:00.000Z',
            },
        ])
    })
})
