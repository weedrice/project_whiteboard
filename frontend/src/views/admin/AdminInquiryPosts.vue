<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminInquiryDetailModal from '@/components/admin/AdminInquiryDetailModal.vue'
import InquiryTimeline from '@/components/inquiry/InquiryTimeline.vue'
import InquiryImageUploader from '@/components/inquiry/InquiryImageUploader.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import { useAdminInquiryPosts } from '@/features/admin/inquiries/useAdminInquiryPosts'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { inquiryApi } from '@/api/inquiry'
import { unwrapAxiosApiData } from '@/api/response'
import type { InquiryCategory, InquiryPriority, InquiryStatus } from '@/types/inquiry'
import {
  AUTH_SCOPED_QUERY_META,
  getCurrentSessionGeneration,
  sessionQueryKey,
} from '@/queryAuthScope'
import { formatDateTimeOrDash } from '@/utils/date'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()
const queryClient = useQueryClient()
const tab = ref<'new' | 'legacy'>('new')
const page = ref(0)
const status = ref<InquiryStatus | ''>('')
const category = ref<InquiryCategory | ''>('')
const priority = ref<InquiryPriority | ''>('')
const keyword = ref('')
const appliedKeyword = ref('')
const fromDate = ref('')
const toDate = ref('')
const selectedId = ref<number | null>(null)
const composeMode = ref<'reply' | 'note'>('reply')
const content = ref('')
const fileIds = ref<number[]>([])
const errorMessage = ref('')
const uploadsPending = ref(false)
const uploader = ref<InstanceType<typeof InquiryImageUploader> | null>(null)
let composeEpoch = 0
const statusLabel = (value: InquiryStatus) => t(`inquiry.status.${value}`)
const priorityLabel = (value?: InquiryPriority | null) => value ? t(`inquiry.priority.${value}`) : '-'
const tabOptions = computed(() => [
  {
    value: 'new',
    label: t('inquiry.admin.newTab'),
    id: 'admin-new-inquiries-tab',
    controls: 'admin-new-inquiries-panel',
  },
  {
    value: 'legacy',
    label: t('inquiry.admin.legacyTab'),
    id: 'admin-legacy-inquiries-tab',
    controls: 'admin-legacy-inquiries-panel',
  },
])

const legacy = useAdminInquiryPosts()
const legacyColumns = computed<TableColumn[]>(() => [
  { key: 'title', label: t('inquiry.admin.legacyTitle'), width: '35%' },
  { key: 'summaryText', label: t('inquiry.admin.legacyContent'), width: '35%' },
  { key: 'authorName', label: t('inquiry.admin.author'), width: '15%' },
  { key: 'createdAtText', label: t('inquiry.admin.createdAt'), width: '15%' },
])

const params = computed(() => ({
  page: page.value,
  size: 20,
  status: status.value || undefined,
  category: category.value || undefined,
  priority: priority.value || undefined,
  keyword: appliedKeyword.value || undefined,
  from: fromDate.value ? `${fromDate.value}T00:00:00` : undefined,
  to: toDate.value ? `${toDate.value}T23:59:59.999999999` : undefined,
}))
watch([status, category, priority, fromDate, toDate], () => { page.value = 0 })

const listQuery = useApiPageQuery({
  queryKey: computed(() => ['admin', 'support-inquiries', params.value]),
  request: ({ signal }) => inquiryApi.getAdminPage(params.value, { signal }),
  meta: AUTH_SCOPED_QUERY_META,
})

const detailQuery = useApiQuery({
  queryKey: computed(() => ['admin', 'support-inquiries', 'detail', selectedId.value]),
  request: ({ signal }) => inquiryApi.getAdminDetail(selectedId.value!, { signal }),
  enabled: computed(() => selectedId.value !== null),
  meta: AUTH_SCOPED_QUERY_META,
})

function resetComposeDraft() {
  const staleUploader = uploader.value
  composeEpoch += 1
  content.value = ''
  fileIds.value = []
  errorMessage.value = ''
  uploadsPending.value = false
  composeMode.value = 'reply'
  void staleUploader?.discardUploads()
}

watch(() => route.params.inquiryId, (value, previousValue) => {
  const parsed = Number(value)
  const nextId = Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
  if (previousValue !== undefined && selectedId.value !== nextId) resetComposeDraft()
  selectedId.value = nextId
}, { immediate: true })

async function refresh(generation: number) {
  await queryClient.invalidateQueries({
    queryKey: sessionQueryKey(generation, ['admin', 'support-inquiries']),
  })
}

function applyFilters() {
  const nextKeyword = keyword.value.trim()
  const pageChanged = page.value !== 0
  const keywordChanged = appliedKeyword.value !== nextKeyword
  page.value = 0
  appliedKeyword.value = nextKeyword
  if (!pageChanged && !keywordChanged) void listQuery.refetch()
}

type AdminAction = 'start' | 'reply' | 'note' | 'close' | 'reopen'
interface AdminActionVariables {
  action: AdminAction
  inquiryId: number
  content: string
  fileIds: number[]
  reason?: string
  generation: number
  composeEpoch: number
  uploader: InstanceType<typeof InquiryImageUploader> | null
}

function isCurrentCompose(variables: AdminActionVariables) {
  return selectedId.value === variables.inquiryId && composeEpoch === variables.composeEpoch
}

const actionMutation = useMutation({
  mutationFn: async (variables: AdminActionVariables) => {
    if (variables.action === 'start') return inquiryApi.start(variables.inquiryId)
    if (variables.action === 'reply') {
      return inquiryApi.reply(variables.inquiryId, {
        content: variables.content,
        fileIds: variables.fileIds,
      })
    }
    if (variables.action === 'note') {
      return inquiryApi.addNote(variables.inquiryId, {
        content: variables.content,
        fileIds: variables.fileIds,
      })
    }
    if (variables.action === 'reopen') return inquiryApi.reopen(variables.inquiryId)
    return inquiryApi.adminClose(variables.inquiryId, variables.reason!)
  },
  onSuccess: async (response, variables) => {
    queryClient.setQueryData(
      sessionQueryKey(variables.generation, [
        'admin',
        'support-inquiries',
        'detail',
        variables.inquiryId,
      ]),
      unwrapAxiosApiData(response),
    )
    if ((variables.action === 'reply' || variables.action === 'note')
      && variables.uploader) {
      variables.uploader?.commitUploads()
    }
    if ((variables.action === 'reply' || variables.action === 'note')
      && isCurrentCompose(variables)
      && uploader.value === variables.uploader) {
      content.value = ''
      fileIds.value = []
    }
    if (isCurrentCompose(variables)) errorMessage.value = ''
    await refresh(variables.generation)
  },
  onError: async (error, variables) => {
    if ((variables.action === 'reply' || variables.action === 'note')
      && variables.uploader) {
      await variables.uploader.failSubmission()
    }
    if (!isCurrentCompose(variables)) return
    if ((variables.action === 'reply' || variables.action === 'note')
      && uploader.value === variables.uploader) {
      fileIds.value = []
    }
    errorMessage.value = extractErrorMessage(error) || t('inquiry.admin.actionFailed')
  },
})

function openDetail(id: number) {
  void router.push(`/admin/inquiries/${id}`)
}

async function closeDetail() {
  await router.push('/admin/inquiries')
}

function runAction(action: AdminAction) {
  if (selectedId.value === null || actionMutation.isPending.value) return
  let reason: string | undefined
  if (action === 'close') {
    reason = window.prompt(t('inquiry.admin.closePrompt'))?.trim()
    if (!reason) {
      errorMessage.value = t('inquiry.admin.closeReasonRequired')
      return
    }
  }
  const submissionUploader = action === 'reply' || action === 'note' ? uploader.value : null
  if (submissionUploader && !submissionUploader.beginSubmission()) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  actionMutation.mutate({
    action,
    inquiryId: selectedId.value,
    content: content.value.trim(),
    fileIds: [...fileIds.value],
    reason,
    generation: getCurrentSessionGeneration(),
    composeEpoch,
    uploader: submissionUploader,
  })
}

function submitMessage() {
  if (uploadsPending.value) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  if (!content.value.trim() || content.value.trim().length > 10_000) {
    errorMessage.value = t('inquiry.admin.contentValidation')
    return
  }
  runAction(composeMode.value)
}
</script>

<template>
  <AdminDataPage :title="t('inquiry.admin.title')" :description="t('inquiry.admin.description')">
    <BaseSegmentedControl
      v-model="tab"
      class="mt-4"
      :options="tabOptions"
      :label="t('inquiry.admin.title')"
      selection-mode="tab"
    />

    <section
      v-if="tab === 'new'"
      id="admin-new-inquiries-panel"
      role="tabpanel"
      aria-labelledby="admin-new-inquiries-tab"
    >
      <form class="mt-4 flex flex-wrap items-end gap-3 rounded-xl border nv-border nv-surface p-4" @submit.prevent="applyFilters">
        <label class="text-sm">{{ t('inquiry.common.status') }}<select v-model="status" class="ml-2 rounded-md border nv-border nv-surface px-2 py-2"><option value="">{{ t('inquiry.common.all') }}</option><option value="NEW">{{ t('inquiry.status.NEW') }}</option><option value="IN_PROGRESS">{{ t('inquiry.status.IN_PROGRESS') }}</option><option value="RESOLVED">{{ t('inquiry.status.RESOLVED') }}</option><option value="CLOSED">{{ t('inquiry.status.CLOSED') }}</option></select></label>
        <label class="text-sm">{{ t('inquiry.common.category') }}<select v-model="category" class="ml-2 rounded-md border nv-border nv-surface px-2 py-2"><option value="">{{ t('inquiry.common.all') }}</option><option value="ACCOUNT">{{ t('inquiry.category.ACCOUNT') }}</option><option value="SERVICE_USE">{{ t('inquiry.category.SERVICE_USE') }}</option><option value="TECHNICAL">{{ t('inquiry.category.TECHNICAL') }}</option><option value="CONTENT_OPERATION">{{ t('inquiry.category.CONTENT_OPERATION') }}</option><option value="SUGGESTION">{{ t('inquiry.category.SUGGESTION') }}</option><option value="OTHER">{{ t('inquiry.category.OTHER') }}</option></select></label>
        <label class="text-sm">{{ t('inquiry.common.priority') }}<select v-model="priority" class="ml-2 rounded-md border nv-border nv-surface px-2 py-2"><option value="">{{ t('inquiry.common.all') }}</option><option value="URGENT">{{ t('inquiry.priority.URGENT') }}</option><option value="HIGH">{{ t('inquiry.priority.HIGH') }}</option><option value="NORMAL">{{ t('inquiry.priority.NORMAL') }}</option></select></label>
        <label class="text-sm">{{ t('inquiry.common.fromDate') }}<input v-model="fromDate" type="date" class="ml-2 rounded-md border nv-border nv-surface px-2 py-2"></label>
        <label class="text-sm">{{ t('inquiry.common.toDate') }}<input v-model="toDate" type="date" :min="fromDate || undefined" class="ml-2 rounded-md border nv-border nv-surface px-2 py-2"></label>
        <label class="text-sm">{{ t('inquiry.common.search') }}<input v-model="keyword" maxlength="200" class="ml-2 rounded-md border nv-border nv-surface px-3 py-2" :placeholder="t('inquiry.common.searchPlaceholder')"></label>
        <BaseButton type="submit" size="sm">{{ t('inquiry.common.query') }}</BaseButton>
      </form>
      <div v-if="listQuery.isLoading.value" class="mt-4 rounded-xl border nv-border p-8 text-center">{{ t('inquiry.common.loading') }}</div>
      <div v-else-if="listQuery.error.value" class="mt-4 rounded-xl nv-status-danger p-4">{{ t('inquiry.admin.loadFailed') }}</div>
      <div v-else class="mt-4 overflow-x-auto rounded-xl border nv-border nv-surface">
        <table class="w-full text-left text-sm">
          <thead><tr class="border-b nv-border"><th class="p-3">{{ t('inquiry.common.priority') }}</th><th class="p-3">{{ t('inquiry.common.status') }}</th><th class="p-3">{{ t('inquiry.admin.legacyTitle') }}</th><th class="p-3">{{ t('inquiry.admin.author') }}</th><th class="p-3">{{ t('inquiry.admin.waitingSince') }}</th></tr></thead>
          <tbody><tr v-for="item in listQuery.data.value?.content" :key="item.inquiryId" class="cursor-pointer border-b nv-border hover:bg-[var(--nv-surface-2)]" @click="openDetail(item.inquiryId)"><td class="p-3 font-semibold">{{ priorityLabel(item.effectivePriority) }}</td><td class="p-3">{{ statusLabel(item.status) }}</td><td class="p-3"><button type="button" class="rounded text-left font-medium hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-accent)]" @click.stop="openDetail(item.inquiryId)">{{ item.title }}</button></td><td class="p-3">{{ item.authorName }}</td><td class="p-3">{{ formatDateTimeOrDash(item.staffActionSince) }}</td></tr></tbody>
        </table>
        <p v-if="!listQuery.data.value?.content.length" class="p-8 text-center nv-text-muted">{{ t('inquiry.admin.empty') }}</p>
      </div>
      <Pagination class="mt-4" :current-page="page" :total-pages="listQuery.data.value?.totalPages ?? 0" @page-change="page = $event" />
    </section>

    <section
      v-else
      id="admin-legacy-inquiries-panel"
      role="tabpanel"
      aria-labelledby="admin-legacy-inquiries-tab"
    >
      <p class="mt-4 rounded-lg nv-status-info p-3 text-sm">{{ t('inquiry.admin.archiveNotice') }}</p>
      <AdminPaginatedTable
        class="mt-4"
        :columns="legacyColumns"
        :caption="t('inquiry.admin.legacyTab')"
        :items="legacy.posts.value"
        row-key="id"
        :loading="legacy.isLoading.value"
        :empty-text="t('inquiry.admin.legacyEmpty')"
        interactive-rows
        :page="legacy.page.value"
        :total-pages="legacy.totalPages.value"
        :summary="t('inquiry.admin.total', { count: legacy.totalElements.value })"
        @row-click="legacy.openDetail($event.id)"
        @page-change="legacy.handlePageChange"
      />
      <AdminInquiryDetailModal
        :is-open="legacy.selectedPostId.value !== null"
        :inquiry="legacy.selectedInquiry.value"
        :loading="legacy.isDetailLoading.value"
        :fetching="legacy.isDetailFetching.value"
        :error="legacy.detailError.value"
        @close="legacy.closeDetail"
      />
    </section>

    <AdminDetailModalShell
      :is-open="selectedId !== null"
      :title="detailQuery.data.value?.title || t('inquiry.detail.title')"
      size="2xl"
      mobile-full
      :loading="detailQuery.isLoading.value"
      :error="detailQuery.error.value"
      :empty="!detailQuery.data.value"
      :empty-text="t('inquiry.common.notFound')"
      :error-text="t('inquiry.common.notFound')"
      content-class="space-y-5 p-1"
      @close="closeDetail"
    >
      <template v-if="detailQuery.data.value">
        <p class="text-sm nv-text-muted" :data-inquiry-status="detailQuery.data.value.status">{{ detailQuery.data.value.authorName }} · {{ statusLabel(detailQuery.data.value.status) }} · {{ priorityLabel(detailQuery.data.value.effectivePriority) }}</p>
        <InquiryTimeline :messages="detailQuery.data.value.messages" admin />
        <div class="flex flex-wrap gap-2">
          <BaseButton v-if="detailQuery.data.value.status === 'NEW'" size="sm" :disabled="actionMutation.isPending.value" @click="runAction('start')">{{ t('inquiry.admin.start') }}</BaseButton>
          <BaseButton v-if="detailQuery.data.value.status === 'CLOSED'" size="sm" :disabled="actionMutation.isPending.value" @click="runAction('reopen')">{{ t('inquiry.admin.reopen') }}</BaseButton>
          <BaseButton v-if="detailQuery.data.value.status !== 'CLOSED'" size="sm" variant="danger" :disabled="actionMutation.isPending.value" @click="runAction('close')">{{ t('inquiry.admin.close') }}</BaseButton>
        </div>
        <form class="space-y-3 rounded-xl border nv-border p-4" @submit.prevent="submitMessage">
          <div class="flex gap-4"><label><input v-model="composeMode" type="radio" value="reply" :disabled="detailQuery.data.value.status === 'CLOSED' || actionMutation.isPending.value"> {{ t('inquiry.admin.publicReply') }}</label><label><input v-model="composeMode" type="radio" value="note" :disabled="actionMutation.isPending.value"> {{ t('inquiry.admin.note') }}</label></div>
          <textarea v-model="content" maxlength="10000" rows="6" class="block w-full rounded-md border nv-border nv-surface px-3 py-2" :placeholder="composeMode === 'note' ? t('inquiry.admin.notePlaceholder') : t('inquiry.admin.replyPlaceholder')" :disabled="actionMutation.isPending.value" />
          <InquiryImageUploader :key="selectedId ?? 'closed'" ref="uploader" v-model="fileIds" :disabled="actionMutation.isPending.value" @error="errorMessage = $event" @uploading="uploadsPending = $event" />
          <p v-if="errorMessage" class="nv-form-error text-sm">{{ errorMessage }}</p>
          <div class="flex justify-end"><BaseButton type="submit" :loading="actionMutation.isPending.value" :disabled="uploadsPending || actionMutation.isPending.value || (composeMode === 'reply' && detailQuery.data.value.status === 'CLOSED')">{{ composeMode === 'note' ? t('inquiry.admin.addNote') : t('inquiry.admin.addReply') }}</BaseButton></div>
        </form>
        <div v-if="detailQuery.data.value.closureDetail" class="rounded-lg nv-status-warning p-3 text-sm">{{ t('inquiry.admin.closureReason', { reason: detailQuery.data.value.closureDetail }) }}</div>
      </template>

      <template #footer>
        <BaseButton variant="secondary" @click="closeDetail">{{ t('inquiry.common.close') }}</BaseButton>
      </template>
    </AdminDetailModalShell>

  </AdminDataPage>
</template>
