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
    let submitRevision = 0

    function reset() {
        submitRevision += 1
        value.value = initialValue
        isSubmitting.value = false
    }

    async function submit() {
        if (isSubmitting.value) return false

        if (!isValid(value.value)) {
            onInvalid(value.value)
            return false
        }

        const revision = ++submitRevision
        isSubmitting.value = true
        try {
            const shouldComplete = await onSubmit(value.value)
            if (revision !== submitRevision) return false
            if (shouldComplete === false) return false

            value.value = initialValue
            onSuccess()
            return true
        } finally {
            if (revision === submitRevision) {
                isSubmitting.value = false
            }
        }
    }

    return {
        value,
        isSubmitting,
        submit,
        reset
    }
}
