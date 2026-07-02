import { describe, expect, it, vi } from 'vitest'
import { useFormSubmit } from '../useFormSubmit'
import { createDeferred } from '@/test/async'

describe('useFormSubmit', () => {
  it('returns the submit function result and resets submitting state', async () => {
    const formSubmit = useFormSubmit()

    await expect(formSubmit.submit(async () => 'saved')).resolves.toBe('saved')

    expect(formSubmit.isSubmitting.value).toBe(false)
  })

  it('rejects duplicate submissions while the first submit is pending', async () => {
    const formSubmit = useFormSubmit()
    const pending = createDeferred<string>()
    const submitFn = vi.fn(() => pending.promise)

    const firstSubmit = formSubmit.submit(submitFn)
    await expect(formSubmit.submit(async () => 'duplicate')).rejects.toThrow('Form is already being submitted')
    expect(submitFn).toHaveBeenCalledTimes(1)

    pending.resolve('saved')
    await expect(firstSubmit).resolves.toBe('saved')
    expect(formSubmit.isSubmitting.value).toBe(false)
  })

  it('resets submitting state when the submit function rejects', async () => {
    const formSubmit = useFormSubmit()

    await expect(formSubmit.submit(async () => {
      throw new Error('failed')
    })).rejects.toThrow('failed')

    expect(formSubmit.isSubmitting.value).toBe(false)
  })

  it('allows manual reset of submitting state', async () => {
    const formSubmit = useFormSubmit()
    const pending = createDeferred<string>()

    void formSubmit.submit(() => pending.promise)
    expect(formSubmit.isSubmitting.value).toBe(true)

    formSubmit.reset()

    expect(formSubmit.isSubmitting.value).toBe(false)
    pending.resolve('done')
    await pending.promise
  })
})
