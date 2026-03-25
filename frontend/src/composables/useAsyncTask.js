import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '../utils/error'

export function useAsyncTask() {
  const loading = ref(false)

  async function run(task, options = {}) {
    const {
      errorMessage = '',
      successMessage = '',
      onSuccess,
      onError
    } = options

    loading.value = true
    try {
      const result = await task()
      if (successMessage) {
        ElMessage.success(successMessage)
      }
      onSuccess?.(result)
      return result
    } catch (error) {
      onError?.(error)
      if (errorMessage) {
        ElMessage.error(getErrorMessage(error, errorMessage))
      }
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    run
  }
}
