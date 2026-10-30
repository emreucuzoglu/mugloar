<script setup lang="ts">
const GROUND = 640
const HALF_WIDTH_OF_HEIGHT = 0.22
const TIERS: [number, number][] = [[0.3, 0.2], [0.55, 0.42], [0.8, 0.66], [1, 0.9]]
const TUCK_IN = 0.45
const TUCK_UP = 0.03
const PINES: [number, number][] = [
  [70, 620], [190, 600], [270, 430], [340, 470], [420, 300], [490, 230], [640, 150],
  [930, 170], [1110, 230], [1180, 300], [1250, 450], [1320, 390], [1400, 580], [1530, 620],
]

function pine([x, height]: [number, number]): string {
  const top = GROUND - height
  const half = height * HALF_WIDTH_OF_HEIGHT
  let d = `M${x} ${top}`
  for (const [from, to] of TIERS) {
    d += ` L${x + half * from} ${top + height * to} L${x + half * from * TUCK_IN} ${top + height * (to - TUCK_UP)}`
  }
  d += ` L${x + half} ${GROUND} L${x - half} ${GROUND}`
  for (const [from, to] of [...TIERS].reverse()) {
    d += ` L${x - half * from * TUCK_IN} ${top + height * (to - TUCK_UP)} L${x - half * from} ${top + height * to}`
  }
  return `${d}Z`
}
</script>

<template>
  <svg class="scene" viewBox="0 0 1600 800" preserveAspectRatio="xMidYMax slice" aria-hidden="true">
    <defs>
      <linearGradient id="dusk" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#d6c9ae" />
        <stop offset="0.2" stop-color="#ecd3a3" />
        <stop offset="0.4" stop-color="#f3b27a" />
        <stop offset="0.6" stop-color="#d98a68" />
      </linearGradient>
      <radialGradient id="sun">
        <stop offset="0" stop-color="#fffbe6" />
        <stop offset="0.22" stop-color="#ffe9a6" />
        <stop offset="1" stop-color="#f7bb76" stop-opacity="0" />
      </radialGradient>
    </defs>
    <rect width="1600" height="800" fill="url(#dusk)" />
    <circle cx="1060" cy="265" r="150" fill="url(#sun)" />
    <path
      fill="#8a7786"
      d="M0 420 L170 300 L320 360 L520 210 L640 300 L780 140 L900 280 L1010 240 L1180 340 L1350 260 L1600 380 V800 H0Z"
    />
    <path
      fill="#5d5266"
      d="M0 480 L220 380 L420 440 L620 340 L760 410 L960 330 L1140 420 L1320 370 L1600 470 V800 H0Z"
    />
    <path fill="#27403d" d="M0 520 Q400 440 800 500 T1600 480 V800 H0Z" />
    <path v-for="p in PINES" :key="p[0]" :d="pine(p)" fill="#0f2f2a" />
  </svg>
</template>

<style scoped>
.scene {
  position: absolute;
  inset: 0 0 auto 0;
  z-index: -1;
  width: 100%;
  height: min(100vh, 50rem);
}
</style>
