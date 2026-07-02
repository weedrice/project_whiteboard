import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { usePostDetailShare } from '../usePostDetailShare'
import { createDeferred } from '@/test/async'
import type { Post } from '@/types'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
  },
}))

const route = {
  fullPath: '/board/free/post/1',
} as RouteLocationNormalizedLoaded

const post = {
  title: 'Post title',
} as Post

function mountShareComposable(overrides: { post?: Post | null } = {}) {
  let share!: ReturnType<typeof usePostDetailShare>

  const wrapper = mount(defineComponent({
    setup() {
      share = usePostDetailShare({
        route,
        post: ref(overrides.post === undefined ? post : overrides.post),
        t: (key: string) => key,
      })
      return () => h('div')
    },
  }))

  return {
    share,
    wrapper,
  }
}

describe('usePostDetailShare', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        origin: 'https://noviis.example',
      },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('falls back to an error toast when clipboard API is unavailable', () => {
    vi.stubGlobal('navigator', {})
    const { share } = mountShareComposable()

    share.handleCopyUrl()

    expect(mocks.addToast).toHaveBeenCalledWith('common.messages.processFailed', 'error')
  })

  it('copies the current URL when clipboard API is available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', {
      clipboard: { writeText },
    })
    const { share } = mountShareComposable()

    share.handleCopyUrl()

    await vi.waitFor(() => {
      expect(writeText).toHaveBeenCalledWith('https://noviis.example/board/free/post/1')
    })
    expect(mocks.addToast).toHaveBeenCalledWith('common.messages.urlCopied', 'success')
  })

  it('uses clipboard fallback when Web Share API is unavailable', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', {
      clipboard: { writeText },
    })
    const { share } = mountShareComposable()

    share.handleShare()

    await vi.waitFor(() => {
      expect(writeText).toHaveBeenCalled()
    })
  })

  it('does not show an error toast when native share is cancelled', async () => {
    const shareApi = vi.fn().mockRejectedValue(new DOMException('cancelled', 'AbortError'))
    vi.stubGlobal('navigator', {
      share: shareApi,
      clipboard: { writeText: vi.fn() },
    })
    const { share } = mountShareComposable()

    share.handleShare()

    await vi.waitFor(() => {
      expect(shareApi).toHaveBeenCalled()
    })
    expect(mocks.addToast).not.toHaveBeenCalledWith('common.messages.processFailed', 'error')
  })

  it('handles non-error native share rejections without throwing', async () => {
    const shareApi = vi.fn().mockRejectedValue('share failed')
    vi.stubGlobal('navigator', {
      share: shareApi,
      clipboard: { writeText: vi.fn() },
    })
    const { share } = mountShareComposable()

    share.handleShare()

    await vi.waitFor(() => {
      expect(mocks.loggerError).toHaveBeenCalledWith('Share failed:', 'share failed')
    })
    expect(mocks.addToast).toHaveBeenCalledWith('common.messages.processFailed', 'error')
  })

  it('ignores clipboard success after the owner component is unmounted', async () => {
    const copyResult = createDeferred()
    const writeText = vi.fn().mockReturnValue(copyResult.promise)
    vi.stubGlobal('navigator', {
      clipboard: { writeText },
    })
    const { share, wrapper } = mountShareComposable()

    share.handleCopyUrl(false)
    wrapper.unmount()
    copyResult.resolve()
    await copyResult.promise
    await Promise.resolve()

    expect(share.showCopyHint.value).toBe(false)
    expect(mocks.addToast).not.toHaveBeenCalled()
  })
})
