import { describe, expect, it } from 'vitest'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '../imageFallback'

describe('imageFallback', () => {
    it('replaces a failed image source with the default emoticon image', () => {
        const image = document.createElement('img')
        image.src = '/api/v1/files/17'
        image.srcset = '/api/v1/files/17 1x'
        const event = new Event('error', { bubbles: false })
        Object.defineProperty(event, 'target', { value: image })

        applyImageFallback(event)

        expect(image.getAttribute('src')).toBe(DEFAULT_EMOTICON_IMAGE_URL)
        expect(image.hasAttribute('srcset')).toBe(false)
        expect(image.dataset.fallbackApplied).toBe('true')
    })

    it('does not loop when the fallback image also fails', () => {
        const image = document.createElement('img')
        image.src = DEFAULT_EMOTICON_IMAGE_URL
        image.dataset.fallbackApplied = 'true'
        const event = new Event('error', { bubbles: false })
        Object.defineProperty(event, 'target', { value: image })

        applyImageFallback(event)

        expect(image.getAttribute('src')).toBe(DEFAULT_EMOTICON_IMAGE_URL)
    })
})
