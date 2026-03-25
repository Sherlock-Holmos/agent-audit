import { computed, ref, watch } from 'vue'

export function useTablePagination(getData, options = {}) {
  const {
    defaultPageSize = 10,
    pageSizes = [10, 20, 50, 100]
  } = options

  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)

  const sourceData = computed(() => {
    const next = typeof getData === 'function' ? getData() : []
    return Array.isArray(next) ? next : []
  })

  const total = computed(() => sourceData.value.length)

  const pagedData = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return sourceData.value.slice(start, start + pageSize.value)
  })

  const showPagination = computed(() => total.value > 0)

  function handleCurrentChange(page) {
    currentPage.value = Number(page) || 1
  }

  function handleSizeChange(size) {
    pageSize.value = Number(size) || defaultPageSize
    currentPage.value = 1
  }

  watch(total, () => {
    const pages = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (currentPage.value > pages) {
      currentPage.value = pages
    }
    if (total.value === 0) {
      currentPage.value = 1
    }
  })

  return {
    currentPage,
    pageSize,
    pageSizes,
    total,
    pagedData,
    showPagination,
    handleCurrentChange,
    handleSizeChange
  }
}
