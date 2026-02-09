<script setup>
import { ref, reactive, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { Lock, User, Mail, Smile, ChevronLeft, CheckCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { isEmpty, isValidEmail, isValidLoginId, isValidPassword, isValidDisplayName } from '@/utils/validation'

const { t } = useI18n()
const toastStore = useToastStore()

const router = useRouter()

const form = ref({
  loginId: '',
  password: '',
  email: '',
  displayName: ''
})

// 필드별 에러 메시지
const fieldErrors = reactive({
  loginId: '',
  password: '',
  email: '',
  displayName: ''
})

// 필드가 한 번이라도 수정되었는지 추적
const touched = reactive({
  loginId: false,
  password: false,
  email: false,
  displayName: false
})

// 실시간 validation 함수들
function validateLoginId() {
  if (!touched.loginId) return
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
  if (isEmpty(form.value.password)) {
    fieldErrors.password = ''
  } else if (!isValidPassword(form.value.password)) {
    fieldErrors.password = t('auth.validation.passwordStrength')
  } else {
    fieldErrors.password = ''
  }
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

// watch로 실시간 validation
watch(() => form.value.loginId, () => {
  touched.loginId = true
  validateLoginId()
})

watch(() => form.value.password, () => {
  touched.password = true
  validatePassword()
})

watch(() => form.value.email, () => {
  touched.email = true
  validateEmail()
})

watch(() => form.value.displayName, () => {
  touched.displayName = true
  validateDisplayName()
})

const verification = reactive({
  code: '',
  isCodeSent: false,
  isVerified: false,
  loading: false,
  timeLeft: 0
})

let timerInterval = null

const error = ref('')
const isLoading = ref(false)

function startTimer() {
  stopTimer()
  verification.timeLeft = 300 // 5 minutes
  timerInterval = setInterval(() => {
    if (verification.timeLeft > 0) {
      verification.timeLeft--
    } else {
      stopTimer()
    }
  }, 1000)
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

onUnmounted(() => {
  stopTimer()
})

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
      startTimer()
      toastStore.addToast(t('auth.codeSent'), 'success')
    }
  } catch (err) {
    const message = err.response?.data?.error?.message || t('auth.sendCodeFailed')
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

  if (verification.timeLeft <= 0) {
    toastStore.addToast(t('auth.codeExpired'), 'error')
    return
  }

  verification.loading = true
  try {
    const { data } = await authApi.verifyCode(form.value.email, verification.code)
    if (data.success) {
      verification.isVerified = true
      stopTimer()
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

  // 모든 필드를 touched로 표시하고 전체 validation 수행
  touched.loginId = true
  touched.password = true
  touched.email = true
  touched.displayName = true

  validateLoginId()
  validatePassword()
  validateEmail()
  validateDisplayName()

  // 에러가 있으면 중단
  if (fieldErrors.loginId || fieldErrors.password || fieldErrors.email || fieldErrors.displayName) {
    return
  }

  // 빈 필드 체크
  if (isEmpty(form.value.loginId)) {
    toastStore.addToast(t('auth.placeholders.loginId'), 'error')
    return
  }
  if (isEmpty(form.value.password)) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
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

  isLoading.value = true

  try {
    const signupData = {
      ...form.value,
      provider: route.query.provider || null,
      providerId: route.query.providerId || null
    }

    const { data } = await authApi.signup(signupData)
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

import { onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

onMounted(() => {
  if (route.query.email) {
    form.value.email = route.query.email
  }
  if (route.query.name) {
    form.value.displayName = route.query.name
  }
})
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
          <p v-if="fieldErrors.loginId" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.loginId }}
          </p>
        </div>
        <div>
          <BaseInput id="password" v-model="form.password" name="password" type="password" required
            :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
            <template #prefix>
              <Lock class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.password" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.password }}
          </p>
        </div>

        <!-- Email Verification -->
        <!-- <div>
          <div class="flex gap-2 items-start">
            <div class="flex-grow">
              <BaseInput id="email" v-model="form.email" name="email" type="email" required
                :placeholder="$t('auth.placeholders.newEmail')" :label="$t('common.email')" hideLabel
                :disabled="verification.isCodeSent && verification.timeLeft > 0">
                <template #prefix>
                  <Mail class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
            </div>
            <BaseButton type="button" @click="sendVerificationCode"
              :disabled="(verification.isCodeSent && verification.timeLeft > 0) || verification.loading"
              :loading="verification.loading && !verification.isCodeSent">
              <span v-if="verification.isCodeSent && verification.timeLeft > 0">
                {{ t('common.sent') }}
              </span>
              <span v-else-if="verification.isCodeSent && verification.timeLeft <= 0">
                {{ t('auth.resendCode') }}
              </span>
              <span v-else>
                {{ t('auth.sendCode') }}
              </span>
            </BaseButton>
          </div>

          <div v-if="verification.isCodeSent && !verification.isVerified"
            class="flex gap-2 items-start mt-4 animate-fade-in-down">
            <div class="flex-grow relative">
              <BaseInput v-model="verification.code" :placeholder="t('auth.codePlaceholder')" hideLabel
                :disabled="verification.timeLeft <= 0">
                <template #prefix>
                  <CheckCircle class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
              <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
                :class="verification.timeLeft <= 60 ? 'text-red-500' : 'text-gray-500'">
                {{ formatTime(verification.timeLeft) }}
              </span>
            </div>
            <BaseButton type="button" @click="verifyCode"
              :disabled="verification.loading || verification.timeLeft <= 0" :loading="verification.loading">
              {{ t('auth.verifyCode') }}
            </BaseButton>
          </div>
          <p v-if="verification.isCodeSent && verification.timeLeft <= 0 && !verification.isVerified"
            class="text-xs text-red-500 mt-1 ml-1">
            {{ t('auth.codeExpired') }}
          </p>
        </div> -->

        <!-- Email Input (Without Verification) -->
        <div>
          <BaseInput id="email" v-model="form.email" name="email" type="email" required
            :placeholder="$t('auth.placeholders.newEmail')" :label="$t('common.email')" hideLabel>
            <template #prefix>
              <Mail class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.email" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.email }}
          </p>
        </div>

        <div>
          <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
            :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel>
            <template #prefix>
              <Smile class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.displayName" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.displayName }}
          </p>
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
