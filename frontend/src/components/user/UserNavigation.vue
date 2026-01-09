<template>
    <div class="relative mb-6">
        <div ref="scrollContainer" class="overflow-x-auto scrollbar-hide -mx-4 px-4 sm:mx-0 sm:px-0"
            @touchstart="handleTouchStart" @touchmove="handleTouchMove" @touchend="handleTouchEnd">
            <nav class="flex space-x-8 border-b border-gray-200 min-w-max relative" aria-label="Tabs" role="tablist">
                <router-link 
                    v-for="(tab, index) in tabs" 
                    :key="tab.nameKey" 
                    :to="tab.href"
                    :ref="el => { if (el) tabRefs[index] = (el as ComponentPublicInstance).$el }"
                    @keydown="(e) => handleTabKeyDown(e, index)"
                    class="whitespace-nowrap py-4 px-1 text-sm transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 rounded-t" 
                    :class="[
                        isActive(tab.href)
                            ? 'text-indigo-600 dark:text-indigo-400 font-bold'
                            : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 font-medium'
                    ]" 
                    :aria-current="isActive(tab.href) ? 'page' : undefined"
                    role="tab"
                    :tabindex="isActive(tab.href) ? 0 : -1">
                    {{ $t(tab.nameKey) }}
                </router-link>

                <!-- Animated Underline -->
                <div class="absolute bottom-0 h-0.5 bg-indigo-500 transition-all duration-300 ease-out"
                    :style="underlineStyle"></div>
            </nav>
        </div>
    </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ref, computed, watch, nextTick, onMounted, onUnmounted, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThrottleFn } from '@/composables/useThrottle'
import { DEBOUNCE_DELAY } from '@/utils/constants'

// User Navigation Component
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

interface Tab {
    nameKey: string;
    href: string;
}

const tabs: Tab[] = [
    { nameKey: 'common.myPage', href: '/mypage' },
    { nameKey: 'user.tabs.settings', href: '/mypage/settings' },
    { nameKey: 'common.notifications', href: '/mypage/notifications' },
    { nameKey: 'common.mailbox', href: '/mypage/messages' },
    { nameKey: 'common.points', href: '/mypage/points' },
    { nameKey: 'user.scraps', href: '/mypage/scraps' },
    { nameKey: 'user.tabs.subscriptions', href: '/mypage/subscriptions' },
    { nameKey: 'user.tabs.recent', href: '/mypage/recent' },
    { nameKey: 'user.tabs.reports', href: '/mypage/reports' },
    { nameKey: 'user.tabs.blocked', href: '/mypage/blocked' },
]

const tabRefs = ref<HTMLElement[]>([])
const scrollContainer = ref<HTMLElement | null>(null)
const underlineStyle = ref({ left: '0px', width: '0px', opacity: 1 })

function isActive(href: string) {
    if (href === '/mypage') {
        return route.path === '/mypage'
    }
    return route.path.startsWith(href)
}

const activeTabIndex = computed(() => {
    return tabs.findIndex(tab => isActive(tab.href))
})

// 키보드 네비게이션
const handleTabKeyDown = (event: KeyboardEvent, currentIndex: number) => {
    const tabCount = tabs.length
    
    switch (event.key) {
        case 'ArrowLeft':
            event.preventDefault()
            const prevIndex = currentIndex > 0 ? currentIndex - 1 : (tabCount - 1)
            router.push(tabs[prevIndex].href)
            nextTick(() => {
                tabRefs.value[prevIndex]?.focus()
            })
            break
            
        case 'ArrowRight':
            event.preventDefault()
            const nextIndex = currentIndex < tabCount - 1 ? currentIndex + 1 : 0
            router.push(tabs[nextIndex].href)
            nextTick(() => {
                tabRefs.value[nextIndex]?.focus()
            })
            break
            
        case 'Home':
            event.preventDefault()
            router.push(tabs[0].href)
            nextTick(() => {
                tabRefs.value[0]?.focus()
            })
            break
            
        case 'End':
            event.preventDefault()
            router.push(tabs[tabCount - 1].href)
            nextTick(() => {
                tabRefs.value[tabCount - 1]?.focus()
            })
            break
    }
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

        // Scroll into view if needed (for mobile)
        if (scrollContainer.value) {
            const container = scrollContainer.value
            const scrollLeft = container.scrollLeft
            const containerWidth = container.clientWidth
            const elLeft = el.offsetLeft
            const elWidth = el.offsetWidth

            if (elLeft < scrollLeft) {
                container.scrollTo({ left: elLeft, behavior: 'smooth' })
            } else if (elLeft + elWidth > scrollLeft + containerWidth) {
                container.scrollTo({ left: elLeft + elWidth - containerWidth, behavior: 'smooth' })
            }
        }
    } else {
        underlineStyle.value = { left: '0px', width: '0px', opacity: 0 }
    }
}

watch(() => route.path, () => {
    nextTick(updateUnderline)
})

const throttledUpdateUnderline = useThrottleFn(updateUnderline, DEBOUNCE_DELAY.RESIZE)

onMounted(() => {
    // Wait for refs to be populated
    nextTick(updateUnderline)
    // Add resize listener to update underline position (throttled)
    window.addEventListener('resize', throttledUpdateUnderline)
})

onUnmounted(() => {
    // Remove resize listener
    window.removeEventListener('resize', throttledUpdateUnderline)
})

// Mobile Swipe Logic
const touchStartX = ref(0)
const touchStartY = ref(0)

const handleTouchStart = (e: TouchEvent) => {
    touchStartX.value = e.touches[0].clientX
    touchStartY.value = e.touches[0].clientY
}

const handleTouchMove = (e: TouchEvent) => {
    // Optional: prevent default if horizontal swipe is detected to stop vertical scroll
    // But usually better to let browser handle it unless it's a dedicated slider
}

const handleTouchEnd = (e: TouchEvent) => {
    const touchEndX = e.changedTouches[0].clientX
    const touchEndY = e.changedTouches[0].clientY

    const diffX = touchStartX.value - touchEndX
    const diffY = touchStartY.value - touchEndY

    // Threshold for swipe
    if (Math.abs(diffX) > 50 && Math.abs(diffY) < 50) {
        const currentIndex = activeTabIndex.value
        if (diffX > 0) {
            // Swipe Left -> Next Tab
            if (currentIndex < tabs.length - 1) {
                router.push(tabs[currentIndex + 1].href)
            }
        } else {
            // Swipe Right -> Prev Tab
            if (currentIndex > 0) {
                router.push(tabs[currentIndex - 1].href)
            }
        }
    }
}
</script>
