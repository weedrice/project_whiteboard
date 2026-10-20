import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { expect, it } from 'vitest'
import { vImageState } from '@/directives/imageState'
import i18n, { setAppLocale } from '@/i18n'

it('updates directive errors without a component locale dependency and stops watching on unmount', async () => {
  await setAppLocale('ko')
  const wrapper = mount(defineComponent({
    directives: { imageState: vImageState },
    template: '<img v-image-state src="/missing.png" alt="Picture" title="Original">',
  }), { attachTo: document.body })
  const image = wrapper.get('img')
  try {
    await image.trigger('error')
    await flushPromises()
    expect(image.attributes('title')).toBe(i18n.global.t('common.imageLoadError'))
    await setAppLocale('en')
    await flushPromises()
    const english = i18n.global.t('common.imageLoadError')
    expect(image.attributes('title')).toBe(english)
    expect(image.attributes('aria-label')).toBe('Picture — ' + english)
    expect(image.attributes('data-image-state')).toBe('error')
  } finally {
    wrapper.unmount()
    await setAppLocale('ko')
    await flushPromises()
  }
  expect(image.element.title).toBe('Original')
  expect(image.element.hasAttribute('aria-label')).toBe(false)
})
