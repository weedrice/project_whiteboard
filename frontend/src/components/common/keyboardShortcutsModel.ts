export interface KeyboardShortcut {
    key: string
    description: string
}

export interface KeyboardShortcutGroup {
    title: string
    shortcuts: KeyboardShortcut[]
}

type Translate = (key: string) => string

export function buildKeyboardShortcutGroups(t: Translate, isAuthenticated: boolean): KeyboardShortcutGroup[] {
    return [
        {
            title: t('layout.shortcuts.global'),
            shortcuts: [
                { key: 'Shift+/', description: t('layout.shortcuts.help') },
                { key: 'H', description: t('layout.shortcuts.home') },
                { key: 'D', description: t('layout.shortcuts.darkMode') },
                { key: 'B', description: t('layout.shortcuts.allBoards') },
                { key: 'Shift+B', description: t('layout.shortcuts.allBoardsPage') },
                ...(isAuthenticated ? [
                    { key: 'S', description: t('layout.shortcuts.subscribedBoards') },
                    { key: 'M', description: t('layout.shortcuts.myPage') },
                    { key: 'Alt+N', description: t('layout.shortcuts.notifications') },
                    { key: 'Q', description: t('layout.shortcuts.logout') },
                ] : []),
            ],
        },
        {
            title: t('layout.shortcuts.dropdown'),
            shortcuts: [
                { key: '1-9, 0', description: t('layout.shortcuts.selectItem') },
                { key: 'Esc', description: t('layout.shortcuts.closeDropdown') },
            ],
        },
        {
            title: t('layout.shortcuts.boardList'),
            shortcuts: [
                { key: ']', description: t('layout.shortcuts.nextPage') },
                { key: '[', description: t('layout.shortcuts.prevPage') },
                { key: 'Shift+]', description: t('layout.shortcuts.lastPage') },
                { key: 'Shift+[', description: t('layout.shortcuts.firstPage') },
                { key: 'N', description: t('layout.shortcuts.write') },
                { key: 'F', description: t('layout.shortcuts.subscribe') },
                { key: '/', description: t('layout.shortcuts.focusSearch') },
            ],
        },
        {
            title: t('layout.shortcuts.postDetail'),
            shortcuts: [
                { key: 'C', description: t('layout.shortcuts.comments') },
                { key: 'U', description: t('layout.shortcuts.toList') },
                { key: 'L', description: t('layout.shortcuts.like') },
                { key: 'Shift+S', description: t('layout.shortcuts.scrap') },
                { key: 'Y', description: t('layout.shortcuts.copyUrl') },
                { key: 'Shift+Y', description: t('layout.shortcuts.share') },
                { key: 'E', description: t('layout.shortcuts.edit') },
            ],
        },
        {
            title: t('layout.shortcuts.writeEdit'),
            shortcuts: [
                { key: 'Ctrl+Enter', description: t('layout.shortcuts.submit') },
                { key: 'Esc', description: t('layout.shortcuts.cancel') },
            ],
        },
        {
            title: t('layout.shortcuts.mypageTabs'),
            shortcuts: [
                { key: ']', description: t('layout.shortcuts.nextTab') },
                { key: '[', description: t('layout.shortcuts.prevTab') },
            ],
        },
    ]
}

export function buildKeyboardShortcutColumns(groups: KeyboardShortcutGroup[]): KeyboardShortcutGroup[][] {
    return [
        [groups[0], groups[1], groups[5]].filter(Boolean),
        [groups[2], groups[4]].filter(Boolean),
        [groups[3]].filter(Boolean),
    ]
}
