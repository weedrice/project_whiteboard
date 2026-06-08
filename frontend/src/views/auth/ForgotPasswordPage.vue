<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useAuthEmailVerificationSection } from '@/composables/useAuthEmailVerificationSection'
import { handleDeletedAccountRedirect } from '@/utils/authRedirect'
import AuthEmailVerificationSection from '@/components/auth/AuthEmailVerificationSection.vue'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()

const form = reactive({
  email: '',
  code: '',
})
const isLoading = ref(false)
const isSent = ref(false)
const isCodeSent = ref(false)

const {
  sectionProps: emailVerificationSectionProps,
  sendVerifyCode: handleSendCode,
  verifyEmailCode: handleSendResetLink,
} = useAuthEmailVerificationSection({
  t,
  idPrefix: 'forgot-password',
  verifyLabelKey: 'auth.sendResetLink',
  codeSent: () => isCodeSent.value,
  loading: () => isLoading.value,
  emailDisabled: () => isCodeSent.value,
  getEmail: () => form.email,
  getCode: () => form.code,
  purpose: 'PASSWORD_RESET',
  validateEmailFormat: false,
  useTimer: false,
  closeOnVerifySuccess: false,
  showVerifySuccessToast: false,
  emailRequiredMessage: t('auth.placeholders.email'),
  onLoadingChange: (loading) => {
    isLoading.value = loading
  },
  afterSend: () => {
    isCodeSent.value = true
    form.code = ''
  },
  afterVerify: async ({ email, verificationTicket }) => {
    const { data } = await authApi.sendPasswordResetLinkByEmail(email, verificationTicket)
    if (data.success) {
      isSent.value = true
      toastStore.addToast(t('auth.resetLinkSent'), 'success')
    }
  },
  onVerifyError: (error) => {
    return handleDeletedAccountRedirect(error, {
      email: form.email,
      t,
      addToast: (message, type) => toastStore.addToast(message, type),
      push: (to) => router.push(to),
    })
  },
})
</script>

<template>
  <AuthFormShell
    :title="t('auth.forgotPassword')"
    :description="t('auth.forgotPasswordDescription')"
    back-to="/login"
  >
    <AuthEmailVerificationSection
      v-if="!isSent"
      v-model:email="form.email"
      v-model:code="form.code"
      v-bind="emailVerificationSectionProps"
      @send="handleSendCode"
      @verify="handleSendResetLink"
    />

    <div v-else class="text-center animate-fade-in">
      <BaseButton variant="primary" class="w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>
  </AuthFormShell>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
