import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import '@fontsource/macondo-swash-caps/400.css'
import '@fontsource/open-sans/300.css'
import '@fontsource/open-sans/400.css'
import '@fontsource/open-sans/600.css'
import '@fontsource/open-sans/700.css'
import './styles.css'

createApp(App).use(createPinia()).mount('#app')
