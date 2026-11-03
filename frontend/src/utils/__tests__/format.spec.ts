import { describe, expect, it } from 'vitest'
import { parseTarget, percent, plural, riskBand } from '../format'

describe('riskBand', () => {
  it.each([
    [0.97, 'safe'],
    [0.88, 'safe'],
    [0.8, 'fair'],
    [0.5, 'risky'],
    [0.1, 'deadly'],
  ])('maps %s to %s', (chance, band) => {
    expect(riskBand(chance)).toBe(band)
  })
})

describe('percent', () => {
  it('rounds and clamps', () => {
    expect(percent(0.944)).toBe('94%')
    expect(percent(1.5)).toBe('100%')
    expect(percent(-1)).toBe('0%')
  })
})

describe('plural', () => {
  it('picks singular or plural', () => {
    expect(plural(1, 'turn')).toBe('1 turn')
    expect(plural(3, 'turn')).toBe('3 turns')
    expect(plural(2, 'life', 'lives')).toBe('2 lives')
  })
})

describe('parseTarget', () => {
  it('accepts whole numbers with no upper limit, and 0 for no target', () => {
    expect(parseTarget(' 1000 ')).toEqual({ value: 1000 })
    expect(parseTarget('0')).toEqual({ value: 0 })
    expect(parseTarget('1000000000')).toEqual({ value: 1_000_000_000 })
  })

  it.each(['', '   ', 'abc', '12.5', '-5', '1e3'])('rejects %j', (raw) => {
    expect(parseTarget(raw)).toHaveProperty('error')
  })

  it('rejects numbers too large to count exactly', () => {
    expect(parseTarget('9007199254740992')).toHaveProperty('error')
  })
})
