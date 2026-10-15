import type { BrowserContext, Page } from '@playwright/test'

/**
 * 시연 영상 자막(화면 위 덧씌움, 클릭을 가로채지 않음). 페이지를 새로 불러와도 sessionStorage로 유지한다.
 * 자막 속 결과 값은 실제 화면에서 읽은 값만 쓴다.
 */
export async function installCaption(context: BrowserContext) {
  await context.addInitScript(() => {
    const render = () => {
      const raw = sessionStorage.getItem('__demoCaption')
      let box = document.getElementById('__demo-caption')
      if (!raw) { box?.remove(); return }
      const { t, d } = JSON.parse(raw) as { t: string; d: string }
      if (!box) {
        // 개발 서버 표시(Next.js dev tools 배지)는 영상에서 가린다(앱 화면과 무관)
        const hide = document.createElement('style'); hide.textContent = 'nextjs-portal{display:none!important}'; document.head.appendChild(hide)
        box = document.createElement('div')
        box.id = '__demo-caption'
        box.setAttribute('aria-hidden', 'true')
        box.style.cssText = 'position:fixed;right:20px;bottom:24px;z-index:2147483647;width:300px;padding:12px 16px;border-radius:14px;'
          + 'background:rgba(17,24,39,.88);color:#fff;font:700 16px/1.4 system-ui,sans-serif;pointer-events:none;box-shadow:0 6px 20px rgba(0,0,0,.25)'
        document.body.appendChild(box)
      }
      box.innerHTML = ''
      const title = document.createElement('div'); title.textContent = t; box.appendChild(title)
      if (d) { const detail = document.createElement('div'); detail.textContent = d; detail.style.cssText = 'margin-top:4px;font-weight:400;font-size:13px;opacity:.85'; box.appendChild(detail) }
    }
    ;(window as unknown as { __renderDemoCaption?: () => void }).__renderDemoCaption = render
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', render)
    else render()
  })
}

export async function caption(page: Page, title: string, detail = '') {
  await page.evaluate(([t, d]) => {
    if (t) sessionStorage.setItem('__demoCaption', JSON.stringify({ t, d }))
    else sessionStorage.removeItem('__demoCaption')
    ;(window as unknown as { __renderDemoCaption?: () => void }).__renderDemoCaption?.()
  }, [title, detail])
}
