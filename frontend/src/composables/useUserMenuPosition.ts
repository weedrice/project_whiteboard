import { nextTick, onUnmounted, ref, watch, type Ref, type CSSProperties } from 'vue'

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
        const minLeft = window.scrollX + horizontalPadding
        const maxLeft = Math.max(minLeft, window.scrollX + window.innerWidth - dropdownWidth - horizontalPadding)
        const preferredTop = rect.bottom + window.scrollY + 5
        const preferredLeft = rect.left + window.scrollX
        let top = preferredTop

        if (dropdownHeight > 0) {
            const maxTop = window.scrollY + window.innerHeight - dropdownHeight - verticalPadding
            if (preferredTop > maxTop) {
                const aboveTop = rect.top + window.scrollY - dropdownHeight - 5
                top = aboveTop >= window.scrollY + verticalPadding
                    ? aboveTop
                    : Math.max(window.scrollY + verticalPadding, maxTop)
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

    const bindViewportListeners = () => {
        window.addEventListener('resize', handleViewportChange)
        window.addEventListener('scroll', handleViewportChange, true)
    }

    const unbindViewportListeners = () => {
        window.removeEventListener('resize', handleViewportChange)
        window.removeEventListener('scroll', handleViewportChange, true)
    }

    watch(isOpen, async (open) => {
        if (!open) {
            unbindViewportListeners()
            return
        }

        await nextTick()
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
