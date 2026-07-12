import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmoticonGridSkeleton from '@/components/emoticon/EmoticonGridSkeleton.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('loading skeleton semantics', () => {
  it('keeps individual skeleton shapes decorative', () => {
    const wrapper = mount(BaseSkeleton)

    expect(wrapper.attributes('aria-hidden')).toBe('true')
  })

  it('announces a skeleton collection as one busy status', () => {
    const wrapper = mount(EmoticonGridSkeleton, { props: { count: 2 } })

    expect(wrapper.attributes()).toMatchObject({
      role: 'status',
      'aria-live': 'polite',
      'aria-busy': 'true',
      'aria-label': 'common.loading',
    })
    expect(wrapper.findAll('[aria-hidden="true"]')).toHaveLength(2)
  })
})
