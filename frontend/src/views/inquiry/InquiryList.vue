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
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import InquiryStatusBadge from '@/components/inquiry/InquiryStatusBadge.vue'
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
const categoryOptions = computed(() => [
  { value: '', label: t('inquiry.common.all') },
  ...categories.map((value) => ({ value, label: categoryLabel(value) })),
])
const statusOptions = computed(() => [
  { value: '', label: t('inquiry.common.all') },
  ...statuses.map((value) => ({ value, label: statusLabel(value) })),
])
</script>

<template>
  <section class="mx-auto max-w-5xl space-y-5">
    <PageHeader :title="t('inquiry.list.title')" :description="t('inquiry.list.description')">
      <template #actions><BaseButton to="/inquiries/new">{{ t('inquiry.list.create') }}</BaseButton></template>
    </PageHeader>

    <BaseCard bordered elevation="none" padding="sm">
      <div class="grid gap-3 sm:grid-cols-2">
        <BaseSelect v-model="status" :label="t('inquiry.common.status')" :options="statusOptions" />
        <BaseSelect v-model="category" :label="t('inquiry.common.category')" :options="categoryOptions" />
      </div>
    </BaseCard>

    <div v-if="query.isLoading.value" class="flex justify-center py-8" role="status" aria-live="polite"><BaseSpinner /><span class="sr-only">{{ t('inquiry.common.loading') }}</span></div>
    <ErrorState v-else-if="query.error.value" :message="t('inquiry.common.loadFailed')" show-retry @retry="query.refetch()" />
    <EmptyState v-else-if="!query.data.value?.content.length" :title="t('inquiry.common.empty')" />
    <ul v-else class="space-y-3">
      <li v-for="item in query.data.value?.content" :key="item.inquiryId">
        <router-link :to="`/inquiries/${item.inquiryId}`" class="block rounded-xl transition hover:ring-1 hover:ring-[var(--nv-accent)]">
          <BaseCard bordered elevation="none" padding="sm">
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div><span class="text-xs nv-text-muted">{{ categoryLabel(item.category) }}</span><h2 class="font-semibold">{{ item.title }}</h2></div>
              <InquiryStatusBadge :status="item.status" />
            </div>
            <p class="mt-2 line-clamp-2 whitespace-pre-wrap text-sm nv-text-muted">{{ item.lastPublicMessageSummary }}</p>
            <time class="mt-3 block text-xs nv-text-muted">{{ t('inquiry.list.modifiedAt', { date: formatDateTimeOrDash(item.modifiedAt) }) }}</time>
          </BaseCard>
        </router-link>
      </li>
    </ul>
    <Pagination :current-page="page" :total-pages="query.data.value?.totalPages ?? 0" @page-change="page = $event" />
  </section>
</template>
