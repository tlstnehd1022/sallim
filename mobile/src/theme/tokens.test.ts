import { getTokens } from './tokens';

describe('getTokens', () => {
  it('Nocturne 토큰은 확정값과 일치한다', () => {
    const tokens = getTokens('nocturne');
    expect(tokens.color.bg).toBe('#161826');
    expect(tokens.color.surface).toBe('#232532');
    expect(tokens.color.text).toBe('#e9e9ed');
    expect(tokens.color.accent).toBe('#9184d9');
    expect(tokens.radius.sm).toBe(4);
    expect(tokens.radius.md).toBe(8);
    expect(tokens.radius.lg).toBe(14);
    expect(tokens.layout.floorPlanHeight).toBe(404);
  });

  it('Organic 토큰은 확정값과 일치한다', () => {
    const tokens = getTokens('organic');
    expect(tokens.color.bg).toBe('#f5ead8');
    expect(tokens.color.surface).toBe('#ebddc5');
    expect(tokens.color.text).toBe('#201e1d');
    expect(tokens.color.accent).toBe('#c67139');
    expect(tokens.color.sage).toBe('#7a8a5e');
    expect(tokens.radius.sm).toBe(8);
    expect(tokens.radius.md).toBe(16);
    expect(tokens.radius.lg).toBe(28);
    expect(tokens.radius.pill).toBe(999);
    expect(tokens.layout.floorPlanHeight).toBe(398);
  });
});
