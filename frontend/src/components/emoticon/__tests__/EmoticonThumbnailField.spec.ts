import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmoticonThumbnailField from '../EmoticonThumbnailField.vue'

describe('EmoticonThumbnailField', () => {
  it('uses the explicit edit button instead of making the preview image interactive', () => {
    const wrapper = mount(EmoticonThumbnailField, {
      props: {
        inputId: 'thumbnail',
        accept: 'image/png',
        preview: '/preview.png',
        mode: 'edit',
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })

    const image = wrapper.get('img')
    const editButton = wrapper.get('button')

    expect(image.attributes('tabindex')).toBeUndefined()
    expect(image.attributes('title')).toBeUndefined()
    expect(editButton.attributes('aria-label')).toBe('common.edit')
  })
})
