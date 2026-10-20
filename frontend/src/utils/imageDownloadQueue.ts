/** An image owner shares four transport slots and can prioritize explicit retries. */
export function createImageDownloadQueue() {
  const waiting: Array<() => void> = []
  let active = 0

  function pump() {
    while (active < 4 && waiting.length) waiting.shift()!()
  }

  return {
    run<T>(task: () => Promise<T>, signal: AbortSignal, priority = false): Promise<T> {
      return new Promise<T>((resolve, reject) => {
        const abort = () => {
          const index = waiting.indexOf(start)
          if (index >= 0) waiting.splice(index, 1)
          signal.removeEventListener('abort', abort)
          reject(new DOMException('Image request cancelled', 'AbortError'))
        }
        const start = () => {
          signal.removeEventListener('abort', abort)
          if (signal.aborted) { abort(); return }
          active += 1
          void Promise.resolve().then(task).then(resolve, reject).finally(() => {
            active -= 1
            pump()
          })
        }
        if (signal.aborted) { abort(); return }
        signal.addEventListener('abort', abort, { once: true })
        if (priority) waiting.unshift(start)
        else waiting.push(start)
        pump()
      })
    },
  }
}

