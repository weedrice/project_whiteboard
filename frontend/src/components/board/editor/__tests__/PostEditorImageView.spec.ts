import { Editor } from '@tiptap/core'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createPostEditorExtensions } from '../postEditorExtensions'
import PostEditorImageView from '../PostEditorImageView.vue'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api', () => ({ default: { get: mocks.get } }))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('PostEditorImageView', () => {
  let editor: Editor | null = null

  beforeEach(() => {
    mocks.get.mockReset()
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:authenticated')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    editor?.destroy()
    editor = null
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  function mountImageView(
    alignment = 'inline',
    attrs: Partial<Record<'width' | 'styleWidth' | 'src' | 'data-server-src', string | null>> = {},
  ) {
    const editorRoot = document.createElement('div')
    Object.defineProperty(editorRoot, 'clientWidth', {
      configurable: true,
      value: 800,
    })
    const updateAttributes = vi.fn()
    const setNodeSelection = vi.fn()
    const wrapper = mount(PostEditorImageView, {
      props: {
        editor: { view: { dom: editorRoot }, commands: { setNodeSelection } },
        node: {
          attrs: {
            src: 'https://cdn.noviis.kr/files/1',
            alt: '기존 설명',
            title: null,
            loading: 'lazy',
            width: null,
            styleWidth: null,
            alignment,
            ...attrs,
          },
        },
        decorations: [],
        innerDecorations: {},
        view: {},
        selected: true,
        extension: {},
        HTMLAttributes: {},
        getPos: () => 1,
        updateAttributes,
        deleteNode: vi.fn(),
      } as never,
    })

    return { wrapper, updateAttributes, setNodeSelection, editorRoot }
  }

  it('restores protected images through authenticated blobs without changing saved node attributes', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image']) })
    const { wrapper, updateAttributes } = mountImageView('inline', {
      src: '/api/v1/files/42',
      'data-server-src': '/api/v1/files/42',
    })
    expect(wrapper.get('img').attributes('src')).toBeUndefined()
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledWith('/files/42', expect.objectContaining({
      responseType: 'blob', signal: expect.any(AbortSignal), skipGlobalErrorHandler: true,
    }))
    expect(wrapper.get('img').attributes('src')).toBe('blob:authenticated')
    expect(wrapper.props('node').attrs.src).toBe('/api/v1/files/42')
    expect(wrapper.props('node').attrs['data-server-src']).toBe('/api/v1/files/42')
    expect(updateAttributes).not.toHaveBeenCalled()
    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:authenticated')
  })

  it.each(['blob:local-upload', 'https://cdn.noviis.kr/files/1'])(
    'preserves the existing %s preview without requesting or revoking it', async (src) => {
      const { wrapper } = mountImageView('inline', { src, 'data-server-src': '/api/v1/files/42' })
      await flushPromises()
      expect(wrapper.get('img').attributes('src')).toBe(src)
      expect(mocks.get).not.toHaveBeenCalled()
      wrapper.unmount()
      expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    },
  )

  it('aborts an old source request and ignores its late response after the source changes', async () => {
    let resolveOld!: (response: { data: Blob }) => void
    mocks.get.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
    mocks.get.mockResolvedValueOnce({ data: new Blob(['new']) })
    const { wrapper } = mountImageView('inline', { src: '/api/v1/files/42' })
    await flushPromises()
    const signal = mocks.get.mock.calls[0]![1].signal as AbortSignal
    await wrapper.setProps({ node: { attrs: { src: '/api/v1/files/43' } } } as never)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(mocks.get).toHaveBeenLastCalledWith('/files/43', expect.any(Object))
    resolveOld({ data: new Blob(['old']) })
    await flushPromises()
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(wrapper.get('img').attributes('src')).toBe('blob:authenticated')
  })

  it('revokes the previous blob when the source changes to a local preview', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image']) })
    const { wrapper } = mountImageView('inline', { src: '/api/v1/files/42' })
    await flushPromises()
    await wrapper.setProps({ node: { attrs: { src: 'blob:local-upload' } } } as never)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:authenticated')
    expect(wrapper.get('img').attributes('src')).toBe('blob:local-upload')
  })

  it('clears the previous session image and keeps forbidden images hidden', async () => {
    mocks.get.mockResolvedValueOnce({ data: new Blob(['image']) })
    mocks.get.mockRejectedValueOnce({ response: { status: 403 } })
    const { wrapper } = mountImageView('inline', { src: '/api/v1/files/42' })
    await flushPromises()
    notifyAuthSessionBoundary(1)
    await flushPromises()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:authenticated')
    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img').attributes('src')).toBeUndefined()
  })

  it('discards an earlier session response even when its request resolves after cancellation', async () => {
    let resolveOld!: (response: { data: Blob }) => void
    mocks.get.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
    mocks.get.mockRejectedValueOnce({ response: { status: 403 } })
    const { wrapper } = mountImageView('inline', { src: '/api/v1/files/42' })
    await flushPromises()
    const signal = mocks.get.mock.calls[0]![1].signal as AbortSignal
    notifyAuthSessionBoundary(3)
    expect(signal.aborted).toBe(true)
    await flushPromises()
    resolveOld({ data: new Blob(['private image']) })
    await flushPromises()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(wrapper.get('img').attributes('src')).toBeUndefined()
  })

  it('aborts pending requests on unmount and unsubscribes from session changes', async () => {
    let resolveImage!: (response: { data: Blob }) => void
    mocks.get.mockImplementationOnce(() => new Promise((resolve) => { resolveImage = resolve }))
    const { wrapper } = mountImageView('inline', { src: '/api/v1/files/42' })
    await flushPromises()
    const signal = mocks.get.mock.calls[0]![1].signal as AbortSignal
    wrapper.unmount()
    expect(signal.aborted).toBe(true)
    resolveImage({ data: new Blob(['image']) })
    notifyAuthSessionBoundary(2)
    await flushPromises()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(mocks.get).toHaveBeenCalledTimes(1)
  })

  it('shows size and alignment controls instead of an alt text dialog', async () => {
    const { wrapper, updateAttributes, setNodeSelection } = mountImageView()

    expect(wrapper.find('.post-editor-image-controls').exists()).toBe(true)
    expect(wrapper.find('.image-alt-popover').exists()).toBe(false)
    expect(wrapper.get('img').attributes('alt')).toBe('기존 설명')

    await wrapper.get('img').trigger('click')
    expect(setNodeSelection).toHaveBeenCalledWith(1)

    await wrapper.get('[data-image-width="50"]').trigger('click')
    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: '50%', width: null, height: null })

    await wrapper.get('[data-image-align="center"]').trigger('click')
    expect(updateAttributes).toHaveBeenCalledWith({ alignment: 'center' })

    await wrapper.get('[data-image-align="inline"]').trigger('click')
    expect(updateAttributes).toHaveBeenCalledWith({ alignment: 'inline' })
  })

  it('reports and adjusts legacy pixel widths as editor-relative percentages', async () => {
    const legacyView = mountImageView('inline', { width: '400' })
    const legacyHandle = legacyView.wrapper.get('.post-editor-image-resize-handle')
    expect(legacyHandle.attributes('aria-valuenow')).toBe('50')

    const pixelStyleView = mountImageView('inline', { styleWidth: '320px' })
    const pixelStyleHandle = pixelStyleView.wrapper.get('.post-editor-image-resize-handle')
    expect(pixelStyleHandle.attributes('aria-valuenow')).toBe('40')

    await pixelStyleHandle.trigger('keydown', { key: 'ArrowLeft' })
    expect(pixelStyleView.updateAttributes).toHaveBeenCalledWith({
      styleWidth: '35%',
      width: null,
      height: null,
    })
  })

  it('refreshes slider values and keyboard limits from the actual image container', async () => {
    const { wrapper, updateAttributes } = mountImageView('inline', { styleWidth: '320px' })
    const imageNode = wrapper.get('.post-editor-image-node').element
    const parentNode = imageNode.parentElement as HTMLElement
    let imageWidth = 320
    let containerWidth = 800
    Object.defineProperty(imageNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: imageWidth,
        height: 180,
        top: 0,
        right: imageWidth,
        bottom: 180,
        left: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })
    Object.defineProperty(parentNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: containerWidth,
        height: 400,
        top: 0,
        right: containerWidth,
        bottom: 400,
        left: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })

    window.dispatchEvent(new Event('resize'))
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
    await nextTick()
    const handle = wrapper.get('.post-editor-image-resize-handle')
    expect(handle.attributes('aria-valuenow')).toBe('40')
    expect(handle.attributes('aria-valuemin')).toBe('10')

    containerWidth = 200
    imageWidth = 100
    window.dispatchEvent(new Event('resize'))
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
    await nextTick()
    expect(handle.attributes('aria-valuenow')).toBe('50')
    expect(handle.attributes('aria-valuemin')).toBe('25')

    await handle.trigger('keydown', { key: 'Home' })
    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: '25%', width: null, height: null })
  })

  it('uses one minimum-width rule for presets, keyboard controls, and slider accessibility', async () => {
    const { wrapper, updateAttributes } = mountImageView('inline', { styleWidth: '10%' })
    const imageNode = wrapper.get('.post-editor-image-node').element
    const parentNode = imageNode.parentElement as HTMLElement
    Object.defineProperty(imageNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ width: 20, height: 20, top: 0, right: 20, bottom: 20, left: 0, x: 0, y: 0, toJSON: () => ({}) }),
    })
    Object.defineProperty(parentNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ width: 200, height: 200, top: 0, right: 200, bottom: 200, left: 0, x: 0, y: 0, toJSON: () => ({}) }),
    })

    window.dispatchEvent(new Event('resize'))
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
    await nextTick()

    const handle = wrapper.get('.post-editor-image-resize-handle')
    expect(handle.attributes('aria-valuenow')).toBe('10')
    expect(handle.attributes('aria-valuemin')).toBe('10')

    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(updateAttributes).toHaveBeenLastCalledWith({ styleWidth: '25%', width: null, height: null })

    await wrapper.get('[data-image-width="25"]').trigger('click')
    expect(updateAttributes).toHaveBeenLastCalledWith({ styleWidth: '25%', width: null, height: null })

    updateAttributes.mockClear()
    handle.element.dispatchEvent(
      createPointerEvent('pointerdown', { pointerId: 9, clientX: 100, button: 0, bubbles: true }),
    )
    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 9, clientX: 0 }))
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 9 }))
    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: '25%', width: null, height: null })
  })

  it('keeps intrinsic sizing explicit and preserves safe legacy width units', () => {
    const intrinsicView = mountImageView('center')
    const intrinsicNode = intrinsicView.wrapper.get('.post-editor-image-node')
    expect(intrinsicNode.classes()).toContain('post-editor-image-node--intrinsic')
    expect(intrinsicNode.attributes('style')).not.toContain('width:')

    const percentageView = mountImageView('right', { width: '50%' })
    const percentageNode = percentageView.wrapper.get('.post-editor-image-node')
    expect(percentageNode.classes()).not.toContain('post-editor-image-node--intrinsic')
    expect(percentageNode.attributes('style')).toContain('width: 50%')
    expect(percentageView.wrapper.get('.post-editor-image-resize-handle').attributes('aria-valuenow')).toBe('50')

    const legacyUnitView = mountImageView('left', { width: '12rem' })
    expect(legacyUnitView.wrapper.get('.post-editor-image-node').attributes('style')).toContain('width: 12px')
  })

  it('keeps the floating controls inside the editor bounds', async () => {
    const resizeState: { callback?: ResizeObserverCallback } = {}
    const observe = vi.fn()
    const disconnect = vi.fn()
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: ResizeObserverCallback) {
        resizeState.callback = callback
      }

      observe = observe
      unobserve = vi.fn()
      disconnect = disconnect
    })
    const { wrapper, editorRoot } = mountImageView('right')
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))

    Object.defineProperty(editorRoot, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: 400,
        height: 600,
        top: 0,
        right: 500,
        bottom: 600,
        left: 100,
        x: 100,
        y: 0,
        toJSON: () => ({}),
      }),
    })
    const controls = wrapper.get('.post-editor-image-controls')
    Object.defineProperty(controls.element, 'getBoundingClientRect', {
      configurable: true,
      value: () => {
        const shift = Number.parseFloat(
          (controls.element as HTMLElement).style.getPropertyValue('--post-editor-image-controls-shift'),
        ) || 0
        return {
          width: 280,
          height: 40,
          top: 0,
          right: 730 + shift,
          bottom: 40,
          left: 450 + shift,
          x: 450 + shift,
          y: 0,
          toJSON: () => ({}),
        }
      },
    })

    expect(observe).toHaveBeenCalledWith(editorRoot)
    expect(observe).toHaveBeenCalledWith(controls.element)
    resizeState.callback?.([], {} as ResizeObserver)
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))

    expect(controls.attributes('style')).toContain('max-width: 384px')
    expect(controls.attributes('style')).toContain('--post-editor-image-controls-shift: -238px')

    wrapper.unmount()
    expect(disconnect).toHaveBeenCalled()
  })

  it('resizes from the corner handle and can restore the original dimensions', async () => {
    const { wrapper, updateAttributes } = mountImageView()
    const imageNode = wrapper.get('.post-editor-image-node').element
    Object.defineProperty(imageNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: 320,
        height: 180,
        top: 0,
        right: 320,
        bottom: 180,
        left: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })

    wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(new MouseEvent('pointerdown', {
      bubbles: true,
      button: 0,
      clientX: 100,
    }))
    document.dispatchEvent(new MouseEvent('pointermove', { clientX: 220 }))
    document.dispatchEvent(new MouseEvent('pointerup'))

    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: '55%', width: null, height: null })

    await wrapper.get('[data-image-width="original"]').trigger('click')
    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: null, width: null, height: null })
  })

  it('ignores move and end events from pointers outside the active resize session', () => {
    const { wrapper, updateAttributes } = mountImageView()
    const imageNode = wrapper.get('.post-editor-image-node').element
    Object.defineProperty(imageNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: 320,
        height: 180,
        top: 0,
        right: 320,
        bottom: 180,
        left: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })

    wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(
      createPointerEvent('pointerdown', { pointerId: 1, clientX: 100, button: 0, bubbles: true }),
    )
    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 2, clientX: 300 }))
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 2 }))
    expect(updateAttributes).not.toHaveBeenCalled()

    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 1, clientX: 220 }))
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 1 }))
    expect(updateAttributes).toHaveBeenCalledWith({ styleWidth: '55%', width: null, height: null })

    updateAttributes.mockClear()
    wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(
      createPointerEvent('pointerdown', { pointerId: 3, clientX: 100, button: 0, bubbles: true }),
    )
    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 3, clientX: 220 }))
    document.dispatchEvent(createPointerEvent('pointercancel', { pointerId: 3 }))
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 3 }))
    expect(updateAttributes).not.toHaveBeenCalled()
  })

  it('cancels active resizing when selection is lost or the window blurs', async () => {
    const { wrapper, updateAttributes } = mountImageView()
    const imageNode = wrapper.get('.post-editor-image-node').element
    Object.defineProperty(imageNode, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        width: 320,
        height: 180,
        top: 0,
        right: 320,
        bottom: 180,
        left: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })

    wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(
      createPointerEvent('pointerdown', { pointerId: 1, clientX: 100, button: 0, bubbles: true }),
    )
    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 1, clientX: 220 }))
    await wrapper.setProps({ selected: false })
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 1 }))
    expect(updateAttributes).not.toHaveBeenCalled()
    expect(wrapper.get('.post-editor-image-node').classes()).toContain('post-editor-image-node--intrinsic')

    await wrapper.setProps({ selected: true })
    wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(
      createPointerEvent('pointerdown', { pointerId: 2, clientX: 100, button: 0, bubbles: true }),
    )
    document.dispatchEvent(createPointerEvent('pointermove', { pointerId: 2, clientX: 220 }))
    window.dispatchEvent(new Event('blur'))
    document.dispatchEvent(createPointerEvent('pointerup', { pointerId: 2 }))
    expect(updateAttributes).not.toHaveBeenCalled()
  })

  it('keeps the resize handle under the pointer for centered and right-aligned images', () => {
    const centerView = mountImageView('center')
    Object.defineProperty(centerView.wrapper.get('.post-editor-image-node').element, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ width: 320, height: 180, top: 0, right: 560, bottom: 180, left: 240, x: 240, y: 0, toJSON: () => ({}) }),
    })
    centerView.wrapper.get('.post-editor-image-resize-handle').element.dispatchEvent(new MouseEvent('pointerdown', {
      bubbles: true,
      button: 0,
      clientX: 560,
    }))
    document.dispatchEvent(new MouseEvent('pointermove', { clientX: 600 }))
    document.dispatchEvent(new MouseEvent('pointerup'))
    expect(centerView.updateAttributes).toHaveBeenCalledWith({ styleWidth: '50%', width: null, height: null })

    const rightView = mountImageView('right')
    Object.defineProperty(rightView.wrapper.get('.post-editor-image-node').element, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ width: 320, height: 180, top: 0, right: 800, bottom: 180, left: 480, x: 480, y: 0, toJSON: () => ({}) }),
    })
    const rightHandle = rightView.wrapper.get('.post-editor-image-resize-handle')
    expect(rightHandle.classes()).toContain('post-editor-image-resize-handle--left')
    rightHandle.element.dispatchEvent(new MouseEvent('pointerdown', {
      bubbles: true,
      button: 0,
      clientX: 480,
    }))
    document.dispatchEvent(new MouseEvent('pointermove', { clientX: 400 }))
    document.dispatchEvent(new MouseEvent('pointerup'))
    expect(rightView.updateAttributes).toHaveBeenCalledWith({ styleWidth: '50%', width: null, height: null })
  })

  it('serializes image width and alignment while preserving existing alt text', () => {
    editor = new Editor({
      content: '<p><img src="https://cdn.noviis.kr/files/1" alt="기존 설명"></p>',
      extensions: createPostEditorExtensions(),
    })
    editor.commands.setNodeSelection(1)
    editor.commands.updateAttributes('image', { styleWidth: '50%', alignment: 'right' })

    const document = new DOMParser().parseFromString(editor.getHTML(), 'text/html')
    const image = document.querySelector('img')
    expect(image?.getAttribute('style')).toContain('width: 50%')
    expect(image?.classList.contains('tiptap-image-align-right')).toBe(true)
    expect(image?.getAttribute('alt')).toBe('기존 설명')
  })
})

function createPointerEvent(
  type: string,
  init: MouseEventInit & { pointerId: number },
): PointerEvent {
  const event = new MouseEvent(type, init)
  Object.defineProperty(event, 'pointerId', { value: init.pointerId })
  return event as PointerEvent
}
