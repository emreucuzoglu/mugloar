import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { setApi, type Api, type GameState, type QuestView, type TurnEvent } from '../api'
import App from '../App.vue'

/** Text from the game server is untrusted, so markup in it has to reach the screen as plain text. */
const HOSTILE = '<img src=x onerror="window.hacked=1"><script>window.hacked=1</script><b>bold</b>'

const state: GameState = { gameId: 'g1', lives: 3, gold: 500, level: 0, score: 0, highScore: 0, turn: 0 }

const quest: QuestView = {
  questId: 'a1',
  message: HOSTILE,
  reward: 30,
  expiresIn: 5,
  probability: HOSTILE,
  encrypted: false,
  questType: HOSTILE,
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.97,
  expectedValue: 27.3,
  acceptable: true,
}

const event: TurnEvent = { type: 'SOLVE', success: true, message: HOSTILE, state: { ...state, turn: 1 } }

function fakeApi(): Api {
  return {
    startGame: vi.fn().mockResolvedValue(state),
    getGame: vi.fn().mockResolvedValue(state),
    getHistory: vi.fn().mockResolvedValue([]),
    getQuests: vi.fn().mockResolvedValue([quest]),
    getShop: vi.fn().mockResolvedValue([{ id: 'hpot', name: HOSTILE, cost: 50, recommended: true }]),
    solve: vi.fn().mockResolvedValue(event),
    buy: vi.fn().mockResolvedValue(event),
    investigate: vi.fn().mockResolvedValue(event),
    autoplayStep: vi.fn().mockResolvedValue([event]),
  }
}

describe('text from the game server', () => {
  it('shows markup as plain text on the board, in the shop and in the log', async () => {
    sessionStorage.clear()
    setApi(fakeApi())
    const wrapper = mount(App, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Start a game')!.trigger('click')
    await flushPromises()
    await wrapper.get('article button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(HOSTILE)
    expect(wrapper.find('img[src="x"]').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('b').exists()).toBe(false)
    expect((window as unknown as { hacked?: number }).hacked).toBeUndefined()
  })
})
