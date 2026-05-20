import { nextTick, onBeforeUnmount, ref, watch, type Ref } from 'vue'

type PopoverStyle = {
    top: string
    left: string
    transform: string
}

const VIEWPORT_MARGIN = 12
const MOBILE_QUERY = '(max-width: 767px)'

function isMobileViewport(): boolean {
    return typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches
}

function getCenteredStyle(): PopoverStyle {
    if (typeof window === 'undefined') {
        return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }
    }

    return {
        top: `${window.innerHeight / 2}px`,
        left: `${window.innerWidth / 2}px`,
        transform: 'translate(-50%, -50%)',
    }
}

export function useAnchoredPopover(popoverRef: Ref<HTMLElement | null>, isOpen: Ref<boolean> = ref(false)) {
    const anchorElement = ref<HTMLElement | null>(null)
    const popoverStyle = ref<PopoverStyle>(getCenteredStyle())
    let isListening = false
    let animationFrameId: number | null = null

    function setAnchor(element?: HTMLElement | null) {
        anchorElement.value = element ?? null
    }

    function clearAnchor() {
        anchorElement.value = null
    }

    async function updatePosition() {
        cancelScheduledUpdate()
        await nextTick()
        const anchor = anchorElement.value
        const popover = popoverRef.value
        if (typeof window === 'undefined' || !anchor || !popover || isMobileViewport()) {
            popoverStyle.value = getCenteredStyle()
            return
        }

        const anchorRect = anchor.getBoundingClientRect()
        const popoverRect = popover.getBoundingClientRect()
        const width = popoverRect.width || 360
        const height = popoverRect.height || 240
        const anchorCenter = anchorRect.left + anchorRect.width / 2
        const preferredTop = anchorRect.bottom + 8
        const shouldOpenAbove = preferredTop + height > window.innerHeight - VIEWPORT_MARGIN
        const rawTop = shouldOpenAbove ? anchorRect.top - height - 8 : preferredTop
        const minLeft = VIEWPORT_MARGIN + width / 2
        const maxLeft = window.innerWidth - VIEWPORT_MARGIN - width / 2

        popoverStyle.value = {
            top: `${Math.max(VIEWPORT_MARGIN, rawTop)}px`,
            left: `${Math.min(Math.max(anchorCenter, minLeft), Math.max(minLeft, maxLeft))}px`,
            transform: 'translateX(-50%)',
        }
    }

    function scheduleUpdatePosition() {
        if (typeof window === 'undefined') return
        if (animationFrameId != null) return
        animationFrameId = window.requestAnimationFrame(() => {
            animationFrameId = null
            void updatePosition()
        })
    }

    function cancelScheduledUpdate() {
        if (typeof window === 'undefined' || animationFrameId == null) return
        window.cancelAnimationFrame(animationFrameId)
        animationFrameId = null
    }

    function onViewportChange() {
        scheduleUpdatePosition()
    }

    function startListening() {
        if (typeof window === 'undefined' || isListening) return
        window.addEventListener('resize', onViewportChange)
        window.addEventListener('scroll', onViewportChange, true)
        isListening = true
    }

    function stopListening() {
        if (typeof window === 'undefined' || !isListening) return
        window.removeEventListener('resize', onViewportChange)
        window.removeEventListener('scroll', onViewportChange, true)
        isListening = false
        cancelScheduledUpdate()
    }

    watch(isOpen, (open) => {
        if (open) {
            startListening()
            void updatePosition()
            return
        }
        stopListening()
    }, { immediate: true })

    function requestPositionUpdate() {
        void updatePosition()
    }

    onBeforeUnmount(() => {
        stopListening()
    })

    return {
        popoverStyle,
        setAnchor,
        clearAnchor,
        updatePosition: requestPositionUpdate,
    }
}
