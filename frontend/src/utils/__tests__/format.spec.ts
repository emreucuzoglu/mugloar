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
  it('accepts whole numbers in range', () => {
    expect(parseTarget(' 1000 ')).toEqual({ value: 1000 })
    expect(parseTarget('1')).toEqual({ value: 1 })
    expect(parseTarget('100000')).toEqual({ value: 100000 })
  })

  it.each(['', '   ', 'abc', '12.5', '-5', '1e3'])('rejects %j', (raw) => {
    expect(parseTarget(raw)).toHaveProperty('error')
  })

  it('rejects out-of-range numbers', () => {
    expect(parseTarget('0')).toHaveProperty('error')
    expect(parseTarget('100001')).toHaveProperty('error')
  })
})
