import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PostViewportEffect from '../PostViewportEffect.vue'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api', () => ({ default: { get } }))
const content = '<template data-noviis-layer="viewport"><style>.x{position:fixed;inset:0}</style><div class="x">효과</div></template>'
const layer = (html: string) => `<template data-noviis-layer="viewport">${html}</template>`
const frame = () => document.querySelector<HTMLIFrameElement>('[data-testid="viewport-effect-frame"]')
let generation = 0

beforeEach(() => {
  generation = 0
  configureAuthQueryScope(() => generation)
  window.matchMedia = vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  get.mockReset()
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  configureAuthQueryScope(() => 0)
})

async function play(wrapper: ReturnType<typeof mount>) {
  await nextTick()
  await wrapper.get('button').trigger('click')
  await flushPromises()
}

describe('PostViewportEffect', () => {
  it('distinguishes unsupported images from download failures', async () => {
    const wrapper = mount(PostViewportEffect, { props: { content: '<template data-noviis-layer="viewport"><img src="https://example.com/image.png"></template>' } })
    expect(wrapper.get('[role="status"]').text()).toContain('이 이미지 주소는 사용할 수 없습니다')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(get).not.toHaveBeenCalled()
    await wrapper.setProps({ content: '<template data-noviis-layer="viewport"><img src="/api/v1/files/1"></template>' })
    get.mockRejectedValue(new Error('Network failure'))
    await play(wrapper)
    expect(wrapper.get('[role="status"]').text()).toContain('불러올')
    expect(wrapper.get('[role="status"]').text()).not.toContain('형식')
  })
  it.each([
    ['duplicate layers', `${content}${content}`, '중복된 영역을 제거'],
    ['HTML length', layer('x'.repeat(65_537)), '65,536자'],
    ['element count', layer('<span></span>'.repeat(301)), '300개'],
    ['CSS resources', layer('<style>.x{background:url(/api/v1/files/1)}</style><div class="x"></div>'), 'CSS로 이미지나 외부 리소스를'],
    ['image count', layer('<img src="/api/v1/files/1">'.repeat(9)), '최대 8개'],
    ['image source', layer('<img src="https://example.com/image.png">'), '일반 편집기에서 업로드한 img 태그'],
  ])('explains %s before playback without downloading', async (_label, html, message) => {
    const wrapper = mount(PostViewportEffect, { props: { content: html, preview: true } })
    expect(wrapper.get('[role="status"]').text()).toContain(message)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    await play(wrapper)
    expect(get).not.toHaveBeenCalled()
    expect(wrapper.find('iframe').exists()).toBe(false)
  })
  it.each([
    ['image MIME', 'image/svg+xml', 1, 1, 'PNG, JPEG, GIF, WebP'],
    ['response bytes', 'image/png', 8 * 1024 * 1024 + 1, 1, '8 MiB 이하'],
    ['repeated image data', 'image/png', 4 * 1024 * 1024, 3, '이미지 크기나 반복 횟수'],
  ])('explains rejected %s after the response', async (_label, type, size, count, message) => {
    const blob = new Blob(['x'], { type })
    Object.defineProperty(blob, 'size', { value: size })
    get.mockResolvedValue({ data: blob })
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL')
    const wrapper = mount(PostViewportEffect, { props: { content: layer('<img src="/api/v1/files/1">'.repeat(count)), preview: true } })
    await nextTick()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    await play(wrapper)
    expect(wrapper.get('[role="status"]').text()).toContain(message)
    expect(get).toHaveBeenCalledTimes(1)
    expect(read).not.toHaveBeenCalled()
    expect(wrapper.find('iframe').exists()).toBe(false)
  })
  it('plays only on request and keeps host-owned isolation attributes', async () => {
    const wrapper = mount(PostViewportEffect, { props: { content } })
    expect(frame()).toBeNull()
    await play(wrapper)
    expect(frame()?.getAttribute('sandbox')).toBe('')
    expect(frame()?.hasAttribute('inert')).toBe(true)
    expect(frame()?.getAttribute('tabindex')).toBe('-1')
    expect(frame()?.srcdoc).toContain("script-src 'none'")
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(frame()).toBeNull()
  })
  it('removes effects when content, access, or session changes', async () => {
    const wrapper = mount(PostViewportEffect, { props: { content } })
    await play(wrapper)
    await wrapper.setProps({ enabled: false })
    expect(frame()).toBeNull()
    expect(wrapper.find('button').exists()).toBe(false)
    await wrapper.setProps({ enabled: true })
    await play(wrapper)
    generation += 1
    notifyAuthSessionBoundary(generation)
    await nextTick()
    expect(frame()).toBeNull()
    await play(wrapper)
    await wrapper.setProps({ content: content.replace('효과', '새 효과') })
    expect(frame()).toBeNull()
  })
  it('stops on timeout, hidden page, and unmount', async () => {
    const wrapper = mount(PostViewportEffect, { props: { content } })
    vi.useFakeTimers()
    await nextTick()
    await wrapper.get('button').trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    expect(frame()).not.toBeNull()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(frame()).toBeNull()
    vi.useRealTimers()
    await play(wrapper)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    await nextTick()
    expect(frame()).toBeNull()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    await play(wrapper)
    wrapper.unmount()
    expect(frame()).toBeNull()
  })
  it('honors reduced motion and never starts a download', async () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() } as unknown as MediaQueryList)
    const wrapper = mount(PostViewportEffect, { props: { content } })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    await wrapper.get('button').trigger('click')
    expect(frame()).toBeNull()
    expect(get).not.toHaveBeenCalled()
  })
  it('drops a download completing after the effect was disabled', async () => {
    let resolve!: (value: { data: Blob }) => void
    get.mockImplementation(() => new Promise(done => { resolve = done }))
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL')
    const wrapper = mount(PostViewportEffect, { props: { content: '<template data-noviis-layer="viewport"><img src="/api/v1/files/1"></template>' } })
    await play(wrapper)
    expect(get).toHaveBeenCalledWith('/files/1', expect.objectContaining({ expectedSession: expect.any(Object), signal: expect.any(AbortSignal) }))
    await wrapper.setProps({ enabled: false })
    resolve({ data: new Blob(['x'], { type: 'image/png' }) })
    await flushPromises()
    expect(read).not.toHaveBeenCalled()
    expect(frame()).toBeNull()
  })
  it('clears image data on close and keeps preview inside its container', async () => {
    get.mockResolvedValue({ data: new Blob(['x'], { type: 'image/webp' }) })
    const wrapper = mount(PostViewportEffect, { props: { content: '<template data-noviis-layer="viewport"><img src="/api/v1/files/1"></template>', preview: true } })
    await play(wrapper)
    await vi.waitFor(() => expect(wrapper.find('iframe').exists()).toBe(true))
    expect(frame()).toBeNull()
    expect(wrapper.find('iframe').exists()).toBe(true)
    await wrapper.get('button').trigger('click')
    expect(wrapper.find('iframe').exists()).toBe(false)
  })
})
