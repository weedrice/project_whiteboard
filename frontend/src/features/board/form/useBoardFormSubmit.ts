import {
  getCurrentScope,
  onScopeDispose,
  type ComputedRef,
  type Ref,
  watch,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { uploadBoardIconFile, validateBoardIconFile } from '@/features/board/icons/useBoardIconUpload'
import type { BoardFormData } from '@/features/board/form/useBoardFormState'
import { useToastStore } from '@/stores/toast'
import { validateBoardWriteFields } from '@/utils/board'
import { isCancellationError } from '@/utils/cancellationError'
import { normalizeBoardWritePayload, trimText } from '@/utils/inputNormalization'
import logger from '@/utils/logger'

export interface BoardFormSubmitContext {
  signal: AbortSignal
  commitUpload: () => void
}

export type BoardFormSubmitAction = (
  data: BoardFormData,
  context: BoardFormSubmitContext,
) => Promise<boolean>

interface UseBoardFormSubmitOptions {
  form: Ref<BoardFormData>
  selectedFile: Ref<File | null>
  isEdit: () => boolean
  canCreate: ComputedRef<boolean>
  boardCreateCost: ComputedRef<number | null>
  submitAction?: BoardFormSubmitAction
  emitSubmit: (data: BoardFormData) => void
  getSessionGeneration?: () => number
}

function toBoardSubmitPayload(form: BoardFormData, iconUrl: string): BoardFormData {
  return normalizeBoardWritePayload({
    ...form,
    iconUrl: trimText(iconUrl),
  })
}

export function useBoardFormSubmit(options: UseBoardFormSubmitOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { isSubmitting, submit } = useFormSubmit()
  const { handleError } = useErrorHandler()
  let operationRevision = 0
  let activeController: AbortController | null = null
  let activeSelectedFile: File | null = null
  let activeMutationStarted = false
  let pendingUploadId: number | null = null
  const discardedUploadIds = new Set<number>()

  async function discardUpload(fileId: number) {
    if (discardedUploadIds.has(fileId)) return
    discardedUploadIds.add(fileId)
    try {
      await fileApi.discardUploads([fileId], {
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      })
    } catch (error: unknown) {
      logger.warn('Failed to discard uncommitted board icon upload:', error)
    }
  }

  function takePendingUpload(): number | null {
    const fileId = pendingUploadId
    pendingUploadId = null
    return fileId
  }

  function discardPendingUpload() {
    const fileId = takePendingUpload()
    if (fileId != null) void discardUpload(fileId)
  }

  function cancelSubmission() {
    const deferDiscardUntilMutationSettles = activeMutationStarted
    operationRevision += 1
    activeController?.abort()
    activeController = null
    activeSelectedFile = null
    activeMutationStarted = false
    if (!deferDiscardUntilMutationSettles) discardPendingUpload()
  }

  async function handleSubmit() {
    if (isSubmitting.value) return

    const initialPayload = toBoardSubmitPayload(options.form.value, options.form.value.iconUrl)
    const requiredFieldValidation = validateBoardWriteFields(initialPayload)
    if (!requiredFieldValidation.valid) {
      toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
      return
    }

    if (!options.isEdit() && !options.canCreate.value) {
      if (options.boardCreateCost.value === null) {
        toastStore.addToast(t('board.form.createCostUnavailable'), 'error')
        return
      }
      toastStore.addToast(t('board.form.insufficientPoints', { cost: options.boardCreateCost.value }), 'error')
      return
    }

    const selectedFile = options.selectedFile.value
    if (selectedFile) {
      const validationError = validateBoardIconFile(selectedFile)
      if (validationError === 'type') {
        toastStore.addToast(t('board.form.invalidIconType'), 'error')
        return
      }
      if (validationError === 'size') {
        toastStore.addToast(t('board.form.iconTooLarge'), 'error')
        return
      }
    }

    await submit(async () => {
      cancelSubmission()
      const operation = operationRevision
      const controller = new AbortController()
      const sessionGeneration = options.getSessionGeneration?.()
      activeController = controller
      activeSelectedFile = selectedFile
      activeMutationStarted = false
      let uploadedFileId: number | null = null
      let uploadCommitted = false
      let uploadCompleted = !selectedFile

      const isCurrent = () => (
        operation === operationRevision
        && !controller.signal.aborted
        && (
          sessionGeneration === undefined
          || options.getSessionGeneration?.() === sessionGeneration
        )
      )
      const commitUpload = () => {
        if (!isCurrent()) return
        uploadCommitted = true
        if (uploadedFileId != null && pendingUploadId === uploadedFileId) {
          pendingUploadId = null
        }
      }
      const discardCurrentUpload = async () => {
        if (uploadedFileId == null || uploadCommitted) return
        if (pendingUploadId === uploadedFileId) pendingUploadId = null
        const fileId = uploadedFileId
        uploadedFileId = null
        await discardUpload(fileId)
      }

      try {
        let iconUrl = initialPayload.iconUrl
        if (selectedFile) {
          const uploadedFile = await uploadBoardIconFile(selectedFile, { signal: controller.signal })
          uploadCompleted = Boolean(uploadedFile?.fileId)
          if (!uploadedFile?.fileId) {
            throw new Error('Board icon upload did not return a file id')
          }

          uploadedFileId = uploadedFile.fileId
          if (!isCurrent()) {
            await discardCurrentUpload()
            return
          }

          const uploadedUrl = resolveFileUploadUrl(uploadedFile)
          if (!uploadedUrl) {
            await discardCurrentUpload()
            throw new Error('Board icon upload did not return a file url')
          }
          pendingUploadId = uploadedFileId
          iconUrl = uploadedUrl
        }

        const payload = toBoardSubmitPayload(options.form.value, iconUrl)
        const finalValidation = validateBoardWriteFields(payload)
        if (!finalValidation.valid) {
          await discardCurrentUpload()
          toastStore.addToast(t('board.form.validation'), 'error')
          return
        }
        if (!isCurrent()) {
          await discardCurrentUpload()
          return
        }

        activeMutationStarted = true
        const succeeded = options.submitAction
          ? await options.submitAction(payload, { signal: controller.signal, commitUpload })
          : (options.emitSubmit(payload), true)

        if (succeeded) {
          commitUpload()
        } else {
          await discardCurrentUpload()
        }
      } catch (error: unknown) {
        await discardCurrentUpload()
        if (
          controller.signal.aborted
          || operation !== operationRevision
          || isCancellationError(error, { codes: ['ERR_CANCELED'] })
        ) return

        handleError(
          error,
          uploadCompleted ? t('common.messages.error') : t('board.form.uploadFailed'),
        )
      } finally {
        if (activeController === controller) activeController = null
        if (activeSelectedFile === selectedFile) activeSelectedFile = null
        if (operation === operationRevision) activeMutationStarted = false
      }
    })
  }

  const stopSelectedFileWatch = watch(options.selectedFile, (selectedFile) => {
    if (activeController && selectedFile !== activeSelectedFile) cancelSubmission()
  }, { flush: 'sync' })
  const stopSessionWatch = options.getSessionGeneration
    ? watch(options.getSessionGeneration, cancelSubmission, { flush: 'sync' })
    : null

  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopSelectedFileWatch()
      stopSessionWatch?.()
      cancelSubmission()
    })
  }

  return {
    isSubmitting,
    handleSubmit,
    cancelSubmission,
  }
}
