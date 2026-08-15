import { View, StyleSheet } from 'react-native';
import { Room } from '../../domain/seedRooms';
import { RoomTile } from './RoomTile';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  rooms: Room[];
}

export function FloorPlanCanvas({ rooms }: Props) {
  const { tokens, theme } = useTheme();
  const canvasHeight = theme === 'nocturne' ? 404 : 398;

  return (
    <View
      testID="floorplan-canvas"
      style={[
        styles.canvas,
        { height: canvasHeight, backgroundColor: tokens.color.bg, marginHorizontal: 22, marginTop: 16 },
      ]}
    >
      {rooms.map((room) => (
        <RoomTile key={room.id} room={room} overdueDaysByChoreIndex={{}} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'relative',
    width: '100%',
  },
});
