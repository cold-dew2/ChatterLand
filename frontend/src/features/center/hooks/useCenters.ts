"use client"

import { useCallback, useEffect, useState } from 'react'
import { centerApi, type Center } from '@/features/center/api/centerApi'
import { errorMessage } from '@/shared/api/client'

/** 센터 목록 조회 상태(로딩·빈 목록·오류·재시도)를 관리한다. */
export function useCenters() {
  const [centers, setCenters] = useState<Center[]>([])
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    centerApi.list().then((rows) => {
      if (!active) return
      setCenters(rows)
      setState('ready')
    }).catch((cause: unknown) => {
      if (active) { setError(errorMessage(cause, '센터 목록을 불러오지 못했어요.')); setState('error') }
    })
    return () => { active = false }
  }, [retryKey])

  const retry = useCallback(() => { setState('loading'); setRetryKey((value) => value + 1) }, [])
  const options = centers.map((center) => ({ value: String(center.centerId), label: center.name }))
  return { centers, options, state, error, retry }
}
