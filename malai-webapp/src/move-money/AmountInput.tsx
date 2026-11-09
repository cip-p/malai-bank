import * as React from "react";
import { Input } from "@/components/ui/input";
import { parseAmount } from "./parseAmount";

type Props = Omit<
  React.ComponentProps<typeof Input>,
  "onChange" | "value" | "type" | "inputMode"
> & {
  value: string;
  onChange: (next: string) => void;
};

export function AmountInput({
  value,
  onChange,
  onBlur,
  className,
  ...rest
}: Props) {
  function handleBlur(e: React.FocusEvent<HTMLInputElement>): void {
    const parsed = parseAmount(value);
    if (parsed.ok) {
      const major = Math.floor(parsed.minorUnits / 100);
      const minor = String(parsed.minorUnits % 100).padStart(2, "0");
      const next = `${major}.${minor}`;
      if (next !== value) onChange(next);
    }
    onBlur?.(e);
  }

  return (
    <div className="relative">
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-muted-foreground"
      >
        £
      </span>
      <Input
        {...rest}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={handleBlur}
        className={`pl-7 ${className ?? ""}`}
      />
    </div>
  );
}
