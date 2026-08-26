<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { inquiryApi } from '@/api/inquiry'
import type { InquiryCategory, InquiryStatus } from '@/types/inquiry'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { formatDateTimeOrDash } from '@/utils/date'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { useI18n } from 'vue-i18n'

const page = ref(0)
const { t } = useI18n()
const status = ref<InquiryStatus | ''>('')
const category = ref<InquiryCategory | ''>('')
watch([status, category], () => { page.value = 0 })

const params = computed(() => ({
  page: page.value,
  size: 20,
  sort: 'createdAt,desc',
  status: status.value || undefined,
  category: category.value || undefined,
}))

const query = useApiPageQuery({
  queryKey: computed(() => ['inquiries', 'mine', params.value]),
  request: ({ signal }) => inquiryApi.getMine(params.value, { signal }),
  meta: AUTH_SCOPED_QUERY_META,
})

const categories: InquiryCategory[] = ['ACCOUNT', 'SERVICE_USE', 'TECHNICAL', 'CONTENT_OPERATION', 'SUGGESTION', 'OTHER']
const statuses: InquiryStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const categoryLabel = (value: InquiryCategory) => t(`inquiry.category.${value}`)
const statusLabel = (value: InquiryStatus) => t(`inquiry.status.${value}`)
</script>

<template>
  <section class="mx-auto max-w-5xl space-y-5">
    <PageHeader :title="t('inquiry.list.title')" :description="t('inquiry.list.description')">
      <template #actions><BaseButton to="/inquiries/new">{{ t('inquiry.list.create') }}</BaseButton></template>
    </PageHeader>

    <div class="flex flex-wrap gap-3 rounded-xl border nv-border nv-surface p-4">
      <label class="text-sm">{{ t('inquiry.common.status') }}
        <select v-model="status" class="ml-2 rounded-md border nv-border nv-surface px-3 py-2">
          <option value="">{{ t('inquiry.common.all') }}</option><option v-for="item in statuses" :key="item" :value="item">{{ statusLabel(item) }}</option>
        </select>
      </label>
      <label class="text-sm">{{ t('inquiry.common.category') }}
        <select v-model="category" class="ml-2 rounded-md border nv-border nv-surface px-3 py-2">
          <option value="">{{ t('inquiry.common.all') }}</option><option v-for="item in categories" :key="item" :value="item">{{ categoryLabel(item) }}</option>
        </select>
      </label>
    </div>

    <div v-if="query.isLoading.value" class="rounded-xl border nv-border nv-surface p-8 text-center nv-text-muted">{{ t('inquiry.common.loading') }}</div>
    <div v-else-if="query.error.value" class="rounded-xl nv-status-danger p-4">{{ t('inquiry.common.loadFailed') }}</div>
    <div v-else-if="!query.data.value?.content.length" class="rounded-xl border nv-border nv-surface p-8 text-center nv-text-muted">{{ t('inquiry.common.empty') }}</div>
    <ul v-else class="space-y-3">
      <li v-for="item in query.data.value?.content" :key="item.inquiryId">
        <router-link :to="`/inquiries/${item.inquiryId}`" class="block rounded-xl border nv-border nv-surface p-4 transition hover:border-[var(--nv-accent)]">
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div><span class="text-xs nv-text-muted">{{ categoryLabel(item.category) }}</span><h2 class="font-semibold">{{ item.title }}</h2></div>
            <span class="rounded-full nv-surface-soft px-3 py-1 text-xs font-semibold">{{ statusLabel(item.status) }}</span>
          </div>
          <p class="mt-2 line-clamp-2 whitespace-pre-wrap text-sm nv-text-muted">{{ item.lastPublicMessageSummary }}</p>
          <time class="mt-3 block text-xs nv-text-muted">{{ t('inquiry.list.modifiedAt', { date: formatDateTimeOrDash(item.modifiedAt) }) }}</time>
        </router-link>
      </li>
    </ul>
    <Pagination :current-page="page" :total-pages="query.data.value?.totalPages ?? 0" @page-change="page = $event" />
  </section>
</template>
