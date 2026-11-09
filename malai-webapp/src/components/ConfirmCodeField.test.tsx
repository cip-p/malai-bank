import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Control } from "react-hook-form";
import { useForm, useWatch } from "react-hook-form";
import { ConfirmCodeField } from "./ConfirmCodeField";

function Harness({
  defaultValue = "",
  error,
  length = 6,
}: {
  defaultValue?: string;
  error?: string;
  length?: number;
}) {
  const form = useForm<{ confirmationCode: string }>({
    defaultValues: { confirmationCode: defaultValue },
    errors: error
      ? { confirmationCode: { type: "manual", message: error } }
      : undefined,
  });
  return (
    <ConfirmCodeField
      name="confirmationCode"
      label="Confirmation code"
      control={form.control}
      length={length}
    />
  );
}

describe("ConfirmCodeField", () => {
  it("renders a 6-slot OTP input with numeric input mode and one-time-code autocomplete", () => {
    render(<Harness />);
    const input = screen.getByLabelText("Confirmation code");
    expect(input).toHaveAttribute("inputmode", "numeric");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
    expect(input).toHaveAttribute("maxlength", "6");
  });

  it("sizes the OTP input to the requested length", () => {
    render(<Harness length={8} />);
    const input = screen.getByLabelText("Confirmation code");
    expect(input).toHaveAttribute("maxlength", "8");
  });

  it("only accepts digit characters (non-digit input is ignored)", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Confirmation code");
    await user.click(input);
    await user.keyboard("12ab34");
    expect(input).toHaveValue("1234");
  });

  it("renders an inline error region when an error is present", () => {
    render(<Harness error="Enter the 6-digit code." />);
    const input = screen.getByLabelText("Confirmation code");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Enter the 6-digit code.")).toBeInTheDocument();
  });

  it("does not render the inline error region when no error is present", () => {
    render(<Harness />);
    expect(
      screen.queryByText(/Enter the 6-digit code/),
    ).not.toBeInTheDocument();
  });

  it("propagates typed digits to the bound form field", async () => {
    const user = userEvent.setup();
    let observedValue = "";
    function Capture() {
      const form = useForm<{ confirmationCode: string }>({
        defaultValues: { confirmationCode: "" },
      });
      return (
        <>
          <ConfirmCodeField
            name="confirmationCode"
            label="Confirmation code"
            control={form.control}
            length={6}
          />
          <ValueReader
            control={form.control}
            onValue={(v) => {
              observedValue = v;
            }}
          />
        </>
      );
    }
    render(<Capture />);
    const input = screen.getByLabelText("Confirmation code");
    await user.click(input);
    await user.keyboard("654321");
    expect(observedValue).toBe("654321");
  });
});

function ValueReader({
  control,
  onValue,
}: {
  control: Control<{ confirmationCode: string }>;
  onValue: (value: string) => void;
}) {
  const value = useWatch({ control, name: "confirmationCode" }) ?? "";
  onValue(value);
  return null;
}
