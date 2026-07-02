import { onMounted, onUnmounted, toValue, watchEffect, type MaybeRefOrGetter, type WatchStopHandle } from 'vue'

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
  let stopWatchingTarget: WatchStopHandle | null = null
  const normalizedListener = listener as EventListenerOrEventListenerObject

  const detach = () => {
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

  const bindCurrentTarget = () => {
    const nextTarget = toValue(target)
    if (!nextTarget) {
      detach()
      return
    }

    if (currentTarget === nextTarget) {
      return
    }

    detach()
    if (options === undefined) {
      nextTarget.addEventListener(type, normalizedListener)
    } else {
      nextTarget.addEventListener(type, normalizedListener, options)
    }
    currentTarget = nextTarget
  }

  const start = () => {
    if (stopWatchingTarget) {
      return
    }

    stopWatchingTarget = watchEffect(bindCurrentTarget)
  }

  const stop = () => {
    stopWatchingTarget?.()
    stopWatchingTarget = null
    detach()
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
