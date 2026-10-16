import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi, mockSettings, mockUser } from './fixtures/mockApi'

const privateMessage = '사용자가 비공개로 설정한 정보입니다.'
const privateOwnerMessage = '다른 사람에게 비공개'
const emptyPage = {
  content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
}
const privateSettings = {
  profileOverviewPublic: false,
  profilePostsPublic: false,
  profileCommentsPublic: false,
}

function fulfillJson(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, error: null }),
  })
}

async function installProfileApi(page: Page, options: {
  authenticated?: boolean
  owner?: boolean
  publicPosts?: boolean
  publicComments?: boolean
} = {}) {
  const { authenticated = false, owner = false, publicPosts = false, publicComments = false } = options
  const auth = await installMockApi(page, { authenticated })
  const userId = owner ? mockUser.userId : 42
  const requests = { profile: 0, badges: 0, posts: 0, comments: 0 }
  await page.route(`**/api/v1/users/${userId}`, (route) => {
    requests.profile += 1
    const canViewOverview = owner && auth.authenticated
    const canViewPosts = canViewOverview || publicPosts
    const canViewComments = canViewOverview || publicComments
    return fulfillJson(route, {
      userId, displayName: 'Privacy Tester', profileImageUrl: null,
      ...privateSettings,
      profilePostsPublic: publicPosts,
      profileCommentsPublic: publicComments,
      canViewOverview, canViewPosts, canViewComments,
      createdAt: canViewOverview ? '2026-01-01T00:00:00' : null,
      postCount: canViewPosts ? 137 : null,
      commentCount: canViewComments ? 251 : null,
    })
  })
  await page.route(`**/api/v1/users/${userId}/badges`, (route) => {
    requests.badges += 1
    return fulfillJson(route, [{
      badgeCode: 'PRIVACY_TEST', name: 'Private badge', description: 'Owner badge details',
      tier: 'BRONZE', acquired: true, representative: false,
    }])
  })
  await page.route(`**/api/v1/users/${userId}/posts?**`, (route) => {
    requests.posts += 1
    return fulfillJson(route, emptyPage)
  })
  await page.route(`**/api/v1/users/${userId}/comments?**`, (route) => {
    requests.comments += 1
    return fulfillJson(route, {
      ...emptyPage,
      content: [{
        commentId: 81, content: 'Private comment contents', createdAt: '2026-01-01T00:00:00',
        post: { postId: 1, title: 'Commented post', boardUrl: 'general' },
      }],
      totalElements: 1, totalPages: 1,
    })
  })
  return { auth, requests, userId }
}

async function expectPrivateMetadata(page: Page) {
  for (const selector of ['meta[name="description"]', 'meta[property="og:description"]']) {
    await expect(page.locator(selector)).toHaveAttribute('content', /Privacy Tester/)
    await expect(page.locator(selector)).not.toHaveAttribute('content', /137|251|null|undefined/)
  }
}

for (const authenticated of [false, true]) {
  test(`${authenticated ? 'another member' : 'an anonymous visitor'} cannot load private profile sections`, async ({ page }) => {
    const { requests, userId } = await installProfileApi(page, { authenticated })
    await page.goto(`/user/${userId}`)
    await expect(page.getByRole('heading', { name: 'Privacy Tester', exact: true })).toBeVisible()
    await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
    await expect(page.getByText(/가입일:/)).toBeHidden()
    await expect(page.getByText('137', { exact: true })).toBeHidden()
    await expect(page.getByText('251', { exact: true })).toBeHidden()
    const profileHeader = page.locator('header').filter({
      has: page.getByRole('heading', { name: 'Privacy Tester', exact: true }),
    })
    await expect(profileHeader.getByText('작성 글', { exact: true })).toBeHidden()
    await expect(profileHeader.getByText('작성 댓글', { exact: true })).toBeHidden()
    await expectPrivateMetadata(page)

    for (const tab of ['작성 글', '작성 댓글', '개요']) {
      await page.getByRole('button', { name: tab, exact: true }).click()
      await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
    }
    await expect(page.getByText('Private badge', { exact: true })).toBeHidden()
    await expect(page.getByText('Private comment contents', { exact: true })).toBeHidden()
    expect(requests.profile).toBeGreaterThan(0)
    expect({ badges: requests.badges, posts: requests.posts, comments: requests.comments })
      .toEqual({ badges: 0, posts: 0, comments: 0 })
  })
}

test('the owner can read private activity with a privacy indicator and lazy tab loading', async ({ page }) => {
  const { requests, userId } = await installProfileApi(page, { authenticated: true, owner: true })
  await page.goto(`/user/${userId}`)
  await expect(page.getByText('Private badge', { exact: true })).toBeVisible()
  await expect(page.getByText(privateOwnerMessage, { exact: true })).toBeVisible()
  await expect(page.getByText('137', { exact: true })).toBeVisible()
  await expect(page.getByText('251', { exact: true })).toBeVisible()
  expect(requests.posts).toBe(0)
  expect(requests.comments).toBe(0)
  // Owner-only counts must never be put in share descriptions.
  await expectPrivateMetadata(page)

  await page.getByRole('button', { name: '작성 글', exact: true }).click()
  await expect.poll(() => requests.posts).toBe(1)
  await expect(page.getByText(privateOwnerMessage, { exact: true })).toBeVisible()
  expect(requests.comments).toBe(0)

  await page.getByRole('button', { name: '작성 댓글', exact: true }).click()
  await expect(page.getByText('Private comment contents', { exact: true })).toBeVisible()
  await expect(page.getByText(privateOwnerMessage, { exact: true })).toBeVisible()
  expect(requests.comments).toBe(1)
})

test('a public activity tab loads only when selected while the other sections stay private', async ({ page }) => {
  const { requests, userId } = await installProfileApi(page, { publicComments: true })
  await page.goto(`/user/${userId}`)
  await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
  await expect(page.getByText('251', { exact: true })).toBeVisible()
  await expect(page.getByText('137', { exact: true })).toBeHidden()
  expect(requests.comments).toBe(0)

  await page.getByRole('button', { name: '작성 글', exact: true }).click()
  await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '작성 댓글', exact: true }).click()
  await expect(page.getByText('Private comment contents', { exact: true })).toBeVisible()
  expect(requests.comments).toBe(1)
  expect(requests.posts).toBe(0)
  expect(requests.badges).toBe(0)
  for (const selector of ['meta[name="description"]', 'meta[property="og:description"]']) {
    await expect(page.locator(selector)).toHaveAttribute('content', /251/)
    await expect(page.locator(selector)).not.toHaveAttribute('content', /137|null|undefined/)
  }
})

test('logging out clears owner-only profile activity before returning through browser history', async ({ page }) => {
  const { requests, userId } = await installProfileApi(page, { authenticated: true, owner: true })
  await page.goto(`/user/${userId}`)
  await expect(page.getByText('Private badge', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '작성 댓글', exact: true }).click()
  await expect(page.getByText('Private comment contents', { exact: true })).toBeVisible()
  const previousProfileRequests = requests.profile
  const previousActivityRequests = { badges: requests.badges, posts: requests.posts, comments: requests.comments }

  await page.locator('#user-dropdown-trigger').click()
  await page.getByRole('menuitem', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL('/')
  await page.goBack()
  await expect(page).toHaveURL(`/user/${userId}`)
  await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
  await expect(page.getByText('137', { exact: true })).toBeHidden()
  await expect(page.getByText('251', { exact: true })).toBeHidden()
  await expect(page.getByText('Private badge', { exact: true })).toBeHidden()
  await expectPrivateMetadata(page)
  await page.getByRole('button', { name: '작성 댓글', exact: true }).click()
  await expect(page.getByText(privateMessage, { exact: true })).toBeVisible()
  await expect(page.getByText('Private comment contents', { exact: true })).toBeHidden()
  expect(requests.profile).toBeGreaterThan(previousProfileRequests)
  expect({ badges: requests.badges, posts: requests.posts, comments: requests.comments })
    .toEqual(previousActivityRequests)
})

test('profile privacy settings save independently and survive a reload', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  let settings = {
    ...mockSettings,
    profileOverviewPublic: true,
    profilePostsPublic: true,
    profileCommentsPublic: true,
  }
  const writes: Record<string, unknown>[] = []
  await page.route('**/api/v1/users/me/settings', (route) => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON()
      writes.push(payload)
      settings = { ...settings, ...payload }
    }
    return fulfillJson(route, settings)
  })

  await page.goto('/mypage/settings')
  const overview = page.getByRole('checkbox', { name: '개요 공개', exact: true })
  const posts = page.getByRole('checkbox', { name: '작성 글 공개', exact: true })
  const comments = page.getByRole('checkbox', { name: '작성 댓글 공개', exact: true })
  const save = page.getByRole('button', { name: '설정 저장', exact: true })
  await expect(overview).toBeChecked()
  await expect(posts).toBeChecked()
  await expect(comments).toBeChecked()
  await overview.click()
  await comments.click()
  await save.click()
  await expect(save).toBeDisabled()
  expect(writes).toHaveLength(1)
  expect(writes[0]).toMatchObject({
    profileOverviewPublic: false, profileCommentsPublic: false,
  })
  expect(writes[0]).not.toHaveProperty('profilePostsPublic')

  await page.reload()
  await expect(overview).not.toBeChecked()
  await expect(posts).toBeChecked()
  await expect(comments).not.toBeChecked()
  await expect(save).toBeDisabled()
})

test('saving an unrelated setting does not republish privacy changed by another session', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  let settings = {
    ...mockSettings,
    theme: 'LIGHT',
    profileOverviewPublic: true,
    profilePostsPublic: true,
    profileCommentsPublic: true,
  }
  const writes: Record<string, unknown>[] = []
  await page.route('**/api/v1/users/me/settings', (route) => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON()
      writes.push(payload)
      settings = { ...settings, ...payload }
    }
    return fulfillJson(route, settings)
  })

  await page.goto('/mypage/settings')
  const overview = page.getByRole('checkbox', { name: '개요 공개', exact: true })
  const posts = page.getByRole('checkbox', { name: '작성 글 공개', exact: true })
  const comments = page.getByRole('checkbox', { name: '작성 댓글 공개', exact: true })
  await expect(overview).toBeChecked()
  await page.getByRole('combobox', { name: '테마', exact: true }).selectOption('DARK')
  // Another browser changes the server while this form retains its original snapshot.
  settings = { ...settings, profileOverviewPublic: false, profilePostsPublic: false, profileCommentsPublic: false }
  const save = page.getByRole('button', { name: '설정 저장', exact: true })
  await save.click()
  await expect(save).toBeDisabled()
  expect(writes).toHaveLength(1)
  for (const field of ['profileOverviewPublic', 'profilePostsPublic', 'profileCommentsPublic']) {
    expect(writes[0]).not.toHaveProperty(field)
  }
  await expect(overview).not.toBeChecked()
  await expect(posts).not.toBeChecked()
  await expect(comments).not.toBeChecked()
  await page.reload()
  await expect(overview).not.toBeChecked()
  await expect(posts).not.toBeChecked()
  await expect(comments).not.toBeChecked()
})
