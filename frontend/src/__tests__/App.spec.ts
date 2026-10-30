import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { nextTick } from 'vue'
import { ApiError, setApi, type QuestView, type Api, type GameState, type TurnEvent } from '../api'
import App from '../App.vue'

const start: GameState = { gameId: 'g1', lives: 3, gold: 100, level: 0, score: 0, highScore: 0, turn: 0 }

const quest: QuestView = {
  questId: 'a1',
  message: 'Help a farmer',
  reward: 30,
  expiresIn: 5,
  probability: 'Sure thing',
  encrypted: false,
  questType: 'Unknown',
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.97,
  expectedValue: 27.3,
  acceptable: true,
}

function event(message: string, state: Partial<GameState> = {}): TurnEvent {
  return { type: 'SOLVE', success: true, message, state: { ...start, turn: 1, ...state } }
}

function fakeApi(overrides: Partial<Api> = {}): Api {
  return {
    startGame: vi.fn().mockResolvedValue(start),
    getGame: vi.fn().mockResolvedValue(start),
    getHistory: vi.fn().mockResolvedValue([]),
    getQuests: vi.fn().mockResolvedValue([quest]),
    getShop: vi.fn().mockResolvedValue([{ id: 'hpot', name: 'Healing potion', cost: 50, recommended: false }]),
    solve: vi.fn().mockResolvedValue(event('Took the job')),
    buy: vi.fn().mockResolvedValue(event('Bought a potion')),
    investigate: vi.fn().mockResolvedValue(event('Reputation checked')),
    autoplayStep: vi.fn().mockResolvedValue([event('Solver moved', { score: 1000 })]),
    ...overrides,
  }
}

async function mountApp(api: Api): Promise<VueWrapper> {
  setApi(api)
  const wrapper = mount(App, { global: { plugins: [createPinia()] } })
  await flushPromises()
  return wrapper
}

async function clickButton(wrapper: VueWrapper, text: string) {
  await wrapper.findAll('button').find((b) => b.text() === text)!.trigger('click')
  await flushPromises()
}

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('opens on the dragon, with the start button at the top', async () => {
    const api = fakeApi()
    const wrapper = await mountApp(api)

    expect(wrapper.find('.sketch').exists()).toBe(true)
    expect(wrapper.find('.board').exists()).toBe(false)
    expect(wrapper.find('.stats__fill').exists()).toBe(false)
    expect(wrapper.findAll('button').map((b) => b.text())).toEqual(['Start a game'])
    expect(wrapper.text()).toContain('Two ways to play')
    expect(wrapper.find('.shop').exists()).toBe(false)
    expect(wrapper.find('.log').exists()).toBe(false)
    expect(wrapper.find('.masthead--compact').exists()).toBe(false)
    expect(api.getGame).not.toHaveBeenCalled()
  })

  it('makes room for the game once it starts, with the status above the controls', async () => {
    const wrapper = await mountApp(fakeApi())

    await clickButton(wrapper, 'Start a game')

    expect(wrapper.find('.masthead--compact').exists()).toBe(true)
    expect(wrapper.find('.panel__sub').exists()).toBe(false)
    const panel = Array.from(wrapper.get('.panel').element.children)
    expect(panel.indexOf(wrapper.get('.hud').element)).toBeLessThan(panel.indexOf(wrapper.get('.panel__top').element))
    expect(wrapper.get('.hud').find('.stats__fill').exists()).toBe(true)
    expect(wrapper.get('.hud').find('.outcome').exists()).toBe(true)
  })

  it('shows and announces the result of a move', async () => {
    const solve = vi.fn().mockResolvedValue(event('Took the job', { score: 30, gold: 130, turn: 1 }))
    const wrapper = await mountApp(fakeApi({ solve }))
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.get('.outcome').text()).toContain('Took the job')
    expect(wrapper.get('.outcome').text()).toContain('+30 points')
    expect(wrapper.get('[role="status"]').text()).toBe('Turn 1: Took the job +30 points, +30 gold.')
  })

  it('announces that autoplay started and how it ended, not every turn', async () => {
    let release: (events: TurnEvent[]) => void = () => {}
    const autoplayStep = vi.fn(() => new Promise<TurnEvent[]>((resolve) => (release = resolve)))
    const wrapper = await mountApp(fakeApi({ autoplayStep }))
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const status = wrapper.get('[role="status"]')
    expect(status.text()).toBe('Autoplaying to 1,000 points.')
    expect(wrapper.get('.outcome').text()).toContain('Autoplaying to 1,000…')

    release([event('Partway', { score: 400 })])
    await flushPromises()
    expect(status.text()).toBe('Autoplaying to 1,000 points.')

    release([event('Finished', { score: 1000, turn: 2 })])
    await flushPromises()
    expect(status.text()).toBe('Turn 2: Finished +600 points. Target reached.')
  })

  it('swaps the dragon for the board, shop and stats once a game starts', async () => {
    const wrapper = await mountApp(fakeApi())

    await clickButton(wrapper, 'Start a game')

    expect(wrapper.find('.sketch').exists()).toBe(false)
    expect(wrapper.text()).toContain('Help a farmer')
    expect(wrapper.text()).toContain('Healing potion')
    expect(wrapper.find('.stats__fill').exists()).toBe(true)
  })

  it('goes back to the dragon when the server forgets the game', async () => {
    const api = fakeApi({ solve: vi.fn().mockRejectedValue(new ApiError(404, 'No game with id g1')) })
    const wrapper = await mountApp(api)
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.sketch').exists()).toBe(true)
    expect(wrapper.find('.shop').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('No game with id g1')
  })

  it('resumes the game saved in this tab on load', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    const api = fakeApi()
    const wrapper = await mountApp(api)

    expect(api.getGame).toHaveBeenCalledWith('g1')
    expect(wrapper.text()).toContain('Help a farmer')
  })

  it('wires the board, shop and controls to the store', async () => {
    const api = fakeApi()
    const wrapper = await mountApp(api)
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('article button').trigger('click')
    await flushPromises()
    expect(api.solve).toHaveBeenCalledWith('g1', 'a1')
    expect(wrapper.text()).toContain('Took the job')

    await wrapper.get('.shop__buy').trigger('click')
    await flushPromises()
    expect(api.buy).toHaveBeenCalledWith('g1', 'hpot')

    await clickButton(wrapper, 'Check reputation')
    expect(api.investigate).toHaveBeenCalledWith('g1')

    await clickButton(wrapper, 'Solver plays one turn')
    expect(api.autoplayStep).toHaveBeenCalledWith('g1')
  })

  it('moves the solver suggestion from the board to the shop, and buys it from either', async () => {
    const api = fakeApi({
      getQuests: vi.fn().mockResolvedValue([{ ...quest, recommended: false }]),
      getShop: vi.fn().mockResolvedValue([
        { id: 'hpot', name: 'Healing potion', cost: 50, recommended: true },
        { id: 'cs', name: 'Claw Sharpening', cost: 100, recommended: false },
      ]),
    })
    const wrapper = await mountApp(api)
    await clickButton(wrapper, 'Start a game')

    expect(wrapper.find('.notice--pick').exists()).toBe(false)
    expect(wrapper.findAll('.shop__item--pick')).toHaveLength(1)
    expect(wrapper.get('.shop__item--pick').text()).toContain('Healing potion')

    await clickButton(wrapper, 'Buy Healing potion for 50 gold')
    expect(api.buy).toHaveBeenCalledWith('g1', 'hpot')
    expect(api.getShop).toHaveBeenCalledTimes(2)
  })

  it('stops suggesting purchases once the game is over', async () => {
    const api = fakeApi({
      getShop: vi.fn().mockResolvedValue([{ id: 'hpot', name: 'Healing potion', cost: 50, recommended: true }]),
      solve: vi.fn().mockResolvedValue(event('Dragon died', { lives: 0, turn: 7 })),
    })
    const wrapper = await mountApp(api)
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.shop__item--pick').exists()).toBe(false)
    expect(wrapper.find('.board__shop-first').exists()).toBe(false)
  })

  it('autoplays to the typed target and announces it', async () => {
    const wrapper = await mountApp(fakeApi())
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('form input').setValue('1000')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.banner--won').text()).toContain('1,000 points')
    expect(wrapper.find('.banner--over').exists()).toBe(false)
  })

  it('announces game over and takes the jobs down', async () => {
    const api = fakeApi({ solve: vi.fn().mockResolvedValue(event('Dragon died', { lives: 0, score: 40, turn: 7 })) })
    const wrapper = await mountApp(api)
    await clickButton(wrapper, 'Start a game')

    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.get('.banner--over').text()).toBe('Game over at 40 points after 7 turns.')
    expect(wrapper.findAll('article')).toHaveLength(0)
  })

  it('keeps its status region mounted and fills it when the game ends', async () => {
    const api = fakeApi({ solve: vi.fn().mockResolvedValue(event('Dragon died', { lives: 0, score: 40, turn: 7 })) })
    const wrapper = await mountApp(api)
    const status = wrapper.get('[role="status"]').element

    expect(status.textContent).toBe('')
    await clickButton(wrapper, 'Start a game')
    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="status"]').element).toBe(status)
    expect(status.textContent).toBe('Game over at 40 points after 7 turns.')
  })

  it('shows neither the dragon nor a closed shop while a saved game loads', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    let finish: (g: GameState) => void = () => {}
    setApi(fakeApi({ getGame: vi.fn(() => new Promise<GameState>((r) => (finish = r))) }))
    const wrapper = mount(App, { global: { plugins: [createPinia()] } })
    await nextTick()

    expect(wrapper.find('.sketch').exists()).toBe(false)
    expect(wrapper.text()).toContain('Loading your game')
    expect(wrapper.text()).toContain('Loading the jobs')
    expect(wrapper.text()).toContain('Loading the shop')
    expect(wrapper.text()).not.toContain('Starting')

    finish(start)
    await flushPromises()
    expect(wrapper.text()).toContain('Help a farmer')
  })

  it('shows a resumed game with its saved target in the field', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    sessionStorage.setItem('mugloar.target', '2500')
    const wrapper = await mountApp(fakeApi())

    expect((wrapper.get('form input').element as HTMLInputElement).value).toBe('2500')
  })

  it('says the shop failed to load, rather than that it is closed', async () => {
    const wrapper = await mountApp(fakeApi({ getShop: vi.fn().mockRejectedValue(new ApiError(502, 'Down')) }))

    await clickButton(wrapper, 'Start a game')

    expect(wrapper.text()).toContain('The shop could not be loaded')
    expect(wrapper.text()).not.toContain('The shop is closed')
  })

  it('shows errors in a banner that can be dismissed', async () => {
    const api = fakeApi({ startGame: vi.fn().mockRejectedValue(new ApiError(0, 'Cannot reach the server.')) })
    const wrapper = await mountApp(api)

    await clickButton(wrapper, 'Start a game')
    expect(wrapper.get('[role="alert"]').text()).toContain('Cannot reach the server.')

    await clickButton(wrapper, 'Dismiss')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
