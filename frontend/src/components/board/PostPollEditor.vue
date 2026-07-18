<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import {
  POST_POLL_OPTION_MAX_LENGTH,
  POST_POLL_QUESTION_MAX_LENGTH,
  type PostFormPoll,
} from '@/utils/postForm'

const props = defineProps<{
  modelValue: PostFormPoll | null
  mode?: 'create' | 'edit'
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: PostFormPoll | null): void
}>()

function update(value: PostFormPoll) {
  emit('update:modelValue', value)
}

function setQuestion(question: string) {
  if (!props.modelValue) return
  update({ ...props.modelValue, question })
}

function setOption(index: number, option: string) {
  if (!props.modelValue) return
  const options = [...props.modelValue.options]
  options[index] = option
  update({ ...props.modelValue, options })
}

function addOption() {
  if (!props.modelValue || props.modelValue.options.length >= 10) return
  update({ ...props.modelValue, options: [...props.modelValue.options, ''] })
}

function removeOption(index: number) {
  if (!props.modelValue || props.modelValue.options.length <= 2) return
  update({
    ...props.modelValue,
    options: props.modelValue.options.filter((_, optionIndex) => optionIndex !== index),
  })
}

function toggle(field: 'multipleChoiceEnabled' | 'anonymousEnabled', checked: boolean) {
  if (!props.modelValue) return
  update({ ...props.modelValue, [field]: checked })
}

function setClosesAt(closesAt: string) {
  if (!props.modelValue) return
  update({ ...props.modelValue, closesAt: closesAt || null })
}
</script>

<template>
  <section v-if="modelValue" class="mt-5 rounded-[var(--nv-radius-xl)] border border-[var(--nv-line)] bg-[var(--nv-elevated)] p-4">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-sm font-semibold nv-title">{{ $t('board.writePost.poll.title') }}</h2>
      <BaseButton type="button" variant="secondary" size="sm" @click="emit('update:modelValue', null)">
        {{ $t('board.writePost.poll.remove') }}
      </BaseButton>
    </div>

    <BaseInput
      id="poll-question"
      data-testid="poll-question"
      class="mt-4"
      :model-value="modelValue.question"
      :label="$t('board.writePost.poll.question')"
      label-class="uppercase tracking-[0.18em] text-[var(--nv-muted)]"
      :placeholder="$t('board.writePost.poll.questionPlaceholder')"
      :maxlength="POST_POLL_QUESTION_MAX_LENGTH"
      :disabled="mode === 'edit'"
      @update:model-value="setQuestion(String($event))"
    />

    <div class="mt-4 space-y-2">
      <div
        v-for="(option, index) in modelValue.options"
        :key="index"
        class="grid gap-2 sm:grid-cols-[1fr_auto]"
      >
        <BaseInput
          :id="`poll-option-${index}`"
          :data-testid="`poll-option-${index}`"
          :model-value="option"
          :label="$t('board.writePost.poll.option', { index: index + 1 })"
          hide-label
          :placeholder="$t('board.writePost.poll.optionPlaceholder')"
          :maxlength="POST_POLL_OPTION_MAX_LENGTH"
          :disabled="mode === 'edit'"
          @update:model-value="setOption(index, String($event))"
        />
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          :disabled="mode === 'edit' || modelValue.options.length <= 2"
          @click="removeOption(index)"
        >
          {{ $t('common.delete') }}
        </BaseButton>
      </div>
    </div>

    <div class="mt-3">
      <BaseButton
        type="button"
        variant="secondary"
        size="sm"
        :disabled="mode === 'edit' || modelValue.options.length >= 10"
        @click="addOption"
      >
        {{ $t('board.writePost.poll.addOption') }}
      </BaseButton>
    </div>

    <div class="mt-4 grid gap-3 sm:grid-cols-2">
      <BaseCheckbox
        id="poll-multiple"
        data-testid="poll-multiple"
        :model-value="modelValue.multipleChoiceEnabled"
        :label="$t('board.writePost.poll.multiple')"
        :disabled="mode === 'edit'"
        @update:model-value="toggle('multipleChoiceEnabled', Boolean($event))"
      />
      <BaseCheckbox
        id="poll-anonymous"
        :model-value="modelValue.anonymousEnabled"
        :label="$t('board.writePost.poll.anonymous')"
        :disabled="mode === 'edit'"
        @update:model-value="toggle('anonymousEnabled', Boolean($event))"
      />
      <BaseInput
        id="poll-closes-at"
        type="datetime-local"
        class="sm:col-span-2"
        :model-value="modelValue.closesAt ?? ''"
        :label="$t('board.writePost.poll.closesAt')"
        label-class="uppercase tracking-[0.18em] text-[var(--nv-muted)]"
        :disabled="mode === 'edit'"
        @update:model-value="setClosesAt(String($event))"
      />
    </div>
  </section>
</template>
