import { View } from "react-native";
import Svg, { Line, Rect, Text as SvgText } from "react-native-svg";

import { useColors } from "@/hooks/use-colors";
import { withAlpha } from "@/lib/utils";

type ChartPoint = {
  id: string;
  label: string;
  value: number;
  tone: "neutral" | "primary" | "success" | "warning" | "error";
};

const COLOR_BY_TONE = {
  neutral: "border",
  primary: "primary",
  success: "success",
  warning: "warning",
  error: "error",
} as const;

export function MiniBarChart({
  points,
  height = 164,
}: {
  points: ChartPoint[];
  height?: number;
}) {
  const colors = useColors();
  const width = Math.max(points.length, 1) * 42;
  const chartHeight = height - 34;
  const maxValue = Math.max(...points.map((point) => point.value), 10);

  return (
    <View className="overflow-hidden rounded-[28px]" style={{ backgroundColor: withAlpha(colors.surface, 0.52) }}>
      <Svg height={height} width={width}>
        <Line
          stroke={withAlpha(colors.border, 0.9)}
          strokeWidth={1}
          x1={16}
          x2={width - 8}
          y1={chartHeight}
          y2={chartHeight}
        />

        {points.map((point, index) => {
          const barWidth = 20;
          const x = 18 + index * 42;
          const valueHeight = Math.max(10, (point.value / maxValue) * (chartHeight - 18));
          const y = chartHeight - valueHeight;
          const colorKey = COLOR_BY_TONE[point.tone];
          const fill = colors[colorKey];

          return (
            <Rect
              key={point.id}
              fill={fill}
              height={valueHeight}
              rx={10}
              ry={10}
              width={barWidth}
              x={x}
              y={y}
            />
          );
        })}

        {points.map((point, index) => (
          <SvgText
            key={`${point.id}-label`}
            fill={colors.muted}
            fontSize="11"
            fontWeight="600"
            textAnchor="middle"
            x={28 + index * 42}
            y={height - 10}
          >
            {point.label}
          </SvgText>
        ))}
      </Svg>
    </View>
  );
}
