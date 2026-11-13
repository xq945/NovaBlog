import { createApp } from 'vue'
import './style.css'
import './styles/theme.css'
import './styles/element-theme.css'
import './styles/element-override.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { hasSessionHint, restoreSession } from './utils/session'

const app = createApp(App)
const pinia = createPinia()

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(ElementPlus, { locale: zhCn })

/**
 * 启动前先恢复会话。
 *
 * AccessToken 只在内存，刷新页面后必须凭 RT Cookie 重新换取；
 * 若先安装路由，守卫的首次导航会赶在恢复完成之前执行，把已登录用户误送到登录页
 */
async function bootstrap() {
  // 旧版本把两个令牌都放在 localStorage，升级后一次性清掉：
  // 残留的长期 RefreshToken 会白白扩大 XSS 的窃取面
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')

  if (hasSessionHint()) {
    await restoreSession()
  }
  app.use(router)
  app.mount('#app')
}

bootstrap()
