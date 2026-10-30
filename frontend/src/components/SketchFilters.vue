<template>
  <!-- Zero-sized rather than display: none, which would disable the filters in some browsers. -->
  <svg class="filters" width="0" height="0" aria-hidden="true">
    <filter id="ink-wobble" x="-5%" y="-5%" width="110%" height="110%">
      <feTurbulence type="fractalNoise" baseFrequency="0.03" numOctaves="1" seed="7" />
      <feDisplacementMap in="SourceGraphic" scale="2.2" />
    </filter>
    <filter id="paper-edge" x="-3%" y="-3%" width="106%" height="106%">
      <feTurbulence type="fractalNoise" baseFrequency="0.045" numOctaves="3" seed="11" />
      <feDisplacementMap in="SourceGraphic" scale="7" />
    </filter>
  </svg>
</template>

<style scoped>
.filters {
  position: absolute;
}
</style>
