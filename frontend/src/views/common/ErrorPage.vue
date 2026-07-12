<template>
    <div class="flex min-h-[60vh] items-center justify-center nv-page">
        <ErrorState title-tag="h1" :code="status" :title="title" :message="message" :show-icon="false">
            <form v-if="numericStatus === 404" class="flex w-full gap-2 sm:order-first" @submit.prevent="search">
                <BaseInput v-model="keyword" :label="t('common.search')" :placeholder="t('common.search')" hide-label class="min-w-0 flex-1" />
                <BaseButton type="submit" :disabled="!keyword.trim()">{{ t('common.search') }}</BaseButton>
            </form>
            <BaseButton v-if="numericStatus === 500" type="button" variant="primary" @click="retry">
                {{ t('common.error.retry') }}
            </BaseButton>
            <BaseButton v-if="numericStatus === 403 && !authStore.isAuthenticated" type="button" variant="primary" @click="goToLogin">
                {{ t('common.login') }}
            </BaseButton>
            <a :href="homeHref" class="btn-secondary flex min-h-[44px] items-center justify-center px-4">
                {{ t('common.error.goHome') }}
            </a>
        </ErrorState>
    </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getSingleQueryValue } from '@/utils/routeQueryValue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const homeHref = computed(() => import.meta.env.BASE_URL || '/')
const status = computed(() => getSingleQueryValue(route.query.status) || 'Error')
const numericStatus = computed(() => Number(status.value))
const keyword = ref('')
const message = computed(() => {
    if (numericStatus.value === 403) return t('common.messages.forbidden')
    if (numericStatus.value === 404) return t('common.messages.notFound')
    if (numericStatus.value === 500) return t('common.messages.serverError')
    return t('common.error.defaultMessage')
})

function search() {
    const q = keyword.value.trim()
    if (q) void router.push({ name: 'search', query: { q } })
}

function retry() {
    window.location.reload()
}

function goToLogin() {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

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
