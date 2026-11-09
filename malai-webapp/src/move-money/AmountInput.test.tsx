import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { AmountInput } from "./AmountInput";

function ControlledAmount({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <AmountInput
      aria-label="Amount"
      value={value}
      onChange={setValue}
      onBlur={() => undefined}
    />
  );
}

describe("AmountInput", () => {
  it("renders a £ adornment next to the input", () => {
    render(<ControlledAmount />);
    expect(screen.getByText("£")).toBeInTheDocument();
    expect(screen.getByLabelText("Amount")).toBeInTheDocument();
  });

  it("uses inputMode='decimal'", () => {
    render(<ControlledAmount />);
    expect(screen.getByLabelText("Amount").getAttribute("inputmode")).toBe(
      "decimal",
    );
  });

  it("normalises the display value to two decimals on blur", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    function Harness() {
      const [v, setV] = useState("");
      return (
        <AmountInput
          aria-label="Amount"
          value={v}
          onChange={(next) => {
            setV(next);
            onChange(next);
          }}
          onBlur={() => undefined}
        />
      );
    }
    render(<Harness />);
    const input = screen.getByLabelText("Amount");
    await user.type(input, "12.4");
    await user.tab();
    expect(input).toHaveValue("12.40");
  });

  it("normalises an integer on blur to two decimals", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [v, setV] = useState("");
      return (
        <AmountInput
          aria-label="Amount"
          value={v}
          onChange={setV}
          onBlur={() => undefined}
        />
      );
    }
    render(<Harness />);
    const input = screen.getByLabelText("Amount");
    await user.type(input, "12");
    await user.tab();
    expect(input).toHaveValue("12.00");
  });

  it("does not transform invalid input on blur", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [v, setV] = useState("");
      return (
        <AmountInput
          aria-label="Amount"
          value={v}
          onChange={setV}
          onBlur={() => undefined}
        />
      );
    }
    render(<Harness />);
    const input = screen.getByLabelText("Amount");
    await user.type(input, "abc");
    await user.tab();
    expect(input).toHaveValue("abc");
  });
});
