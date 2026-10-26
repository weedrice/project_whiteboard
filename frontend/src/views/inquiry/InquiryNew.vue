<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation, type AuthScopedMutationFunctionContext } from '@/composables/useAuthScopedMutation'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { useConfirm } from '@/composables/useConfirm'
import { isNavigationFailure, useRouter } from 'vue-router'
import { inquiryApi } from '@/api/inquiry'
import { unwrapAxiosApiData } from '@/api/response'
import type { InquiryCategory } from '@/types/inquiry'
import InquiryImageUploader from '@/components/inquiry/InquiryImageUploader.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'
import { getCurrentSessionGeneration, sessionQueryKey } from '@/queryAuthScope'

const router = useRouter()
const queryClient = useQueryClient()
const operations = useAuthScopedOperation()
const { t } = useI18n()
const { confirm } = useConfirm()
const category = ref<InquiryCategory>('SERVICE_USE')
const title = ref('')
const content = ref('')
const fileIds = ref<number[]>([])
const errorMessage = ref('')
const uploadsPending = ref(false)
const uploader = ref<InstanceType<typeof InquiryImageUploader> | null>(null)
const createdInquiryId = ref<number | null>(null)
const formLocked = computed(() => operations.isPending('create') || createdInquiryId.value !== null)
useUnsavedChangesGuard(
  computed(() => createdInquiryId.value === null && (
    title.value.trim().length > 0 || content.value.trim().length > 0
    || fileIds.value.length > 0 || category.value !== 'SERVICE_USE'
  )),
  computed(() => createdInquiryId.value === null && (operations.isPending('create') || uploadsPending.value)),
  () => t('inquiry.common.leaveConfirm'),
  confirm,
)
let disposed = false
onScopeDispose(() => { disposed = true })

function canUpdateView(generation: number, context: AuthScopedMutationFunctionContext) {
  return !disposed && getCurrentSessionGeneration() === generation && context.isCurrent()
}

const categories: InquiryCategory[] = ['ACCOUNT', 'SERVICE_USE', 'TECHNICAL', 'CONTENT_OPERATION', 'SUGGESTION', 'OTHER']
const categoryOptions = computed(() => categories.map((value) => ({
  value,
  label: t(`inquiry.category.${value}`),
})))

interface CreateInquiryVariables {
  category: InquiryCategory
  title: string
  content: string
  fileIds: number[]
  generation: number
  uploader: InstanceType<typeof InquiryImageUploader> | null
}

const createMutation = useAuthScopedMutation({
  mutationFn: (variables: CreateInquiryVariables, mutationContext) => inquiryApi.create({
    category: variables.category,
    title: variables.title,
    content: variables.content,
    fileIds: variables.fileIds,
  }, mutationContext.requestConfig),
  onSuccess: async (response, variables, _result, context) => {
    const inquiry = unwrapAxiosApiData(response)
    variables.uploader?.commitUploads()
    if (getCurrentSessionGeneration() !== variables.generation || !context.isCurrent()) return
    if (canUpdateView(variables.generation, context)) {
      fileIds.value = []
      createdInquiryId.value = inquiry.inquiryId
    }
    try {
      await queryClient.invalidateQueries({
        queryKey: sessionQueryKey(variables.generation, ['inquiries', 'mine']),
      })
    } catch {
      // The detail route remains authoritative even if a background refresh fails.
    }
    if (!canUpdateView(variables.generation, context)) return
    try {
      const failure = await router.replace(`/inquiries/${inquiry.inquiryId}`)
      if (canUpdateView(variables.generation, context) && isNavigationFailure(failure)) {
        errorMessage.value = t('inquiry.form.createdNavigationFailed')
      }
    } catch {
      if (canUpdateView(variables.generation, context)) {
        errorMessage.value = t('inquiry.form.createdNavigationFailed')
      }
    }
  },
  onError: (error, variables, _result, context) => {
    if (!canUpdateView(variables.generation, context)) return
    fileIds.value = []
    errorMessage.value = extractErrorMessage(error) || t('inquiry.form.failed')
  },
  onCleanup: async (_data, error, variables, _result, context) => {
    if (error) await variables.uploader?.failSubmission(context.requestConfig)
  },
})

function submit() {
  if (formLocked.value) return
  errorMessage.value = ''
  if (uploadsPending.value) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  if (!title.value.trim() || title.value.trim().length > 200 || !content.value.trim() || content.value.trim().length > 10_000) {
    errorMessage.value = t('inquiry.form.validation')
    return
  }
  const submissionUploader = uploader.value
  if (submissionUploader && !submissionUploader.beginSubmission()) {
    errorMessage.value = t('inquiry.upload.uploading')
    return
  }
  void operations.run('create', async () => createMutation.mutateAsync({
    category: category.value,
    title: title.value.trim(),
    content: content.value.trim(),
    fileIds: [...fileIds.value],
    generation: getCurrentSessionGeneration(),
    uploader: submissionUploader,
  })).catch(() => undefined)
}

async function cancel() {
  if (formLocked.value) return
  // The uploader cleans up on unmount, after the route guard accepts leaving.
  await router.push('/inquiries')
}
useAuthSessionReset(() => {
  category.value = 'SERVICE_USE'
  title.value = ''
  content.value = ''
  fileIds.value = []
  errorMessage.value = ''
  uploadsPending.value = false
  createdInquiryId.value = null
})
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-5">
    <PageHeader :title="t('inquiry.form.title')" :description="t('inquiry.form.description')">
      <template #actions><BaseButton to="/inquiries" variant="secondary">{{ t('inquiry.list.title') }}</BaseButton></template>
    </PageHeader>
    <form @submit.prevent="submit">
      <BaseCard bordered elevation="none" padding="lg">
        <fieldset :disabled="formLocked" class="m-0 space-y-5 border-0 p-0">
          <BaseSelect v-model="category" :label="t('inquiry.form.category')" :options="categoryOptions" :disabled="formLocked" />
          <BaseInput v-model="title" :label="t('inquiry.form.subject')" maxlength="200" required :disabled="formLocked" autocomplete="off" />
          <div>
            <BaseTextarea v-model="content" :label="t('inquiry.form.content')" maxlength="10000" required rows="12" :disabled="formLocked" input-class="resize-y" />
            <span class="mt-1 block text-right text-xs nv-text-muted">{{ t('inquiry.form.count', { count: content.length }) }}</span>
          </div>
          <InquiryImageUploader ref="uploader" v-model="fileIds" :disabled="formLocked" @error="errorMessage = $event" @uploading="uploadsPending = $event" />
          <p v-if="errorMessage" class="nv-form-error text-sm" role="alert">{{ errorMessage }}</p>
          <div v-if="createdInquiryId !== null" class="flex justify-end">
            <BaseButton :to="`/inquiries/${createdInquiryId}`">{{ t('inquiry.form.openCreated') }}</BaseButton>
          </div>
          <div v-else class="flex justify-end gap-2"><BaseButton variant="secondary" :disabled="formLocked" @click="cancel">{{ t('inquiry.form.cancel') }}</BaseButton><BaseButton type="submit" :loading="operations.isPending('create')" :disabled="uploadsPending || formLocked">{{ t('inquiry.form.submit') }}</BaseButton></div>
        </fieldset>
      </BaseCard>
    </form>
  </section>
</template>
