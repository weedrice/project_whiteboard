<template>
    <div class="relative mb-6">
        <div ref="scrollContainer" class="overflow-x-auto scrollbar-hide -mx-4 px-4 sm:mx-0 sm:px-0 scroll-smooth">
            <nav class="flex space-x-4 sm:space-x-6 md:space-x-8 border-b nv-border min-w-max relative"
                :aria-label="$t('layout.a11y.userNavigation')">
                <a v-for="(tab, index) in tabs" :key="tab.nameKey" :href="tab.href"
                    :ref="el => { if (el) tabRefs[index] = el as HTMLElement }"
                    @click="(e) => handleTabClick(e, tab.href)"
                    @keydown="(e) => handleTabKeyDown(e, index)"
                    class="nv-focus-ring flex min-h-[44px] min-w-[44px] items-center justify-center whitespace-nowrap rounded-t px-1 py-3 text-xs transition-colors duration-200 touch-manipulation sm:py-4 sm:text-sm"
                    :class="[
                        isActive(tab.href)
                            ? 'text-[var(--nv-accent)] font-bold'
                            : 'nv-text-subtle user-nav-link font-medium'
                    ]" :aria-current="isActive(tab.href) ? 'page' : undefined">
                    {{ $t(tab.nameKey) }}
                </a>

                <div class="absolute bottom-0 h-0.5 bg-[var(--nv-accent)] transition-all duration-300 ease-out"
                    :style="underlineStyle"></div>
            </nav>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useEventListener } from '@/composables/useEventListener'
import { useThrottleFn } from '@/composables/useThrottle'
import { DEBOUNCE_DELAY } from '@/utils/constants'
import { isNarrowViewport } from '@/utils/browserEnv'
import { isInputFocused } from '@/utils/keyboard'
import { getMotionAwareScrollBehavior } from '@/utils/motion'
import type { UserNavigationTab } from '@/types/userNavigation'

const props = defineProps<{
    activePath: string
    tabs: UserNavigationTab[]
}>()

const emit = defineEmits<{
    (e: 'navigate', href: string): void
}>()

const tabRefs = ref<HTMLElement[]>([])
const scrollContainer = ref<HTMLElement | null>(null)
const underlineStyle = ref({ left: '0px', width: '0px', opacity: 1 })

function isActive(href: string) {
    if (href === '/mypage') {
        return props.activePath === '/mypage'
    }
    return props.activePath.startsWith(href)
}

const activeTabIndex = computed(() => {
    return props.tabs.findIndex(tab => isActive(tab.href))
})

function navigateToTab(index: number) {
    const tab = props.tabs[index]
    if (!tab) return
    emit('navigate', tab.href)
}

function focusTab(index: number) {
    nextTick(() => {
        tabRefs.value[index]?.focus()
    })
}

function navigateToTabAndFocus(index: number) {
    navigateToTab(index)
    focusTab(index)
}

function handleTabClick(event: MouseEvent, href: string) {
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey) {
        return
    }

    event.preventDefault()
    emit('navigate', href)
}

const handleTabKeyDown = (event: KeyboardEvent, currentIndex: number) => {
    const tabCount = props.tabs.length
    if (tabCount === 0) return

    switch (event.key) {
        case 'ArrowLeft':
            event.preventDefault()
            const prevIndex = currentIndex > 0 ? currentIndex - 1 : (tabCount - 1)
            navigateToTabAndFocus(prevIndex)
            break

        case 'ArrowRight':
            event.preventDefault()
            const nextIndex = currentIndex < tabCount - 1 ? currentIndex + 1 : 0
            navigateToTabAndFocus(nextIndex)
            break

        case 'Home':
            event.preventDefault()
            navigateToTabAndFocus(0)
            break

        case 'End':
            event.preventDefault()
            navigateToTabAndFocus(tabCount - 1)
            break
    }
}

const scrollTabToCenter = (el: HTMLElement) => {
    if (!scrollContainer.value) return
    const container = scrollContainer.value
    const containerWidth = container.clientWidth
    const elLeft = el.offsetLeft
    const elWidth = el.offsetWidth
    const scrollTarget = elLeft - (containerWidth / 2) + (elWidth / 2)
    container.scrollTo({ left: Math.max(0, scrollTarget), behavior: getMotionAwareScrollBehavior() })
}

const updateUnderline = () => {
    const index = activeTabIndex.value
    if (index !== -1 && tabRefs.value[index]) {
        const el = tabRefs.value[index]
        underlineStyle.value = {
            left: `${el.offsetLeft}px`,
            width: `${el.offsetWidth}px`,
            opacity: 1
        }

        if (scrollContainer.value && isNarrowViewport(640)) {
            scrollTabToCenter(el)
        } else if (scrollContainer.value) {
            const container = scrollContainer.value
            const scrollLeft = container.scrollLeft
            const containerWidth = container.clientWidth
            const elLeft = el.offsetLeft
            const elWidth = el.offsetWidth

            if (elLeft < scrollLeft) {
                container.scrollTo({ left: elLeft, behavior: getMotionAwareScrollBehavior() })
            } else if (elLeft + elWidth > scrollLeft + containerWidth) {
                container.scrollTo({ left: elLeft + elWidth - containerWidth, behavior: getMotionAwareScrollBehavior() })
            }
        }
    } else {
        underlineStyle.value = { left: '0px', width: '0px', opacity: 0 }
    }
}

watch([() => props.activePath, () => props.tabs], () => {
    nextTick(updateUnderline)
})

const throttledUpdateUnderline = useThrottleFn(updateUnderline, DEBOUNCE_DELAY.RESIZE)

function handleDocumentKeyDown(e: KeyboardEvent) {
    if (isInputFocused() || e.ctrlKey || e.altKey || e.metaKey) return
    const idx = activeTabIndex.value
    if (idx === -1) return
    const n = props.tabs.length
    if (n === 0) return
    if (e.key === '[' || e.key === '{') {
        e.preventDefault()
        const prev = idx > 0 ? idx - 1 : n - 1
        navigateToTab(prev)
    } else if (e.key === ']' || e.key === '}') {
        e.preventDefault()
        const next = idx < n - 1 ? idx + 1 : 0
        navigateToTab(next)
    }
}

useEventListener(() => window, 'resize', throttledUpdateUnderline)
useEventListener(() => document, 'keydown', handleDocumentKeyDown)

onMounted(() => {
    nextTick(updateUnderline)
})

onUnmounted(() => {
    throttledUpdateUnderline.cancel()
})
</script>

<style scoped>
.user-nav-link:hover {
    color: var(--nv-ink);
}
</style>
