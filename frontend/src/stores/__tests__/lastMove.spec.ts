import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, setApi, type Api, type GameState, type TurnEvent } from '../../api'
import { useGameStore } from '../game'

const start: GameState = { gameId: 'g1', lives: 3, gold: 100, level: 0, score: 0, highScore: 0, turn: 0 }

const event = (message: string, state: Partial<GameState>, success = true): TurnEvent => ({
  type: 'SOLVE',
  success,
  message,
  state: { ...start, ...state },
})

function fakeApi(overrides: Partial<Api> = {}): Api {
  return {
    startGame: vi.fn().mockResolvedValue(start),
    getGame: vi.fn().mockResolvedValue(start),
    getHistory: vi.fn().mockResolvedValue([]),
    getQuests: vi.fn().mockResolvedValue([]),
    getShop: vi.fn().mockResolvedValue([]),
    solve: vi.fn().mockResolvedValue(event('Won', { score: 30, gold: 130, turn: 1 })),
    buy: vi.fn(),
    investigate: vi.fn(),
    autoplayStep: vi.fn(),
    ...overrides,
  }
}

describe('lastMove', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  it('has none until a move is made', async () => {
    setApi(fakeApi())
    const store = useGameStore()

    await store.startGame()

    expect(store.lastMove).toBeNull()
  })

  it('holds the newest event and what it changed', async () => {
    setApi(fakeApi())
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.lastMove?.event.message).toBe('Won')
    expect(store.lastMove?.changes.map((c) => c.text)).toEqual(['+30 points', '+30 gold'])
  })

  it('adds up every event in one step, and reports the last', async () => {
    const autoplayStep = vi.fn().mockResolvedValue([
      event('Refused', { gold: 100, turn: 1 }, false),
      event('Won', { score: 30, gold: 130, turn: 2 }),
    ])
    setApi(fakeApi({ autoplayStep }))
    const store = useGameStore()
    await store.startGame()

    await store.step()

    expect(store.lastMove?.event.message).toBe('Won')
    expect(store.lastMove?.changes.map((c) => c.text)).toEqual(['+30 points', '+30 gold'])
  })

  it('forgets the old game when a new one starts', async () => {
    setApi(fakeApi())
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')

    await store.startGame()

    expect(store.lastMove).toBeNull()
  })

  it('forgets it when the server loses the game', async () => {
    const solve = vi.fn().mockResolvedValueOnce(event('Won', { score: 30, turn: 1 }))
    solve.mockRejectedValueOnce(new ApiError(404, 'No game with id g1'))
    setApi(fakeApi({ solve }))
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')

    await store.solve('a1')

    expect(store.lastMove).toBeNull()
  })

  it('claims no change after catching up with the server, as it spans unknown moves', async () => {
    const solve = vi.fn().mockResolvedValueOnce(event('Won', { score: 30, turn: 1 }))
    solve.mockRejectedValueOnce(new ApiError(409, 'Quest is no longer on the board'))
    setApi(fakeApi({ solve, getGame: vi.fn().mockResolvedValue({ ...start, score: 90, turn: 3 }) }))
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')

    await store.solve('a1')

    expect(store.game?.score).toBe(90)
    expect(store.lastMove).toBeNull()
  })
})
