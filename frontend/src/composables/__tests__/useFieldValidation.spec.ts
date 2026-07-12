import { describe, expect, it, vi } from 'vitest'
import { useFieldValidation } from '../useFieldValidation'

type Field = 'title' | 'content'

describe('useFieldValidation', () => {
  const createValidation = () => useFieldValidation<Field>({
    validators: {
      title: (values) => String(values.title ?? '').trim() ? '' : 'Enter a title',
      content: (values) => String(values.content ?? '').trim() ? '' : 'Enter content',
    },
  })

  it('shows a field error only after blur', () => {
    const validation = createValidation()
    const values = { title: '', content: '' }

    validation.validateField('title', values)
    expect(validation.visibleError('title')).toBe('')

    validation.touchField('title', values)
    expect(validation.visibleError('title')).toBe('Enter a title')
  })

  it('validates every field on submit and focuses the first error', async () => {
    const validation = createValidation()
    const focus = vi.fn()
    validation.registerFocus('title', focus)

    await expect(validation.validateAll({ title: '', content: '' })).resolves.toBe(false)
    expect(validation.visibleError('content')).toBe('Enter content')
    expect(focus).toHaveBeenCalledOnce()
  })

  it('clears touched and submitted state', async () => {
    const validation = createValidation()
    await validation.validateAll({ title: '', content: '' })

    validation.clearValidation()

    expect(validation.submitted.value).toBe(false)
    expect(validation.visibleError('title')).toBe('')
  })
})
