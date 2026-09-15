import { computed, watch, type Ref } from 'vue'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'

type DialogLifecycleOptions = {
    isOpen: () => boolean
    dialogRef: Ref<HTMLElement | null>
    overlayRef: Ref<HTMLElement | null>
    close: () => void
    reset: () => void
}

export const useEmoticonPickerDialogLifecycle = ({
    isOpen,
    dialogRef,
    overlayRef,
    close,
    reset,
}: DialogLifecycleOptions) => {
    const open = computed(isOpen)
    const lifecycle = useDialogLifecycle({
        isOpen: open,
        dialogRef,
        overlayRef,
        close,
        lockScroll: false,
    })

    watch(isOpen, (newVal) => {
        if (!newVal) reset()
    })

    return lifecycle
}
