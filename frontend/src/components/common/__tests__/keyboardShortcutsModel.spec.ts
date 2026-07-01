import { describe, expect, it } from 'vitest'
import {
    buildKeyboardShortcutColumns,
    buildKeyboardShortcutGroups,
} from '@/components/common/keyboardShortcutsModel'

const t = (key: string) => key

describe('keyboardShortcutsModel', () => {
    it('includes authenticated-only shortcuts for signed-in users', () => {
        const globalGroup = buildKeyboardShortcutGroups(t, true)[0]

        expect(globalGroup.shortcuts.map((shortcut) => shortcut.key)).toEqual([
            'Shift+/',
            'H',
            'D',
            'B',
            'Shift+B',
            'S',
            'M',
            'Alt+N',
            'Q',
        ])
    })

    it('omits authenticated-only shortcuts for guests', () => {
        const globalGroup = buildKeyboardShortcutGroups(t, false)[0]

        expect(globalGroup.shortcuts.map((shortcut) => shortcut.key)).toEqual([
            'Shift+/',
            'H',
            'D',
            'B',
            'Shift+B',
        ])
    })

    it('keeps modal shortcut columns in the intended group order', () => {
        const groups = buildKeyboardShortcutGroups(t, true)
        const columns = buildKeyboardShortcutColumns(groups)

        expect(columns.map((column) => column.map((group) => group.title))).toEqual([
            ['layout.shortcuts.global', 'layout.shortcuts.dropdown', 'layout.shortcuts.mypageTabs'],
            ['layout.shortcuts.boardList', 'layout.shortcuts.writeEdit'],
            ['layout.shortcuts.postDetail'],
        ])
    })
})
