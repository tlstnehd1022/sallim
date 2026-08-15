import { View, Text, StyleSheet } from 'react-native';
import { Room } from '../../domain/seedRooms';
import { computeCleanliness } from '../../domain/cleanliness';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  room: Room;
  overdueDaysByChoreIndex: Record<number, number>;
}

export function RoomTile({ room, overdueDaysByChoreIndex }: Props) {
  const { tokens } = useTheme();
  const overdue = Object.values(overdueDaysByChoreIndex).map((overdueDays) => ({ overdueDays }));
  const dust = computeCleanliness(overdue);
  const isDirty = dust > 0;

  return (
    <View
      testID={`room-tile-${room.id}`}
      style={[
        styles.tile,
        {
          left: `${room.x}%`,
          top: `${room.y}%`,
          width: `${room.w}%`,
          height: `${room.h}%`,
          zIndex: room.z,
          backgroundColor: isDirty ? tokens.color.surfaceDim : tokens.color.surface,
          borderColor: tokens.color.border,
          borderRadius: tokens.radius.sm,
        },
      ]}
    >
      <Text style={{ color: tokens.color.text, fontSize: 12 }}>{room.name}</Text>
      {isDirty && (
        <Text style={{ color: tokens.color.mutedDim, fontSize: 10 }}>
          미완료 {overdue.length}건
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  tile: {
    position: 'absolute',
    borderWidth: 1,
    padding: 6,
  },
});
