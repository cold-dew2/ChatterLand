import Image from 'next/image'
import mascot from '@/shared/assets/images/mascot.jpg'

type MascotProps = {
  /** avatar: 동그란 얼굴(말풍선·빈 상태) · scene: 초원 장면 전체(스플래시) */
  variant?: 'avatar' | 'scene'
  /** avatar 지름(px) */
  size?: number
  /** 의미 있는 그림이면 설명, 장식이면 빈 문자열 */
  alt?: string
  className?: string
  eager?: boolean
}

// Next.js에서는 StaticImageData, 테스트(Vite)에서는 문자열 경로로 들어온다.
const mascotSrc: string = typeof mascot === 'string' ? mascot : mascot.src

/** 채터랜드 마스코트(공룡과 병아리) 수채 일러스트. 학생·인증 화면에서만 쓰고 선생님 화면에는 쓰지 않는다. */
export default function Mascot({ variant = 'avatar', size = 72, alt = '', className = '', eager = false }: MascotProps) {
  if (variant === 'scene') {
    return (
      <Image src={mascot} alt={alt} width={900} height={900} sizes="(max-width: 448px) 100vw, 448px" loading={eager ? 'eager' : 'lazy'}
        className={`h-full w-full object-cover ${className}`.trim()} />
    )
  }
  // 얼굴만 보이게 확대한 배경 이미지로 그려, 확대한 그림이 동그라미 밖 레이아웃에 영향을 주지 않게 한다.
  return (
    <span role={alt ? 'img' : undefined} aria-label={alt || undefined} aria-hidden={alt ? undefined : true}
      className={`inline-block shrink-0 rounded-full border-2 border-white bg-[var(--sky-100)] bg-no-repeat ${className}`.trim()}
      style={{ width: size, height: size, backgroundImage: `url(${mascotSrc})`, backgroundSize: '190%', backgroundPosition: '38% 40%' }} />
  )
}
