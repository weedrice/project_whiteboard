import { flushPromises } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'

export const identityT = (key: string, params?: Record<string, unknown>) => {
  if (params?.count !== undefined) {
    return `${key}:${params.count}`
  }
  return key
}

export const BaseModalStub = defineComponent({
  name: 'BaseModalStub',
  props: {
    isOpen: Boolean,
    title: String,
  },
  setup(props, { slots }) {
    return () => props.isOpen
      ? h('div', { 'data-test': 'modal' }, [
        h('h1', props.title),
        slots.default?.(),
        slots.footer?.(),
      ])
      : null
  },
})

export const BaseBadgeStub = defineComponent({
  name: 'BaseBadgeStub',
  setup(_, { slots }) {
    return () => h('span', slots.default?.())
  },
})

export const BaseButtonStub = defineComponent({
  name: 'BaseButtonStub',
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { type: 'button', onClick: () => emit('click') }, slots.default?.())
  },
})

export function createPaginationStub(nextPage = 2) {
  return defineComponent({
    name: 'PaginationStub',
    props: {
      currentPage: Number,
      totalPages: Number,
    },
    emits: ['page-change'],
    template: `<button data-test="pagination" type="button" @click="$emit('page-change', ${nextPage})">{{ currentPage }}/{{ totalPages }}</button>`,
  })
}

export function createPrevNextPaginationStub() {
  return defineComponent({
    name: 'PrevNextPaginationStub',
    props: {
      currentPage: Number,
      totalPages: Number,
    },
    emits: ['page-change'],
    template: `
      <div data-test="pagination">
        <button type="button" data-test="prev-page" :disabled="currentPage === 0" @click="$emit('page-change', currentPage - 1)">prev</button>
        <button type="button" data-test="next-page" :disabled="currentPage >= totalPages - 1" @click="$emit('page-change', currentPage + 1)">next</button>
      </div>
    `,
  })
}

export async function flushAll() {
  await flushPromises()
  await nextTick()
}
