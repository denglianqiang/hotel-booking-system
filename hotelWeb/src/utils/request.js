import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 axios 实例
//
// baseURL 由构建环境决定，两种运行方式共用同一份代码：
//   · 开发（npm run dev）：.env.development 里 VITE_API_BASE=/api，
//     请求 /api/xxx 再由 vite.config.js 的代理去掉 /api 转发到 8088；
//   · 打包后由后端 jar 同源提供（.env.production 里 VITE_API_BASE= 留空），
//     请求直接打到 /xxx，与后端接口路径一致，无需再有任何代理。
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 10000,
})

// 请求拦截器：注入统一登录 token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 跳转登录页（避免重复提示和重复跳转）
let redirecting = false
function redirectToLogin() {
  if (redirecting) return
  redirecting = true
  ElMessage.warning('请先登录')
  router.push('/login').finally(() => {
    redirecting = false
  })
}

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res
    } else if (res.code === 401) {
      redirectToLogin()
      return Promise.reject(new Error(res.message || '未登录'))
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        redirectToLogin()
      } else if (status === 404) {
        ElMessage.error('请求资源不存在')
      } else if (status >= 500) {
        ElMessage.error('服务器异常，请稍后重试')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.message.includes('timeout')) {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
