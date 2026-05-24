import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import FeedCard from '../FeedCard.vue'
import type { FeedPost } from '@/types'

const routerPush = vi.fn()

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: routerPush,
    }),
}))

const post: FeedPost = {
    postId: 11,
    title: '오늘의 글',
    summary: '요약',
    viewCount: 10,
    likeCount: 4,
    commentCount: 2,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    author: {
        userId: 1,
        displayName: '작성자',
    },
    createdAt: '2026-05-24T10:00:00',
    boardUrl: 'free',
    boardName: '자유',
    authorName: '작성자',
    liked: false,
    scrapped: false,
    subscribed: false,
}

const mountFeedCard = () => mount(FeedCard, {
    props: { post },
    global: {
        mocks: {
            $t: (key: string) => key,
        },
        stubs: {
            'router-link': RouterLinkStub,
            Bookmark: true,
            Eye: true,
            MessageSquare: true,
            ThumbsUp: true,
            User: true,
        },
    },
})

describe('FeedCard', () => {
    it('names action buttons with their counts', () => {
        const wrapper = mountFeedCard()

        expect(wrapper.get('button[aria-label="좋아요 4개"]').attributes('aria-label')).toBe('좋아요 4개')
        expect(wrapper.get('button[aria-label="댓글 2개 보기"]').attributes('aria-label')).toBe('댓글 2개 보기')
    })

    it('navigates to comments when the comments action is clicked', async () => {
        const wrapper = mountFeedCard()

        await wrapper.get('button[aria-label="댓글 2개 보기"]').trigger('click')

        expect(routerPush).toHaveBeenCalledWith('/board/free/post/11#comments')
    })
})
