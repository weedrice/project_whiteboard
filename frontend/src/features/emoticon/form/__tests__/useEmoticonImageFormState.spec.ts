import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useEmoticonImageFormState } from '../useEmoticonImageFormState'
import { createDeferred } from '@/test/async'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'

const revokeEmoticonPreviewUrl = vi.hoisted(() => vi.fn())

vi.mock('@/utils/emoticonImage', () => ({
  revokeEmoticonPreviewUrl,
}))

function createInputEvent(file: File) {
  const input = document.createElement('input')
  input.type = 'file'
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file],
  })
  Object.defineProperty(input, 'value', {
    configurable: true,
    writable: true,
    value: `C:\\fakepath\\${file.name}`,
  })

  return {
    input,
    event: { target: input } as unknown as Event,
  }
}

function createHarness(
  selectThumbnailImage: (file: File) => Promise<EmoticonImagePreview | null>,
) {
  let composable!: ReturnType<typeof useEmoticonImageFormState>
  const wrapper = mount(defineComponent({
    setup() {
      composable = useEmoticonImageFormState({
        selectThumbnailImage,
        selectEmoticonImages: vi.fn().mockResolvedValue([]),
        getRemainingSlots: () => 10,
      })

      return () => h('div')
    },
  }))

  return { composable, wrapper }
}

describe('useEmoticonImageFormState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps the latest thumbnail selection when async previews resolve out of order', async () => {
    const firstFile = new File(['first'], 'first.png', { type: 'image/png' })
    const secondFile = new File(['second'], 'second.png', { type: 'image/png' })
    const firstSelection = createDeferred<EmoticonImagePreview>()
    const secondSelection = createDeferred<EmoticonImagePreview>()
    const selectThumbnailImage = vi.fn()
      .mockReturnValueOnce(firstSelection.promise)
      .mockReturnValueOnce(secondSelection.promise)
    const { composable, wrapper } = createHarness(selectThumbnailImage)

    const firstHandle = composable.handleThumbnailSelect(createInputEvent(firstFile).event)
    const secondHandle = composable.handleThumbnailSelect(createInputEvent(secondFile).event)

    secondSelection.resolve({ clientId: 'second', file: secondFile, preview: 'blob:second', width: 100, height: 100 })
    await secondHandle

    expect(composable.thumbnailFile.value).toBe(secondFile)
    expect(composable.thumbnailPreview.value).toBe('blob:second')

    firstSelection.resolve({ clientId: 'first', file: firstFile, preview: 'blob:first', width: 100, height: 100 })
    await firstHandle

    expect(composable.thumbnailFile.value).toBe(secondFile)
    expect(composable.thumbnailPreview.value).toBe('blob:second')
    expect(revokeEmoticonPreviewUrl).toHaveBeenCalledWith('blob:first')

    wrapper.unmount()
  })
})
