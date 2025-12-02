<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { Lock, User, Mail, Smile } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
  isLoading.value = true
  
  try {
    const { data } = await authApi.signup(form.value)
    if (data.success) {
      alert(t('auth.signupSuccess'))
      router.push('/login')
    }
  } catch (err) {
    error.value = err.response?.data?.error?.message || t('auth.signupFailed')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-start justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8 pt-20">
    <div class="max-w-md w-full space-y-8">
      <div>
        <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900">
          {{ $t('auth.createAccountTitle') }}
        </h2>
        <p class="mt-2 text-center text-sm text-gray-600">
          {{ $t('auth.alreadyHaveAccount') }}
          <router-link to="/login" class="font-medium text-indigo-600 hover:text-indigo-500">
            {{ $t('auth.login') }}
          </router-link>
        </p>
      </div>
      <form class="mt-8 space-y-6" @submit.prevent="handleSignup">
        <div class="rounded-md shadow-sm -space-y-px">
          <div>
            <label for="login-id" class="sr-only">{{ $t('auth.loginId') }}</label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <User class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="login-id"
                v-model="form.loginId"
                name="loginId"
                type="text"
                required
                class="appearance-none rounded-none rounded-t-md relative block w-full px-3 py-2 pl-10 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 focus:z-10 sm:text-sm"
                :placeholder="$t('auth.placeholders.loginId')"
              />
            </div>
          </div>
          <div>
            <label for="password" class="sr-only">{{ $t('auth.password') }}</label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Lock class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="password"
                v-model="form.password"
                name="password"
                type="password"
                required
                class="appearance-none relative block w-full px-3 py-2 pl-10 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 focus:z-10 sm:text-sm"
                :placeholder="$t('auth.placeholders.password')"
              />
            </div>
          </div>
          <div>
            <label for="email" class="sr-only">{{ $t('auth.email') }}</label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Mail class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="email"
                v-model="form.email"
                name="email"
                type="email"
                required
                class="appearance-none relative block w-full px-3 py-2 pl-10 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 focus:z-10 sm:text-sm"
                :placeholder="$t('auth.placeholders.email')"
              />
            </div>
          </div>
          <div>
            <label for="display-name" class="sr-only">{{ $t('auth.displayName') }}</label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Smile class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="display-name"
                v-model="form.displayName"
                name="displayName"
                type="text"
                required
                class="appearance-none rounded-none rounded-b-md relative block w-full px-3 py-2 pl-10 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 focus:z-10 sm:text-sm"
                :placeholder="$t('auth.placeholders.displayName')"
              />
            </div>
          </div>
        </div>

        <div v-if="error" class="text-red-500 text-sm text-center">
          {{ error }}
        </div>

        <div>
          <button
            type="submit"
            :disabled="isLoading"
            class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
          >
            <span v-if="isLoading">{{ $t('auth.creatingAccount') }}</span>
            <span v-else>{{ $t('auth.signup') }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
