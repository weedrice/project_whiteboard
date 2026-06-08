import { onMounted, onUnmounted, toValue, type MaybeRefOrGetter } from 'vue'

type EventListenerTarget = EventTarget | null | undefined
type EventListenerLifecycleOptions = {
  autoStart?: boolean
}

export function useEventListener<TEvent extends Event = Event>(
  target: MaybeRefOrGetter<EventListenerTarget>,
  type: string,
  listener: EventListenerOrEventListenerObject | ((event: TEvent) => void),
  options?: AddEventListenerOptions | boolean,
  lifecycleOptions: EventListenerLifecycleOptions = {},
) {
  let currentTarget: EventTarget | null = null
  const normalizedListener = listener as EventListenerOrEventListenerObject

  const stop = () => {
    if (!currentTarget) {
      return
    }

    if (options === undefined) {
      currentTarget.removeEventListener(type, normalizedListener)
    } else {
      currentTarget.removeEventListener(type, normalizedListener, options)
    }
    currentTarget = null
  }

  const start = () => {
    const nextTarget = toValue(target)
    if (!nextTarget || currentTarget === nextTarget) {
      return
    }

    stop()
    if (options === undefined) {
      nextTarget.addEventListener(type, normalizedListener)
    } else {
      nextTarget.addEventListener(type, normalizedListener, options)
    }
    currentTarget = nextTarget
  }

  if (lifecycleOptions.autoStart ?? true) {
    onMounted(start)
  }
  onUnmounted(stop)

  return {
    start,
    stop,
  }
}
