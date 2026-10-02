const TARGET_SAMPLE_RATE = 16000

/**
 * 브라우저 녹음(webm/opus, mp4 등)을 16kHz mono 16-bit PCM WAV로 변환한다.
 * 서버의 로컬 음성 인식(whisper.cpp)은 WAV 입력을 사용하므로 별도 변환 도구(ffmpeg) 없이 처리하기 위함이다.
 */
export async function toWav16k(recording: Blob): Promise<Blob> {
  const AudioContextClass = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!AudioContextClass || typeof OfflineAudioContext === 'undefined') throw new Error('이 브라우저에서는 녹음 파일을 변환할 수 없어요.')
  const context = new AudioContextClass()
  try {
    const decoded = await context.decodeAudioData(await recording.arrayBuffer())
    const frameCount = Math.max(1, Math.ceil(decoded.duration * TARGET_SAMPLE_RATE))
    const offline = new OfflineAudioContext(1, frameCount, TARGET_SAMPLE_RATE)
    const source = offline.createBufferSource()
    source.buffer = decoded
    source.connect(offline.destination)
    source.start()
    const rendered = await offline.startRendering()
    return encodeWav(rendered.getChannelData(0), TARGET_SAMPLE_RATE)
  } finally {
    void context.close()
  }
}

function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeAscii = (offset: number, value: string) => { for (let i = 0; i < value.length; i += 1) view.setUint8(offset + i, value.charCodeAt(i)) }
  writeAscii(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(8, 'WAVE')
  writeAscii(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  for (let i = 0; i < samples.length; i += 1) {
    const value = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(44 + i * 2, value < 0 ? value * 0x8000 : value * 0x7fff, true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}
