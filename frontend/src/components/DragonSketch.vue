<template>
  <figure class="sketch">
    <svg viewBox="0 0 320 220" aria-hidden="true">
      <g
        fill="none"
        stroke="#2a1c0f"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
        filter="url(#ink-wobble)"
      >
        <!-- Body, belly scales and tail -->
        <path d="M92 198 C70 150 96 106 132 102 M176 110 C194 126 196 170 172 198" />
        <path d="M150 198 C172 172 176 138 160 112" />
        <path d="M160 182 L176 186 M168 162 L185 164 M170 142 L187 140 M165 124 L181 118" />
        <path d="M92 198 C60 202 28 192 26 168 C25 154 42 150 50 162 M50 162 L38 150 M50 162 L60 150" />
        <path d="M86 172 C70 180 56 178 50 162" />
        <!-- Wing -->
        <path d="M118 120 L40 30 C62 34 78 44 88 60 C92 46 102 36 116 34 C110 52 114 76 128 100" />
        <path d="M118 120 L88 60 M118 120 L112 58" />
        <!-- Neck and head -->
        <path d="M132 102 C120 78 130 52 158 44 M176 110 C176 92 172 78 178 68" />
        <path d="M158 44 C170 34 190 34 200 44 L232 52 C240 56 238 64 230 65 L206 70 C196 76 184 74 178 68" />
        <path d="M232 60 L206 62" />
        <path d="M168 38 L152 18 L178 34 M182 36 L176 14 L194 38" />
        <path d="M125 84 L113 80 L121 92 M133 64 L120 56 L126 70" />
        <!-- Legs -->
        <path d="M110 198 C104 176 124 160 140 172 C150 180 146 198 146 198 M100 198 H152" />
        <path d="M170 134 C188 148 188 172 184 198 M172 198 H198" />
        <!-- Fire, and the notice it has set light to -->
        <path d="M240 58 C252 50 256 40 252 28 C262 36 266 32 266 22 C278 36 276 54 258 64 C252 66 246 64 240 58Z" />
        <path d="M284 30 Q290 22 286 12 M298 48 Q306 42 304 32" />
        <path d="M284 198 V146 M258 96 H310 V146 H258Z M267 110 H301 M267 122 H296 M267 134 H288" />
        <path d="M264 96 Q268 84 276 88 Q280 78 288 88 Q298 84 302 96" />
        <path d="M8 200 H312" />
      </g>
      <path d="M186 48 Q191 43 196 48 Q191 52 186 48Z" fill="#2a1c0f" />
      <path d="M226 54 Q229 52 231 55 Q228 57 226 54Z" fill="#2a1c0f" />
    </svg>
  </figure>
</template>

<style scoped>
.sketch {
  position: relative;
  isolation: isolate;
  max-width: 30rem;
  margin: 1.5rem auto;
  padding: 1.5rem 2rem;
}

.sketch::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: var(--parchment-wash);
  filter: url(#paper-edge) drop-shadow(0 3px 6px rgb(40 25 10 / 0.45));
}

.sketch svg {
  display: block;
  width: 100%;
  height: auto;
}
</style>
