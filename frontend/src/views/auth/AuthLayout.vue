<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const transitionName = ref('slide-left')
const cardHeight = ref<number | null>(null)
const contentRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | null = null

const routeOrder = {
    'login': 1,
    'signup': 2,
    'find-account': 3,
    'forgot-password': 4,
    'reset-password': 5
}

watch(() => route.name, (to, from) => {
    const toDepth = (to && routeOrder[to as keyof typeof routeOrder]) || 0
    const fromDepth = (from && routeOrder[from as keyof typeof routeOrder]) || 0
    transitionName.value = toDepth < fromDepth ? 'slide-right' : 'slide-left'
})

function updateHeight(entries: ResizeObserverEntry[]) {
    const entry = entries[0]
    if (entry) {
        cardHeight.value = entry.contentRect.height
    }
}

onMounted(() => {
    resizeObserver = new ResizeObserver(updateHeight)
    nextTick(() => {
        if (resizeObserver && contentRef.value) {
            resizeObserver.observe(contentRef.value)
        }
    })
})

onUnmounted(() => {
    resizeObserver?.disconnect()
})
</script>

<template>
    <div
        class="min-h-screen flex items-start justify-center bg-gray-50 dark:bg-gray-900 px-4 sm:px-6 lg:px-8 pt-20 transition-colors duration-200">
        <div
            class="auth-card max-w-lg w-full bg-white dark:bg-gray-800 shadow-xl rounded-2xl overflow-hidden relative flex flex-col transition-[min-height] duration-300 ease-out"
            :style="cardHeight !== null ? { minHeight: `${cardHeight}px` } : {}"
        >
            <div ref="contentRef" class="flex flex-col min-h-0">
                <router-view v-slot="{ Component }">
                    <transition :name="transitionName" mode="out-in">
                        <component :is="Component" class="flex-1 min-h-0" />
                    </transition>
                </router-view>
            </div>
        </div>
    </div>
</template>

<style scoped>
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
    transition: all 0.3s ease-out;
}

.slide-left-enter-from {
    opacity: 0;
    transform: translateX(20px);
}

.slide-left-leave-to {
    opacity: 0;
    transform: translateX(-20px);
}

.slide-right-enter-from {
    opacity: 0;
    transform: translateX(-20px);
}

.slide-right-leave-to {
    opacity: 0;
    transform: translateX(20px);
}
</style>
