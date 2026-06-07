import { onMounted, onUnmounted, toValue, type MaybeRefOrGetter } from 'vue'

type EventListenerTarget = EventTarget | null | undefined

export function useEventListener<TEvent extends Event = Event>(
  target: MaybeRefOrGetter<EventListenerTarget>,
  type: string,
  listener: EventListenerOrEventListenerObject | ((event: TEvent) => void),
  options?: AddEventListenerOptions | boolean
) {
  let currentTarget: EventTarget | null = null
  const normalizedListener = listener as EventListenerOrEventListenerObject

  const stop = () => {
    if (!currentTarget) {
      return
    }

    currentTarget.removeEventListener(type, normalizedListener, options)
    currentTarget = null
  }

  const start = () => {
    const nextTarget = toValue(target)
    if (!nextTarget || currentTarget === nextTarget) {
      return
    }

    stop()
    nextTarget.addEventListener(type, normalizedListener, options)
    currentTarget = nextTarget
  }

  onMounted(start)
  onUnmounted(stop)

  return {
    start,
    stop,
  }
}
