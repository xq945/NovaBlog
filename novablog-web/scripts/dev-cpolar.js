import { createInterface } from 'readline'
import { spawn } from 'child_process'

const rl = createInterface({
  input: process.stdin,
  output: process.stdout
})

rl.question('请输入后端 cpolar 地址 (如 https://xxxx.cpolar.top): ', (url) => {
  rl.close()

  if (!url || !url.match(/^https?:\/\//)) {
    console.error('错误：地址必须以 http:// 或 https:// 开头')
    process.exit(1)
  }

  console.log(`\n使用后端地址: ${url}`)
  console.log('启动中...\n')

  // 通过 Vite 代理转发而不是让浏览器直连外部地址：
  // RefreshToken 在 HttpOnly Cookie 里，前端与 API 跨站时 SameSite=Lax 的
  // Cookie 不会随请求发送，登录会直接失效。代理后浏览器看到的始终是同源 /api
  const vite = spawn('npx', ['vite', '--mode', 'cpolar'], {
    stdio: 'inherit',
    shell: true,
    env: { ...process.env, VITE_API_PROXY_TARGET: url }
  })

  vite.on('exit', (code) => process.exit(code))
})
