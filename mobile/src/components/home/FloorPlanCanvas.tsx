import { View, StyleSheet } from 'react-native';
import { Room } from '../../domain/seedRooms';
import { RoomTile } from './RoomTile';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  rooms: Room[];
  overdueByRoom?: Record<string, Record<number, number>>;
}

export function FloorPlanCanvas({ rooms, overdueByRoom }: Props) {
  const { tokens } = useTheme();

  return (
    <View
      testID="floorplan-canvas"
      style={[
        styles.canvas,
        {
          height: tokens.layout.floorPlanHeight,
          backgroundColor: tokens.color.bg,
          marginHorizontal: tokens.spacing.lg,
          marginTop: 16,
        },
      ]}
    >
      {rooms.map((room) => (
        <RoomTile
          key={room.id}
          room={room}
          overdueDaysByChoreIndex={overdueByRoom?.[room.id] ?? {}}
        />
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
