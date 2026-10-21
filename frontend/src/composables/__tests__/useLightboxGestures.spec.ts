import { describe, expect, it, vi } from 'vitest'
import { useLightboxGestures } from '../useLightboxGestures'

function setup() {
  const close = vi.fn()
  const go = vi.fn()
  const image = document.createElement('img')
  Object.defineProperties(image, { clientWidth: { configurable: true, value: 600 }, clientHeight: { configurable: true, value: 400 } })
  const pointer = (id: number, x: number, y = 100) => ({
    pointerId: id, clientX: x, clientY: y, currentTarget: image, preventDefault: vi.fn(),
  } as unknown as PointerEvent)
  return { ...useLightboxGestures({ close, go }), close, go, image, pointer }
}

describe('useLightboxGestures', () => {
  it('does not turn a completed pinch at the minimum scale into a swipe', () => {
    const g = setup()
    g.handleImagePointerDown(g.pointer(1, 100))
    g.handleImagePointerDown(g.pointer(2, 280))
    g.handleImagePointerMove(g.pointer(1, 190))
    g.handleImagePointerMove(g.pointer(2, 200))
    g.handleImagePointerUp(g.pointer(1, 190))
    g.handleImagePointerUp(g.pointer(2, 200))
    expect(g.imageTransform.value.transform).toContain('scale(1)')
    expect(g.go).not.toHaveBeenCalled()
    expect(g.close).not.toHaveBeenCalled()
    g.handleImagePointerDown(g.pointer(3, 200))
    g.handleImagePointerUp(g.pointer(3, 100))
    expect(g.go).toHaveBeenCalledExactlyOnceWith(1)
  })

  it('does not navigate or close on cancelled or untracked pointers', () => {
    const g = setup()
    g.handleImagePointerDown(g.pointer(1, 100))
    g.handleImagePointerMove(g.pointer(1, 200))
    g.handleImagePointerCancel(g.pointer(1, 200))
    g.handleImagePointerUp(g.pointer(1, 200, 300))
    g.handleImagePointerDown(g.pointer(2, 100))
    g.handleImagePointerCancel(g.pointer(2, 100, 300))
    expect(g.go).not.toHaveBeenCalled()
    expect(g.close).not.toHaveBeenCalled()
  })

  it('rebases a remaining pinch finger before continuing to pan', () => {
    const g = setup()
    g.handleImagePointerDown(g.pointer(1, 100))
    g.handleImagePointerDown(g.pointer(2, 200))
    g.handleImagePointerMove(g.pointer(2, 300))
    expect(g.imageTransform.value.transform).toContain('scale(2)')
    g.handleImagePointerUp(g.pointer(1, 100))
    g.handleImagePointerMove(g.pointer(2, 310))
    expect(g.imageTransform.value.transform).toContain('translate3d(10px, 0px, 0)')
    g.handleImagePointerUp(g.pointer(2, 310))
    expect(g.go).not.toHaveBeenCalled()
  })

  it('clears active pointers when resetting the image', () => {
    const g = setup()
    g.handleImagePointerDown(g.pointer(1, 100))
    g.resetImageTransform()
    g.handleImagePointerUp(g.pointer(1, 200))
    expect(g.go).not.toHaveBeenCalled()
    expect(g.close).not.toHaveBeenCalled()
  })
})
