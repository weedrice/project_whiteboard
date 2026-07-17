<template>
    <Transition name="slide-down">
        <div v-if="isOffline" class="network-status offline" role="status" aria-live="polite">
            <div class="network-status-content">
                <span class="network-status-icon" aria-hidden="true">!</span>
                <span class="network-status-message">{{ t('common.network.offline') }}</span>
            </div>
        </div>
        <div v-else-if="wasOffline" class="network-status online" role="status" aria-live="polite">
            <div class="network-status-content">
                <span class="network-status-icon" aria-hidden="true">OK</span>
                <span class="network-status-message">{{ t('common.network.online') }}</span>
            </div>
        </div>
        <div v-else-if="pwaUpdateStatus === 'failed'" class="network-status update-failed" role="alert">
            <div class="network-status-content">
                <span class="network-status-message">{{ t('common.pwa.updateFailed') }}</span>
                <button type="button" class="network-status-retry nv-focus-ring" @click="retryPwaUpdate">
                    {{ t('common.pwa.retryUpdate') }}
                </button>
            </div>
        </div>
    </Transition>
</template>

<script setup lang="ts">
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { useI18n } from 'vue-i18n'
import { watch, onUnmounted } from 'vue'
import { pwaUpdateStatus, retryPwaUpdate } from '@/pwa'

const { isOnline, isOffline, wasOffline, resetWasOffline } = useNetworkStatus()
const { t } = useI18n()

let timeoutId: ReturnType<typeof setTimeout> | null = null

watch(
    [isOnline, wasOffline],
    ([newIsOnline, newWasOffline]) => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }

        if (newIsOnline && newWasOffline) {
            timeoutId = setTimeout(() => {
                resetWasOffline()
                timeoutId = null
            }, 3000)
        }
    },
    { immediate: true },
)

onUnmounted(() => {
    if (timeoutId) {
        clearTimeout(timeoutId)
    }
})
</script>

<style scoped>
.network-status {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: var(--nv-z-toast);
    padding: 0.75rem 1rem;
    text-align: center;
    font-weight: 500;
    box-shadow: var(--nv-shadow-card);
}

.network-status.offline {
    background-color: var(--nv-danger);
    color: white;
}

.network-status.online {
    background-color: var(--nv-success);
    color: white;
}

.network-status.update-failed {
    background-color: var(--nv-warning);
    color: var(--nv-on-warning, #111827);
}

.network-status-retry {
    border: 1px solid currentColor;
    border-radius: 0.5rem;
    cursor: pointer;
    font: inherit;
    padding: 0.25rem 0.75rem;
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
