import { nextTick, onUnmounted, ref, watch, type Ref, type CSSProperties } from 'vue'
import { useViewportListeners } from '@/composables/useViewportListeners'
import { getViewportSize, getWindowScroll } from '@/utils/browserEnv'

export function useUserMenuPosition(
    buttonRef: Ref<HTMLElement | null>,
    dropdownRef: Ref<HTMLElement | null>,
    isOpen: Ref<boolean>,
) {
    const dropdownStyle = ref<CSSProperties>({})

    const updateDropdownPosition = () => {
        if (!buttonRef.value) return

        const rect = buttonRef.value.getBoundingClientRect()
        const horizontalPadding = 8
        const verticalPadding = 8
        const dropdownWidth = dropdownRef.value?.offsetWidth ?? 224
        const dropdownHeight = dropdownRef.value?.offsetHeight ?? 0
        const viewport = getViewportSize()
        const scroll = getWindowScroll()
        const minLeft = scroll.x + horizontalPadding
        const maxLeft = Math.max(minLeft, scroll.x + viewport.width - dropdownWidth - horizontalPadding)
        const preferredTop = rect.bottom + scroll.y + 5
        const preferredLeft = rect.left + scroll.x
        let top = preferredTop

        if (dropdownHeight > 0) {
            const maxTop = scroll.y + viewport.height - dropdownHeight - verticalPadding
            if (preferredTop > maxTop) {
                const aboveTop = rect.top + scroll.y - dropdownHeight - 5
                top = aboveTop >= scroll.y + verticalPadding
                    ? aboveTop
                    : Math.max(scroll.y + verticalPadding, maxTop)
            }
        }

        dropdownStyle.value = {
            top: `${top}px`,
            left: `${Math.min(Math.max(preferredLeft, minLeft), maxLeft)}px`,
        }
    }

    const handleViewportChange = () => {
        if (!isOpen.value) return
        updateDropdownPosition()
    }

    const {
        startListening: bindViewportListeners,
        stopListening: unbindViewportListeners,
    } = useViewportListeners(handleViewportChange)

    watch(isOpen, async (open) => {
        if (!open) {
            unbindViewportListeners()
            return
        }

        await nextTick()
        if (!isOpen.value) return
        updateDropdownPosition()
        bindViewportListeners()
    })

    onUnmounted(() => {
        unbindViewportListeners()
    })

    return {
        dropdownStyle,
        updateDropdownPosition,
        unbindViewportListeners,
    }
}
