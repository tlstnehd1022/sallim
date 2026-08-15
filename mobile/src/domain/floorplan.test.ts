import { clampRoomSize } from './floorplan';

describe('clampRoomSize', () => {
  it('너비는 최소 8, 최대 100-x로 clamp된다', () => {
    expect(clampRoomSize({ x: 90, y: 0, w: 50, h: 10 })).toEqual({ w: 10, h: 10 });
    expect(clampRoomSize({ x: 0, y: 0, w: 2, h: 10 })).toEqual({ w: 8, h: 10 });
  });

  it('높이는 최소 6, 최대 100-y로 clamp된다', () => {
    expect(clampRoomSize({ x: 0, y: 95, w: 10, h: 50 })).toEqual({ w: 10, h: 5 });
    expect(clampRoomSize({ x: 0, y: 0, w: 10, h: 2 })).toEqual({ w: 10, h: 6 });
  });
});
