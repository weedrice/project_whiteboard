import { computed, reactive, ref } from 'vue'

interface UseLightboxGesturesOptions {
  close: () => void
  go: (delta: number) => void
}

interface DragStart {
  x: number
  y: number
  offsetX: number
  offsetY: number
}

export function useLightboxGestures(options: UseLightboxGesturesOptions) {
  const imageScale = ref(1)
  const imageOffset = reactive({ x: 0, y: 0 })
  let dragStart: DragStart | null = null
  let pinchStartDistance = 0
  let pinchStartScale = 1
  const activePointers = new Map<number, { x: number; y: number }>()

  const imageTransform = computed(() => ({
    transform: `translate3d(${imageOffset.x}px, ${imageOffset.y}px, 0) scale(${imageScale.value})`,
  }))

  function resetImageTransform() {
    imageScale.value = 1
    imageOffset.x = 0
    imageOffset.y = 0
    dragStart = null
    pinchStartDistance = 0
    activePointers.clear()
  }

  function getPointerDistance() {
    const pointers = Array.from(activePointers.values())
    if (pointers.length < 2) return 0
    const [first, second] = pointers
    if (!first || !second) return 0
    return Math.hypot(first.x - second.x, first.y - second.y)
  }

  function clampScale(scale: number) {
    return Math.min(Math.max(scale, 1), 4)
  }

  function clampOffset(value: number, imageSize: number, viewportSize: number) {
    const overflow = Math.max(0, (imageSize * imageScale.value - viewportSize) / 2)
    const limit = overflow + 48
    return Math.min(Math.max(value, -limit), limit)
  }

  function clampImageOffset(target: EventTarget | null) {
    const image = target instanceof HTMLImageElement ? target : null
    if (!image || imageScale.value <= 1) {
      if (imageScale.value <= 1) {
        imageOffset.x = 0
        imageOffset.y = 0
      }
      return
    }
    imageOffset.x = clampOffset(imageOffset.x, image.clientWidth, window.innerWidth)
    imageOffset.y = clampOffset(imageOffset.y, image.clientHeight, window.innerHeight)
  }

  function handleImagePointerDown(event: PointerEvent) {
    activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
    ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)

    if (activePointers.size === 1) {
      dragStart = { x: event.clientX, y: event.clientY, offsetX: imageOffset.x, offsetY: imageOffset.y }
      return
    }

    if (activePointers.size === 2) {
      pinchStartDistance = getPointerDistance()
      pinchStartScale = imageScale.value
    }
  }

  function handleImagePointerMove(event: PointerEvent) {
    if (!activePointers.has(event.pointerId)) return
    activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY })

    if (activePointers.size >= 2 && pinchStartDistance > 0) {
      event.preventDefault()
      imageScale.value = clampScale(pinchStartScale * (getPointerDistance() / pinchStartDistance))
      if (imageScale.value === 1) {
        imageOffset.x = 0
        imageOffset.y = 0
      } else {
        clampImageOffset(event.currentTarget)
      }
      return
    }

    if (!dragStart) return
    const deltaX = event.clientX - dragStart.x
    const deltaY = event.clientY - dragStart.y
    if (imageScale.value > 1) {
      event.preventDefault()
      imageOffset.x = dragStart.offsetX + deltaX
      imageOffset.y = dragStart.offsetY + deltaY
      clampImageOffset(event.currentTarget)
    }
  }

  function handleImagePointerUp(event: PointerEvent) {
    activePointers.delete(event.pointerId)
    ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)

    if (dragStart && imageScale.value === 1) {
      const deltaX = event.clientX - dragStart.x
      const deltaY = event.clientY - dragStart.y
      if (deltaY > 80 && Math.abs(deltaY) > Math.abs(deltaX)) {
        options.close()
      } else if (Math.abs(deltaX) > 64 && Math.abs(deltaY) < 60) {
        options.go(deltaX < 0 ? 1 : -1)
      }
    }

    if (activePointers.size === 0) {
      dragStart = null
      pinchStartDistance = 0
    }
  }

  function toggleZoom() {
    if (imageScale.value > 1) {
      resetImageTransform()
      return
    }
    imageScale.value = 2
  }

  return {
    imageTransform,
    resetImageTransform,
    handleImagePointerDown,
    handleImagePointerMove,
    handleImagePointerUp,
    toggleZoom,
  }
}

