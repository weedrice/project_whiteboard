import { ref, type Ref } from 'vue'

/**
 * Tracks form submission state and rejects duplicate submits while one is pending.
 */
export function useFormSubmit() {
    const isSubmitting = ref(false)

    const submit = async <T>(submitFn: () => Promise<T>): Promise<T> => {
        if (isSubmitting.value) {
            return Promise.reject(new Error('Form is already being submitted'))
        }

        isSubmitting.value = true
        try {
            return await submitFn()
        } finally {
            isSubmitting.value = false
        }
    }

    const reset = () => {
        isSubmitting.value = false
    }

    return {
        isSubmitting: isSubmitting as Readonly<Ref<boolean>>,
        submit,
        reset
    }
}
