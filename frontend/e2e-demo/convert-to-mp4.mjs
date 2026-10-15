// 시연 영상(webm, VP8)을 발표 프로그램(Keynote·QuickTime)에서 재생되는 mp4(H.264)로 바꾼다.
// 추가 설치 없이 Chrome의 MediaRecorder로 실시간 재인코딩한다(영상 길이만큼 걸린다). 내용·순서는 바꾸지 않는다.
// 사용: node e2e-demo/convert-to-mp4.mjs <입력.webm> <출력.mp4>
import fs from 'node:fs'
import { chromium } from '@playwright/test'

const [input, output] = process.argv.slice(2)
if (!input || !output) { console.error('사용: node e2e-demo/convert-to-mp4.mjs <입력.webm> <출력.mp4>'); process.exit(2) }

const browser = await chromium.launch({ channel: 'chrome', args: ['--autoplay-policy=no-user-gesture-required'] })
const page = await browser.newPage()
// 같은 출처로 제공해야 captureStream이 막히지 않는다.
await page.route('http://convert.local/**', (route) => route.request().url().endsWith('/video.webm')
  ? route.fulfill({ status: 200, contentType: 'video/webm', body: fs.readFileSync(input) })
  : route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><video id="v" src="/video.webm" muted playsinline></video>' }))
await page.goto('http://convert.local/')
const base64 = await page.evaluate(async () => {
  const mime = ['video/mp4;codecs=avc1.640028', 'video/mp4;codecs=avc1.42E01F', 'video/mp4'].find((type) => MediaRecorder.isTypeSupported(type))
  if (!mime) throw new Error('이 Chrome은 MediaRecorder mp4(H.264)를 지원하지 않습니다.')
  const video = document.getElementById('v')
  await new Promise((resolve) => { if (video.readyState >= 1) resolve(); else video.onloadedmetadata = resolve })
  const recorder = new MediaRecorder(video.captureStream(25), { mimeType: mime, videoBitsPerSecond: 4_000_000 })
  const chunks = []
  recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data) }
  const stopped = new Promise((resolve) => { recorder.onstop = resolve })
  recorder.start(1000)
  await video.play()
  await new Promise((resolve) => { video.onended = resolve })
  recorder.stop()
  await stopped
  const bytes = new Uint8Array(await new Blob(chunks, { type: mime }).arrayBuffer())
  let binary = ''
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  return btoa(binary)
})
fs.writeFileSync(output, Buffer.from(base64, 'base64'))
await browser.close()
console.log(`저장: ${output}`)
