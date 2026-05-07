import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import './index.css'
import router from './router'
import { useReservationsStore } from './stores/reservations'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

useReservationsStore().hydrate()

app.use(router)

app.mount('#app')
