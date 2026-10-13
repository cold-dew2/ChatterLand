/**
 * 연습 콘텐츠 선택 목록(학생 선택 연습·선생님 숙제 콘텐츠 공통).
 * 콘텐츠 전체 객체가 아니라 ID와 표시 이름만 고른 순서대로 저장한다(검색어·쪽이 바뀌어도 유지).
 */
export type SelectedContent = { id: string; label: string; categoryName?: string }

export const MAX_SELECTION = 30

export function isSelected(list: SelectedContent[], id: string) {
  return list.some((item) => item.id === id)
}

/** 있으면 빼고 없으면 끝에 더한다(최대 개수를 넘으면 그대로) */
export function toggleSelection(list: SelectedContent[], item: SelectedContent, max = MAX_SELECTION) {
  if (isSelected(list, item.id)) return list.filter((entry) => entry.id !== item.id)
  return list.length >= max ? list : [...list, item]
}

/** 아직 고르지 않은 것만 순서대로 더한다(최대 개수까지) */
export function addSelection(list: SelectedContent[], items: SelectedContent[], max = MAX_SELECTION) {
  const next = [...list]
  for (const item of items) {
    if (next.length >= max) break
    if (!isSelected(next, item.id)) next.push(item)
  }
  return next
}

export function removeSelection(list: SelectedContent[], id: string) {
  return list.filter((item) => item.id !== id)
}
