import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, setApi, type QuestView, type Api, type GameState, type TurnEvent } from '../../api'
import { LOG_LIMIT, useGameStore } from '../game'

const start: GameState = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 0 }

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

function solved(score: number, turn: number, lives = 3): TurnEvent {
  return { type: 'SOLVE', success: true, message: 'Succeeded', state: { ...start, score, gold: score, turn, lives } }
}

function fakeApi(overrides: Partial<Api> = {}): Api {
  return {
    startGame: vi.fn().mockResolvedValue(start),
    getGame: vi.fn().mockResolvedValue(start),
    getHistory: vi.fn().mockResolvedValue([]),
    getQuests: vi.fn().mockResolvedValue([quest]),
    getShop: vi.fn().mockResolvedValue([{ id: 'hpot', name: 'Healing potion', cost: 50, recommended: false }]),
    solve: vi.fn().mockResolvedValue(solved(30, 1)),
    buy: vi.fn(),
    investigate: vi.fn(),
    autoplayStep: vi.fn(),
    ...overrides,
  }
}

describe('game store', () => {
  let api: Api

  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    api = fakeApi()
    setApi(api)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('starts a game and loads board and shop', async () => {
    const store = useGameStore()

    await store.startGame()

    expect(store.game).toEqual(start)
    expect(store.quests).toEqual([quest])
    expect(store.shop).toHaveLength(1)
    expect(store.canAct).toBe(true)
    expect(sessionStorage.getItem('mugloar.gameId')).toBe('g1')
  })

  it('applies a move, logs it and refreshes the board', async () => {
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(api.solve).toHaveBeenCalledWith('g1', 'a1')
    expect(store.game?.score).toBe(30)
    expect(store.log[0].message).toBe('Succeeded')
    expect(api.getQuests).toHaveBeenCalledTimes(2)
    expect(store.busy).toBe(false)
  })

  it('ignores moves while another is running', async () => {
    let finish: (e: TurnEvent) => void = () => {}
    api = fakeApi({ solve: vi.fn(() => new Promise<TurnEvent>((r) => (finish = r))) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    const first = store.solve('a1')
    await store.solve('a1')
    finish(solved(30, 1))
    await first

    expect(api.solve).toHaveBeenCalledTimes(1)
  })

  it('shows the error and refreshes the board when a quest has expired', async () => {
    api = fakeApi({ solve: vi.fn().mockRejectedValue(new ApiError(409, 'Quest a1 is no longer on the board')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.error).toBe('Quest a1 is no longer on the board')
    expect(api.getQuests).toHaveBeenCalledTimes(2)
  })

  it('marks the game over on 410', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockRejectedValue(new ApiError(410, 'Game over')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.step()

    expect(store.isOver).toBe(true)
    expect(store.canAct).toBe(false)
    expect(store.quests).toEqual([])
  })

  it('autoplays until the target is reached', async () => {
    let score = 0
    api = fakeApi({
      autoplayStep: vi.fn(async () => {
        score += 400
        return [solved(score, score / 400)]
      }),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(3)
    expect(store.game?.score).toBe(1200)
    expect(store.reachedTarget).toBe(true)
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay stops when the dragon dies', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([solved(10, 1, 0)]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.isOver).toBe(true)
  })

  it('autoplay stops when a step reports nothing', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay can be stopped', async () => {
    const store = useGameStore()
    let calls = 0
    api = fakeApi({
      autoplayStep: vi.fn(async () => {
        calls++
        if (calls === 2) {
          store.stopAutoplay()
        }
        return [solved(calls * 10, calls)]
      }),
    })
    setApi(api)
    await store.startGame()

    await store.autoplay(1000)

    expect(calls).toBe(2)
    expect(store.autoplaying).toBe(false)
    expect(store.stopRequested).toBe(false)
  })

  it('autoplay stops on an error and reports it', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockRejectedValue(new ApiError(502, 'The game server could not be reached.')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(store.error).toBe('The game server could not be reached.')
    expect(store.autoplaying).toBe(false)
  })

  it.each([0, -5, 1.5, 100_001, Number.NaN])('rejects autoplay target %s', async (bad) => {
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(bad)

    expect(store.error).toMatch(/Pick a target/)
    expect(api.autoplayStep).not.toHaveBeenCalled()
  })

  it('caps the log', async () => {
    let turn = 0
    api = fakeApi({ autoplayStep: vi.fn(async () => [solved(++turn, turn)]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(LOG_LIMIT + 50)

    expect(store.log).toHaveLength(LOG_LIMIT)
    expect(store.log[0].state.turn).toBe(LOG_LIMIT + 50)
  })

  it('resumes the game saved in this tab', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    const history = [solved(30, 1)]
    api = fakeApi({ getGame: vi.fn().mockResolvedValue(history[0].state), getHistory: vi.fn().mockResolvedValue(history) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.game?.score).toBe(30)
    expect(store.log.map((e) => e.message)).toEqual(['Succeeded'])
  })

  it('gives every log entry its own lasting id, even within a turn', async () => {
    const skipped: TurnEvent = { type: 'SKIP', success: false, message: 'Skipped', state: { ...start, turn: 1 } }
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([skipped]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.step()
    const firstId = store.log[0].id
    await store.step()

    expect(store.log[1].id).toBe(firstId)
    expect(store.log[0].id).not.toBe(firstId)
  })

  it.each([
    ['a timeout', new ApiError(0, 'The server took too long to answer. Try again.')],
    ['a conflict', new ApiError(409, 'Not enough gold')],
  ])('catches up with the server after %s on a move', async (_name, failure) => {
    const history = [solved(77, 5)]
    api = fakeApi({
      solve: vi.fn().mockRejectedValue(failure),
      getGame: vi.fn().mockResolvedValue(history[0].state),
      getHistory: vi.fn().mockResolvedValue(history),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.game?.score).toBe(77)
    expect(store.log.map((e) => e.message)).toEqual(['Succeeded'])
    expect(api.getQuests).toHaveBeenCalledTimes(2)
    expect(store.error).toBe(failure.message)
    expect(store.busy).toBe(false)
  })

  it('catches up with the server after a timeout during autoplay', async () => {
    const history = [solved(77, 5)]
    api = fakeApi({
      autoplayStep: vi.fn().mockRejectedValue(new ApiError(0, 'The server took too long to answer. Try again.')),
      getGame: vi.fn().mockResolvedValue(history[0].state),
      getHistory: vi.fn().mockResolvedValue(history),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(store.game?.score).toBe(77)
    expect(store.autoplaying).toBe(false)
  })

  it('clears the board when catching up shows that the game ended', async () => {
    api = fakeApi({
      solve: vi.fn().mockRejectedValue(new ApiError(0, 'The server took too long to answer. Try again.')),
      getGame: vi.fn().mockResolvedValue({ ...start, lives: 0 }),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.isOver).toBe(true)
    expect(store.quests).toEqual([])
  })

  it('does not catch up after other failures', async () => {
    api = fakeApi({ solve: vi.fn().mockRejectedValue(new ApiError(502, 'Down')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(api.getGame).not.toHaveBeenCalled()
  })

  it('stays busy until both reads have finished, even if one has already failed', async () => {
    let finishBoard: (quests: QuestView[]) => void = () => {}
    api = fakeApi({
      getQuests: vi.fn(() => new Promise<QuestView[]>((r) => (finishBoard = r))),
      getShop: vi.fn().mockRejectedValue(new ApiError(502, 'Down')),
    })
    setApi(api)
    const store = useGameStore()

    const starting = store.startGame()
    await vi.waitFor(() => expect(api.getShop).toHaveBeenCalled())
    await new Promise((resolve) => setTimeout(resolve))
    expect(store.busy).toBe(true)
    expect(store.loading).toBe(true)

    finishBoard([quest])
    await starting

    expect(store.busy).toBe(false)
    expect(store.loading).toBe(false)
    expect(store.quests).toEqual([quest])
    expect(store.error).toBe('Down')
  })

  it('reports loading and resuming while a saved game comes back', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    const store = useGameStore()

    const resuming = store.resume()
    expect(store.resuming).toBe(true)
    expect(store.loading).toBe(true)
    await resuming

    expect(store.resuming).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('brings back the target of a resumed game', async () => {
    const store = useGameStore()
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([solved(5000, 1)]) })
    setApi(api)
    await store.startGame()
    await store.autoplay(5000)

    setActivePinia(createPinia())
    const reloaded = useGameStore()
    await reloaded.resume()

    expect(reloaded.target).toBe(5000)
  })

  it('ignores a saved target that is not valid', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    sessionStorage.setItem('mugloar.target', 'lots')
    const store = useGameStore()

    await store.resume()

    expect(store.target).toBe(1000)
  })

  it('forgets the saved target along with the game', async () => {
    sessionStorage.setItem('mugloar.gameId', 'old')
    sessionStorage.setItem('mugloar.target', '5000')
    api = fakeApi({ getGame: vi.fn().mockRejectedValue(new ApiError(404, 'No game with id old')) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(sessionStorage.getItem('mugloar.target')).toBeNull()
  })

  it('forgets a saved game the server no longer knows', async () => {
    sessionStorage.setItem('mugloar.gameId', 'old')
    api = fakeApi({ getGame: vi.fn().mockRejectedValue(new ApiError(404, 'No game with id old')) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.game).toBeNull()
    expect(store.error).toBeNull()
    expect(sessionStorage.getItem('mugloar.gameId')).toBeNull()
  })

  it('reports an unreachable backend on start', async () => {
    api = fakeApi({ startGame: vi.fn().mockRejectedValue(new ApiError(0, 'Cannot reach the server.')) })
    setApi(api)
    const store = useGameStore()

    await store.startGame()

    expect(store.game).toBeNull()
    expect(store.error).toBe('Cannot reach the server.')
  })

  it('sends buy, investigate and step to the API', async () => {
    const event = solved(0, 1)
    api = fakeApi({
      buy: vi.fn().mockResolvedValue(event),
      investigate: vi.fn().mockResolvedValue(event),
      autoplayStep: vi.fn().mockResolvedValue([event]),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.buy('hpot')
    await store.investigate()
    await store.step()

    expect(api.buy).toHaveBeenCalledWith('g1', 'hpot')
    expect(api.investigate).toHaveBeenCalledWith('g1')
    expect(api.autoplayStep).toHaveBeenCalledWith('g1')
    expect(store.log).toHaveLength(3)
  })

  it('logs several events from one step newest first and keeps the last state', async () => {
    const refused: TurnEvent = { type: 'PURCHASE', success: false, message: 'Refused', state: { ...start, turn: 1 } }
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([refused, solved(30, 2)]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.step()

    expect(store.log.map((e) => e.message)).toEqual(['Succeeded', 'Refused'])
    expect(store.game?.turn).toBe(2)
  })

  it('leaves the state alone when a step reports nothing', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.step()

    expect(store.game).toEqual(start)
    expect(store.log).toEqual([])
  })

  it('clears the board when a move ends the game', async () => {
    api = fakeApi({ solve: vi.fn().mockResolvedValue(solved(10, 1, 0)) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.isOver).toBe(true)
    expect(store.quests).toEqual([])
    expect(api.getQuests).toHaveBeenCalledTimes(1)
  })

  it('refuses moves once the game is over', async () => {
    api = fakeApi({ solve: vi.fn().mockResolvedValue(solved(10, 1, 0)) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')

    await store.solve('a1')

    expect(api.solve).toHaveBeenCalledTimes(1)
  })

  it('does nothing when a move is requested before a game exists', async () => {
    const store = useGameStore()

    await store.solve('a1')

    expect(api.solve).not.toHaveBeenCalled()
    expect(store.canAct).toBe(false)
  })

  it('forgets the game when the server loses it mid-game', async () => {
    api = fakeApi({ solve: vi.fn().mockRejectedValue(new ApiError(404, 'No game with id g1')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.game).toBeNull()
    expect(store.quests).toEqual([])
    expect(store.error).toBe('No game with id g1')
    expect(sessionStorage.getItem('mugloar.gameId')).toBeNull()
  })

  it('hides unexpected errors behind a readable message', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const bug = new TypeError('undefined is not a function')
    api = fakeApi({ solve: vi.fn().mockRejectedValue(bug) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.error).toMatch(/Something unexpected went wrong/)
    expect(consoleError).toHaveBeenCalledWith(bug)
    expect(store.busy).toBe(false)
  })

  it('keeps the original error when reloading after a conflict also fails', async () => {
    api = fakeApi({
      solve: vi.fn().mockRejectedValue(new ApiError(409, 'Quest a1 is no longer on the board')),
      getQuests: vi.fn().mockResolvedValueOnce([quest]).mockRejectedValue(new ApiError(502, 'Down')),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.error).toBe('Quest a1 is no longer on the board')
    expect(store.busy).toBe(false)
  })

  it('keeps a finished move when the board refresh fails', async () => {
    api = fakeApi({ getQuests: vi.fn().mockResolvedValueOnce([quest]).mockRejectedValue(new ApiError(502, 'Down')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.solve('a1')

    expect(store.game?.score).toBe(30)
    expect(store.error).toBe('Down')
  })

  it('clears an earlier error when the next move starts', async () => {
    api = fakeApi({
      solve: vi.fn().mockRejectedValueOnce(new ApiError(502, 'Down')).mockResolvedValue(solved(30, 1)),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')
    expect(store.error).toBe('Down')

    await store.solve('a1')

    expect(store.error).toBeNull()
  })

  it('clearError dismisses the message', async () => {
    api = fakeApi({ startGame: vi.fn().mockRejectedValue(new ApiError(0, 'Cannot reach the server.')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    store.clearError()

    expect(store.error).toBeNull()
  })

  it('starting again replaces the old game and clears its log', async () => {
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')
    expect(store.log).toHaveLength(1)

    await store.startGame()

    expect(store.log).toEqual([])
    expect(store.game?.score).toBe(0)
  })

  it('ignores start while a game is starting', async () => {
    let finish: (g: GameState) => void = () => {}
    api = fakeApi({ startGame: vi.fn(() => new Promise<GameState>((r) => (finish = r))) })
    setApi(api)
    const store = useGameStore()

    const first = store.startGame()
    await store.startGame()
    finish(start)
    await first

    expect(api.startGame).toHaveBeenCalledTimes(1)
  })

  it('autoplay remembers the target and reports progress capped at 100%', async () => {
    const store = useGameStore()
    expect(store.progress).toBe(0)
    api = fakeApi({ autoplayStep: vi.fn().mockResolvedValue([solved(500, 1)]) })
    setApi(api)
    await store.startGame()

    await store.autoplay(400)

    expect(store.target).toBe(400)
    expect(store.progress).toBe(1)
    expect(store.reachedTarget).toBe(true)
  })

  it('autoplay makes no move when the target is already reached', async () => {
    api = fakeApi({ startGame: vi.fn().mockResolvedValue({ ...start, score: 1000 }) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).not.toHaveBeenCalled()
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay does nothing without a game', async () => {
    const store = useGameStore()

    await store.autoplay(1000)

    expect(api.autoplayStep).not.toHaveBeenCalled()
    expect(store.error).toBeNull()
  })

  it('stopAutoplay is ignored when autoplay is not running', () => {
    const store = useGameStore()

    store.stopAutoplay()

    expect(store.stopRequested).toBe(false)
  })

  it('ignores start while autoplay is running', async () => {
    let finish: (events: TurnEvent[]) => void = () => {}
    api = fakeApi({ autoplayStep: vi.fn(() => new Promise<TurnEvent[]>((r) => (finish = r))) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    const running = store.autoplay(10)
    await store.startGame()
    finish([solved(10, 1)])
    await running

    expect(api.startGame).toHaveBeenCalledTimes(1)
    expect(store.game?.score).toBe(10)
  })

  it('autoplay stops but keeps the move when the refresh after it fails', async () => {
    api = fakeApi({
      autoplayStep: vi.fn().mockResolvedValue([solved(10, 1)]),
      getQuests: vi.fn().mockResolvedValueOnce([quest]).mockRejectedValue(new ApiError(502, 'Down')),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.game?.score).toBe(10)
    expect(store.log).toHaveLength(1)
    expect(store.error).toBe('Down')
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay stops and catches up when the server refuses to continue', async () => {
    const message = 'Autoplay stopped because the game server refused its last 5 moves.'
    api = fakeApi({
      autoplayStep: vi.fn().mockRejectedValue(new ApiError(409, message)),
      getGame: vi.fn().mockResolvedValue({ ...start, turn: 3 }),
    })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.error).toBe(message)
    expect(store.game?.turn).toBe(3)
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay ends the game when the server says it is gone', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockRejectedValue(new ApiError(410, 'Game over')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.isOver).toBe(true)
    expect(store.quests).toEqual([])
    expect(store.autoplaying).toBe(false)
  })

  it('autoplay forgets the game when the server no longer knows it', async () => {
    api = fakeApi({ autoplayStep: vi.fn().mockRejectedValue(new ApiError(404, 'No game with id g1')) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    await store.autoplay(1000)

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.game).toBeNull()
    expect(sessionStorage.getItem('mugloar.gameId')).toBeNull()
    expect(store.autoplaying).toBe(false)
  })

  it('a stop pressed during a refresh takes effect before the next step', async () => {
    let finishRefresh: (quests: QuestView[]) => void = () => {}
    const getQuests = vi
      .fn()
      .mockResolvedValueOnce([quest])
      .mockImplementationOnce(() => new Promise<QuestView[]>((r) => (finishRefresh = r)))
    api = fakeApi({ getQuests, autoplayStep: vi.fn().mockResolvedValue([solved(10, 1)]) })
    setApi(api)
    const store = useGameStore()
    await store.startGame()

    const running = store.autoplay(1000)
    await vi.waitFor(() => expect(getQuests).toHaveBeenCalledTimes(2))
    store.stopAutoplay()
    finishRefresh([quest])
    await running

    expect(api.autoplayStep).toHaveBeenCalledTimes(1)
    expect(store.autoplaying).toBe(false)
    expect(store.stopRequested).toBe(false)
  })

  it('keeps the current game when starting a new one fails', async () => {
    const store = useGameStore()
    await store.startGame()
    await store.solve('a1')
    api = fakeApi({ startGame: vi.fn().mockRejectedValue(new ApiError(502, 'Down')) })
    setApi(api)

    await store.startGame()

    expect(store.game?.score).toBe(30)
    expect(store.log).toHaveLength(1)
    expect(store.error).toBe('Down')
    expect(sessionStorage.getItem('mugloar.gameId')).toBe('g1')
    expect(store.canAct).toBe(true)
  })

  it('keeps the resumed game and reports it when its history cannot be read', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    api = fakeApi({
      getGame: vi.fn().mockResolvedValue({ ...start, score: 40 }),
      getHistory: vi.fn().mockRejectedValue(new ApiError(502, 'Down')),
    })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.game?.score).toBe(40)
    expect(store.log).toEqual([])
    expect(store.error).toBe('Down')
    expect(store.busy).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('shows a resumed game as finished when the shop read says it is gone', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    api = fakeApi({ getShop: vi.fn().mockRejectedValue(new ApiError(410, 'Game over')) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.isOver).toBe(true)
    expect(store.quests).toEqual([])
    expect(store.canAct).toBe(false)
    expect(store.busy).toBe(false)
  })

  it('does nothing on resume when no game was saved', async () => {
    const store = useGameStore()

    await store.resume()

    expect(api.getGame).not.toHaveBeenCalled()
    expect(store.busy).toBe(false)
  })

  it('does not replace a loaded game on resume', async () => {
    const store = useGameStore()
    await store.startGame()

    await store.resume()

    expect(api.getGame).not.toHaveBeenCalled()
  })

  it('reports other failures on resume and keeps the saved game', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    api = fakeApi({ getGame: vi.fn().mockRejectedValue(new ApiError(502, 'Down')) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.error).toBe('Down')
    expect(store.busy).toBe(false)
    expect(sessionStorage.getItem('mugloar.gameId')).toBe('g1')
  })

  it('resumes a finished game without loading a board', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    api = fakeApi({ getGame: vi.fn().mockResolvedValue({ ...start, lives: 0 }) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.isOver).toBe(true)
    expect(api.getQuests).not.toHaveBeenCalled()
  })

  it('caps a resumed log', async () => {
    sessionStorage.setItem('mugloar.gameId', 'g1')
    const history = Array.from({ length: LOG_LIMIT + 5 }, (_, i) => solved(i, i))
    api = fakeApi({ getHistory: vi.fn().mockResolvedValue(history) })
    setApi(api)
    const store = useGameStore()

    await store.resume()

    expect(store.log).toHaveLength(LOG_LIMIT)
  })

  it('still works when sessionStorage is unavailable', async () => {
    const denied = () => {
      throw new DOMException('Denied', 'SecurityError')
    }
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(denied)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(denied)
    const store = useGameStore()

    await store.resume()
    await store.startGame()

    expect(api.getGame).not.toHaveBeenCalled()
    expect(store.game).toEqual(start)
    expect(store.error).toBeNull()
  })
})
