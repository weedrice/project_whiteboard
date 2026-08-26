<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { useApiQuery } from '@/composables/useApiQuery'
import { inquiryApi } from '@/api/inquiry'
import { unwrapAxiosApiData } from '@/api/response'
import {
  AUTH_SCOPED_QUERY_META,
  getCurrentSessionGeneration,
  sessionQueryKey,
} from '@/queryAuthScope'
import InquiryTimeline from '@/components/inquiry/InquiryTimeline.vue'
import InquiryImageUploader from '@/components/inquiry/InquiryImageUploader.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'
import type { InquiryCategory, InquiryClosureReason, InquiryStatus } from '@/types/inquiry'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()
const queryClient = useQueryClient()
const inquiryId = computed(() => Number(route.params.inquiryId))
const queryKey = computed(() => ['inquiries', 'detail', inquiryId.value])
const content = ref('')
const fileIds = ref<number[]>([])
const errorMessage = ref('')
const uploadsPending = ref(false)
const uploader = ref<InstanceType<typeof InquiryImageUploader> | null>(null)
let draftEpoch = 0
const categoryLabel = (value: InquiryCategory) => t(`inquiry.category.${value}`)
const statusLabel = (value: InquiryStatus) => t(`inquiry.status.${value}`)
const closureReasonLabel = (value: InquiryClosureReason) => t(`inquiry.closureReason.${value}`)

const detailQuery = useApiQuery({
  queryKey,
  request: ({ signal }) => inquiryApi.getMineDetail(inquiryId.value, { signal }),
  enabled: computed(() => Number.isSafeInteger(inquiryId.value) && inquiryId.value > 0),
  meta: AUTH_SCOPED_QUERY_META,
})

async function refresh(targetInquiryId: number, generation: number) {
  await queryClient.invalidateQueries({
    queryKey: sessionQueryKey(generation, ['inquiries', 'detail', targetInquiryId]),
  })
  await queryClient.invalidateQueries({ queryKey: sessionQueryKey(generation, ['inquiries', 'mine']) })
}

interface MessageMutationVariables {
  inquiryId: number
  content: string
  fileIds: number[]
  generation: number
  draftEpoch: number
  uploader: InstanceType<typeof InquiryImageUploader> | null
}

interface ActionMutationVariables {
  action: 'withdraw' | 'close'
  inquiryId: number
  generation: number
  draftEpoch: number
}

function isCurrentDraft(variables: { inquiryId: number, draftEpoch: number }) {
  return inquiryId.value === variables.inquiryId && draftEpoch === variables.draftEpoch
}

watch(inquiryId, () => {
  const staleUploader = uploader.value
  draftEpoch += 1
  content.value = ''
  fileIds.value = []
  errorMessage.value = ''
  uploadsPending.value = false
  void staleUploader?.discardUploads()
})

const messageMutation = useMutation({
  mutationFn: (variables: MessageMutationVariables) => inquiryApi.addMessage(
    variables.inquiryId,
    { content: variables.content, fileIds: variables.fileIds },
  ),
  onSuccess: async (response, variables) => {
    queryClient.setQueryData(
      sessionQueryKey(variables.generation, ['inquiries', 'detail', variables.inquiryId]),
      unwrapAxiosApiData(response),
    )
    variables.uploader?.commitUploads()
    if (isCurrentDraft(variables) && uploader.value === variables.uploader) {
      content.value = ''
      fileIds.value = []
      errorMessage.value = ''
    }
    await refresh(variables.inquiryId, variables.generation)
  },
  onError: async (error, variables) => {
    await variables.uploader?.failSubmission()
    if (!isCurrentDraft(variables) || uploader.value !== variables.uploader) return
    fileIds.value = []
    errorMessage.value = extractErrorMessage(error) || t('inquiry.detail.messageFailed')
  },
})

const actionMutation = useMutation({
  mutationFn: (variables: ActionMutationVariables) => variables.action === 'withdraw'
    ? inquiryApi.withdraw(variables.inquiryId)
    : inquiryApi.close(variables.inquiryId),
  onSuccess: async (response, variables) => {
    queryClient.setQueryData(
      sessionQueryKey(variables.generation, ['inquiries', 'detail', variables.inquiryId]),
      unwrapAxiosApiData(response),
    )
    if (isCurrentDraft(variables)) errorMessage.value = ''
    await refresh(variables.inquiryId, variables.generation)
  },
  onError: (error, variables) => {
    if (isCurrentDraft(variables)) {
      errorMessage.value = extractErrorMessage(error) || t('inquiry.detail.actionFailed')
    }
  },
})

const interactionPending = computed(() => (
  messageMutation.isPending.value || actionMutation.isPending.value
))

function addMessage() {
  if (interactionPending.value) return
  errorMessage.value = ''
  if (uploadsPending.value) { errorMessage.value = t('inquiry.upload.uploading'); return }
  if (!content.value.trim() || content.value.trim().length > 10_000) { errorMessage.value = t('inquiry.detail.messageValidation'); return }
  const submissionUploader = uploader.value
  if (submissionUploader && !submissionUploader.beginSubmission()) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  messageMutation.mutate({
    inquiryId: inquiryId.value,
    content: content.value.trim(),
    fileIds: [...fileIds.value],
    generation: getCurrentSessionGeneration(),
    draftEpoch,
    uploader: submissionUploader,
  })
}

function runAction(action: 'withdraw' | 'close') {
  if (interactionPending.value) return
  const message = action === 'withdraw' ? t('inquiry.detail.withdrawConfirm') : t('inquiry.detail.closeConfirm')
  if (window.confirm(message)) {
    actionMutation.mutate({
      action,
      inquiryId: inquiryId.value,
      generation: getCurrentSessionGeneration(),
      draftEpoch,
    })
  }
}
</script>

<template>
  <section class="mx-auto max-w-4xl space-y-5">
    <PageHeader :title="detailQuery.data.value?.title || t('inquiry.detail.title')" :description="t('inquiry.detail.description')">
      <template #actions><BaseButton variant="secondary" @click="router.push('/inquiries')">{{ t('inquiry.detail.list') }}</BaseButton></template>
    </PageHeader>
    <div v-if="detailQuery.isLoading.value" class="rounded-xl border nv-border nv-surface p-8 text-center">{{ t('inquiry.common.loading') }}</div>
    <div v-else-if="detailQuery.error.value || !detailQuery.data.value" class="rounded-xl nv-status-danger p-4">{{ t('inquiry.common.notFound') }}</div>
    <template v-else>
      <div class="flex flex-wrap items-center gap-2 rounded-xl border nv-border nv-surface p-4 text-sm">
        <strong>{{ statusLabel(detailQuery.data.value.status) }}</strong><span>{{ categoryLabel(detailQuery.data.value.category) }}</span>
        <span v-if="detailQuery.data.value.closureReason" class="nv-text-muted">{{ closureReasonLabel(detailQuery.data.value.closureReason) }}</span>
      </div>
      <InquiryTimeline :messages="detailQuery.data.value.messages" />
      <form v-if="detailQuery.data.value.allowedActions.canAddMessage" class="space-y-3 rounded-xl border nv-border nv-surface p-4" @submit.prevent="addMessage">
        <label class="block text-sm font-medium">{{ t('inquiry.detail.addMessage') }}<textarea v-model="content" maxlength="10000" rows="6" class="mt-2 block w-full rounded-md border nv-border nv-surface px-3 py-2" :disabled="interactionPending" /></label>
        <InquiryImageUploader :key="inquiryId" ref="uploader" v-model="fileIds" :disabled="interactionPending" @error="errorMessage = $event" @uploading="uploadsPending = $event" />
        <div class="flex justify-end"><BaseButton type="submit" :loading="messageMutation.isPending.value" :disabled="uploadsPending || interactionPending">{{ t('inquiry.detail.submitMessage') }}</BaseButton></div>
      </form>
      <p v-if="errorMessage" class="nv-form-error text-sm" role="alert">{{ errorMessage }}</p>
      <div class="flex justify-end gap-2">
        <BaseButton v-if="detailQuery.data.value.allowedActions.canWithdraw" variant="danger" :loading="actionMutation.isPending.value" :disabled="interactionPending" @click="runAction('withdraw')">{{ t('inquiry.detail.withdraw') }}</BaseButton>
        <BaseButton v-if="detailQuery.data.value.allowedActions.canClose" variant="secondary" :loading="actionMutation.isPending.value" :disabled="interactionPending" @click="runAction('close')">{{ t('inquiry.detail.close') }}</BaseButton>
      </div>
    </template>
  </section>
</template>
