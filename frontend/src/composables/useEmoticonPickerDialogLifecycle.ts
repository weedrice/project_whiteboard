import { nextTick, onMounted, onUnmounted, watch, type Ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'
import { useFocusTrap } from '@/composables/useFocusTrap'

type DialogLifecycleOptions = {
    isOpen: () => boolean
    dialogRef: Ref<HTMLElement | null>
    close: () => void
    reset: () => void
}

export const useEmoticonPickerDialogLifecycle = ({
    isOpen,
    dialogRef,
    close,
    reset,
}: DialogLifecycleOptions) => {
    const { trapFocus, restoreFocus } = useFocusTrap(dialogRef, isOpen)

    const focusWhenOpen = () => {
        nextTick(() => {
            if (isOpen()) {
                trapFocus()
            }
        })
    }

    const handleDocumentKeydown = (event: KeyboardEvent) => {
        if (!isOpen() || event.key !== 'Escape') return

        event.preventDefault()
        close()
    }

    watch(isOpen, (newVal) => {
        if (!newVal) {
            reset()
            restoreFocus()
            return
        }

        focusWhenOpen()
    })

    useEventListener(() => document, 'keydown', handleDocumentKeydown)

    onMounted(() => {
        if (isOpen()) {
            focusWhenOpen()
        }
    })

    onUnmounted(() => {
        reset()
        restoreFocus()
    })
}
