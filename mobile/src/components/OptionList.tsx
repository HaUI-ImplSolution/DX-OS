// SPDX-License-Identifier: Apache-2.0
import { Pressable, StyleSheet, Text, View } from "react-native";
import { colors, fontSize, radius, sizes, spacing } from "@/theme";

interface Option<T extends string> {
  value: T;
  label: string;
  caption?: string;
}

interface Props<T extends string> {
  options: Option<T>[];
  value: T | null;
  onChange: (value: T) => void;
}

// A short list of choices shown as large tappable rows.
export function OptionList<T extends string>({ options, value, onChange }: Props<T>) {
  return (
    <View style={styles.list}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={[styles.option, selected && styles.selected]}
          >
            <Text style={[styles.label, selected && styles.selectedLabel]}>{option.label}</Text>
            {option.caption && <Text style={styles.caption}>{option.caption}</Text>}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm },
  option: {
    minHeight: sizes.touch,
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.button,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  selected: { borderColor: colors.primary, backgroundColor: "#e3f1e4" },
  label: { fontSize: fontSize.body, color: colors.text },
  selectedLabel: { fontWeight: "700", color: colors.primaryDark },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
});
