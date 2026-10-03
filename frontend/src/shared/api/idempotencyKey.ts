/**
 * Idempotency-Key 헤더 값(요청 의도마다 하나). 서버 형식: 영문·숫자·-·_ 8~64자.
 * crypto.randomUUID는 HTTPS·localhost에서만 있으므로, 없으면 crypto.getRandomValues로 같은 길이의 무작위 값을 만든다.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}
