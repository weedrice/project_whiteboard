<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminContentState from '@/components/admin/AdminContentState.vue'
import AdminInquiryDetailModal from '@/components/admin/AdminInquiryDetailModal.vue'
import InquiryTimeline from '@/components/inquiry/InquiryTimeline.vue'
import InquiryImageUploader from '@/components/inquiry/InquiryImageUploader.vue'
import InquiryStatusBadge from '@/components/inquiry/InquiryStatusBadge.vue'
import InquiryPriorityBadge from '@/components/inquiry/InquiryPriorityBadge.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useAdminInquiryPosts } from '@/features/admin/inquiries/useAdminInquiryPosts'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { useConfirm } from '@/composables/useConfirm'
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
const { confirmWithReason } = useConfirm()
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
const statusOptions = computed(() => [
  { value: '', label: t('inquiry.common.all') },
  ...(['NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as InquiryStatus[]).map((value) => ({ value, label: t(`inquiry.status.${value}`) })),
])
const categoryOptions = computed(() => [
  { value: '', label: t('inquiry.common.all') },
  ...(['ACCOUNT', 'SERVICE_USE', 'TECHNICAL', 'CONTENT_OPERATION', 'SUGGESTION', 'OTHER'] as InquiryCategory[]).map((value) => ({ value, label: t(`inquiry.category.${value}`) })),
])
const priorityOptions = computed(() => [
  { value: '', label: t('inquiry.common.all') },
  ...(['URGENT', 'HIGH', 'NORMAL'] as InquiryPriority[]).map((value) => ({ value, label: t(`inquiry.priority.${value}`) })),
])
const composeOptions = computed(() => [
  { value: 'reply', label: t('inquiry.admin.publicReply') },
  { value: 'note', label: t('inquiry.admin.note') },
])
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
const newColumns = computed<TableColumn[]>(() => [
  { key: 'effectivePriority', label: t('inquiry.common.priority'), width: '14%' },
  { key: 'status', label: t('inquiry.common.status'), width: '14%' },
  { key: 'title', label: t('inquiry.admin.legacyTitle'), width: '32%' },
  { key: 'authorName', label: t('inquiry.admin.author'), width: '18%' },
  { key: 'staffActionSince', label: t('inquiry.admin.waitingSince'), width: '22%' },
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

async function runAction(action: AdminAction) {
  if (selectedId.value === null || actionMutation.isPending.value) return
  const targetInquiryId = selectedId.value
  const targetComposeEpoch = composeEpoch
  let reason: string | undefined
  if (action === 'close') {
    reason = await confirmWithReason(t('inquiry.admin.closePrompt')) ?? undefined
    if (!reason) {
      errorMessage.value = t('inquiry.admin.closeReasonRequired')
      return
    }
    if (selectedId.value !== targetInquiryId || composeEpoch !== targetComposeEpoch) return
  }
  const submissionUploader = action === 'reply' || action === 'note' ? uploader.value : null
  if (submissionUploader && !submissionUploader.beginSubmission()) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  actionMutation.mutate({
    action,
    inquiryId: targetInquiryId,
    content: content.value.trim(),
    fileIds: [...fileIds.value],
    reason,
    generation: getCurrentSessionGeneration(),
    composeEpoch: targetComposeEpoch,
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
      <AdminFilterPanel class="mt-4" :title="t('inquiry.common.query')">
        <form class="flex flex-wrap items-end gap-3" @submit.prevent="applyFilters">
          <AdminFilterField :label="t('inquiry.common.status')" for-id="inquiry-status" width="select"><BaseSelect id="inquiry-status" v-model="status" :options="statusOptions" /></AdminFilterField>
          <AdminFilterField :label="t('inquiry.common.category')" for-id="inquiry-category" width="select"><BaseSelect id="inquiry-category" v-model="category" :options="categoryOptions" /></AdminFilterField>
          <AdminFilterField :label="t('inquiry.common.priority')" for-id="inquiry-priority" width="select"><BaseSelect id="inquiry-priority" v-model="priority" :options="priorityOptions" /></AdminFilterField>
          <AdminFilterField :label="t('inquiry.common.fromDate')" for-id="inquiry-from" width="date"><BaseInput id="inquiry-from" v-model="fromDate" type="date" /></AdminFilterField>
          <AdminFilterField :label="t('inquiry.common.toDate')" for-id="inquiry-to" width="date"><BaseInput id="inquiry-to" v-model="toDate" type="date" :min="fromDate || undefined" /></AdminFilterField>
          <AdminFilterField :label="t('inquiry.common.search')" for-id="inquiry-keyword" width="search"><BaseInput id="inquiry-keyword" v-model="keyword" maxlength="200" :placeholder="t('inquiry.common.searchPlaceholder')" /></AdminFilterField>
          <BaseButton type="submit" size="sm">{{ t('inquiry.common.query') }}</BaseButton>
        </form>
      </AdminFilterPanel>
      <AdminContentState :error="Boolean(listQuery.error.value)" padding-class="" :error-text="t('inquiry.admin.loadFailed')" @retry="listQuery.refetch()">
        <AdminPaginatedTable
          :columns="newColumns"
          :caption="t('inquiry.admin.newTab')"
          :items="listQuery.data.value?.content ?? []"
          row-key="inquiryId"
          :loading="listQuery.isLoading.value"
          :empty-text="t('inquiry.admin.empty')"
          interactive-rows
          :row-action-label="(item) => item.title"
          :page="page"
          :total-pages="listQuery.data.value?.totalPages ?? 0"
          :total-elements="listQuery.data.value?.totalElements ?? 0"
          :summary="t('inquiry.admin.total', { count: listQuery.data.value?.totalElements ?? 0 })"
          @row-click="openDetail($event.inquiryId)"
          @page-change="page = $event"
        >
          <template #cell-effectivePriority="{ item }"><InquiryPriorityBadge v-if="item.effectivePriority" :priority="item.effectivePriority" /><span v-else>-</span></template>
          <template #cell-status="{ item }"><InquiryStatusBadge :status="item.status" /></template>
          <template #cell-title="{ item }"><span class="font-medium nv-title">{{ item.title }}</span></template>
          <template #cell-staffActionSince="{ item }">{{ formatDateTimeOrDash(item.staffActionSince) }}</template>
        </AdminPaginatedTable>
      </AdminContentState>
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
        <div class="flex flex-wrap items-center gap-2 text-sm nv-text-muted"><span>{{ detailQuery.data.value.authorName }}</span><InquiryStatusBadge :status="detailQuery.data.value.status" /><InquiryPriorityBadge v-if="detailQuery.data.value.effectivePriority" :priority="detailQuery.data.value.effectivePriority" /></div>
        <InquiryTimeline :messages="detailQuery.data.value.messages" admin />
        <div class="flex flex-wrap gap-2">
          <BaseButton v-if="detailQuery.data.value.status === 'NEW'" size="sm" :disabled="actionMutation.isPending.value" @click="runAction('start')">{{ t('inquiry.admin.start') }}</BaseButton>
          <BaseButton v-if="detailQuery.data.value.status === 'CLOSED'" size="sm" :disabled="actionMutation.isPending.value" @click="runAction('reopen')">{{ t('inquiry.admin.reopen') }}</BaseButton>
          <BaseButton v-if="detailQuery.data.value.status !== 'CLOSED'" size="sm" variant="danger" :disabled="actionMutation.isPending.value" @click="runAction('close')">{{ t('inquiry.admin.close') }}</BaseButton>
        </div>
        <form class="space-y-3 rounded-xl border nv-border p-4" @submit.prevent="submitMessage">
          <BaseSegmentedControl v-model="composeMode" :options="composeOptions" :label="t('inquiry.admin.detail')" selection-mode="radio" :disabled="actionMutation.isPending.value" />
          <BaseTextarea v-model="content" :label="composeMode === 'note' ? t('inquiry.admin.note') : t('inquiry.admin.publicReply')" maxlength="10000" rows="6" :placeholder="composeMode === 'note' ? t('inquiry.admin.notePlaceholder') : t('inquiry.admin.replyPlaceholder')" :disabled="actionMutation.isPending.value || (composeMode === 'reply' && detailQuery.data.value.status === 'CLOSED')" />
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
