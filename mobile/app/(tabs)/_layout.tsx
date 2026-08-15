import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../src/theme/ThemeContext';

export default function TabsLayout() {
  const { tokens } = useTheme();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: tokens.color.accent,
        tabBarInactiveTintColor: tokens.color.mutedDim,
        tabBarStyle: {
          height: 76,
          borderTopWidth: 1,
          borderTopColor: tokens.color.border,
          backgroundColor: tokens.color.surfaceDim,
          paddingHorizontal: 26,
          paddingBottom: 10,
        },
        tabBarLabelStyle: { fontSize: 10 },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '홈',
          tabBarIcon: ({ color, size }) => <Ionicons name="home" size={20} color={color} />,
        }}
      />
      <Tabs.Screen
        name="calendar"
        options={{
          title: '캘린더',
          tabBarIcon: ({ color }) => <Ionicons name="calendar-outline" size={20} color={color} />,
        }}
      />
      <Tabs.Screen
        name="ledger"
        options={{
          title: '가계부',
          tabBarIcon: ({ color }) => <Ionicons name="wallet-outline" size={20} color={color} />,
        }}
      />
    </Tabs>
  );
}
