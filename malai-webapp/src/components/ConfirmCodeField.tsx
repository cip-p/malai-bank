import { useId } from "react";
import {
  type Control,
  Controller,
  type FieldPath,
  type FieldValues,
} from "react-hook-form";
import { OTPInput, REGEXP_ONLY_DIGITS, type SlotProps } from "input-otp";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";

export type ConfirmCodeFieldProps<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = {
  name: TName;
  control: Control<TFieldValues>;
  label: string;
  length: number;
};

export function ConfirmCodeField<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
>({
  name,
  control,
  label,
  length,
}: ConfirmCodeFieldProps<TFieldValues, TName>) {
  const inputId = useId();
  const errorId = `${inputId}-error`;

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        const errorMessage = fieldState.error?.message;
        return (
          <div className="space-y-1.5">
            <Label htmlFor={inputId}>{label}</Label>
            <OTPInput
              id={inputId}
              ref={field.ref}
              name={field.name}
              value={(field.value as string | undefined) ?? ""}
              onChange={field.onChange}
              onBlur={field.onBlur}
              maxLength={length}
              pattern={REGEXP_ONLY_DIGITS}
              inputMode="numeric"
              autoComplete="one-time-code"
              aria-invalid={errorMessage ? true : undefined}
              aria-describedby={errorMessage ? errorId : undefined}
              containerClassName="flex items-center gap-2"
              render={({ slots }) => (
                <>
                  {slots.map((slot, idx) => (
                    <Slot key={idx} {...slot} />
                  ))}
                </>
              )}
            />
            {errorMessage ? (
              <p id={errorId} className="text-sm text-destructive">
                {errorMessage}
              </p>
            ) : null}
          </div>
        );
      }}
    />
  );
}

function Slot({ char, isActive, hasFakeCaret }: SlotProps) {
  return (
    <div
      data-slot="confirm-code-slot"
      className={cn(
        "relative flex h-10 min-w-0 flex-1 basis-0 max-w-10 items-center justify-center rounded-lg border border-input bg-transparent text-base font-medium pointer-coarse:h-11 pointer-coarse:max-w-11 motion-safe:transition-colors",
        isActive && "border-ring ring-3 ring-ring/50",
      )}
    >
      {char}
      {hasFakeCaret ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="h-5 w-px animate-caret-blink bg-foreground motion-reduce:animate-none" />
        </div>
      ) : null}
    </div>
  );
}
