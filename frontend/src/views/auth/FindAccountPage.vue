<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useEmailVerificationFlow } from '@/composables/useEmailVerificationFlow'
import { useFindIdFlow } from '@/composables/useFindIdFlow'
import { usePasswordResetByVerificationFlow } from '@/composables/usePasswordResetByVerificationFlow'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { Mail, Key, User, CheckCircle } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('id')
const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const status = reactive({
  isCodeSent: false,
  isVerified: false,
  verificationTicket: '',
  loading: false,
  foundId: '',
})

const resetState = () => {
  form.email = ''
  form.code = ''
  form.newPassword = ''
  form.confirmPassword = ''
  status.isCodeSent = false
  status.isVerified = false
  status.verificationTicket = ''
  status.foundId = ''
  status.loading = false
}

const resetVerificationState = () => {
  form.code = ''
  status.isCodeSent = false
  status.isVerified = false
  status.verificationTicket = ''
}

const switchTab = (tab: string) => {
  activeTab.value = tab
  resetState()
}

const { findId } = useFindIdFlow({
  getEmail: () => form.email,
  onLoadingChange: (loading) => {
    status.loading = loading
  },
  onSuccess: ({ loginId, verificationTicket }) => {
    status.isVerified = true
    status.verificationTicket = verificationTicket
    status.foundId = loginId
  },
})

const {
  completeVerification: completePasswordResetVerification,
  resetPassword: handleResetPassword,
} = usePasswordResetByVerificationFlow({
  getEmail: () => form.email,
  getVerificationTicket: () => status.verificationTicket,
  getNewPassword: () => form.newPassword,
  getConfirmPassword: () => form.confirmPassword,
  onLoadingChange: (loading) => {
    status.loading = loading
  },
  onVerified: (verificationTicket) => {
    status.isVerified = true
    status.verificationTicket = verificationTicket
  },
})

const {
  sendVerifyCode: handleSendCode,
  verifyEmailCode: handleVerifyCode,
} = useEmailVerificationFlow({
  getEmail: () => form.email,
  getCode: () => form.code,
  purpose: () => activeTab.value === 'id' ? 'FIND_ID' : 'PASSWORD_RESET',
  validateEmailFormat: false,
  useTimer: false,
  closeOnVerifySuccess: false,
  showVerifySuccessToast: false,
  emailRequiredMessage: t('auth.placeholders.email'),
  onLoadingChange: (loading) => {
    status.loading = loading
  },
  afterSend: () => {
    resetVerificationState()
    status.isCodeSent = true
    status.foundId = ''
  },
  afterVerify: async ({ verificationTicket }) => {
    if (activeTab.value === 'id') {
      await findId(verificationTicket)
    } else {
      completePasswordResetVerification(verificationTicket)
    }
  },
})
</script>

<template>
  <AuthFormShell back-to="/login">
    <div class="flex border-b nv-border mb-6" role="tablist" :aria-label="t('auth.findAccount')">
      <BaseButton
        variant="ghost"
        class="flex-1 rounded-b-none border-b-2"
        role="tab"
        :aria-selected="activeTab === 'id'"
        :class="activeTab === 'id'
          ? 'border-[var(--nv-accent)] text-[var(--nv-accent)]'
          : 'border-transparent nv-text-subtle hover:text-[var(--nv-text)]'"
        @click="switchTab('id')"
      >
        <User class="mr-2 h-4 w-4" />
        {{ t('auth.findId') }}
      </BaseButton>
      <BaseButton
        variant="ghost"
        class="flex-1 rounded-b-none border-b-2"
        role="tab"
        :aria-selected="activeTab === 'password'"
        :class="activeTab === 'password'
          ? 'border-[var(--nv-accent)] text-[var(--nv-accent)]'
          : 'border-transparent nv-text-subtle hover:text-[var(--nv-text)]'"
        @click="switchTab('password')"
      >
        <Key class="mr-2 h-4 w-4" />
        {{ t('auth.findPassword') }}
      </BaseButton>
    </div>

    <div class="space-y-6">
      <div v-if="!status.foundId && (!status.isVerified || activeTab === 'id')">
        <div class="space-y-4">
          <div class="flex gap-2 items-end">
            <div class="flex-grow">
              <BaseInput
                id="find-account-email"
                v-model="form.email"
                name="email"
                type="email"
                autocomplete="email"
                :label="t('auth.email')"
                :placeholder="t('auth.placeholders.email')"
                :disabled="status.loading"
                hideLabel
              >
                <template #prefix>
                  <Mail class="h-5 w-5 nv-text-subtle" />
                </template>
              </BaseInput>
            </div>
            <BaseButton
              class="mb-[2px] h-[42px]"
              :disabled="status.loading"
              :loading="status.loading && !status.isCodeSent"
              @click="handleSendCode"
            >
              {{ status.isCodeSent ? t('auth.resendCode') : t('auth.sendCode') }}
            </BaseButton>
          </div>

          <div v-if="status.isCodeSent && !status.isVerified" class="flex gap-2 items-end animate-fade-in-down">
            <div class="flex-grow">
              <BaseInput
                id="find-account-verification-code"
                v-model="form.code"
                name="verificationCode"
                inputmode="numeric"
                autocomplete="one-time-code"
                :placeholder="t('auth.codePlaceholder')"
                :label="t('auth.codePlaceholder')"
                hideLabel
              >
                <template #prefix>
                  <CheckCircle class="h-5 w-5 nv-text-subtle" />
                </template>
              </BaseInput>
            </div>
            <BaseButton class="mb-[2px] h-[42px]" :loading="status.loading" @click="handleVerifyCode">
              {{ t('auth.verifyCode') }}
            </BaseButton>
          </div>
        </div>
      </div>

      <div
        v-if="activeTab === 'id' && status.foundId"
        class="text-center py-8 nv-surface-muted rounded-lg border nv-border animate-fade-in"
      >
        <p class="nv-text-muted mb-2">{{ t('auth.yourIdIs', { id: '' }).replace('{id}', '') }}</p>
        <p class="text-2xl font-bold nv-accent-text mb-6">{{ status.foundId }}</p>
        <BaseButton full-width @click="router.push('/login')">
          {{ t('auth.login') }}
        </BaseButton>
      </div>

      <div v-if="activeTab === 'password' && status.isVerified" class="space-y-6 animate-fade-in">
        <BaseInput
          id="find-account-new-password"
          v-model="form.newPassword"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          :label="t('auth.newPassword')"
          required
        />
        <BaseInput
          id="find-account-confirm-password"
          v-model="form.confirmPassword"
          name="confirmPassword"
          type="password"
          autocomplete="new-password"
          :label="t('auth.newPasswordConfirm')"
          required
        />
        <BaseButton full-width variant="primary" :loading="status.loading" @click="handleResetPassword">
          {{ t('auth.resetPassword') }}
        </BaseButton>
      </div>
    </div>
  </AuthFormShell>
</template>

<style scoped>
.animate-fade-in-down {
  animation: fadeInDown 0.3s ease-out;
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
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
