import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';

export default function HomeScreen() {
  const { tokens } = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <Text style={{ color: tokens.color.text }}>홈 — Task 2에서 구현 예정</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
