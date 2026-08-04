import { describe, expect, it } from 'vitest'
import {
    buildPostFormPayload,
    extractFileIdFromPostImageSrc,
    extractPostFileIdsFromContent,
    resolvePostFormFileIds,
    toEmbedPostVideoUrl,
    toSafePostLinkUrl,
    validatePostDraftPoll,
    validatePostDraftPollContract,
    validatePostDraftContent,
    validatePostFormPoll,
    validatePostFormContent,
} from '../postForm'
import { decodeSandboxedPostHtml } from '../postHtmlSandbox'

const baseForm = {
    title: 'Title',
    content: '<p>Hello</p>',
    categoryId: '3',
    tags: ['a'],
    isNsfw: true,
    isSpoiler: true,
    isNotice: true,
    isSecret: true,
}

describe('postForm', () => {
    it('enforces the shared post write limits', () => {
        const valid = { title: 'title', content: 'body', tags: ['tag'], fileIds: [1] }
        expect(validatePostFormContent(valid)).toBeNull()
        expect(validatePostFormContent({ ...valid, title: 't'.repeat(201) })).toBe('titleTooLong')
        expect(validatePostFormContent({ ...valid, content: 'c'.repeat(100_001) })).toBe('contentTooLong')
        expect(validatePostFormContent({ ...valid, tags: Array.from({ length: 11 }, (_, index) => `tag${index}`) })).toBe('tooManyTags')
        expect(validatePostFormContent({ ...valid, tags: ['t'.repeat(101)] })).toBe('tagTooLong')
        expect(validatePostFormContent({ ...valid, title: '<b>title</b>' })).toBe('titleContainsHtml')
        expect(validatePostFormContent({ ...valid, tags: [' '] })).toBe('tagRequired')
        expect(validatePostFormContent({ ...valid, fileIds: Array.from({ length: 21 }, (_, index) => index + 1) })).toBe('tooManyFiles')
    })

    it('extracts unique local persisted file ids from images and attachment links', () => {
        expect(extractFileIdFromPostImageSrc('/api/v1/files/12', 'https://noviis.kr')).toBe(12)
        expect(extractFileIdFromPostImageSrc('/files/13', 'https://noviis.kr')).toBe(13)
        expect(extractFileIdFromPostImageSrc('https://external.test/files/14', 'https://noviis.kr')).toBeNull()

        const content = [
            '<img src="blob:https://noviis.kr/local" data-file-id="15">',
            '<img src="/api/v1/files/16">',
            '<img src="https://external.test/files/17">',
            '<a href="/api/v1/files/18">attachment</a>',
            '<a href="/files/16">duplicate attachment</a>',
            '<a href="https://external.test/files/19">external attachment</a>',
        ].join('')

        expect(extractPostFileIdsFromContent(content)).toEqual([15, 16, 18])
    })

    it('filters draft payload file ids to files tracked by the draft', () => {
        const content = '<img src="/api/v1/files/10"><img src="/api/v1/files/11">'

        expect(resolvePostFormFileIds(content, [11], 'content')).toEqual([10, 11])
        expect(resolvePostFormFileIds(content, [11], 'draft')).toEqual([11])
    })

    it('builds the same post payload shape used by create and update flows', () => {
        expect(buildPostFormPayload({
            form: {
                ...baseForm,
                title: '  Title  ',
            },
            mode: 'create',
            showNotice: true,
            canShowNsfw: false,
            fileIds: [1, 2],
        })).toEqual({
            title: 'Title',
            categoryId: 3,
            tags: ['a'],
            contents: '<p>Hello</p>',
            isNsfw: false,
            isSpoiler: true,
            isSecret: true,
            isNotice: true,
            fileIds: [1, 2],
        })

        expect(buildPostFormPayload({
            form: baseForm,
            mode: 'edit',
            hideCategory: true,
            hideTags: true,
            hideSpoiler: true,
            hideSecret: true,
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).toEqual({
            title: 'Title',
            tags: [],
            contents: '<p>Hello</p>',
            isNsfw: true,
            isSpoiler: false,
            isSecret: false,
            isNotice: true,
            seriesId: null,
            fileIds: [],
        })
    })

    it('omits an empty series on create and explicitly clears it on edit', () => {
        const formWithoutSeries = { ...baseForm, seriesId: '' }

        expect(buildPostFormPayload({
            form: formWithoutSeries,
            mode: 'create',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).not.toHaveProperty('seriesId')

        expect(buildPostFormPayload({
            form: formWithoutSeries,
            mode: 'edit',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).toHaveProperty('seriesId', null)
    })

    it('includes valid poll data for create and explicitly enabled scheduled-edit payloads', () => {
        const formWithPoll = {
            ...baseForm,
            poll: {
                question: '  Pick one  ',
                options: [' Alpha ', '', 'Beta'],
                multipleChoiceEnabled: true,
                anonymousEnabled: false,
                closesAt: null,
            },
        }

        expect(buildPostFormPayload({
            form: formWithPoll,
            mode: 'create',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).toMatchObject({
            poll: {
                question: 'Pick one',
                options: ['Alpha', 'Beta'],
                multipleChoiceEnabled: true,
                anonymousEnabled: false,
                closesAt: null,
            },
        })

        expect(buildPostFormPayload({
            form: formWithPoll,
            mode: 'edit',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).not.toHaveProperty('poll')

        expect(buildPostFormPayload({
            form: formWithPoll,
            mode: 'edit',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
            includePoll: true,
        })).toHaveProperty('poll.question', 'Pick one')
    })

    it('validates complete poll limits and a future close time before publishing', () => {
        const validPoll = {
            question: 'Pick one',
            options: ['Alpha', 'Beta'],
            multipleChoiceEnabled: false,
            anonymousEnabled: false,
            closesAt: '2026-01-02T00:00:00Z',
        }
        const now = new Date('2026-01-01T00:00:00Z').getTime()

        expect(validatePostFormPoll(validPoll, now)).toBeNull()
        expect(validatePostFormPoll({ ...validPoll, question: ' '.repeat(2) }, now)).toBe('questionRequired')
        expect(validatePostFormPoll({ ...validPoll, question: 'q'.repeat(201) }, now)).toBe('questionTooLong')
        expect(validatePostFormPoll({ ...validPoll, options: ['Alpha'] }, now)).toBe('optionCount')
        expect(validatePostFormPoll({ ...validPoll, options: Array.from({ length: 11 }, (_, index) => `Option ${index}`) }, now)).toBe('optionCount')
        expect(validatePostFormPoll({ ...validPoll, options: ['Alpha', ' '] }, now)).toBe('optionRequired')
        expect(validatePostFormPoll({ ...validPoll, options: ['Alpha', 'b'.repeat(101)] }, now)).toBe('optionTooLong')
        expect(validatePostFormPoll({ ...validPoll, closesAt: '2025-12-31T23:59:59Z' }, now)).toBe('closesAtFuture')
        expect(validatePostFormPoll({ ...validPoll, closesAt: 'not-a-date' }, now)).toBe('closesAtFuture')
        expect(validatePostFormPoll(
            { ...validPoll, closesAt: '2026-01-02T00:01:00Z' },
            now,
            '2026-01-02T00:00:00Z',
        )).toBe('closesAtAfterSchedule')
        expect(validatePostFormPoll(
            { ...validPoll, closesAt: '2026-01-02T00:01:01Z' },
            now,
            '2026-01-02T00:00:00Z',
        )).toBeNull()
    })

    it('validates the transformed draft payload sent to the server', () => {
        const rawContent = `<style>${'x'.repeat(75_000)}</style>`
        const payload = buildPostFormPayload({
            form: { ...baseForm, content: rawContent },
            mode: 'create',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })

        expect(validatePostDraftContent({
            title: baseForm.title,
            content: rawContent,
            tags: baseForm.tags,
            fileIds: [],
        })).toBeNull()
        expect(validatePostDraftContent({
            title: payload.title,
            content: payload.contents,
            tags: payload.tags,
            fileIds: payload.fileIds,
        })).toBe('contentTooLong')
    })

    it('validates draft poll structure without rejecting an expired close time', () => {
        const expiredPoll = {
            question: 'Pick one',
            options: ['Alpha', 'Beta'],
            multipleChoiceEnabled: false,
            anonymousEnabled: false,
            closesAt: '2025-01-01T00:00:00Z',
        }

        expect(validatePostDraftPoll(expiredPoll)).toBeNull()
        expect(validatePostDraftPoll({ ...expiredPoll, options: ['Alpha'] })).toBe('optionCount')
    })

    it('allows incomplete draft polls while enforcing only storage upper bounds', () => {
        const incompletePoll = {
            question: '',
            options: [''],
            multipleChoiceEnabled: false,
            anonymousEnabled: false,
            closesAt: null,
        }

        expect(validatePostDraftPollContract(incompletePoll)).toBeNull()
        expect(validatePostDraftPollContract({ ...incompletePoll, question: 'q'.repeat(201) })).toBe('questionTooLong')
        expect(validatePostDraftPollContract({ ...incompletePoll, options: Array(11).fill('') })).toBe('optionCount')
        expect(validatePostDraftPollContract({ ...incompletePoll, options: ['o'.repeat(101)] })).toBe('optionTooLong')
    })

    // `datetime-local` 입력이 만드는 값은 offset이 없는 서버 기준(KST) 벽시계다.
    // 이를 기기 지역으로 읽으면 KST 밖 사용자는 실제로는 미래인 마감 시각을
    // "현재보다 이전"이라며 거부당한다.
    it('reads an offset-less close time as the server zone, not the device zone', () => {
        const poll = {
            question: 'Pick one',
            options: ['Alpha', 'Beta'],
            multipleChoiceEnabled: false,
            anonymousEnabled: false,
            // KST 2026-07-26 09:00 = 2026-07-26T00:00:00Z
            closesAt: '2026-07-26T09:00',
        }
        // 진짜 현재는 마감 30분 전이다.
        const now = new Date('2026-07-25T23:30:00Z').getTime()

        expect(validatePostFormPoll(poll, now)).toBeNull()

        // 같은 벽시계 값이 실제로 과거라면 여전히 거부해야 한다.
        const afterClose = new Date('2026-07-26T00:30:00Z').getTime()
        expect(validatePostFormPoll(poll, afterClose)).toBe('closesAtFuture')
    })

    it('compares an offset-less close time against an offset-less schedule in the same zone', () => {
        const poll = {
            question: 'Pick one',
            options: ['Alpha', 'Beta'],
            multipleChoiceEnabled: false,
            anonymousEnabled: false,
            closesAt: '2026-07-26T09:00',
        }
        const now = new Date('2026-07-25T23:30:00Z').getTime()

        expect(validatePostFormPoll(poll, now, '2026-07-26T08:59')).toBe('closesAtAfterSchedule')
        expect(validatePostFormPoll(poll, now, '2026-07-26T08:00')).toBeNull()
    })

    it('encodes script-style html widgets before submit', () => {
        const rawWidget = '<style>.cl{display:grid}</style><button onclick="toggle()">여권</button><script>function toggle(){}</script>'
        const payload = buildPostFormPayload({
            form: {
                ...baseForm,
                content: rawWidget,
            },
            mode: 'create',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })

        expect(payload.contents).toContain('noviis-sandboxed-post-html')
        expect(payload.contents).not.toContain('<script>')
        expect(payload.contents).not.toContain('onclick=')
        expect(decodeSandboxedPostHtml(payload.contents)).toBe(rawWidget)
    })

    it('normalizes supported video URLs to backend-allowed embed URLs', () => {
        expect(toEmbedPostVideoUrl('https://youtu.be/abc_123')).toBe('https://www.youtube.com/embed/abc_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube.com/watch?v=watch_123')).toBe('https://www.youtube.com/embed/watch_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://youtube.com/shorts/short_123')).toBe('https://www.youtube.com/embed/short_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube.com/embed/embed_123?start=10')).toBe('https://www.youtube.com/embed/embed_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube-nocookie.com/embed/private_123')).toBe('https://www.youtube-nocookie.com/embed/private_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://vimeo.com/12345')).toBe('https://player.vimeo.com/video/12345')
        expect(toEmbedPostVideoUrl('https://player.vimeo.com/video/67890?autoplay=1')).toBe('https://player.vimeo.com/video/67890')
    })

    it('rejects unsupported or unsafe video URLs', () => {
        expect(toEmbedPostVideoUrl('https://example.com/video')).toBe('')
        expect(toEmbedPostVideoUrl('javascript:alert(1)')).toBe('')
        expect(toEmbedPostVideoUrl('data:text/html,video')).toBe('')
        expect(toEmbedPostVideoUrl('https://youtube.com/watch')).toBe('')
        expect(toEmbedPostVideoUrl('https://player.vimeo.com/channels/staffpicks/12345')).toBe('')
    })

    it('normalizes only safe post link URLs', () => {
        expect(toSafePostLinkUrl('https://example.com/path')).toBe('https://example.com/path')
        expect(toSafePostLinkUrl('http://example.com')).toBe('http://example.com/')
        expect(toSafePostLinkUrl('example.com/docs')).toBe('https://example.com/docs')
        expect(toSafePostLinkUrl('javascript:alert(1)')).toBe('')
        expect(toSafePostLinkUrl('data:text/html,link')).toBe('')
        expect(toSafePostLinkUrl('ftp://example.com/file')).toBe('')
        expect(toSafePostLinkUrl('https://user:pass@example.com')).toBe('')
        expect(toSafePostLinkUrl('http://')).toBe('')
    })
})
