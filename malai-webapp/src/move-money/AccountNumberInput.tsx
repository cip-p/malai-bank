import * as React from "react";
import { Input } from "@/components/ui/input";

type Props = Omit<
  React.ComponentProps<typeof Input>,
  "onChange" | "value" | "type" | "inputMode"
> & {
  value: string;
  onChange: (next: string) => void;
};

export function AccountNumberInput({ value, onChange, ...rest }: Props) {
  return (
    <Input
      {...rest}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={value}
      onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(0, 8))}
    />
  );
}
