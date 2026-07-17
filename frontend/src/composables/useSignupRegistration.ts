import { onMounted, onScopeDispose, reactive, ref, watch } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { ComposerTranslation } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
import { useAuthPasswordValidation } from '@/composables/useAuthPasswordValidation'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import {
  buildSignupPayload,
  createSignupFieldState,
  createSignupForm,
  createSignupTouchedState,
  hasSignupFieldErrors,
  hydrateSignupFormFromOAuthTicket,
  hydrateSignupFormFromQuery,
  isMaskedReregisterLoginBlocked,
  markAllSignupFieldsTouched,
  type SignupForm,
} from '@/composables/signupRegistrationModel'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { isEmpty, isValidDisplayName, isValidEmail, isValidLoginId } from '@/utils/validation'

interface SignupRegistrationOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  t: ComposerTranslation
}

export function useSignupRegistration({ route, router, t }: SignupRegistrationOptions) {
  const toastStore = useToastStore()
  const {
    validatePasswordValue,
    validatePasswordConfirmValue,
    validatePasswordPair
  } = useAuthPasswordValidation()

  const form = ref<SignupForm>(createSignupForm())

  const fieldErrors = reactive(createSignupFieldState())

  const touched = reactive(createSignupTouchedState())

  const error = ref('')
  const isLoading = ref(false)
  const isReregister = ref(false)
  let requestRevision = 0
  let requestController: AbortController | null = null

  const beginRequest = () => {
    requestController?.abort()
    requestController = new AbortController()
    requestRevision += 1
    return { revision: requestRevision, controller: requestController }
  }

  const isCurrentRequest = (revision: number, controller: AbortController) => (
    revision === requestRevision && requestController === controller && !controller.signal.aborted
  )

  const {
    emailVerification: verification,
    formatVerifyTime: formatTime,
    sendVerifyCode: sendVerificationCode,
    verifyEmailCode: verifyCode
  } = useEmailVerificationFlow({
    getEmail: () => form.value.email,
    purpose: 'SIGNUP',
    validateEmailFormat: false,
    closeOnVerifySuccess: false,
    emailRequiredMessage: t('auth.placeholders.email'),
    afterVerify: ({ response }) => {
      isReregister.value = response.isReregister
      if (response.isReregister && response.loginId) {
        form.value.loginId = response.loginId
      }
    }
  })

  function validateLoginId() {
    if (!touched.loginId) return
    if (isReregister.value && !verification.isVerified) {
      fieldErrors.loginId = ''
      return
    }
    if (isEmpty(form.value.loginId)) {
      fieldErrors.loginId = ''
    } else if (!isValidLoginId(form.value.loginId)) {
      fieldErrors.loginId = t('auth.validation.loginIdFormat')
    } else {
      fieldErrors.loginId = ''
    }
  }

  function validatePassword() {
    if (!touched.password) return
    fieldErrors.password = validatePasswordValue(form.value.password, {
      messages: {
        invalid: t('auth.validation.passwordStrength')
      }
    }) ?? ''
    if (touched.passwordConfirm) {
      validatePasswordConfirm()
    }
  }

  function validatePasswordConfirm() {
    if (!touched.passwordConfirm) return
    fieldErrors.passwordConfirm = validatePasswordConfirmValue(
      form.value.password,
      form.value.passwordConfirm,
      {
        messages: {
          mismatch: t('auth.passwordMismatch')
        }
      }
    ) ?? ''
  }

  function validateEmail() {
    if (!touched.email) return
    if (isEmpty(form.value.email)) {
      fieldErrors.email = ''
    } else if (!isValidEmail(form.value.email)) {
      fieldErrors.email = t('auth.validation.emailFormat')
    } else {
      fieldErrors.email = ''
    }
  }

  function validateDisplayName() {
    if (!touched.displayName) return
    if (isEmpty(form.value.displayName)) {
      fieldErrors.displayName = ''
    } else if (!isValidDisplayName(form.value.displayName)) {
      fieldErrors.displayName = t('auth.validation.displayNameLength')
    } else {
      fieldErrors.displayName = ''
    }
  }

  function touchAllFields() {
    markAllSignupFieldsTouched(touched)
  }

  function validateAllFields() {
    validateLoginId()
    validatePassword()
    validatePasswordConfirm()
    validateEmail()
    validateDisplayName()
  }

  function hasFieldErrors() {
    return hasSignupFieldErrors(fieldErrors)
  }

  async function handleSignup() {
    error.value = ''

    touchAllFields()
    validateAllFields()

    if (hasFieldErrors()) {
      return
    }

    if (isEmpty(form.value.loginId)) {
      toastStore.addToast(t('auth.placeholders.loginId'), 'error')
      return
    }

    const passwordError = validatePasswordPair(form.value.password, form.value.passwordConfirm, {
      requirePassword: true,
      requireConfirm: true,
      messages: {
        required: t('auth.placeholders.password'),
        invalid: t('auth.validation.passwordStrength'),
        mismatch: isEmpty(form.value.passwordConfirm)
          ? t('auth.newPasswordConfirm')
          : t('auth.passwordMismatch')
      }
    })
    if (passwordError) {
      toastStore.addToast(passwordError, 'error')
      return
    }

    if (isEmpty(form.value.email)) {
      toastStore.addToast(t('auth.placeholders.newEmail'), 'error')
      return
    }
    if (isEmpty(form.value.displayName)) {
      toastStore.addToast(t('auth.placeholders.displayName'), 'error')
      return
    }

    if (!verification.isVerified || !verification.verificationTicket) {
      toastStore.addToast(t('auth.verificationRequired'), 'error')
      return
    }

    if (isMaskedReregisterLoginBlocked(isReregister.value, form.value.loginId)) {
      toastStore.addToast(t('auth.verificationRequired'), 'error')
      return
    }

    const { revision, controller } = beginRequest()
    isLoading.value = true

    try {
      const { data } = await authApi.signup(buildSignupPayload(
        form.value,
        verification.verificationTicket,
      ), { signal: controller.signal })
      if (isCurrentRequest(revision, controller) && data.success) {
        toastStore.addToast(t('auth.signupSuccess'), 'success')
        router.push('/login')
      }
    } catch (err: unknown) {
      if (!isCurrentRequest(revision, controller)) return
      const message = extractErrorMessage(err) || t('auth.signupFailed')
      toastStore.addToast(message, 'error')
    } finally {
      if (isCurrentRequest(revision, controller)) isLoading.value = false
    }
  }

  async function initializeFromRouteQuery() {
    const { revision, controller } = beginRequest()
    try {
      const { data } = await authApi.getOAuthSignupTicket({ signal: controller.signal })
      if (isCurrentRequest(revision, controller)) {
        hydrateSignupFormFromOAuthTicket(form.value, unwrapApiData(data))
      }
    } catch {
      if (isCurrentRequest(revision, controller)) {
        // A missing OAuth ticket is the normal direct-signup path.
        hydrateSignupFormFromQuery(form.value, route.query)
      }
    }
  }

  onScopeDispose(() => {
    requestRevision += 1
    requestController?.abort()
    requestController = null
    if (isLoading.value) {
      isLoading.value = false
    }
  })

  watch(() => route.fullPath, () => {
    requestRevision += 1
    requestController?.abort()
    requestController = null
    isLoading.value = false
    hydrateSignupFormFromQuery(form.value, route.query)
  })

  watch(() => form.value.loginId, () => {
    touched.loginId = true
    validateLoginId()
  })

  watch(() => form.value.password, () => {
    touched.password = true
    validatePassword()
  })

  watch(() => form.value.passwordConfirm, () => {
    touched.passwordConfirm = true
    validatePasswordConfirm()
  })

  watch(() => form.value.email, () => {
    touched.email = true
    validateEmail()
  })

  watch(() => form.value.displayName, () => {
    touched.displayName = true
    validateDisplayName()
  })

  onMounted(initializeFromRouteQuery)

  return {
    form,
    fieldErrors,
    error,
    isLoading,
    isReregister,
    verification,
    formatTime,
    sendVerificationCode,
    verifyCode,
    handleSignup,
    initializeFromRouteQuery
  }
}
