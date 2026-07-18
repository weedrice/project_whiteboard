import { ref, type Ref } from 'vue'

interface UseModalSubmitOptions<TValue> {
    initialValue: TValue
    isValid: (value: TValue) => boolean
    onInvalid: (value: TValue) => void
    onSubmit: (value: TValue) => Promise<boolean | void>
    onSuccess: () => void
}

export function useModalSubmit<TValue>({
    initialValue,
    isValid,
    onInvalid,
    onSubmit,
    onSuccess
}: UseModalSubmitOptions<TValue>) {
    const value = ref(initialValue) as Ref<TValue>
    const isSubmitting = ref(false)

    function reset() {
        value.value = initialValue
    }

    async function submit() {
        if (isSubmitting.value) return false

        if (!isValid(value.value)) {
            onInvalid(value.value)
            return false
        }

        isSubmitting.value = true
        try {
            const shouldComplete = await onSubmit(value.value)
            if (shouldComplete === false) return false

            reset()
            onSuccess()
            return true
        } finally {
            isSubmitting.value = false
        }
    }

    return {
        value,
        isSubmitting,
        submit,
        reset
    }
}
