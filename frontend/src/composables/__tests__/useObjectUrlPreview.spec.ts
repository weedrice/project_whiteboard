import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useObjectUrlPreview } from '../useObjectUrlPreview'

const mountedWrappers: VueWrapper[] = []

const mountHarness = (initialUrl: string | null = null) => {
  let preview!: ReturnType<typeof useObjectUrlPreview>

  const Harness = defineComponent({
    setup() {
      preview = useObjectUrlPreview(initialUrl)
      return () => h('div')
    },
  })

  const wrapper = mount(Harness)
  mountedWrappers.push(wrapper)

  return {
    preview,
    wrapper,
  }
}

describe('useObjectUrlPreview', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => `blob:${(blob as File).name || 'preview'}`)
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => {
      wrapper.unmount()
    })
    vi.restoreAllMocks()
  })

  it('creates an object URL from a file and revokes the previous blob URL on replacement', () => {
    const { preview } = mountHarness()

    preview.setPreviewFile(new File(['first'], 'first.png', { type: 'image/png' }))
    preview.setPreviewFile(new File(['second'], 'second.png', { type: 'image/png' }))

    expect(preview.previewUrl.value).toBe('blob:second.png')
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first.png')
  })

  it('does not revoke non-blob URLs when replacing an existing remote preview', () => {
    const { preview } = mountHarness('/image.png')

    preview.setPreviewFile(new File(['next'], 'next.png', { type: 'image/png' }))

    expect(preview.previewUrl.value).toBe('blob:next.png')
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  })

  it('revokes the current blob URL on unmount', () => {
    const { preview, wrapper } = mountHarness()

    preview.setPreviewFile(new File(['avatar'], 'avatar.png', { type: 'image/png' }))
    wrapper.unmount()

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:avatar.png')
  })
})
