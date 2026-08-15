export interface OverdueChore {
  overdueDays: number;
}

const DELAY_COEFFICIENT = 0.15;

/**
 * 마스터 스펙 4.2: 먼지 = f( Σ 미완료 할 일마다 (1 + 지연일수 × 계수) )
 * 반환값은 정규화되지 않은 raw dust score — UI 레이어에서 시각 강도로 매핑한다.
 */
export function computeCleanliness(overdueChores: OverdueChore[]): number {
  return overdueChores.reduce(
    (sum, chore) => sum + (1 + chore.overdueDays * DELAY_COEFFICIENT),
    0
  );
}
