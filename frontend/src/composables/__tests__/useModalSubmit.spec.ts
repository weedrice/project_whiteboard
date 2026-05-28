import { describe, expect, it, vi } from 'vitest'
import { useModalSubmit } from '../useModalSubmit'

describe('useModalSubmit', () => {
    it('validates before submitting', async () => {
        const onInvalid = vi.fn()
        const onSubmit = vi.fn()
        const submit = useModalSubmit({
            initialValue: '',
            isValid: (value) => value.trim().length > 0,
            onInvalid,
            onSubmit,
            onSuccess: vi.fn()
        })

        await expect(submit.submit()).resolves.toBe(false)

        expect(onInvalid).toHaveBeenCalledTimes(1)
        expect(onSubmit).not.toHaveBeenCalled()
    })

    it('blocks duplicate submissions while pending', async () => {
        let resolveSubmit: (value: boolean) => void = () => undefined
        const onSubmit = vi.fn(() => new Promise<boolean>((resolve) => {
            resolveSubmit = resolve
        }))
        const onSuccess = vi.fn()
        const submit = useModalSubmit({
            initialValue: '',
            isValid: () => true,
            onInvalid: vi.fn(),
            onSubmit,
            onSuccess
        })
        submit.value.value = 'hello'

        const firstSubmit = submit.submit()
        await expect(submit.submit()).resolves.toBe(false)
        expect(onSubmit).toHaveBeenCalledTimes(1)

        resolveSubmit(true)
        await expect(firstSubmit).resolves.toBe(true)
        expect(submit.value.value).toBe('')
        expect(onSuccess).toHaveBeenCalledTimes(1)
    })

    it('keeps the value when submit reports failure', async () => {
        const onSuccess = vi.fn()
        const submit = useModalSubmit({
            initialValue: 'draft',
            isValid: () => true,
            onInvalid: vi.fn(),
            onSubmit: vi.fn().mockResolvedValue(false),
            onSuccess
        })

        await expect(submit.submit()).resolves.toBe(false)

        expect(submit.value.value).toBe('draft')
        expect(onSuccess).not.toHaveBeenCalled()
        expect(submit.isSubmitting.value).toBe(false)
    })
})
