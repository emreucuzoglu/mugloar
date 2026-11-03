import { expect, test, type Page } from '@playwright/test'

const state = { gameId: 'g1', lives: 3, gold: 100, level: 0, score: 0, highScore: 0, turn: 0 }

const quest = {
  questId: 'q1',
  message: 'Help a farmer',
  reward: 30,
  expiresIn: 5,
  probability: 'Sure thing',
  encrypted: false,
  questType: 'Fix',
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.97,
  expectedValue: 27.3,
  acceptable: true,
}

interface MockOptions {
  /** What a move leaves the game looking like. */
  after?: Partial<typeof state>
  shopPick?: boolean
  /** Holds autoplay steps until the test lets them go. */
  stepGate?: Promise<void>
}

async function mockApi(page: Page, { after = { turn: 1, score: 30 }, shopPick = false, stepGate }: MockOptions = {}) {
  let current = state
  let turn = 0
  const move = () => {
    turn += 1
    current = { ...current, turn, ...after, ...('turn' in after ? { turn } : {}) }
    return { type: 'SOLVE', success: true, message: `Move ${turn}`, state: current }
  }
  await page.route('**/api/games**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const json = (body: unknown) => route.fulfill({ json: body })
    if (request.method() === 'POST') {
      if (path.endsWith('/games')) {
        return json(state)
      }
      if (path.endsWith('/autoplay/step')) {
        await stepGate
        return json([move()])
      }
      return json(move())
    }
    if (path.endsWith('/quests')) {
      return json(current.lives > 0 ? [{ ...quest, questId: `q${turn}`, message: `Job ${turn}`, recommended: !shopPick }] : [])
    }
    if (path.endsWith('/shop')) {
      return json([{ id: 'hpot', name: 'Healing potion', cost: 50, recommended: shopPick }])
    }
    if (path.endsWith('/history')) {
      return json([])
    }
    return json(current)
  })
}

/** The CSP reports blocked content on the console, so a violation fails the test that caused it. */
function collectConsoleErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text())
    }
  })
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}

const focusedLabel = (page: Page) =>
  page.evaluate(() => {
    const active = document.activeElement as HTMLElement
    return `${active.tagName}:${active.textContent?.trim()}`
  })

async function startGame(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Start a game' }).click()
  await expect(page.getByRole('button', { name: /^Take this job/ })).toBeVisible()
}

test('plays a turn on the production build without a CSP violation', async ({ page }) => {
  const errors = collectConsoleErrors(page)
  await mockApi(page)

  await startGame(page)
  await page.getByLabel('Autoplay to').fill('30')
  await page.getByRole('button', { name: 'Autoplay' }).click()

  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '30')
  const fill = page.locator('.stats__fill')
  await expect.poll(() => fill.evaluate((el) => (el as HTMLElement).style.width)).toBe('100%')
  expect(errors).toEqual([])
})

test('keeps keyboard focus on the board after taking a job', async ({ page }) => {
  await mockApi(page)
  await startGame(page)

  await page.getByRole('button', { name: /^Take this job/ }).focus()
  await page.keyboard.press('Enter')

  await expect(page.getByText('Job 1')).toBeVisible()
  await expect.poll(() => focusedLabel(page)).toBe('H2:Jobs on the board')
})

test('keeps keyboard focus on a control button after its turn', async ({ page }) => {
  await mockApi(page)
  await startGame(page)

  await page.getByRole('button', { name: 'Solver plays one turn' }).focus()
  await page.keyboard.press('Enter')

  await expect(page.locator('.stats__number')).toHaveText('30 points')
  await expect.poll(() => focusedLabel(page)).toBe('BUTTON:Solver plays one turn')
})

test('moves focus to the new game button when the game ends', async ({ page }) => {
  await mockApi(page, { after: { lives: 0, turn: 1 } })
  await startGame(page)

  await page.getByRole('button', { name: 'Solver plays one turn' }).focus()
  await page.keyboard.press('Enter')

  await expect(page.getByRole('status').first()).toContainText('Game over')
  await expect(page.getByRole('button', { name: 'Solver plays one turn' })).toHaveCount(0)
  await expect.poll(() => focusedLabel(page)).toBe('BUTTON:Start a new game')
})

test('the autoplay button keeps focus while it turns into stop and back', async ({ page }) => {
  let release = () => {}
  const stepGate = new Promise<void>((resolve) => (release = resolve))
  await mockApi(page, { after: { score: 2000, turn: 1 }, stepGate })
  await startGame(page)
  await page.getByLabel('Autoplay to').fill('1000')

  await page.getByRole('button', { name: 'Autoplay', exact: true }).focus()
  await page.keyboard.press('Enter')

  await expect(page.getByRole('button', { name: 'Stop', exact: true })).toBeFocused()
  release()
  await expect(page.getByRole('button', { name: 'Autoplay', exact: true })).toBeFocused()
})

test('a disabled solver pick in the shop stays readable', async ({ page }) => {
  let release = () => {}
  const stepGate = new Promise<void>((resolve) => (release = resolve))
  await mockApi(page, { shopPick: true, stepGate })
  await startGame(page)

  await page.getByRole('button', { name: 'Solver plays one turn' }).click()

  const buy = page.getByRole('button', { name: 'Buy Healing potion for 50 gold' }).last()
  await expect(buy).toBeDisabled()
  const colours = await buy.evaluate((el) => {
    const style = getComputedStyle(el)
    return { text: style.color, background: style.backgroundColor }
  })
  release()
  expect(colours.text).toBe('rgb(255, 255, 255)')
  expect(colours.background).not.toBe('rgba(0, 0, 0, 0)')
})
