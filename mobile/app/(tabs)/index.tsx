import { ScrollView, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';
import { SummaryStrip } from '../../src/components/home/SummaryStrip';
import { FloorPlanCanvas } from '../../src/components/home/FloorPlanCanvas';
import { DEFAULT_ROOMS } from '../../src/domain/seedRooms';

export default function HomeScreen() {
  const { tokens } = useTheme();
  return (
    <ScrollView style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <SummaryStrip
        todayEventCount={3}
        monthlySpendManwon={82}
        message="공용욕실이 3일째 조용히 삐져 있어. 오늘 5분만 쓰면 풀려."
      />
      <FloorPlanCanvas rooms={DEFAULT_ROOMS} overdueByRoom={{ bath: { 0: 3 } }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
});
