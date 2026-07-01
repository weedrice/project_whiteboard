import { type ComponentPublicInstance, type Ref } from 'vue'

type PostContentViewExpose = ComponentPublicInstance & {
  element?: HTMLElement | { value: HTMLElement | null } | null
}

export function usePostContentViewRef(contentRef: Ref<HTMLElement | null>) {
  function assignContentRef(value: Element | ComponentPublicInstance | null) {
    const exposedElement = (value as PostContentViewExpose | null)?.element
    if (exposedElement && typeof exposedElement === 'object' && 'value' in exposedElement) {
      contentRef.value = exposedElement.value
      return
    }
    contentRef.value = exposedElement instanceof HTMLElement ? exposedElement : null
  }

  return {
    assignContentRef,
  }
}
