import { onMounted, reactive, ref, watch } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { ComposerTranslation } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useAuthPasswordValidation } from '@/composables/useAuthPasswordValidation'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { getSingleQueryValue } from '@/utils/oauthCallbackTokens'
import { isEmpty, isValidDisplayName, isValidEmail, isValidLoginId } from '@/utils/validation'

interface SignupRegistrationOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  t: ComposerTranslation
}

type SignupForm = {
  loginId: string
  password: string
  passwordConfirm: string
  email: string
  displayName: string
}

type SignupPayload = Omit<SignupForm, 'passwordConfirm'> & {
  verificationTicket: string
  provider: string | null
  providerId: string | null
}

export function useSignupRegistration({ route, router, t }: SignupRegistrationOptions) {
  const toastStore = useToastStore()
  const {
    validatePasswordValue,
    validatePasswordConfirmValue,
    validatePasswordPair
  } = useAuthPasswordValidation()

  const form = ref<SignupForm>({
    loginId: '',
    password: '',
    passwordConfirm: '',
    email: '',
    displayName: ''
  })

  const fieldErrors = reactive({
    loginId: '',
    password: '',
    passwordConfirm: '',
    email: '',
    displayName: ''
  })

  const touched = reactive({
    loginId: false,
    password: false,
    passwordConfirm: false,
    email: false,
    displayName: false
  })

  const error = ref('')
  const isLoading = ref(false)
  const isReregister = ref(false)

  async function checkEmailForReregister(email: string) {
    const checkRes = await authApi.checkEmailForReregister(email)
    if (checkRes.data.success && checkRes.data.data?.canReregister && checkRes.data.data?.maskedLoginId) {
      isReregister.value = true
      form.value.loginId = checkRes.data.data.maskedLoginId
      return
    }

    isReregister.value = false
    form.value.loginId = ''
  }

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
    beforeSend: checkEmailForReregister,
    afterVerify: ({ response }) => {
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
    touched.loginId = true
    touched.password = true
    touched.passwordConfirm = true
    touched.email = true
    touched.displayName = true
  }

  function validateAllFields() {
    validateLoginId()
    validatePassword()
    validatePasswordConfirm()
    validateEmail()
    validateDisplayName()
  }

  function hasFieldErrors() {
    return Boolean(
      fieldErrors.loginId ||
      fieldErrors.password ||
      fieldErrors.passwordConfirm ||
      fieldErrors.email ||
      fieldErrors.displayName
    )
  }

  function buildSignupPayload(): SignupPayload {
    const { passwordConfirm, ...formData } = form.value
    void passwordConfirm

    return {
      ...formData,
      email: form.value.email.trim(),
      verificationTicket: verification.verificationTicket,
      provider: getSingleQueryValue(route.query.provider),
      providerId: getSingleQueryValue(route.query.providerId)
    }
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

    if (isReregister.value && form.value.loginId.includes('*')) {
      toastStore.addToast(t('auth.verificationRequired'), 'error')
      return
    }

    isLoading.value = true

    try {
      const { data } = await authApi.signup(buildSignupPayload())
      if (data.success) {
        toastStore.addToast(t('auth.signupSuccess'), 'success')
        router.push('/login')
      }
    } catch (err: unknown) {
      const message = extractErrorMessage(err) || t('auth.signupFailed')
      toastStore.addToast(message, 'error')
    } finally {
      isLoading.value = false
    }
  }

  async function initializeFromRouteQuery() {
    const email = getSingleQueryValue(route.query.email)
    const name = getSingleQueryValue(route.query.name)

    if (email) {
      form.value.email = email
    }
    if (name) {
      form.value.displayName = name
    }
    if (email) {
      try {
        const { data } = await authApi.checkEmailForReregister(email)
        if (data.success && data.data?.canReregister && data.data?.maskedLoginId) {
          isReregister.value = true
          form.value.loginId = data.data.maskedLoginId
        }
      } catch {
        // Continue as a normal signup when reregister lookup fails.
      }
    }
  }

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
