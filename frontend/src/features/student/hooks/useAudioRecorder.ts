"use client"

import { useCallback, useEffect, useRef, useState } from 'react'

export type RecorderStatus = 'idle' | 'requesting' | 'recording' | 'recorded'

const MAX_RECORDING_SECONDS = 30

/** 녹음 시작 · 중지 · 다시 녹음과 마이크 권한 오류를 관리한다. */
export function useAudioRecorder() {
  const [status, setStatus] = useState<RecorderStatus>('idle')
  const [recording, setRecording] = useState<Blob | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState('')
  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<BlobPart[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stopTracks = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = null
  }

  useEffect(() => () => {
    if (recorderRef.current?.state === 'recording') { recorderRef.current.onstop = null; recorderRef.current.stop() }
    stopTracks()
  }, [])

  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }, [previewUrl])

  const stop = useCallback(() => {
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
  }, [])

  const start = useCallback(async () => {
    if (status === 'requesting' || status === 'recording') return
    setError('')
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('이 브라우저에서는 음성 녹음을 지원하지 않아요. 최신 Chrome, Edge, Safari에서 다시 시도해 주세요.')
      return
    }
    setStatus('requesting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
      streamRef.current = stream
      const recorder = new MediaRecorder(stream)
      chunksRef.current = []
      recorder.ondataavailable = (event) => { if (event.data.size) chunksRef.current.push(event.data) }
      recorder.onstop = () => {
        stopTracks()
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' })
        if (!blob.size) { setStatus('idle'); setError('녹음된 소리가 없어요. 다시 녹음해 주세요.'); return }
        setRecording(blob)
        setPreviewUrl(URL.createObjectURL(blob))
        setStatus('recorded')
      }
      recorderRef.current = recorder
      recorder.start()
      setElapsed(0)
      timerRef.current = setInterval(() => setElapsed((value) => value + 1), 1000)
      setStatus('recording')
    } catch (cause) {
      stopTracks()
      setStatus('idle')
      const name = cause instanceof DOMException ? cause.name : ''
      setError(name === 'NotFoundError' || name === 'OverconstrainedError'
        ? '사용할 수 있는 마이크를 찾지 못했어요. 마이크를 연결한 뒤 다시 시도해 주세요.'
        : name === 'NotAllowedError' || name === 'SecurityError'
          ? '마이크 권한이 거부되었어요. 브라우저 주소창의 권한 설정에서 마이크를 허용한 뒤 다시 시도해 주세요.'
          : '마이크를 시작하지 못했어요. 다른 앱이 마이크를 사용 중인지 확인해 주세요.')
    }
  }, [status])

  // 최대 녹음 시간에 도달하면 자동으로 중지한다.
  useEffect(() => {
    if (status === 'recording' && elapsed >= MAX_RECORDING_SECONDS) stop()
  }, [status, elapsed, stop])

  /** 녹음 중이면 결과를 버리고 멈춘다(화면 이탈·취소). */
  const cancel = useCallback(() => {
    if (recorderRef.current?.state === 'recording') { recorderRef.current.onstop = null; recorderRef.current.stop() }
    stopTracks()
    setRecording(null)
    setPreviewUrl(null)
    setElapsed(0)
    setStatus('idle')
  }, [])

  const reset = useCallback(() => {
    setRecording(null)
    setPreviewUrl(null)
    setElapsed(0)
    setError('')
    setStatus('idle')
  }, [])

  return { status, recording, previewUrl, elapsed, error, maxSeconds: MAX_RECORDING_SECONDS, start, stop, cancel, reset }
}
