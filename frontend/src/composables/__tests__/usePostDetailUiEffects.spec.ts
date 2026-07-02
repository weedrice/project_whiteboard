import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePostDetailUiEffects } from '../usePostDetailUiEffects'

describe('usePostDetailUiEffects', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('reveals spoiler content when the countdown reaches zero', () => {
    const effects = usePostDetailUiEffects()

    effects.isBlurred.value = true
    effects.timeLeft.value = 2
    effects.startBlurTimer()

    vi.advanceTimersByTime(2000)

    expect(effects.timeLeft.value).toBe(0)
    expect(effects.isBlurred.value).toBe(false)
  })

  it('does not focus the composer textarea after disposal', () => {
    const effects = usePostDetailUiEffects()
    const composer = document.createElement('div')
    const textarea = document.createElement('textarea')
    const focus = vi.spyOn(textarea, 'focus')
    composer.append(textarea)

    effects.scheduleComposerFocus(composer)
    effects.disposePostDetailUiEffects()
    vi.advanceTimersByTime(250)

    expect(focus).not.toHaveBeenCalled()
    expect(effects.isPostDetailUiDisposed()).toBe(true)
  })

  it('updates the composer CTA from IntersectionObserver entries on small screens', () => {
    let callback: IntersectionObserverCallback | null = null
    class MockIntersectionObserver {
      observe = vi.fn()
      disconnect = vi.fn()

      constructor(cb: IntersectionObserverCallback) {
        callback = cb
      }
    }
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 375,
    })
    const composer = document.createElement('div')
    composer.id = 'comment-composer'
    document.body.append(composer)
    const effects = usePostDetailUiEffects()

    effects.setupComposerObserver()
    expect(callback).toBeTypeOf('function')
    const observerCallback = callback as unknown as IntersectionObserverCallback
    observerCallback([{ isIntersecting: false } as IntersectionObserverEntry], {} as IntersectionObserver)

    expect(effects.showComposerCta.value).toBe(true)

    observerCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)

    expect(effects.showComposerCta.value).toBe(false)
  })
})
