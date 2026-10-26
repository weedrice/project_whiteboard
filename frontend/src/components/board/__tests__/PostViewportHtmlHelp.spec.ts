import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PostViewportHtmlHelp from '../PostViewportHtmlHelp.vue'

describe('PostViewportHtmlHelp', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the example as text and only copies it after an explicit action', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    const wrapper = mount(PostViewportHtmlHelp)

    expect(wrapper.get('details').attributes('open')).toBeUndefined()
    expect(wrapper.find('style').exists()).toBe(false)
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.get('code').text()).toContain('<template data-noviis-layer="viewport">')
    expect(writeText).not.toHaveBeenCalled()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith(wrapper.get('code').text())
    expect(wrapper.get('[role="status"]').text()).toBe('예시를 복사했습니다.')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('keeps the example available for manual copying if the clipboard fails', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } })
    const wrapper = mount(PostViewportHtmlHelp)
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('직접 선택해 복사')
    expect(wrapper.get('code').text()).toContain('@keyframes float')
  })
})
