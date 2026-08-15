import { render, screen } from '@testing-library/react-native';
import { RoomTile } from './RoomTile';
import { ThemeProvider } from '../../theme/ThemeContext';
import { DEFAULT_ROOMS } from '../../domain/seedRooms';

describe('RoomTile', () => {
  it('방 이름을 렌더링한다', async () => {
    await render(
      <ThemeProvider>
        <RoomTile room={DEFAULT_ROOMS[0]} overdueDaysByChoreIndex={{}} />
      </ThemeProvider>
    );
    expect(screen.getByText(DEFAULT_ROOMS[0].name)).toBeTruthy();
  });

  it('미완료 할 일이 있으면 건수를 표시한다', async () => {
    await render(
      <ThemeProvider>
        <RoomTile room={DEFAULT_ROOMS[0]} overdueDaysByChoreIndex={{ 0: 2 }} />
      </ThemeProvider>
    );
    expect(screen.getByText('미완료 1건')).toBeTruthy();
  });
});
