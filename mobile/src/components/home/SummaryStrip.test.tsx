import { render, screen } from '@testing-library/react-native';
import { SummaryStrip } from './SummaryStrip';
import { ThemeProvider } from '../../theme/ThemeContext';

describe('SummaryStrip', () => {
  it('일정 건수와 지출 금액, 문구를 렌더링한다', async () => {
    await render(
      <ThemeProvider>
        <SummaryStrip todayEventCount={3} monthlySpendManwon={82} message="테스트 문구" />
      </ThemeProvider>
    );
    expect(screen.getByText(/오늘 일정 3건/)).toBeTruthy();
    expect(screen.getByText(/이번 달 82만원/)).toBeTruthy();
    expect(screen.getByText('테스트 문구')).toBeTruthy();
  });
});
