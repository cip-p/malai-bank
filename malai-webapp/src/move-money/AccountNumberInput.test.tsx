import { useState } from "react";
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AccountNumberInput } from "./AccountNumberInput";

function Harness({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <AccountNumberInput
      aria-label="Account number"
      value={value}
      onChange={setValue}
    />
  );
}

describe("AccountNumberInput", () => {
  it("strips non-digit characters from typed input", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Account number");
    await user.type(input, "12a3-45_67_8");
    expect((input as HTMLInputElement).value).toBe("12345678");
  });

  it("clips input beyond 8 digits", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Account number");
    await user.click(input);
    await user.paste("1234567890123");
    expect((input as HTMLInputElement).value).toBe("12345678");
  });

  it("strips spaces from a pasted bank-statement format", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Account number");
    await user.click(input);
    await user.paste("1234 5678");
    expect((input as HTMLInputElement).value).toBe("12345678");
  });
});
