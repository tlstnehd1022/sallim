import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';

export default function CalendarScreen() {
  const { tokens } = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <Text style={{ color: tokens.color.text }}>캘린더 — 다음 서브프로젝트에서 구현</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
