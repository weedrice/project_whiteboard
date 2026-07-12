import { onScopeDispose, watch, type Ref } from 'vue'

let lockCount = 0
let previousBodyOverflow: string | null = null

const lockBodyScroll = () => {
  if (lockCount === 0) previousBodyOverflow = document.body.style.overflow
  lockCount += 1
  document.body.style.overflow = 'hidden'
}

const unlockBodyScroll = () => {
  if (lockCount === 0) return
  lockCount -= 1
  if (lockCount !== 0) return

  document.body.style.overflow = previousBodyOverflow ?? ''
  previousBodyOverflow = null
}

export function useBodyScrollLock(locked: Readonly<Ref<boolean>>) {
  let ownsLock = false

  const releaseLock = () => {
    if (!ownsLock) return
    unlockBodyScroll()
    ownsLock = false
  }

  watch(locked, (shouldLock) => {
    if (shouldLock && !ownsLock) {
      lockBodyScroll()
      ownsLock = true
      return
    }

    if (!shouldLock) releaseLock()
  }, { immediate: true })

  onScopeDispose(releaseLock, true)
}
