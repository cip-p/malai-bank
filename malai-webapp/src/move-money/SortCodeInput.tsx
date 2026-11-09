import * as React from "react";
import { Input } from "@/components/ui/input";
import { formatSortCodeInput } from "@/lib/formatSortCodeInput";

type Props = Omit<
  React.ComponentProps<typeof Input>,
  "onChange" | "value" | "type" | "inputMode"
> & {
  value: string;
  onChange: (next: string) => void;
};

export function SortCodeInput({ value, onChange, ...rest }: Props) {
  return (
    <Input
      {...rest}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={value}
      onChange={(e) => onChange(formatSortCodeInput(e.target.value))}
    />
  );
}
