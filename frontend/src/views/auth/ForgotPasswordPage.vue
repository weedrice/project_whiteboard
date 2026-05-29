<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import { handleDeletedAccountRedirect } from '@/utils/authRedirect'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useToastStore } from '@/stores/toast'
import { ChevronLeft, Mail, CheckCircle } from 'lucide-vue-next'

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
  sendVerifyCode: handleSendCode,
  verifyEmailCode: handleSendResetLink
} = useEmailVerificationFlow({
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
  }
})
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link
        to="/login"
        class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
      >
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>

    <div class="text-center mb-8">
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white">
        {{ t('auth.forgotPassword') }}
      </h2>
      <p class="mt-2 text-sm text-gray-600 dark:text-gray-400 whitespace-pre-line">
        {{ t('auth.forgotPasswordDescription') }}
      </p>
    </div>

    <div v-if="!isSent" class="w-[80%] mx-auto space-y-6">
      <BaseInput
        id="forgot-password-email"
        v-model="form.email"
        name="email"
        type="email"
        autocomplete="email"
        :placeholder="t('auth.placeholders.email')"
        :label="t('auth.email')"
        :disabled="isCodeSent"
        hideLabel
      >
        <template #prefix>
          <Mail class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>

      <BaseButton
        type="button"
        variant="primary"
        class="w-full"
        :loading="isLoading"
        :disabled="isLoading"
        @click="handleSendCode"
      >
        {{ isCodeSent ? t('auth.resendCode') : t('auth.sendCode') }}
      </BaseButton>

      <div v-if="isCodeSent" class="space-y-4">
        <BaseInput
          id="forgot-password-verification-code"
          v-model="form.code"
          name="verificationCode"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          :placeholder="t('auth.codePlaceholder')"
          :label="t('auth.codePlaceholder')"
          hideLabel
        >
          <template #prefix>
            <CheckCircle class="h-5 w-5 text-gray-400" />
          </template>
        </BaseInput>

        <BaseButton
          type="button"
          variant="primary"
          class="w-full"
          :loading="isLoading"
          :disabled="isLoading"
          @click="handleSendResetLink"
        >
          {{ t('auth.sendResetLink') }}
        </BaseButton>
      </div>
    </div>

    <div v-else class="w-[80%] mx-auto text-center animate-fade-in">
      <BaseButton variant="primary" class="w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>
  </div>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
