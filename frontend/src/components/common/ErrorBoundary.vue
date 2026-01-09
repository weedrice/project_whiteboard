<template>
    <div v-if="hasError" class="error-boundary">
        <div class="error-boundary-content">
            <h2 class="error-boundary-title">{{ title }}</h2>
            <p class="error-boundary-message">{{ message }}</p>
            <div class="error-boundary-actions">
                <BaseButton @click="handleRetry" variant="primary">
                    {{ t('common.error.retry') }}
                </BaseButton>
                <BaseButton @click="handleGoHome" variant="secondary">
                    {{ t('common.error.goHome') }}
                </BaseButton>
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
import { ref, onErrorCaptured, provide, inject } from 'vue'
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

const hasError = ref(false)
const error = ref<Error | null>(null)
const errorInfo = ref<string>('')
const errorStack = ref<string>('')

const title = ref(t('common.error.unknown'))
const message = ref(t('common.error.defaultMessage'))

onErrorCaptured((err: Error, instance: any, info: string) => {
    hasError.value = true
    error.value = err
    errorInfo.value = info
    errorStack.value = err.stack || err.toString()

    // 에러 로깅
    logger.error('ErrorBoundary caught error:', err, info)

    // 커스텀 fallback 호출
    if (props.fallback) {
        props.fallback(err, instance, info)
    }

    // 부모에게 에러 전달
    emit('error', err, instance, info)

    // 에러 타입에 따른 메시지 설정
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

    // 에러가 전파되지 않도록 false 반환
    return false
})

const handleRetry = () => {
    hasError.value = false
    error.value = null
    errorInfo.value = ''
    errorStack.value = ''
    // 페이지 새로고침
    window.location.reload()
}

const handleGoHome = () => {
    router.push('/')
}

// 에러 리셋 함수 제공 (자식 컴포넌트에서 사용 가능)
const resetError = () => {
    hasError.value = false
    error.value = null
    errorInfo.value = ''
    errorStack.value = ''
}

provide('resetError', resetError)
</script>

<style scoped>
.error-boundary {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f9fafb;
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
    color: #111827;
}

.error-boundary-message {
    margin-top: 0.5rem;
    font-size: 0.875rem;
    color: #4b5563;
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
    background-color: #f3f4f6;
    border-radius: 0.375rem;
    font-size: 0.75rem;
    overflow: auto;
    max-height: 16rem;
    font-family: monospace;
}
</style>
