import { readdirSync, readFileSync } from 'node:fs'
import { extname, join } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const WIDTHS = [320, 768, 1440]
const VIEW_HEIGHT = 800
const SERIOUS_IMPACTS = ['serious', 'critical']
const UNBROKEN = 'Unbrokentextfromthegameserver'.repeat(12)
const DIST = 'dist'

const state = { gameId: 'g1', lives: 3, gold: 500, level: 2, score: 1200, highScore: 1200, turn: 12 }

const quest = {
  questId: 'q1',
  message: 'Help a farmer',
  reward: 30,
  expiresIn: 5,
  probability: 'Sure thing',
  encrypted: true,
  questType: 'Fix',
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.97,
  expectedValue: 27.3,
  acceptable: true,
}

const quests = (message: string) => [
  { ...quest, message },
  { ...quest, questId: 'q2', recommended: false, probability: 'Risky', successChance: 0.4, acceptable: true },
  {
    ...quest,
    questId: 'q3',
    message: 'Steal super awesome diamond mug',
    recommended: false,
    questType: 'Trap',
    trap: true,
    successChance: 0,
    acceptable: false,
  },
]

const shop = (name: string) => [
  { id: 'hpot', name, cost: 50, recommended: false },
  { id: 'ch', name: 'Claw Honing', cost: 300, recommended: true },
  { id: 'rf', name: 'Rocket Fuel', cost: 3000, recommended: false },
]

async function mockApi(page: Page, { text = 'Help a farmer', lives = 3 } = {}) {
  const current = { ...state, lives }
  const event = { type: 'SOLVE', success: true, message: text, state: current }
  await page.route('**/api/games**', (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() === 'POST') {
      if (path.endsWith('/games')) {
        return route.fulfill({ json: current })
      }
      return route.fulfill({ json: path.endsWith('/autoplay/step') ? [event] : event })
    }
    if (path.endsWith('/quests')) {
      return route.fulfill({ json: lives > 0 ? quests(text) : [] })
    }
    if (path.endsWith('/shop')) {
      return route.fulfill({ json: shop(text === 'Help a farmer' ? 'Healing potion' : text) })
    }
    if (path.endsWith('/history')) {
      return route.fulfill({ json: [event] })
    }
    return route.fulfill({ json: current })
  })
}

async function startGame(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Start a game' }).click()
  await expect(page.getByRole('progressbar')).toBeVisible()
}

/** A move puts entries in the log, which only appears once there is something to show. */
async function playAMove(page: Page) {
  await page.getByRole('button', { name: /^Take this job/ }).first().click()
  await expect(page.getByRole('button', { name: /^Take this job/ }).first()).toBeEnabled()
}

const overflow = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)

async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze()
  return results.violations
    .filter((violation) => SERIOUS_IMPACTS.includes(violation.impact ?? ''))
    .map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`)
}

for (const width of WIDTHS) {
  test.describe(`at ${width}px`, () => {
    test.use({ viewport: { width, height: VIEW_HEIGHT } })

    test('the landing page does not scroll sideways', async ({ page }) => {
      await page.goto('/')
      await expect(page.getByRole('button', { name: 'Start a game' })).toBeVisible()

      expect(await overflow(page)).toBeLessThanOrEqual(0)
    })

    test('a game does not scroll sideways', async ({ page }) => {
      await mockApi(page)
      await startGame(page)
      await playAMove(page)

      expect(await overflow(page)).toBeLessThanOrEqual(0)
    })

    test('long unbroken text from the server does not scroll sideways', async ({ page }) => {
      await mockApi(page, { text: UNBROKEN })
      await startGame(page)
      await playAMove(page)

      await expect(page.getByText(UNBROKEN).first()).toBeVisible()
      expect(await overflow(page)).toBeLessThanOrEqual(0)
    })

    test('has no serious accessibility problems during a game', async ({ page }) => {
      await mockApi(page)
      await startGame(page)
      await playAMove(page)

      expect(await seriousViolations(page)).toEqual([])
    })
  })
}

test('has no serious accessibility problems on the landing page', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('button', { name: 'Start a game' })).toBeVisible()

  expect(await seriousViolations(page)).toEqual([])
})

test('has no serious accessibility problems once the game is over', async ({ page }) => {
  await mockApi(page, { lives: 0 })
  await startGame(page)

  expect(await seriousViolations(page)).toEqual([])
})

test('has no serious accessibility problems with an error on screen', async ({ page }) => {
  await page.route('**/api/games', (route) =>
    route.fulfill({ status: 502, json: { title: 'Game server error', detail: 'The game server could not be reached.' } }),
  )
  await page.goto('/')
  await page.getByRole('button', { name: 'Start a game' }).click()
  await expect(page.getByRole('alert')).toBeVisible()

  expect(await seriousViolations(page)).toEqual([])
})

test('sends the security headers', async ({ request }) => {
  const headers = (await request.get('/')).headers()

  expect(headers['content-security-policy']).toContain("default-src 'self'")
  expect(headers['content-security-policy']).toContain("object-src 'none'")
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'")
  expect(headers['x-content-type-options']).toBe('nosniff')
  expect(headers['referrer-policy']).toBe('no-referrer')
})

test.describe('the built files', () => {
  const files = (extension: string, dir = DIST): string[] =>
    readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) {
        return files(extension, path)
      }
      return extname(path) === extension ? [path] : []
    })
  const read = (extension: string) => files(extension).map((path) => readFileSync(path, 'utf8'))

  test('the page has no inline script or style and calls no third party', () => {
    const [html] = read('.html')

    expect(html).toBeDefined()
    expect(html).not.toMatch(/<style/i)
    expect(html).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/i)
    expect(html).not.toMatch(/\sstyle=/i)
    expect(html).not.toMatch(/(src|href)=["']https?:/i)
  })

  test('the styles use no data: URLs and load nothing from a third party', () => {
    const css = read('.css')

    expect(css.length).toBeGreaterThan(0)
    for (const sheet of css) {
      expect(sheet).not.toMatch(/url\(["']?data:/i)
      expect(sheet).not.toMatch(/url\(["']?https?:/i)
    }
  })
})
