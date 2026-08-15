import { render, screen } from '@testing-library/react-native';
import { FloorPlanCanvas } from './FloorPlanCanvas';
import { ThemeProvider } from '../../theme/ThemeContext';
import { DEFAULT_ROOMS } from '../../domain/seedRooms';

describe('FloorPlanCanvas', () => {
  it('DEFAULT_ROOMS의 방 10개를 모두 렌더링한다', async () => {
    await render(
      <ThemeProvider>
        <FloorPlanCanvas rooms={DEFAULT_ROOMS} />
      </ThemeProvider>
    );
    DEFAULT_ROOMS.forEach((room) => {
      expect(screen.getByText(room.name)).toBeTruthy();
    });
  });
});
