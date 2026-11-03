import { describe, expect, it, vi } from 'vitest'
import { ApiError, createApi } from '../client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 0 }
const event = { type: 'SOLVE', success: true, message: 'Done', state }
const quest = {
  questId: 'a1',
  message: 'Help',
  reward: 10,
  expiresIn: 3,
  probability: 'Sure thing',
  encrypted: false,
  questType: 'Help',
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.95,
  expectedValue: 9.5,
  acceptable: true,
}

function respondWith(body: unknown): typeof fetch {
  return vi.fn().mockResolvedValue(jsonResponse(body))
}

describe('createApi', () => {
  it('starts a game with POST /api/games', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(state, 201))
    const api = createApi({ fetchFn })

    const game = await api.startGame()

    expect(game.gameId).toBe('g1')
    expect(fetchFn).toHaveBeenCalledWith('/api/games', expect.objectContaining({ method: 'POST' }))
  })

  it('builds move URLs', async () => {
    // A Response body can only be read once, so each call needs its own.
    const fetchFn = vi.fn().mockImplementation(async (url: string) => jsonResponse(url.endsWith('/step') ? [event] : event))
    const api = createApi({ fetchFn, baseUrl: '/x' })

    await api.solve('g1', 'ad1')
    await api.buy('g1', 'hpot')
    await api.autoplayStep('g1')

    expect(fetchFn.mock.calls.map((c) => c[0])).toEqual([
      '/x/games/g1/quests/ad1/solve',
      '/x/games/g1/shop/hpot/buy',
      '/x/games/g1/autoplay/step',
    ])
  })

  it('uses the right method and URL for every other endpoint', async () => {
    const fetchFn = vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith('/investigate')) {
        return jsonResponse(event)
      }
      return jsonResponse(url.endsWith('/g1') ? state : [])
    })
    const api = createApi({ fetchFn })

    await api.getGame('g1')
    await api.getHistory('g1')
    await api.getQuests('g1')
    await api.getShop('g1')
    await api.investigate('g1')

    expect(fetchFn.mock.calls.map((c) => [c[1].method, c[0]])).toEqual([
      ['GET', '/api/games/g1'],
      ['GET', '/api/games/g1/history'],
      ['GET', '/api/games/g1/quests'],
      ['GET', '/api/games/g1/shop'],
      ['POST', '/api/games/g1/investigate'],
    ])
  })

  it('asks for JSON', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(state))

    await createApi({ fetchFn }).getGame('g1')

    expect(fetchFn.mock.calls[0][1].headers).toEqual({ Accept: 'application/json' })
  })

  it('uses the global fetch by default', async () => {
    const globalFetch = vi.fn().mockResolvedValue(jsonResponse(state))
    vi.stubGlobal('fetch', globalFetch)

    const game = await createApi().getGame('g1')
    vi.unstubAllGlobals()

    expect(game.gameId).toBe('g1')
    expect(globalFetch).toHaveBeenCalledTimes(1)
  })

  it('reports a successful response it cannot parse', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('<html>maintenance</html>', { status: 200 }))
    const error = await createApi({ fetchFn }).getGame('g1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(200)
    expect(error.message).toMatch(/could not be read/)
  })

  it.each([
    ['a game with a missing field', () => createApi({ fetchFn: respondWith({ gameId: 'g1' }) }).getGame('g1')],
    ['an event without a state', () => createApi({ fetchFn: respondWith({ ...event, state: null }) }).solve('g1', 'a1')],
    ['a list that is not a list', () => createApi({ fetchFn: respondWith({}) }).getQuests('g1')],
    ['a shop item with the wrong type', () =>
      createApi({ fetchFn: respondWith([{ id: 'hpot', name: 'Potion', cost: '50', recommended: false }]) }).getShop('g1')],
    ['a quest without the solver verdict', () =>
      createApi({ fetchFn: respondWith([{ ...quest, acceptable: undefined }]) }).getQuests('g1')],
  ])('rejects %s', async (_name, call) => {
    const error = await call().catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toMatch(/could not be read/)
  })

  it('accepts a complete quest', async () => {
    const quests = await createApi({ fetchFn: respondWith([quest]) }).getQuests('g1')

    expect(quests).toEqual([quest])
  })

  it('accepts a turn type it has not seen before', async () => {
    const result = await createApi({ fetchFn: respondWith({ ...event, type: 'NEW_TYPE' }) }).investigate('g1')

    expect(result.message).toBe('Done')
  })

  it('flags 404 as not found', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ title: 'Game not found' }, 404))
    const error = await createApi({ fetchFn }).getGame('g1').catch((e) => e)

    expect(error.isNotFound).toBe(true)
    expect(error.isGameOver).toBe(false)
    expect(error.isConflict).toBe(false)
  })

  it('turns problem responses into ApiError with the detail', async () => {
    const fetchFn = vi.fn().mockResolvedValue(
      jsonResponse({ title: 'Not available', detail: 'Quest a1 is no longer on the board' }, 409),
    )
    const api = createApi({ fetchFn })

    const error = await api.solve('g1', 'a1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.isConflict).toBe(true)
    expect(error.message).toBe('Quest a1 is no longer on the board')
  })

  it('flags 410 as game over', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ title: 'Game over' }, 410))
    const error = await createApi({ fetchFn }).autoplayStep('g1').catch((e) => e)

    expect(error.isGameOver).toBe(true)
    expect(error.message).toBe('Game over')
  })

  it('falls back to a generic message for non-JSON errors', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('<html>oops</html>', { status: 500 }))
    const error = await createApi({ fetchFn }).getGame('g1').catch((e) => e)

    expect(error.message).toBe('Request failed (HTTP 500).')
  })

  it('reports an unreachable backend', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const error = await createApi({ fetchFn }).getGame('g1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/Cannot reach the server/)
  })

  it('times out slow requests', async () => {
    vi.useFakeTimers()
    const fetchFn = vi.fn((_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }),
    )
    const pending = createApi({ fetchFn: fetchFn as typeof fetch, timeoutMs: 1000 })
      .getGame('g1')
      .catch((e) => e)

    await vi.advanceTimersByTimeAsync(1000)
    const error = await pending
    vi.useRealTimers()

    expect(error.message).toMatch(/took too long/)
    expect(error.isNoResponse).toBe(true)
  })

  it('times out a response that stalls after its headers', async () => {
    vi.useFakeTimers()
    const fetchFn = vi.fn(async (_url: string, init?: RequestInit) => ({
      ok: true,
      status: 200,
      json: () =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
    }))
    const pending = createApi({ fetchFn: fetchFn as unknown as typeof fetch, timeoutMs: 1000 })
      .getGame('g1')
      .catch((e) => e)

    await vi.advanceTimersByTimeAsync(1000)
    const error = await pending
    vi.useRealTimers()

    expect(error).toBeInstanceOf(ApiError)
    expect(error.isNoResponse).toBe(true)
    expect(error.message).toMatch(/took too long/)
  })

  it('accepts real ids', async () => {
    const fetchFn = vi.fn().mockImplementation(async () => jsonResponse(event))
    const api = createApi({ fetchFn })

    await api.solve('g1', 'DW7XIae5')
    await api.buy('g1', 'wingpotmax')

    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('rejects malformed ids without calling the server', async () => {
    const fetchFn = vi.fn()
    const api = createApi({ fetchFn })

    await expect(api.solve('g1', '../../admin')).rejects.toBeInstanceOf(ApiError)
    await expect(api.solve('g1', 'x'.repeat(65))).rejects.toBeInstanceOf(ApiError)
    await expect(api.buy('g1', 'HPOT')).rejects.toBeInstanceOf(ApiError)
    await expect(api.getGame('')).rejects.toBeInstanceOf(ApiError)
    expect(fetchFn).not.toHaveBeenCalled()
  })
})
