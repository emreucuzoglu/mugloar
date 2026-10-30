import { describe, expect, it } from 'vitest'
import type { GameState } from '../../api'
import { formatNumber, moveChanges, signed, turnTone } from '../format'

const before: GameState = { gameId: 'g1', lives: 3, gold: 100, level: 1, score: 50, highScore: 50, turn: 4 }

describe('moveChanges', () => {
  it('lists only what changed, in a fixed order', () => {
    const after = { ...before, score: 111, gold: 161, turn: 5 }

    expect(moveChanges(before, after).map((c) => c.text)).toEqual(['+61 points', '+61 gold'])
  })

  it('uses the singular for one', () => {
    const after = { ...before, score: 51, level: 2, lives: 4 }

    expect(moveChanges(before, after).map((c) => c.text)).toEqual(['+1 point', '+1 life', '+1 level'])
  })

  it('counts a lost life as bad, but not spent gold', () => {
    const after = { ...before, gold: 50, lives: 2 }

    expect(moveChanges(before, after)).toEqual([
      { text: '−50 gold', bad: false },
      { text: '−1 life', bad: true },
    ])
  })

  it('is empty when nothing changed', () => {
    expect(moveChanges(before, { ...before, turn: 5 })).toEqual([])
  })
})

describe('number helpers', () => {
  it('groups thousands', () => {
    expect(formatNumber(11039)).toBe('11,039')
  })

  it.each([
    [48.9, '+49'],
    [-12.4, '−12'],
    [0, '+0'],
    [1500.2, '+1,500'],
  ])('signs %s as %s', (value, text) => {
    expect(signed(value)).toBe(text)
  })
})

describe('turnTone', () => {
  const event = { message: 'x', state: before }

  it.each([
    ['SOLVE', true, 'good'],
    ['SOLVE', false, 'bad'],
    ['PURCHASE', false, 'bad'],
    ['SKIP', false, 'muted'],
    ['GAME_OVER', true, 'bad'],
  ] as const)('%s with success %s is %s', (type, success, tone) => {
    expect(turnTone({ ...event, type, success })).toBe(tone)
  })
})
