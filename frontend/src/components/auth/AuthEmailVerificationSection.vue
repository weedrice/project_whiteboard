<script setup lang="ts">
import { CheckCircle, Mail } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

const props = withDefaults(defineProps<{
  email: string
  code: string
  loading?: boolean
  codeSent?: boolean
  emailDisabled?: boolean
  idPrefix?: string
  layout?: 'stacked' | 'inline'
  emailLabel: string
  emailPlaceholder: string
  codeLabel: string
  sendLabel: string
  resendLabel: string
  verifyLabel: string
}>(), {
  loading: false,
  codeSent: false,
  emailDisabled: false,
  idPrefix: 'email-verification',
  layout: 'stacked',
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
        type="button"
        variant="primary"
        :class="isInline ? 'mb-[2px] h-[42px]' : 'w-full'"
        :loading="loading && !codeSent"
        :disabled="loading"
        @click="emit('send')"
      >
        {{ codeSent ? resendLabel : sendLabel }}
      </BaseButton>
    </div>

    <div v-if="codeSent" :class="isInline ? 'flex items-end gap-2 animate-fade-in-down' : 'space-y-4'">
      <div class="flex-grow">
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
          @update:model-value="emit('update:code', String($event))"
        >
          <template #prefix>
            <CheckCircle class="h-5 w-5 nv-text-subtle" />
          </template>
        </BaseInput>
      </div>
      <BaseButton
        type="button"
        variant="primary"
        :class="isInline ? 'mb-[2px] h-[42px]' : 'w-full'"
        :loading="loading"
        :disabled="loading"
        @click="emit('verify')"
      >
        {{ verifyLabel }}
      </BaseButton>
    </div>
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
