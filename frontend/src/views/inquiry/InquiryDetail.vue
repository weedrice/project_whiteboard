<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { useApiQuery } from '@/composables/useApiQuery'
import { useConfirm } from '@/composables/useConfirm'
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
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import InquiryStatusBadge from '@/components/inquiry/InquiryStatusBadge.vue'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'
import type { InquiryCategory, InquiryClosureReason } from '@/types/inquiry'

const route = useRoute()
const { t } = useI18n()
const { confirm } = useConfirm()
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

async function runAction(action: 'withdraw' | 'close') {
  if (interactionPending.value) return
  const targetInquiryId = inquiryId.value
  const targetDraftEpoch = draftEpoch
  const message = action === 'withdraw' ? t('inquiry.detail.withdrawConfirm') : t('inquiry.detail.closeConfirm')
  if (!(await confirm(message))) return
  if (inquiryId.value !== targetInquiryId || draftEpoch !== targetDraftEpoch) return
  actionMutation.mutate({
    action,
    inquiryId: targetInquiryId,
    generation: getCurrentSessionGeneration(),
    draftEpoch: targetDraftEpoch,
  })
}
</script>

<template>
  <section class="mx-auto max-w-4xl space-y-5">
    <PageHeader :title="detailQuery.data.value?.title || t('inquiry.detail.title')" :description="t('inquiry.detail.description')">
      <template #actions><BaseButton variant="secondary" @click="router.push('/inquiries')">{{ t('inquiry.detail.list') }}</BaseButton></template>
    </PageHeader>
    <div v-if="detailQuery.isLoading.value" class="flex justify-center py-8" role="status" aria-live="polite"><BaseSpinner /><span class="sr-only">{{ t('inquiry.common.loading') }}</span></div>
    <ErrorState v-else-if="detailQuery.error.value || !detailQuery.data.value" :message="t('inquiry.common.notFound')" />
    <template v-else>
      <BaseCard bordered elevation="none" padding="sm">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <InquiryStatusBadge :status="detailQuery.data.value.status" /><span>{{ categoryLabel(detailQuery.data.value.category) }}</span>
          <span v-if="detailQuery.data.value.closureReason" class="nv-text-muted">{{ closureReasonLabel(detailQuery.data.value.closureReason) }}</span>
        </div>
      </BaseCard>
      <InquiryTimeline :messages="detailQuery.data.value.messages" />
      <form v-if="detailQuery.data.value.allowedActions.canAddMessage" @submit.prevent="addMessage">
        <BaseCard bordered elevation="none" padding="sm">
          <div class="space-y-3">
            <BaseTextarea v-model="content" :label="t('inquiry.detail.addMessage')" maxlength="10000" rows="6" :disabled="interactionPending" />
            <InquiryImageUploader :key="inquiryId" ref="uploader" v-model="fileIds" :disabled="interactionPending" @error="errorMessage = $event" @uploading="uploadsPending = $event" />
            <div class="flex justify-end"><BaseButton type="submit" :loading="messageMutation.isPending.value" :disabled="uploadsPending || interactionPending">{{ t('inquiry.detail.submitMessage') }}</BaseButton></div>
          </div>
        </BaseCard>
      </form>
      <p v-if="errorMessage" class="nv-form-error text-sm" role="alert">{{ errorMessage }}</p>
      <div class="flex justify-end gap-2">
        <BaseButton v-if="detailQuery.data.value.allowedActions.canWithdraw" variant="danger" :loading="actionMutation.isPending.value" :disabled="interactionPending" @click="runAction('withdraw')">{{ t('inquiry.detail.withdraw') }}</BaseButton>
        <BaseButton v-if="detailQuery.data.value.allowedActions.canClose" variant="secondary" :loading="actionMutation.isPending.value" :disabled="interactionPending" @click="runAction('close')">{{ t('inquiry.detail.close') }}</BaseButton>
      </div>
    </template>
  </section>
</template>
