import { get } from '@/api/client'
import { defineStore } from 'pinia'
import { ref } from 'vue'

interface HealthResponse {
  status: string
}

export const useHealthStore = defineStore('health', () => {
  const status = ref<string | null>(null)
  const error = ref<string | null>(null)
  const loading = ref(false)

  async function fetchHealth() {
    loading.value = true
    error.value = null
    status.value = null

    const result = await get<HealthResponse>('/api/health')

    if (result.error) {
      error.value = result.error
    } else {
      status.value = result.data?.status ?? null
    }

    loading.value = false
  }

  return { status, error, loading, fetchHealth }
})
