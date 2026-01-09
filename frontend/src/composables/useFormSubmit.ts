import { ref, type Ref } from 'vue'

/**
 * 폼 제출 상태 관리 및 중복 제출 방지를 위한 composable
 * 
 * @example
 * ```typescript
 * const { isSubmitting, submit } = useFormSubmit()
 * 
 * const handleSubmit = async () => {
 *   await submit(async () => {
 *     await someApiCall()
 *   })
 * }
 * ```
 */
export function useFormSubmit() {
    const isSubmitting = ref(false)

    /**
     * 폼 제출 함수를 래핑하여 중복 제출을 방지합니다.
     * @param submitFn 제출 함수
     * @returns 제출 함수의 반환값
     */
    const submit = async <T>(submitFn: () => Promise<T>): Promise<T> => {
        if (isSubmitting.value) {
            // 이미 제출 중이면 무시
            return Promise.reject(new Error('Form is already being submitted'))
        }

        isSubmitting.value = true
        try {
            return await submitFn()
        } finally {
            isSubmitting.value = false
        }
    }

    /**
     * 제출 상태를 수동으로 리셋합니다.
     */
    const reset = () => {
        isSubmitting.value = false
    }

    return {
        isSubmitting: isSubmitting as Readonly<Ref<boolean>>,
        submit,
        reset
    }
}
