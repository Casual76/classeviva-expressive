import { Text, View } from "react-native";

import { useColors } from "@/hooks/use-colors";
import type { AgendaItemViewModel } from "@/lib/student-data";
import { withAlpha } from "@/lib/utils";

interface DayScheduleGridProps {
  lessons: AgendaItemViewModel[];
  startHour?: number;
  endHour?: number;
}

type ParsedLesson = {
  lesson: AgendaItemViewModel;
  start: number;
  end: number;
};

const MIN_SCHOOL_HOUR = 7;
const MAX_SCHOOL_HOUR = 16;
const DEFAULT_START_HOUR = 8;
const DEFAULT_END_HOUR = 14;
const SLOT_HEIGHT = 64;

function hashSubject(subject: string): number {
  let hash = 0;
  for (let index = 0; index < subject.length; index += 1) {
    hash = subject.charCodeAt(index) + ((hash << 5) - hash);
  }

  return Math.abs(hash);
}

function parseTimeToken(value: string): number | null {
  const match = value.match(/^(\d{1,2}):(\d{2})$/);
  if (!match) {
    return null;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
    return null;
  }

  return hours + minutes / 60;
}

function parseLessonTimeRange(timeLabel: string): { start: number; end: number } | null {
  const matches = timeLabel.match(/\d{1,2}:\d{2}/g);
  if (!matches || matches.length === 0) {
    return null;
  }

  const start = parseTimeToken(matches[0]);
  if (start === null) {
    return null;
  }

  const parsedEnd = matches[1] ? parseTimeToken(matches[1]) : null;
  const end = parsedEnd !== null && parsedEnd > start ? parsedEnd : start + 1;

  return { start, end };
}

function deriveVisibleRange(parsedLessons: ParsedLesson[], startHour?: number, endHour?: number) {
  if (typeof startHour === "number" && typeof endHour === "number" && endHour > startHour) {
    return {
      start: Math.max(MIN_SCHOOL_HOUR, startHour),
      end: Math.min(MAX_SCHOOL_HOUR, endHour),
    };
  }

  if (parsedLessons.length === 0) {
    return { start: DEFAULT_START_HOUR, end: DEFAULT_END_HOUR };
  }

  const minStart = Math.min(...parsedLessons.map((lesson) => lesson.start));
  const maxEnd = Math.max(...parsedLessons.map((lesson) => lesson.end));
  let start = Math.max(MIN_SCHOOL_HOUR, Math.floor(minStart) - 1);
  let end = Math.min(MAX_SCHOOL_HOUR, Math.ceil(maxEnd) + 1);

  if (end - start < 4) {
    const targetEnd = Math.min(MAX_SCHOOL_HOUR, start + 4);
    start = Math.max(MIN_SCHOOL_HOUR, targetEnd - 4);
    end = Math.max(targetEnd, start + 1);
  }

  if (end <= start) {
    return { start: DEFAULT_START_HOUR, end: DEFAULT_END_HOUR };
  }

  return { start, end };
}

export function DayScheduleGrid({ lessons, startHour, endHour }: DayScheduleGridProps) {
  const colors = useColors();

  if (lessons.length === 0) {
    return null;
  }

  const parsedLessons = lessons
    .map((lesson) => {
      const parsedRange = parseLessonTimeRange(lesson.timeLabel);
      if (!parsedRange) {
        return null;
      }

      return {
        lesson,
        start: parsedRange.start,
        end: parsedRange.end,
      } satisfies ParsedLesson;
    })
    .filter((lesson): lesson is ParsedLesson => lesson !== null);
  const untimedLessons = lessons.filter((lesson) => parseLessonTimeRange(lesson.timeLabel) === null);
  const visibleRange = deriveVisibleRange(parsedLessons, startHour, endHour);
  const totalHours = Math.max(visibleRange.end - visibleRange.start, 1);
  const gridHeight = totalHours * SLOT_HEIGHT;
  const timeLabels = Array.from({ length: totalHours + 1 }, (_, index) => visibleRange.start + index);
  const lessonPalettes = [
    { accent: colors.primary, background: withAlpha(colors.primary, 0.16), border: withAlpha(colors.primary, 0.22) },
    { accent: colors.secondary ?? colors.primary, background: withAlpha(colors.secondary ?? colors.primary, 0.16), border: withAlpha(colors.secondary ?? colors.primary, 0.22) },
    { accent: colors.tertiary ?? colors.primary, background: withAlpha(colors.tertiary ?? colors.primary, 0.18), border: withAlpha(colors.tertiary ?? colors.primary, 0.24) },
    { accent: colors.success, background: withAlpha(colors.success, 0.16), border: withAlpha(colors.success, 0.22) },
    { accent: colors.warning, background: withAlpha(colors.warning, 0.16), border: withAlpha(colors.warning, 0.22) },
    { accent: colors.error, background: withAlpha(colors.error, 0.16), border: withAlpha(colors.error, 0.22) },
  ];

  return (
    <View className="gap-4">
      {parsedLessons.length > 0 ? (
        <View style={{ flexDirection: "row", gap: 10 }}>
          <View style={{ width: 42, height: gridHeight + SLOT_HEIGHT }}>
            {timeLabels.map((hour) => (
              <View key={hour} style={{ height: SLOT_HEIGHT, justifyContent: "flex-start", paddingTop: 2 }}>
                <Text
                  style={{
                    fontSize: 11,
                    color: colors.onSurfaceVariant ?? colors.muted,
                    textAlign: "right",
                  }}
                >
                  {String(hour).padStart(2, "0")}:00
                </Text>
              </View>
            ))}
          </View>

          <View style={{ flex: 1, height: gridHeight, position: "relative" }}>
            {timeLabels.map((hour, index) => (
              <View
                key={hour}
                style={{
                  position: "absolute",
                  top: index * SLOT_HEIGHT,
                  left: 0,
                  right: 0,
                  height: 1,
                  backgroundColor: colors.outlineVariant ?? colors.border,
                  opacity: 0.45,
                }}
              />
            ))}

            {parsedLessons.map((entry) => {
              const palette = lessonPalettes[hashSubject(entry.lesson.subtitle || entry.lesson.title) % lessonPalettes.length];
              const clippedStart = Math.max(entry.start, visibleRange.start);
              const clippedEnd = Math.min(entry.end, visibleRange.end);
              if (clippedEnd <= clippedStart) {
                return null;
              }

              const top = (clippedStart - visibleRange.start) * SLOT_HEIGHT;
              const height = Math.max((clippedEnd - clippedStart) * SLOT_HEIGHT - 6, SLOT_HEIGHT - 6);
              const secondaryText =
                entry.lesson.title !== entry.lesson.subtitle ? entry.lesson.title : entry.lesson.detail;

              return (
                <View
                  key={entry.lesson.id}
                  style={{
                    position: "absolute",
                    top: top + 3,
                    left: 2,
                    right: 2,
                    height,
                    backgroundColor: palette.background,
                    borderRadius: 16,
                    borderWidth: 1,
                    borderColor: palette.border,
                    borderLeftWidth: 4,
                    borderLeftColor: palette.accent,
                    paddingHorizontal: 10,
                    paddingVertical: 8,
                    justifyContent: "flex-start",
                  }}
                >
                  <Text style={{ color: colors.foreground, fontSize: 12, fontWeight: "600" }} numberOfLines={1}>
                    {entry.lesson.subtitle}
                  </Text>
                  <Text
                    style={{ color: colors.onSurfaceVariant ?? colors.muted, fontSize: 10, marginTop: 2 }}
                    numberOfLines={2}
                  >
                    {secondaryText}
                  </Text>
                  <Text style={{ color: colors.onSurfaceVariant ?? colors.muted, fontSize: 10, marginTop: 4 }}>
                    {entry.lesson.timeLabel}
                  </Text>
                </View>
              );
            })}
          </View>
        </View>
      ) : null}

      {untimedLessons.length > 0 ? (
        <View className="gap-2">
          <Text
            className="text-[11px] font-medium uppercase tracking-[1.5px]"
            style={{ color: colors.onSurfaceVariant ?? colors.muted }}
          >
            Orari non strutturati
          </Text>
          <View className="gap-2">
            {untimedLessons.map((lesson) => (
              <View
                key={lesson.id}
                className="gap-1 rounded-2xl p-3"
                style={{
                  backgroundColor: colors.surfaceContainerHighest ?? colors.surfaceContainerHigh ?? colors.surface,
                  borderWidth: 1,
                  borderColor: colors.outlineVariant ?? colors.border,
                }}
              >
                <View className="flex-row items-start justify-between gap-3">
                  <View className="flex-1 gap-0.5">
                    <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                      {lesson.subtitle}
                    </Text>
                    <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      {lesson.title}
                    </Text>
                  </View>
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.5px]"
                    style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                  >
                    {lesson.timeLabel}
                  </Text>
                </View>
                <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                  {lesson.detail}
                </Text>
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );
}
