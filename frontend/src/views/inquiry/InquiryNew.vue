<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { isNavigationFailure, useRouter } from 'vue-router'
import { inquiryApi } from '@/api/inquiry'
import { unwrapAxiosApiData } from '@/api/response'
import type { InquiryCategory } from '@/types/inquiry'
import InquiryImageUploader from '@/components/inquiry/InquiryImageUploader.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'
import { getCurrentSessionGeneration, sessionQueryKey } from '@/queryAuthScope'

const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const category = ref<InquiryCategory>('SERVICE_USE')
const title = ref('')
const content = ref('')
const fileIds = ref<number[]>([])
const errorMessage = ref('')
const uploadsPending = ref(false)
const uploader = ref<InstanceType<typeof InquiryImageUploader> | null>(null)
const createdInquiryId = ref<number | null>(null)
const formLocked = computed(() => createMutation.isPending.value || createdInquiryId.value !== null)

const categories: InquiryCategory[] = ['ACCOUNT', 'SERVICE_USE', 'TECHNICAL', 'CONTENT_OPERATION', 'SUGGESTION', 'OTHER']

interface CreateInquiryVariables {
  category: InquiryCategory
  title: string
  content: string
  fileIds: number[]
  generation: number
  uploader: InstanceType<typeof InquiryImageUploader> | null
}

const createMutation = useMutation({
  mutationFn: (variables: CreateInquiryVariables) => inquiryApi.create({
    category: variables.category,
    title: variables.title,
    content: variables.content,
    fileIds: variables.fileIds,
  }),
  onSuccess: async (response, variables) => {
    const inquiry = unwrapAxiosApiData(response)
    variables.uploader?.commitUploads()
    if (getCurrentSessionGeneration() !== variables.generation) return
    fileIds.value = []
    createdInquiryId.value = inquiry.inquiryId
    try {
      await queryClient.invalidateQueries({
        queryKey: sessionQueryKey(variables.generation, ['inquiries', 'mine']),
      })
    } catch {
      // The detail route remains authoritative even if a background refresh fails.
    }
    try {
      const failure = await router.replace(`/inquiries/${inquiry.inquiryId}`)
      if (isNavigationFailure(failure)) {
        errorMessage.value = t('inquiry.form.createdNavigationFailed')
      }
    } catch {
      errorMessage.value = t('inquiry.form.createdNavigationFailed')
    }
  },
  onError: async (error, variables) => {
    await variables.uploader?.failSubmission()
    if (getCurrentSessionGeneration() !== variables.generation) return
    fileIds.value = []
    errorMessage.value = extractErrorMessage(error) || t('inquiry.form.failed')
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
  createMutation.mutate({
    category: category.value,
    title: title.value.trim(),
    content: content.value.trim(),
    fileIds: [...fileIds.value],
    generation: getCurrentSessionGeneration(),
    uploader: submissionUploader,
  })
}

async function cancel() {
  if (formLocked.value) return
  await uploader.value?.discardUploads()
  await router.push('/inquiries')
}
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-5">
    <PageHeader :title="t('inquiry.form.title')" :description="t('inquiry.form.description')">
      <template #actions><BaseButton to="/inquiries" variant="secondary">{{ t('inquiry.list.title') }}</BaseButton></template>
    </PageHeader>
    <form class="space-y-5 rounded-xl border nv-border nv-surface p-5" @submit.prevent="submit">
      <label class="block text-sm font-medium">{{ t('inquiry.form.category') }}
        <select v-model="category" :disabled="formLocked" class="mt-2 block w-full rounded-md border nv-border nv-surface px-3 py-2">
          <option v-for="item in categories" :key="item" :value="item">{{ t(`inquiry.category.${item}`) }}</option>
        </select>
      </label>
      <label class="block text-sm font-medium">{{ t('inquiry.form.subject') }}
        <input v-model="title" maxlength="200" required :disabled="formLocked" class="mt-2 block w-full rounded-md border nv-border nv-surface px-3 py-2" autocomplete="off">
      </label>
      <label class="block text-sm font-medium">{{ t('inquiry.form.content') }}
        <textarea v-model="content" maxlength="10000" required rows="12" :disabled="formLocked" class="mt-2 block w-full resize-y rounded-md border nv-border nv-surface px-3 py-2" />
        <span class="mt-1 block text-right text-xs nv-text-muted">{{ t('inquiry.form.count', { count: content.length }) }}</span>
      </label>
      <InquiryImageUploader ref="uploader" v-model="fileIds" :disabled="formLocked" @error="errorMessage = $event" @uploading="uploadsPending = $event" />
      <p v-if="errorMessage" class="nv-form-error text-sm" role="alert">{{ errorMessage }}</p>
      <div v-if="createdInquiryId !== null" class="flex justify-end">
        <BaseButton :to="`/inquiries/${createdInquiryId}`">{{ t('inquiry.form.openCreated') }}</BaseButton>
      </div>
      <div v-else class="flex justify-end gap-2"><BaseButton variant="secondary" :disabled="formLocked" @click="cancel">{{ t('inquiry.form.cancel') }}</BaseButton><BaseButton type="submit" :loading="createMutation.isPending.value" :disabled="uploadsPending || formLocked">{{ t('inquiry.form.submit') }}</BaseButton></div>
    </form>
  </section>
</template>
