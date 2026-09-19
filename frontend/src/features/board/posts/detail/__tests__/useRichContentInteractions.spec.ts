import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { Router } from 'vue-router'
import { useRichContentInteractions } from '../useRichContentInteractions'

function setup(html: string) {
  const isDisabled = ref(false)
  const interactions = useRichContentInteractions({
    isDisabled,
    router: { push: vi.fn() } as unknown as Router,
    getCodeCopyLabels: () => ({ copy: 'Copy', copied: 'Copied', failed: 'Failed' }),
    getImageOpenLabel: (alt) => alt,
  })
  const article = document.createElement('article')
  article.innerHTML = '<div class="nv-rich-content">' + html + '</div>'
  interactions.articleRef.value = article
  article.addEventListener('click', interactions.handleContentClick)
  article.addEventListener('keydown', interactions.handleContentKeydown)
  return { ...interactions, article, isDisabled }
}

describe('useRichContentInteractions', () => {
  it('opens an authenticated image hydrated after the initial lightbox render', () => {
    const state = setup('<img alt="protected" data-authenticated-file-src="/files/42">')
    expect(state.lightboxImages.value).toEqual([])
    const image = state.article.querySelector('img')!
    image.src = 'blob:authenticated'
    image.click()
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxImages.value).toEqual([{ src: 'blob:authenticated', alt: 'protected' }])
    expect(state.lightboxIndex.value).toBe(0)
  })

  it('refreshes current image sources and selects the clicked occurrence on each opening', () => {
    const state = setup('<img src="blob:old" alt="first"><img src="blob:old" alt="second">')
    const images = state.article.querySelectorAll('img')
    images[1]!.click()
    expect(state.lightboxIndex.value).toBe(1)
    state.lightboxOpen.value = false
    images[1]!.src = 'blob:new'
    images[1]!.click()
    expect(state.lightboxImages.value[1]).toEqual({ src: 'blob:new', alt: 'second' })
    expect(state.lightboxOpen.value).toBe(true)
  })

  it.each(['Enter', ' '])('opens hydrated images through the %s key', (key) => {
    const state = setup('<img><img alt="loaded">')
    expect(state.lightboxImages.value).toEqual([])
    const image = state.article.querySelectorAll('img')[1]!
    image.src = 'blob:loaded'
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
    image.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxIndex.value).toBe(0)
    expect(state.lightboxImages.value).toHaveLength(1)
  })

  it('does not open unloaded images or images behind the spoiler guard', () => {
    const state = setup('<img alt="protected">')
    const image = state.article.querySelector('img')!
    image.click()
    expect(state.lightboxOpen.value).toBe(false)
    image.src = 'blob:protected'
    state.isDisabled.value = true
    image.click()
    image.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(state.lightboxOpen.value).toBe(false)
  })
})
