<script setup lang="ts">
import { computed, ref, useAttrs, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import SandboxedHtmlFrame from '@/components/common/SandboxedHtmlFrame.vue'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import { renderPostContentHtml } from '@/utils/postContentHtml'
import { decodeSandboxedPostHtml, requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'

defineOptions({
  inheritAttrs: false,
})

const props = defineProps<{
  content: string | null | undefined
  sandboxTitle?: string
}>()

const attrs = useAttrs()
const { t } = useI18n()
const element = ref<HTMLElement | null>(null)
const sandboxWrapper = ref<HTMLElement | null>(null)
const decodedSandboxHtml = computed(() => decodeSandboxedPostHtml(props.content))
const sandboxHtml = computed(() => decodedSandboxHtml.value ?? (requiresSandboxedPostHtml(props.content) ? props.content ?? '' : null))
const shouldUseSandbox = computed(() => sandboxHtml.value != null)
const sanitizedHtml = computed(() => renderPostContentHtml(props.content, {
  copy: t('board.writePost.codeBlock.copy'),
  copyAriaLabel: t('board.writePost.codeBlock.copyAriaLabel'),
}))

type SanitizedHtmlViewExpose = ComponentPublicInstance & {
  element?: HTMLElement | { value: HTMLElement | null } | null
}

function assignSanitizedRef(value: Element | ComponentPublicInstance | null) {
  const exposedElement = (value as SanitizedHtmlViewExpose | null)?.element
  if (exposedElement && typeof exposedElement === 'object' && 'value' in exposedElement) {
    element.value = exposedElement.value
    return
  }
  element.value = exposedElement instanceof HTMLElement ? exposedElement : null
}

function assignSandboxRef(value: Element | ComponentPublicInstance | null) {
  sandboxWrapper.value = value instanceof HTMLElement ? value : null
  element.value = sandboxWrapper.value
}

defineExpose({
  element,
})
</script>

<template>
  <div
    v-if="shouldUseSandbox"
    :ref="assignSandboxRef"
    v-bind="attrs"
  >
    <SandboxedHtmlFrame
      :html="sandboxHtml ?? ''"
      :title="sandboxTitle"
    />
  </div>
  <SanitizedHtmlView
    v-else
    :ref="assignSanitizedRef"
    tag="div"
    v-bind="attrs"
    :html="sanitizedHtml"
  />
</template>
