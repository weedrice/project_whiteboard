<script setup lang="ts">
import { computed, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthEmailVerificationSection } from '@/composables/useAuthEmailVerificationSection'
import { useFindIdFlow } from '@/composables/useFindIdFlow'
import { usePasswordResetByVerificationFlow } from '@/composables/usePasswordResetByVerificationFlow'
import AuthEmailVerificationSection from '@/components/auth/AuthEmailVerificationSection.vue'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import AuthPasswordPairFields from '@/components/auth/AuthPasswordPairFields.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import { Key, User } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('id')
const findAccountTabs = computed(() => [
  { value: 'id', label: t('auth.findId'), icon: User },
  { value: 'password', label: t('auth.findPassword'), icon: Key },
])
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
  sectionProps: emailVerificationSectionProps,
  sendVerifyCode: handleSendCode,
  verifyEmailCode: handleVerifyCode,
} = useAuthEmailVerificationSection({
  t,
  idPrefix: 'find-account',
  layout: 'inline',
  verifyLabelKey: 'auth.verifyCode',
  codeSent: () => status.isCodeSent,
  loading: () => status.loading,
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
    <BaseSegmentedControl
      :model-value="activeTab"
      :options="findAccountTabs"
      :label="t('auth.findAccount')"
      variant="underline"
      selection-mode="tab"
      class="mb-6"
      @update:model-value="switchTab"
    />

    <div class="space-y-6">
      <AuthEmailVerificationSection
        v-if="!status.foundId && (!status.isVerified || activeTab === 'id')"
        v-model:email="form.email"
        v-model:code="form.code"
        v-bind="emailVerificationSectionProps"
        @send="handleSendCode"
        @verify="handleVerifyCode"
      />

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
        <AuthPasswordPairFields
          v-model:password="form.newPassword"
          v-model:confirm-password="form.confirmPassword"
          password-id="find-account-new-password"
          confirm-password-id="find-account-confirm-password"
          password-name="newPassword"
          confirm-password-name="confirmPassword"
          :password-label="t('auth.newPassword')"
          :confirm-password-label="t('auth.newPasswordConfirm')"
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
