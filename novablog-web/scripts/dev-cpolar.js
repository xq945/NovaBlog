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

  const vite = spawn('npx', ['vite', '--mode', 'cpolar'], {
    stdio: 'inherit',
    shell: true,
    env: { ...process.env, VITE_API_BASE_URL: url }
  })

  vite.on('exit', (code) => process.exit(code))
})
