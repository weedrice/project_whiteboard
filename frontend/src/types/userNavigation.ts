export interface UserNavigationTab {
    nameKey: string
    href: string
}

export interface UserNavigationGroup {
    nameKey: string
    items: UserNavigationTab[]
}
