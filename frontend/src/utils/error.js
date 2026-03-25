export function getErrorMessage(error, fallback = '请求失败') {
  return error?.response?.data?.message || error?.message || fallback
}
