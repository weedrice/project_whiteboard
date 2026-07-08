type HighlightNode = {
    type: 'text'
    value: string
}

type HighlightResult = {
    children: HighlightNode[]
    data?: {
        language?: string
    }
}

const createResult = (value: string, language?: string): HighlightResult => ({
    children: [{ type: 'text', value }],
    data: language ? { language } : undefined,
})

export const common = {}

export function createLowlight() {
    return {
        listLanguages: () => [],
        register: () => undefined,
        registerAlias: () => undefined,
        registered: () => false,
        highlight: (language: string, value: string) => createResult(value, language),
        highlightAuto: (value: string) => createResult(value, 'text'),
    }
}

export const lowlight = createLowlight()
