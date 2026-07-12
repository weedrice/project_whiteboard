import { nextTick, reactive, ref } from 'vue'

type FieldValues<TKey extends string> = Record<TKey, unknown>
type FieldValidator<TKey extends string> = (values: FieldValues<TKey>) => string

interface UseFieldValidationOptions<TKey extends string> {
  validators: Partial<Record<TKey, FieldValidator<TKey>>>
  fieldIds?: Partial<Record<TKey, string>>
}

export function useFieldValidation<TKey extends string>({ validators, fieldIds = {} }: UseFieldValidationOptions<TKey>) {
  const touched = reactive({} as Record<TKey, boolean>)
  const errors = reactive({} as Record<TKey, string>)
  const submitted = ref(false)
  const focusHandlers = new Map<TKey, () => void>()

  function validateField(field: TKey, values: FieldValues<TKey>) {
    const validator = validators[field]
    errors[field] = validator?.(values) ?? ''
    return !errors[field]
  }

  function touchField(field: TKey, values: FieldValues<TKey>) {
    touched[field] = true
    return validateField(field, values)
  }

  function visibleError(field: TKey) {
    return touched[field] || submitted.value ? errors[field] ?? '' : ''
  }

  function registerFocus(field: TKey, handler: () => void) {
    focusHandlers.set(field, handler)
    return () => focusHandlers.delete(field)
  }

  async function focusFirstError() {
    const field = Object.keys(validators).find((key) => Boolean(errors[key as TKey])) as TKey | undefined
    if (!field) return

    await nextTick()
    const customFocus = focusHandlers.get(field)
    if (customFocus) {
      customFocus()
      return
    }

    const element = document.getElementById(fieldIds[field] ?? field)
    element?.focus()
    element?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }

  async function validateAll(values: FieldValues<TKey>) {
    submitted.value = true
    const valid = (Object.keys(validators) as TKey[])
      .map((field) => validateField(field, values))
      .every(Boolean)
    if (!valid) await focusFirstError()
    return valid
  }

  function clearValidation() {
    submitted.value = false
    for (const field of Object.keys(validators) as TKey[]) {
      touched[field] = false
      errors[field] = ''
    }
  }

  return {
    touched,
    errors,
    submitted,
    touchField,
    validateField,
    validateAll,
    visibleError,
    registerFocus,
    focusFirstError,
    clearValidation,
  }
}
