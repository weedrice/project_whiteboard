<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const transitionName = ref('slide-left')

const routeOrder = {
    'login': 1,
    'signup': 2,
    'find-account': 3
}

watch(() => route.name, (to, from) => {
    const toDepth = routeOrder[to] || 0
    const fromDepth = routeOrder[from] || 0
    transitionName.value = toDepth < fromDepth ? 'slide-right' : 'slide-left'
})
</script>

<template>
    <div
        class="min-h-screen flex items-start justify-center bg-gray-50 dark:bg-gray-900 px-4 sm:px-6 lg:px-8 pt-20 transition-colors duration-200">
        <div
            class="max-w-md w-full bg-white dark:bg-gray-800 shadow-xl rounded-2xl overflow-hidden relative min-h-[500px] flex flex-col">
            <router-view v-slot="{ Component }">
                <transition :name="transitionName" mode="out-in">
                    <component :is="Component" class="flex-1" />
                </transition>
            </router-view>
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
