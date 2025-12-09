<template>
    <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
        <div class="max-w-md w-full space-y-8 text-center">
            <div>
                <h2 class="mt-6 text-center text-9xl font-extrabold text-gray-900">
                    {{ status }}
                </h2>
                <h2 class="mt-4 text-3xl font-bold text-gray-900">
                    {{ title }}
                </h2>
                <p class="mt-2 text-sm text-gray-600">
                    {{ message }}
                </p>
            </div>
            <div class="mt-5">
                <button
                    @click="goHome"
                    class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                    {{ $t('common.error.goHome') }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const status = computed(() => route.query.status || 'Error')
const message = computed(() => route.query.message || t('common.error.defaultMessage'))

const title = computed(() => {
    switch (Number(status.value)) {
        case 404:
            return t('common.error.notFound')
        case 403:
            return t('common.error.forbidden')
        case 500:
            return t('common.error.serverError')
        default:
            return t('common.error.unknown')
    }
})

const goHome = () => {
    router.push('/')
}
</script>
