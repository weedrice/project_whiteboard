<template>
    <div class="min-h-screen flex items-center justify-center nv-page py-12 px-4 sm:px-6 lg:px-8">
        <div class="max-w-md w-full space-y-8 text-center">
            <div>
                <h2 class="mt-6 text-center text-9xl font-extrabold nv-title">
                    {{ status }}
                </h2>
                <h2 class="mt-4 text-3xl font-bold nv-title">
                    {{ title }}
                </h2>
                <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
                    {{ message }}
                </p>
            </div>
            <div class="mt-5">
                <a :href="homeHref" class="btn-primary flex w-full items-center justify-center">
                    {{ $t('common.error.goHome') }}
                </a>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()

const homeHref = computed(() => import.meta.env.BASE_URL || '/')
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
</script>
