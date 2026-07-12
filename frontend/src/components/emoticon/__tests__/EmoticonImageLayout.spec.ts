import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmoticonImageGridUploader from '../EmoticonImageGridUploader.vue'
import EmoticonImageTile from '../EmoticonImageTile.vue'

describe('emoticon image layout', () => {
  it('uses a fluid image grid without fixed pixel tiles', () => {
    const wrapper = mount(EmoticonImageGridUploader, {
      props: {
        inputId: 'images',
        accept: 'image/png',
        currentCount: 0,
        newImages: [],
      },
      global: {
        mocks: { $t: (key: string) => key },
      },
    })

    expect(wrapper.get('.emoticon-image-grid').classes()).toContain('grid')
    expect(wrapper.get('button').classes()).toEqual(expect.arrayContaining(['aspect-square', 'w-full']))
    expect(wrapper.get('button').attributes('style')).toBeUndefined()
  })

  it('lets image tiles size to their grid cell', () => {
    const wrapper = mount(EmoticonImageTile, {
      props: {
        src: '/image.png',
        alt: 'Preview',
        action: 'delete',
        actionLabel: 'Delete',
      },
    })

    const image = wrapper.get('img')
    const action = wrapper.get('button')
    expect(image.classes()).toEqual(expect.arrayContaining(['aspect-square', 'w-full']))
    expect(image.attributes('style')).toBeUndefined()
    expect(action.classes()).toEqual(expect.arrayContaining(['h-11', 'w-11']))
  })
})
