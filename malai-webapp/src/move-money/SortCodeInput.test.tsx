import { useState } from "react";
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SortCodeInput } from "./SortCodeInput";

function Harness({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <SortCodeInput aria-label="Sort code" value={value} onChange={setValue} />
  );
}

describe("SortCodeInput", () => {
  it("formats keystrokes to 12-34-56 as the user types", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Sort code");
    await user.type(input, "123456");
    expect((input as HTMLInputElement).value).toBe("12-34-56");
  });

  it("normalises pasted-with-spaces input", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Sort code");
    await user.click(input);
    await user.paste("12 34 56");
    expect((input as HTMLInputElement).value).toBe("12-34-56");
  });

  it("renders the initial value untouched when it is already well-formatted", () => {
    render(<Harness initial="12-34-56" />);
    expect((screen.getByLabelText("Sort code") as HTMLInputElement).value).toBe(
      "12-34-56",
    );
  });
});
