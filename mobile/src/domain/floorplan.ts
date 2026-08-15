export interface RoomBounds {
  x: number;
  y: number;
  w: number;
  h: number;
}

function clamp(value: number, lo: number, hi: number): number {
  if (hi < lo) return hi;
  return Math.max(lo, Math.min(hi, value));
}

export function clampRoomSize({ x, y, w, h }: RoomBounds): { w: number; h: number } {
  return {
    w: clamp(w, 8, 100 - x),
    h: clamp(h, 6, 100 - y),
  };
}
