import { computed } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { useHead } from '@unhead/vue'
import { shouldNoIndexAppRoute } from '@/composables/appShellModel'
import { buildCanonicalUrl } from '@/utils/seoUrl'

const SITE_NAME = '노비스'

export function useAppSeo(route: RouteLocationNormalizedLoaded, t: (key: string) => string) {
    const shouldNoIndex = computed(() => shouldNoIndexAppRoute(route))
    const canonicalUrl = computed(() => buildCanonicalUrl(route.path))

    useHead({
        titleTemplate: computed(() => `%s - ${t('common.appName')}`),
        title: computed(() => t('common.appName')),
        meta: [
            {
                name: 'description',
                content: computed(() => t('common.seo.description'))
            },
            { property: 'og:site_name', content: SITE_NAME },
            { property: 'og:type', content: 'website' },
            { name: 'robots', content: computed(() => (shouldNoIndex.value ? 'noindex, nofollow' : 'index, follow')) },
            { name: 'googlebot', content: computed(() => (shouldNoIndex.value ? 'noindex, nofollow' : 'index, follow')) }
        ],
        link: computed(() => (shouldNoIndex.value ? [] : [
            { rel: 'canonical', href: canonicalUrl.value }
        ]))
    })

    return {
        shouldNoIndex,
        canonicalUrl,
    }
}
