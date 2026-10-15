/**
 * 보호자 연결·해제 알림(자녀 계정, 앱 안). 서버의 연결 기록에서 만든 사건만 보여 준다(GET /students/me/guardian-events).
 * 연결 상태와 같은 기록에서 나오므로 실패한 연결 시도에는 알림이 없고, 같은 사건이 두 번 나오지 않는다(key 기준).
 * 안전과 관련된 알림이라 알림 설정으로 끄지 않는다.
 */
export type GuardianEvent = { key: string; type: 'GUARDIAN_LINKED' | 'GUARDIAN_UNLINKED'; guardianName: string; occurredAt: string; unlinkedBy?: 'GUARDIAN' | 'STUDENT' }
export type GuardianNotice = { key: string; kind: 'guardianLinked' | 'guardianUnlinked'; title: string; message: string }

export function mapGuardianEvents(rows: unknown): GuardianEvent[] {
  return (Array.isArray(rows) ? rows as Record<string, unknown>[] : [])
    .filter((row) => row.type === 'GUARDIAN_LINKED' || row.type === 'GUARDIAN_UNLINKED')
    .map((row) => ({
      key: String(row.key), type: row.type as GuardianEvent['type'], guardianName: String(row.guardianName ?? '보호자'), occurredAt: String(row.occurredAt ?? ''),
      ...(row.unlinkedBy === 'GUARDIAN' || row.unlinkedBy === 'STUDENT' ? { unlinkedBy: row.unlinkedBy } : {}),
    }))
}

export function guardianNotices(events: GuardianEvent[]): GuardianNotice[] {
  const seen = new Set<string>()
  return events.filter((event) => !seen.has(event.key) && seen.add(event.key)).map((event) => {
    const when = event.occurredAt.slice(0, 16)
    return event.type === 'GUARDIAN_LINKED'
      ? { key: event.key, kind: 'guardianLinked', title: `${event.guardianName}님과 보호자 연결`, message: `${when} 연결됐어요. 내 학습 기록을 볼 수 있어요.` }
      : { key: event.key, kind: 'guardianUnlinked', title: `${event.guardianName}님과 보호자 연결 해제`,
          message: `${when} ${event.unlinkedBy === 'GUARDIAN' ? '보호자가' : '내가'} 연결을 해제했어요. 이제 내 학습 기록을 볼 수 없어요(기록은 그대로 남아요).` }
  })
}
