<script setup lang="ts">
import { CheckCircle, Mail } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

const props = withDefaults(defineProps<{
  email: string
  code: string
  loading?: boolean
  codeSent?: boolean
  verified?: boolean
  emailDisabled?: boolean
  resendCooldown?: number
  timeLeft?: number
  idPrefix?: string
  layout?: 'stacked' | 'inline'
  emailLabel: string
  emailPlaceholder: string
  codeLabel: string
  sendLabel: string
  sentLabel?: string
  resendLabel: string
  verifyLabel: string
  verifiedLabel?: string
  expiredLabel?: string
  formatTime?: (seconds: number) => string
}>(), {
  loading: false,
  codeSent: false,
  verified: false,
  emailDisabled: false,
  resendCooldown: 0,
  timeLeft: undefined,
  idPrefix: 'email-verification',
  layout: 'stacked',
  sentLabel: undefined,
  verifiedLabel: undefined,
  expiredLabel: undefined,
  formatTime: undefined,
})

const emit = defineEmits<{
  'update:email': [value: string]
  'update:code': [value: string]
  send: []
  verify: []
}>()

const isInline = props.layout === 'inline'
</script>

<template>
  <div class="auth-email-verification-section space-y-4">
    <div :class="isInline ? 'flex items-end gap-2' : 'space-y-4'">
      <div class="flex-grow">
        <BaseInput
          :id="`${idPrefix}-email`"
          :model-value="email"
          name="email"
          type="email"
          autocomplete="email"
          :label="emailLabel"
          :placeholder="emailPlaceholder"
          :disabled="emailDisabled || loading"
          hideLabel
          @update:model-value="emit('update:email', String($event))"
        >
          <template #prefix>
            <Mail class="h-5 w-5 nv-text-subtle" />
          </template>
        </BaseInput>
      </div>
      <BaseButton
        v-if="!verified"
        type="button"
        variant="primary"
        :class="isInline ? 'mb-[2px] h-[42px]' : 'w-full'"
        :loading="loading && !codeSent"
        :disabled="loading || resendCooldown > 0"
        @click="emit('send')"
      >
        {{ resendCooldown > 0 ? (sentLabel ?? sendLabel) : (codeSent ? resendLabel : sendLabel) }}
      </BaseButton>
      <span v-else class="flex items-center text-[var(--nv-success-text)] text-sm font-medium whitespace-nowrap py-2 px-3">
        <CheckCircle class="h-4 w-4 mr-1" />
        {{ verifiedLabel ?? verifyLabel }}
      </span>
    </div>

    <div v-if="codeSent && !verified" :class="isInline ? 'flex items-end gap-2 animate-fade-in-down' : 'space-y-4'">
      <div class="flex-grow relative">
        <BaseInput
          :id="`${idPrefix}-verification-code`"
          :model-value="code"
          name="verificationCode"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          :placeholder="codeLabel"
          :label="codeLabel"
          hideLabel
          :disabled="timeLeft !== undefined && timeLeft <= 0"
          @update:model-value="emit('update:code', String($event))"
        >
          <template #prefix>
            <CheckCircle class="h-5 w-5 nv-text-subtle" />
          </template>
        </BaseInput>
        <span
          v-if="timeLeft !== undefined && formatTime"
          class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
          :class="timeLeft <= 60 ? 'nv-form-error' : 'nv-text-subtle'"
        >
          {{ formatTime(timeLeft) }}
        </span>
      </div>
      <BaseButton
        type="button"
        variant="primary"
        :class="isInline ? 'mb-[2px] h-[42px]' : 'w-full'"
        :loading="loading"
        :disabled="loading || (timeLeft !== undefined && timeLeft <= 0)"
        @click="emit('verify')"
      >
        {{ verifyLabel }}
      </BaseButton>
    </div>
    <p v-if="codeSent && !verified && timeLeft !== undefined && timeLeft <= 0 && expiredLabel" class="text-xs nv-form-error mt-1 ml-1">
      {{ expiredLabel }}
    </p>
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
