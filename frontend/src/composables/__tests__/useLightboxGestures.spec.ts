import { describe, expect, it, vi } from 'vitest'
import { useLightboxGestures } from '../useLightboxGestures'

function setup() {
  const close = vi.fn()
  const go = vi.fn()
  const image = document.createElement('img')
  Object.defineProperties(image, { clientWidth: { value: 600 }, clientHeight: { value: 400 } })
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
    expect(g.imageScale.value).toBe(1)
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
    expect(g.imageScale.value).toBe(2)
    g.handleImagePointerUp(g.pointer(1, 100))
    g.handleImagePointerMove(g.pointer(2, 310))
    expect(g.imageTransform.value.transform).toContain('translate3d(10px, 0px, 0)')
    g.handleImagePointerUp(g.pointer(2, 310))
    expect(g.go).not.toHaveBeenCalled()
  })

  it('bounds keyboard zoom and pan and clears gestures on reset', () => {
    const g = setup()
    g.zoomBy(10, g.image)
    expect(g.imageScale.value).toBe(4)
    g.panBy(10000, -10000, g.image)
    const xLimit = Math.max(0, (600 * 4 - window.innerWidth) / 2) + 48
    const yLimit = Math.max(0, (400 * 4 - window.innerHeight) / 2) + 48
    expect(g.imageTransform.value.transform).toContain(`translate3d(${xLimit}px, ${-yLimit}px, 0)`)
    g.zoomBy(-10, g.image)
    expect(g.imageTransform.value.transform).toBe('translate3d(0px, 0px, 0) scale(1)')
    g.handleImagePointerDown(g.pointer(1, 100))
    g.resetImageTransform()
    g.handleImagePointerUp(g.pointer(1, 200))
    expect(g.go).not.toHaveBeenCalled()
  })
})
