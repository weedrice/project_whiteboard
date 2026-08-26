<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { fileApi } from '@/api/file'
import { FILE_UPLOAD_TARGETS } from '@/api/fileUploadTargets'
import { unwrapAxiosApiData } from '@/api/response'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useI18n } from 'vue-i18n'

const MAX_FILES = 5
const MAX_FILE_SIZE = 10 * 1024 * 1024
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

const props = withDefaults(defineProps<{
  disabled?: boolean
}>(), {
  disabled: false,
})
const fileIds = defineModel<number[]>({ default: () => [] })
const { t } = useI18n()
const emit = defineEmits<{
  (event: 'error', message: string): void
  (event: 'uploading', value: boolean): void
}>()
const uploading = ref(false)
const names = ref(new Map<number, string>())
const temporaryIds = new Set<number>()
const submittedIds = new Set<number>()
let activeController: AbortController | null = null
let activeBatch: Promise<void> | null = null
const submissionPending = ref(false)
const disposed = ref(false)
let disposeOnPageExit = false

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files ?? [])
  input.value = ''
  if (disposed.value || props.disabled || uploading.value || submissionPending.value || selected.length === 0) return
  if (fileIds.value.length + selected.length > MAX_FILES) {
    emit('error', t('inquiry.upload.max'))
    return
  }
  const invalid = selected.find((file) => !ALLOWED_TYPES.has(file.type) || file.size > MAX_FILE_SIZE)
  if (invalid) {
    emit('error', t('inquiry.upload.invalid'))
    return
  }

  const controller = new AbortController()
  const uploadedThisBatch: number[] = []
  activeController = controller
  uploading.value = true
  emit('uploading', true)
  activeBatch = (async () => {
    try {
      for (const file of selected) {
        const uploaded = unwrapAxiosApiData(await fileApi.uploadFile(
          file,
          { skipGlobalErrorHandler: true, signal: controller.signal },
          FILE_UPLOAD_TARGETS.INQUIRY_MESSAGE,
        ))
        if (disposed.value || controller.signal.aborted) {
          await discardDetachedFileIds([uploaded.fileId])
          return
        }
        temporaryIds.add(uploaded.fileId)
        uploadedThisBatch.push(uploaded.fileId)
        names.value.set(uploaded.fileId, file.name)
        fileIds.value = [...fileIds.value, uploaded.fileId]
      }
    } catch (error) {
      if (!disposed.value) await discardFileIds(uploadedThisBatch)
      if (!disposed.value && !controller.signal.aborted) {
        emit('error', extractErrorMessage(error) || t('inquiry.upload.failed'))
      }
    } finally {
      if (activeController === controller) activeController = null
      uploading.value = false
      if (!disposed.value) emit('uploading', false)
    }
  })()
  await activeBatch
  if (activeBatch) activeBatch = null
}

async function discardFileIds(ids: number[]) {
  if (ids.length === 0) return
  const discarded = new Set(ids)
  fileIds.value = fileIds.value.filter((id) => !discarded.has(id))
  for (const fileId of discarded) {
    temporaryIds.delete(fileId)
    names.value.delete(fileId)
  }
  try {
    await fileApi.discardUploads([...discarded], { skipGlobalErrorHandler: true })
  } catch {
    // The server also expires unassociated uploads.
  }
}

async function discardDetachedFileIds(ids: number[]) {
  if (ids.length === 0) return
  if (disposeOnPageExit) {
    fileApi.discardUploadsOnPageExit(ids)
    return
  }
  try {
    await fileApi.discardUploads(ids, { skipGlobalErrorHandler: true })
  } catch {
    // The server also expires unassociated uploads.
  }
}

async function remove(fileId: number) {
  if (disposed.value || props.disabled || uploading.value || submissionPending.value) return
  if (!temporaryIds.has(fileId)) {
    fileIds.value = fileIds.value.filter((id) => id !== fileId)
    names.value.delete(fileId)
    return
  }
  await discardFileIds([fileId])
}

function beginSubmission() {
  if (disposed.value || uploading.value || submissionPending.value) return false
  submissionPending.value = true
  submittedIds.clear()
  for (const fileId of temporaryIds) submittedIds.add(fileId)
  return true
}

function commitUploads() {
  submissionPending.value = false
  for (const fileId of submittedIds) {
    temporaryIds.delete(fileId)
    names.value.delete(fileId)
  }
  submittedIds.clear()
}

async function discardPendingUploads() {
  activeController?.abort()
  const pending = [...temporaryIds]
  temporaryIds.clear()
  if (pending.length > 0) {
    const ownedIds = new Set(pending)
    fileIds.value = fileIds.value.filter((id) => !ownedIds.has(id))
    for (const fileId of pending) names.value.delete(fileId)
  }
  const batch = activeBatch
  if (batch) await batch
  await discardDetachedFileIds(pending)
}

async function discardUploads() {
  if (submissionPending.value) return
  await discardPendingUploads()
}

async function failSubmission() {
  submissionPending.value = false
  const failedIds = [...submittedIds]
  submittedIds.clear()
  for (const fileId of failedIds) {
    temporaryIds.delete(fileId)
    names.value.delete(fileId)
  }
  if (failedIds.length === 0) return
  try {
    await fileApi.discardUploads(failedIds, { skipGlobalErrorHandler: true })
  } catch {
    // The server also expires unassociated uploads.
  }
}

onBeforeUnmount(() => {
  disposed.value = true
  disposeOnPageExit = true
  activeController?.abort()
  if (submissionPending.value) return
  const pending = [...temporaryIds]
  temporaryIds.clear()
  if (pending.length > 0) {
    const ownedIds = new Set(pending)
    fileIds.value = fileIds.value.filter((id) => !ownedIds.has(id))
  }
  if (pending.length > 0) fileApi.discardUploadsOnPageExit(pending)
})

defineExpose({ beginSubmission, commitUploads, discardUploads, failSubmission })
</script>

<template>
  <div class="space-y-2">
    <label :class="['inline-flex min-h-11 items-center rounded-md border nv-border px-3 py-2 text-sm', props.disabled || submissionPending || disposed ? 'cursor-not-allowed opacity-60' : 'cursor-pointer']">
      <input class="sr-only" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple :disabled="uploading || submissionPending || disposed || props.disabled" @change="handleFiles">
      {{ uploading ? t('inquiry.upload.uploading') : t('inquiry.upload.choose', { count: fileIds.length }) }}
    </label>
    <ul v-if="fileIds.length" class="space-y-1 text-sm nv-text-muted">
      <li v-for="fileId in fileIds" :key="fileId" class="flex items-center justify-between gap-3 rounded-md nv-surface-soft px-3 py-2">
        <span class="truncate">{{ names.get(fileId) || t('inquiry.upload.fallbackName', { id: fileId }) }}</span>
        <button type="button" class="text-[var(--nv-danger-text)] disabled:cursor-not-allowed disabled:opacity-50" :disabled="props.disabled || uploading || submissionPending || disposed" @click="remove(fileId)">{{ t('inquiry.upload.remove') }}</button>
      </li>
    </ul>
  </div>
</template>
