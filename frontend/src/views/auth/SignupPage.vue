<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { Lock, User, Mail, Smile, ChevronLeft } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const { t } = useI18n()
const toastStore = useToastStore()

const router = useRouter()

const form = ref({
  loginId: '',
  password: '',
  email: '',
  displayName: ''
})

const error = ref('')
const isLoading = ref(false)

async function handleSignup() {
  error.value = ''

  if (!form.value.loginId) {
    toastStore.addToast(t('auth.placeholders.loginId'), 'error')
    return
  }
  if (!form.value.password) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
    return
  }
  if (!form.value.email) {
    toastStore.addToast(t('auth.placeholders.email'), 'error')
    return
  }
  if (!form.value.displayName) {
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
        <span class="text-sm font-medium">{{ $t('common.back') || 'Back' }}</span>
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
        <div>
          <BaseInput id="email" v-model="form.email" name="email" type="email" required
            :placeholder="$t('auth.placeholders.email')" :label="$t('common.email')" hideLabel>
            <template #prefix>
              <Mail class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
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
