import { describe, expect, it } from 'vitest'
import {
    getOptimizedImageUrl,
    getOptimizedProfileImageUrl,
    getOptimizedBoardIconUrl,
    getOptimizedPostImageUrl,
    handleImageError,
} from '@/utils/image'

describe('image utils', () => {
    it('returns empty string for invalid image URL values', () => {
        expect(getOptimizedImageUrl('')).toBe('')
        expect(getOptimizedImageUrl(null)).toBe('')
        expect(getOptimizedImageUrl(undefined)).toBe('')
    })

    it('returns original URL even when optimization options are provided', () => {
        const url = 'https://example.com/image.png'
        expect(getOptimizedImageUrl(url)).toBe(url)
        expect(
            getOptimizedImageUrl(url, {
                width: 800,
                height: 600,
                quality: 80,
                format: 'webp',
            }),
        ).toBe(url)
    })

    it('builds profile, board and post image URLs via shared optimizer', () => {
        const url = 'https://example.com/image.png'
        expect(getOptimizedProfileImageUrl(url)).toBe(url)
        expect(getOptimizedProfileImageUrl(url, 300)).toBe(url)
        expect(getOptimizedBoardIconUrl(url)).toBe(url)
        expect(getOptimizedBoardIconUrl(url, 120)).toBe(url)
        expect(getOptimizedPostImageUrl(url)).toBe(url)
        expect(getOptimizedPostImageUrl(url, 1024, 512)).toBe(url)
    })

    it('sets placeholder image on load error when placeholder URL is provided', () => {
        const img = document.createElement('img')
        img.srcset = 'https://example.com/original.png 1x'
        const event = { target: img } as unknown as Event

        handleImageError(event, 'https://example.com/placeholder.png')
        expect(img.src).toBe('https://example.com/placeholder.png')
        expect(img.hasAttribute('srcset')).toBe(false)
        expect(img.dataset.fallbackApplied).toBe('true')
    })

    it('sets default inline SVG placeholder when no placeholder is provided', () => {
        const img = document.createElement('img')
        const event = { target: img } as unknown as Event

        handleImageError(event)
        expect(img.src.startsWith('data:image/svg+xml')).toBe(true)
    })

    it('does not reapply the fallback after the placeholder also fails', () => {
        const img = document.createElement('img')
        img.dataset.fallbackApplied = 'true'
        img.src = 'https://example.com/placeholder.png'
        const event = { target: img } as unknown as Event

        handleImageError(event, 'https://example.com/next-placeholder.png')
        expect(img.src).toBe('https://example.com/placeholder.png')
    })
})
