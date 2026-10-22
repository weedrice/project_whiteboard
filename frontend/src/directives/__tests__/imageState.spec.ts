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

it.each([
  { title: 'Replacement title', label: 'Replacement label' },
  { title: undefined, label: undefined },
  { title: 'Original title', label: 'Replacement label' },
  { title: 'Replacement title', label: 'Original label' },
])('preserves Vue-patched metadata when a failed image is replaced: %j', async ({ title, label }) => {
  const wrapper = mount(defineComponent({
    directives: { imageState: vImageState },
    props: {
      src: String,
      title: String,
      label: String,
      failed: Boolean,
      pending: Boolean,
    },
    template: '<img v-image-state="{ failed, pending }" :src="src" :title="title" :aria-label="label" alt="Picture">',
  }), {
    props: { src: '/failed.png', title: 'Original title', label: 'Original label', failed: true },
    attachTo: document.body,
  })
  const image = wrapper.get('img').element
  try {
    expect(image.dataset.imageState).toBe('error')
    await wrapper.setProps({ src: '/replacement.png', title, label, failed: false, pending: true })
    await flushPromises()
    expect(wrapper.get('img').element).toBe(image)
    expect(image.dataset.imageState).toBe('loading')
    expect(image.getAttribute('title')).toBe(title ?? null)
    expect(image.getAttribute('aria-label')).toBe(label ?? null)

    Object.defineProperties(image, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 100 },
    })
    await wrapper.setProps({ pending: false })
    await wrapper.get('img').trigger('load')
    expect(image.dataset.imageState).toBe('loaded')
    expect(image.getAttribute('title')).toBe(title ?? null)
    expect(image.getAttribute('aria-label')).toBe(label ?? null)
  } finally {
    wrapper.unmount()
  }
})
