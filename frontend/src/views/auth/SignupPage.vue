<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { Lock, User, Mail, Smile, ChevronLeft, CheckCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { isEmpty, isValidEmail, isValidLoginId, isValidPassword } from '@/utils/validation'

const { t } = useI18n()
const toastStore = useToastStore()

const router = useRouter()

const form = ref({
  loginId: '',
  password: '',
  email: '',
  displayName: ''
})

const verification = reactive({
  code: '',
  isCodeSent: false,
  isVerified: false,
  loading: false
})

const error = ref('')
const isLoading = ref(false)

async function sendVerificationCode() {
  if (!form.value.email) {
    toastStore.addToast(t('auth.placeholders.email'), 'error')
    return
  }

  verification.loading = true
  try {
    const { data } = await authApi.sendVerificationCode(form.value.email)
    if (data.success) {
      verification.isCodeSent = true
      toastStore.addToast(t('auth.codeSent'), 'success')
    }
  } catch (err) {
    const message = err.response?.data?.error?.message || 'Failed to send verification code'
    toastStore.addToast(message, 'error')
  } finally {
    verification.loading = false
  }
}

async function verifyCode() {
  if (!verification.code) {
    toastStore.addToast(t('auth.codePlaceholder'), 'error')
    return
  }

  verification.loading = true
  try {
    const { data } = await authApi.verifyCode(form.value.email, verification.code)
    if (data.success) {
      verification.isVerified = true
      toastStore.addToast(t('auth.codeVerified'), 'success')
    }
  } catch (err) {
    const message = err.response?.data?.error?.message || t('auth.verificationFailed')
    toastStore.addToast(message, 'error')
  } finally {
    verification.loading = false
  }
}

async function handleSignup() {
  error.value = ''

  if (isEmpty(form.value.loginId)) {
    toastStore.addToast(t('auth.placeholders.loginId'), 'error')
    return
  }
  if (!isValidLoginId(form.value.loginId)) {
    toastStore.addToast(t('auth.validation.loginIdFormat'), 'error')
    return
  }

  if (isEmpty(form.value.password)) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
    return
  }
  if (!isValidPassword(form.value.password)) {
    toastStore.addToast(t('auth.validation.passwordStrength'), 'error')
    return
  }

  if (isEmpty(form.value.email)) {
    toastStore.addToast(t('auth.placeholders.email'), 'error')
    return
  }
  if (!isValidEmail(form.value.email)) {
    toastStore.addToast(t('auth.validation.emailFormat'), 'error')
    return
  }

  if (!verification.isVerified) {
    toastStore.addToast(t('auth.emailNotVerified'), 'error')
    return
  }
  if (isEmpty(form.value.displayName)) {
    toastStore.addToast(t('auth.placeholders.displayName'), 'error')
    return
  }

  isLoading.value = true

  try {
    const { data } = await authApi.signup(form.value)
    if (data.success) {
      toastStore.addToast(t('auth.signupSuccess'), 'success')
      router.push('/login')
    }
  } catch (err) {
    const message = err.response?.data?.error?.message || t('auth.signupFailed')
    toastStore.addToast(message, 'error', 3000, 'top-center')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link to="/login"
        class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors">
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>
    <div class="text-center mb-12 mt-16">
      <h2 class="text-3xl font-extrabold text-gray-900 dark:text-white">
        {{ $t('auth.createAccountTitle') }}
      </h2>
    </div>

    <form class="space-y-6" @submit.prevent="handleSignup">
      <div class="space-y-4 w-[80%] mx-auto">
        <div>
          <BaseInput id="login-id" v-model="form.loginId" name="loginId" type="text" required
            :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel>
            <template #prefix>
              <User class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
        </div>
        <div>
          <BaseInput id="password" v-model="form.password" name="password" type="password" required
            :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
            <template #prefix>
              <Lock class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
        </div>

        <!-- Email Verification -->
        <div>
          <div class="flex gap-2 items-end">
            <div class="flex-grow">
              <BaseInput id="email" v-model="form.email" name="email" type="email" required
                :placeholder="$t('auth.placeholders.email')" :label="$t('common.email')" hideLabel
                :disabled="verification.isCodeSent">
                <template #prefix>
                  <Mail class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
            </div>
            <BaseButton type="button" @click="sendVerificationCode"
              :disabled="verification.isCodeSent || verification.loading"
              :loading="verification.loading && !verification.isCodeSent" class="mb-[2px] h-[42px]" size="sm">
              {{ verification.isCodeSent ? t('common.sent') : t('auth.sendCode') }}
            </BaseButton>
          </div>

          <div v-if="verification.isCodeSent && !verification.isVerified"
            class="flex gap-2 items-end mt-4 animate-fade-in-down">
            <div class="flex-grow">
              <BaseInput v-model="verification.code" :placeholder="t('auth.codePlaceholder')" hideLabel>
                <template #prefix>
                  <CheckCircle class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
            </div>
            <BaseButton type="button" @click="verifyCode" :loading="verification.loading" class="mb-[2px] h-[42px]"
              size="sm">
              {{ t('auth.verifyCode') }}
            </BaseButton>
          </div>
        </div>

        <div>
          <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
            :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel>
            <template #prefix>
              <Smile class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
        </div>
      </div>


      <div class="flex justify-center mt-8">
        <BaseButton type="submit" :loading="isLoading" class="w-[80%]" variant="primary">
          {{ $t('auth.signup') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<style scoped>
.animate-fade-in-down {
  animation: fadeInDown 0.3s ease-out;
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
</style>
