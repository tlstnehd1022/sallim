import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  todayEventCount: number;
  monthlySpendManwon: number;
  message: string;
}

export function SummaryStrip({ todayEventCount, monthlySpendManwon, message }: Props) {
  const { tokens } = useTheme();
  return (
    <View
      style={[
        styles.strip,
        {
          backgroundColor: tokens.color.surfaceDim,
          borderColor: tokens.color.border,
          borderRadius: tokens.radius.lg,
          marginHorizontal: tokens.spacing.lg,
          padding: tokens.spacing.md,
        },
      ]}
    >
      <Text style={{ color: tokens.color.muted, fontSize: 13 }}>
        📅 오늘 일정 {todayEventCount}건 · 💰 이번 달 {monthlySpendManwon}만원
      </Text>
      <Text style={{ color: tokens.color.mutedDim, fontSize: 13, lineHeight: 19, marginTop: 4 }}>
        {message}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: {
    marginTop: 16,
    borderWidth: 1,
  },
});
