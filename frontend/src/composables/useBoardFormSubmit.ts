import { useI18n } from 'vue-i18n'
import { type ComputedRef, type Ref } from 'vue'
import { useToastStore } from '@/stores/toast'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { uploadBoardIconFile, validateBoardIconFile } from '@/composables/useBoardIconUpload'
import { validateRequiredBoardFields } from '@/utils/board'
import type { BoardFormData } from '@/composables/useBoardFormState'

interface UseBoardFormSubmitOptions {
  form: Ref<BoardFormData>
  selectedFile: Ref<File | null>
  isEdit: () => boolean
  canCreate: ComputedRef<boolean>
  boardCreateCost: ComputedRef<number>
  emitSubmit: (data: BoardFormData) => void
}

export function useBoardFormSubmit(options: UseBoardFormSubmitOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { isSubmitting, submit } = useFormSubmit()
  const { handleError } = useErrorHandler()

  async function handleSubmit() {
    const requiredFieldValidation = validateRequiredBoardFields(options.form.value)
    if (!requiredFieldValidation.valid) {
      toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
      return
    }

    if (!options.isEdit() && !options.canCreate.value) {
      toastStore.addToast(t('board.form.insufficientPoints', { cost: options.boardCreateCost.value }), 'error')
      return
    }

    await submit(async () => {
      let iconUrl = options.form.value.iconUrl

      try {
        if (options.selectedFile.value) {
          const validationError = validateBoardIconFile(options.selectedFile.value)
          if (validationError === 'type') {
            toastStore.addToast(t('board.form.invalidIconType'), 'error')
            return
          }
          if (validationError === 'size') {
            toastStore.addToast(t('board.form.iconTooLarge'), 'error')
            return
          }

          iconUrl = await uploadBoardIconFile(options.selectedFile.value) ?? iconUrl
        }

        options.emitSubmit({
          ...options.form.value,
          iconUrl,
          agentUseYn: options.form.value.isPublic ? options.form.value.agentUseYn : false,
        })
      } catch (err) {
        handleError(err, t('board.form.uploadFailed'))
        throw err
      }
    })
  }

  return {
    isSubmitting,
    handleSubmit,
  }
}
