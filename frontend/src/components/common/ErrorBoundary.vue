<template>
    <div v-if="hasError" class="error-boundary">
        <div class="error-boundary-content">
            <h2 class="error-boundary-title">{{ title }}</h2>
            <p class="error-boundary-message">{{ message }}</p>
            <div class="error-boundary-actions">
                <BaseButton @click="handleRetry" variant="primary">
                    {{ t('common.error.retry') }}
                </BaseButton>
                <a :href="homeHref" class="btn-secondary flex items-center justify-center" @click="handleGoHome">
                    {{ t('common.error.goHome') }}
                </a>
            </div>
            <details v-if="showDetails" class="error-boundary-details">
                <summary>{{ t('common.error.showDetails') }}</summary>
                <pre class="error-boundary-stack">{{ errorStack }}</pre>
            </details>
        </div>
    </div>
    <slot v-else />
</template>

<script setup lang="ts">
import { computed, ref, onErrorCaptured, provide } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import logger from '@/utils/logger'

interface Props {
    fallback?: (error: Error, instance: any, info: string) => void
    showDetails?: boolean
}

const props = withDefaults(defineProps<Props>(), {
    showDetails: false
})

const emit = defineEmits<{
    error: [error: Error, instance: any, info: string]
}>()

const router = useRouter()
const { t } = useI18n()
const homeHref = computed(() => import.meta.env.BASE_URL || '/')

const hasError = ref(false)
const error = ref<Error | null>(null)
const errorInfo = ref<string>('')
const errorStack = ref<string>('')

const title = ref(t('common.error.unknown'))
const message = ref(t('common.error.defaultMessage'))

const isChunkLoadError = (err: Error | null) => {
    if (!err) return false
    return (
        err.name === 'ChunkLoadError'
        || err.message.includes('Loading chunk')
        || err.message.includes('Failed to fetch dynamically imported module')
        || err.message.includes('Importing a module script failed')
    )
}

onErrorCaptured((err: Error, instance: any, info: string) => {
    hasError.value = true
    error.value = err
    errorInfo.value = info
    errorStack.value = err.stack || err.toString()

    logger.error('ErrorBoundary caught error:', err, info)

    if (props.fallback) {
        props.fallback(err, instance, info)
    }

    emit('error', err, instance, info)

    if (err.name === 'ChunkLoadError' || err.message.includes('Loading chunk')) {
        title.value = t('common.error.chunkLoadError')
        message.value = t('common.error.chunkLoadErrorDescription')
    } else if (err.name === 'NetworkError' || err.message.includes('network')) {
        title.value = t('common.error.networkError')
        message.value = t('common.error.networkErrorDescription')
    } else {
        title.value = t('common.error.unknown')
        message.value = err.message || t('common.error.defaultMessage')
    }

    return false
})

const resetError = () => {
    hasError.value = false
    error.value = null
    errorInfo.value = ''
    errorStack.value = ''
}

const handleRetry = async () => {
    const shouldReload = isChunkLoadError(error.value)
    resetError()

    if (shouldReload) {
        window.location.reload()
        return
    }

    try {
        await router.replace(router.currentRoute.value.fullPath)
    } catch (retryError: unknown) {
        logger.warn('ErrorBoundary soft retry failed; falling back to reload.', retryError)
        window.location.reload()
    }
}

const handleGoHome = () => {
    resetError()
}

provide('resetError', resetError)
</script>

<style scoped>
.error-boundary {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: var(--nv-page);
    padding: 3rem 1rem;
}

.error-boundary-content {
    max-width: 28rem;
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 2rem;
    text-align: center;
}

.error-boundary-title {
    margin-top: 1.5rem;
    text-align: center;
    font-size: 1.875rem;
    font-weight: 800;
    color: var(--nv-text);
}

.error-boundary-message {
    margin-top: 0.5rem;
    font-size: 0.875rem;
    color: var(--nv-text-muted);
}

.error-boundary-actions {
    margin-top: 1.25rem;
    display: flex;
    gap: 1rem;
    justify-content: center;
}

.error-boundary-details {
    margin-top: 1.5rem;
    text-align: left;
}

.error-boundary-stack {
    margin-top: 0.5rem;
    padding: 1rem;
    background-color: var(--nv-surface-muted);
    border-radius: 0.375rem;
    font-size: 0.75rem;
    overflow: auto;
    max-height: 16rem;
    font-family: monospace;
}
</style>
