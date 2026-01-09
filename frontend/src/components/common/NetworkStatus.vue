<template>
    <Transition name="slide-down">
        <div v-if="isOffline" class="network-status offline">
            <div class="network-status-content">
                <span class="network-status-icon">📡</span>
                <span class="network-status-message">{{ t('common.network.offline') }}</span>
            </div>
        </div>
        <div v-else-if="wasOffline" class="network-status online">
            <div class="network-status-content">
                <span class="network-status-icon">✅</span>
                <span class="network-status-message">{{ t('common.network.online') }}</span>
            </div>
        </div>
    </Transition>
</template>

<script setup lang="ts">
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { useI18n } from 'vue-i18n'
import { watch } from 'vue'

const { isOnline, isOffline, wasOffline } = useNetworkStatus()
const { t } = useI18n()

// 온라인 상태로 복귀 시 일정 시간 후 메시지 숨김
watch(isOnline, (newValue) => {
    if (newValue && wasOffline.value) {
        setTimeout(() => {
            // wasOffline은 useNetworkStatus에서 관리
        }, 3000)
    }
})
</script>

<style scoped>
.network-status {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 9999;
    padding: 0.75rem 1rem;
    text-align: center;
    font-weight: 500;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.network-status.offline {
    background-color: #ef4444;
    color: white;
}

.network-status.online {
    background-color: #10b981;
    color: white;
}

.network-status-content {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
}

.network-status-icon {
    font-size: 1.25rem;
}

.network-status-message {
    font-size: 0.875rem;
}

.slide-down-enter-active,
.slide-down-leave-active {
    transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
    transform: translateY(-100%);
    opacity: 0;
}
</style>
