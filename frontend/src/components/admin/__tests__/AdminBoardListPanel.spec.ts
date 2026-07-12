import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import AdminBoardListPanel from '../AdminBoardListPanel.vue'
import type { AdminBoard } from '@/types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('vuedraggable', () => ({
  default: defineComponent({
    props: { modelValue: { type: Array, default: () => [] } },
    setup(props, { slots }) {
      return () => h('div', (props.modelValue as AdminBoard[]).map((board, index) =>
        slots.item?.({ element: board, index }),
      ))
    },
  }),
}))

const makeBoard = (boardId: number, boardName: string): AdminBoard => ({
  boardId,
  boardName,
  boardUrl: boardName.toLowerCase(),
  sortOrder: boardId,
  allowNsfw: false,
  isActive: true,
  isPublic: true,
  agentUseYn: false,
})

describe('AdminBoardListPanel', () => {
  it('reorders boards with keyboard-operable controls', async () => {
    const boards = [makeBoard(1, 'General'), makeBoard(2, 'News')]
    const wrapper = mount(AdminBoardListPanel, {
      props: { boards, loading: false, selectedBoardId: 1 },
      global: {
        stubs: {
          AdminPanel: { template: '<div><slot /></div>' },
          AdminContentState: { template: '<div><slot /></div>' },
          BooleanBadge: true,
          ChevronDown: true,
          ChevronUp: true,
          GripVertical: true,
        },
      },
    })

    const moveDown = wrapper.find('button[aria-label="common.moveDown"]')
    expect(moveDown.attributes('disabled')).toBeUndefined()
    expect(moveDown.classes()).toEqual(expect.arrayContaining(['nv-touch-target-square', 'nv-focus-ring']))
    expect(wrapper.findAll('button').find((button) => button.text().includes('General'))?.classes()).toEqual(
      expect.arrayContaining(['min-h-11', 'nv-focus-ring']),
    )
    await moveDown.trigger('click')

    expect(wrapper.emitted('update:boards')?.[0]?.[0]).toEqual([boards[1], boards[0]])
    expect(wrapper.emitted('drag-end')).toHaveLength(1)
    expect(wrapper.get('[role="status"]').text()).toBe('common.moved')
  })
})
