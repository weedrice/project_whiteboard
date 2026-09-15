import { computed, nextTick, onUnmounted, shallowReactive, type Ref, watch } from 'vue'
import { useBodyScrollLock } from '@/composables/useBodyScrollLock'
import { useEventListener } from '@/composables/useEventListener'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { isComposingKeyboardEvent } from '@/utils/keyboard'

const dialogStack = shallowReactive<symbol[]>([])
const dialogFocusTargets = new Map<symbol, HTMLElement>()
const dialogOverlayTargets = new Map<symbol, HTMLElement>()
const managedBackgroundInert = new Set<HTMLElement>()

function syncBackgroundInert() {
  managedBackgroundInert.forEach((element) => element.removeAttribute('inert'))
  managedBackgroundInert.clear()

  const topId = dialogStack[dialogStack.length - 1]
  const topOverlay = topId ? dialogOverlayTargets.get(topId) : null
  if (!topOverlay) return

  let activeBranch: HTMLElement = topOverlay
  let parent = activeBranch.parentElement
  while (parent) {
    Array.from(parent.children).forEach((element) => {
      if (!(element instanceof HTMLElement) || element === activeBranch) return
      if (!element.hasAttribute('inert')) {
        element.setAttribute('inert', '')
        managedBackgroundInert.add(element)
      }
    })
    if (parent === document.body) break
    activeBranch = parent
    parent = parent.parentElement
  }
}

function registerDialog(dialogId: symbol) {
  if (!dialogStack.includes(dialogId)) dialogStack.push(dialogId)
}

function unregisterDialog(dialogId: symbol) {
  const index = dialogStack.indexOf(dialogId)
  if (index !== -1) dialogStack.splice(index, 1)
  dialogFocusTargets.delete(dialogId)
  dialogOverlayTargets.delete(dialogId)
  syncBackgroundInert()
}

function isTopDialogId(dialogId: symbol) {
  return dialogStack[dialogStack.length - 1] === dialogId
}

interface DialogLifecycleOptions {
  isOpen: Readonly<Ref<boolean>>
  dialogRef: Ref<HTMLElement | null>
  overlayRef?: Ref<HTMLElement | null>
  close: () => void
  closeOnEscape?: boolean | (() => boolean)
  lockScroll?: boolean
  initialFocus?: 'first' | 'container'
  returnFocusTarget?: () => HTMLElement | null
}

export function useDialogLifecycle({
  isOpen,
  dialogRef,
  overlayRef = dialogRef,
  close,
  closeOnEscape = true,
  lockScroll = true,
  initialFocus = 'first',
  returnFocusTarget,
}: DialogLifecycleOptions) {
  const dialogId = Symbol('dialog-lifecycle')
  const isTopDialog = computed(() => isOpen.value && isTopDialogId(dialogId))
  const shouldLockScroll = computed(() => lockScroll && isOpen.value)
  const { trapFocus } = useFocusTrap(dialogRef, isTopDialog)
  let returnFocusElement: HTMLElement | null = null

  useBodyScrollLock(shouldLockScroll)

  const rememberFocus = (event: FocusEvent) => {
    if (event.target instanceof HTMLElement && dialogRef.value?.contains(event.target)) {
      dialogFocusTargets.set(dialogId, event.target)
    }
  }

  const restoreReturnFocus = () => {
    const target = returnFocusElement
    returnFocusElement = null
    if (!target) return
    void nextTick(() => target.focus())
  }

  const focusTopDialog = () => {
    void nextTick(() => {
      if (!isTopDialog.value) return
      if (overlayRef.value) dialogOverlayTargets.set(dialogId, overlayRef.value)
      syncBackgroundInert()
      const rememberedFocus = dialogFocusTargets.get(dialogId)
      if (rememberedFocus?.isConnected) {
        rememberedFocus.focus()
        return
      }
      if (initialFocus === 'container') {
        if (dialogRef.value && !dialogRef.value.hasAttribute('tabindex')) {
          dialogRef.value.setAttribute('tabindex', '-1')
        }
        dialogRef.value?.focus()
      } else {
        trapFocus()
      }
      if (document.activeElement instanceof HTMLElement) {
        dialogFocusTargets.set(dialogId, document.activeElement)
      }
    })
  }

  const escapeEnabled = () => typeof closeOnEscape === 'function' ? closeOnEscape() : closeOnEscape

  const handleKeyDown = (event: KeyboardEvent) => {
    if (
      event.key !== 'Escape'
      || event.defaultPrevented
      || isComposingKeyboardEvent(event)
      || !isOpen.value
      || !escapeEnabled()
    ) return
    if (!isTopDialogId(dialogId)) return
    event.preventDefault()
    event.stopImmediatePropagation()
    close()
  }

  watch(isOpen, (open) => {
    if (open) {
      const parentDialogId = dialogStack[dialogStack.length - 1]
      const activeElement = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null
      const activeParentFocus = parentDialogId && activeElement?.closest('[role="dialog"]')
        ? activeElement
        : null
      if (parentDialogId && activeParentFocus) {
        dialogFocusTargets.set(parentDialogId, activeParentFocus)
      }
      returnFocusElement = (
        activeParentFocus
        ?? returnFocusTarget?.()
        ?? (parentDialogId ? dialogFocusTargets.get(parentDialogId) : null)
        ?? activeElement
      )
      registerDialog(dialogId)
      focusTopDialog()
      return
    }

    const wasTopDialog = isTopDialogId(dialogId)
    unregisterDialog(dialogId)
    if (wasTopDialog && dialogStack.length === 0) restoreReturnFocus()
    else returnFocusElement = null
  }, { immediate: true, flush: 'sync' })

  watch(isTopDialog, (isTop, wasTop) => {
    if (
      !isTop
      && wasTop
      && document.activeElement instanceof HTMLElement
      && dialogRef.value?.contains(document.activeElement)
    ) {
      dialogFocusTargets.set(dialogId, document.activeElement)
    }
    if (isTop && !wasTop) focusTopDialog()
  }, { flush: 'sync' })

  useEventListener(() => document, 'keydown', handleKeyDown, { capture: true })
  useEventListener(() => document, 'focusin', rememberFocus)
  useEventListener(() => document, 'focusout', rememberFocus)

  onUnmounted(() => {
    const wasTopDialog = isTopDialogId(dialogId)
    unregisterDialog(dialogId)
    if (wasTopDialog && dialogStack.length === 0) restoreReturnFocus()
    else returnFocusElement = null
  })

  return {
    isTopDialog,
  }
}
