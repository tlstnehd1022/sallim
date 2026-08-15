import { computeCleanliness } from './cleanliness';

describe('computeCleanliness', () => {
  it('미완료 할 일이 없으면 0을 반환한다 (완전히 깨끗함)', () => {
    expect(computeCleanliness([])).toBe(0);
  });

  it('미완료 할 일 1개, 지연 0일이면 1을 반환한다', () => {
    expect(computeCleanliness([{ overdueDays: 0 }])).toBe(1);
  });

  it('지연일수가 클수록 값이 커진다', () => {
    const score3days = computeCleanliness([{ overdueDays: 3 }]);
    const score10days = computeCleanliness([{ overdueDays: 10 }]);
    expect(score10days).toBeGreaterThan(score3days);
  });

  it('미완료 할 일이 여러 개면 합산된다', () => {
    const one = computeCleanliness([{ overdueDays: 2 }]);
    const two = computeCleanliness([{ overdueDays: 2 }, { overdueDays: 2 }]);
    expect(two).toBeCloseTo(one * 2, 5);
  });
});
