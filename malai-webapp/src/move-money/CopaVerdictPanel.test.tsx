import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopaVerdictPanel } from "./CopaVerdictPanel";

const NOOP = () => {};

describe("CopaVerdictPanel", () => {
  it("renders nothing for MATCH", () => {
    const { container } = render(
      <CopaVerdictPanel
        state={{ kind: "MATCH" }}
        enteredName="Sarah Mitchell"
        onAccept={NOOP}
        onEdit={NOOP}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the CLOSE_MATCH message naming both the entered and on-record names", () => {
    render(
      <CopaVerdictPanel
        state={{
          kind: "CLOSE_MATCH",
          onRecordName: "Sarah Beatrice Mitchell",
          intent: "save-payee",
        }}
        enteredName="Sara Mitchell"
        onAccept={NOOP}
        onUseOnRecord={NOOP}
        onEdit={NOOP}
      />,
    );
    expect(screen.getByTestId("copa-panel").getAttribute("data-verdict")).toBe(
      "CLOSE_MATCH",
    );
    const message = screen.getByTestId("copa-message");
    expect(message.textContent).toContain("Sarah Beatrice Mitchell");
    expect(message.textContent).toContain("Sara Mitchell");
    expect(message.textContent).toContain("On record as");
    expect(message.textContent).toContain("you entered");
  });

  it("fires onUseOnRecord when 'Use the on-record name' is clicked", async () => {
    const user = userEvent.setup();
    const onUseOnRecord = vi.fn();
    const onAccept = vi.fn();
    render(
      <CopaVerdictPanel
        state={{
          kind: "CLOSE_MATCH",
          onRecordName: "Sarah Beatrice Mitchell",
          intent: "save-payee",
        }}
        enteredName="Sara Mitchell"
        onAccept={onAccept}
        onUseOnRecord={onUseOnRecord}
        onEdit={NOOP}
      />,
    );
    await user.click(screen.getByTestId("copa-use-on-record"));
    expect(onUseOnRecord).toHaveBeenCalledTimes(1);
    expect(onAccept).not.toHaveBeenCalled();
  });

  it("fires onAccept when 'Keep what I typed and continue' is clicked", async () => {
    const user = userEvent.setup();
    const onUseOnRecord = vi.fn();
    const onAccept = vi.fn();
    render(
      <CopaVerdictPanel
        state={{
          kind: "CLOSE_MATCH",
          onRecordName: "On Record",
          intent: "save-payee",
        }}
        enteredName="Entered"
        onAccept={onAccept}
        onUseOnRecord={onUseOnRecord}
        onEdit={NOOP}
      />,
    );
    await user.click(screen.getByTestId("copa-accept"));
    expect(onAccept).toHaveBeenCalledTimes(1);
    expect(onUseOnRecord).not.toHaveBeenCalled();
  });

  it("renders the NO_MATCH warning and labels the destructive button 'Save anyway' for save-payee", () => {
    render(
      <CopaVerdictPanel
        state={{ kind: "NO_MATCH", intent: "save-payee" }}
        enteredName="Sara Mitchell"
        onAccept={NOOP}
        onEdit={NOOP}
        onSendAnyway={NOOP}
      />,
    );
    expect(screen.getByTestId("copa-panel").getAttribute("data-verdict")).toBe(
      "NO_MATCH",
    );
    expect(screen.getByTestId("copa-message").textContent).toContain(
      "We couldn’t verify this account. Please double-check the details.",
    );
    expect(screen.getByTestId("copa-send-anyway").textContent).toBe(
      "Save anyway",
    );
  });

  it("labels the destructive button 'Send anyway' for transfer intent", () => {
    render(
      <CopaVerdictPanel
        state={{ kind: "NO_MATCH", intent: "transfer" }}
        enteredName="Sara Mitchell"
        onAccept={NOOP}
        onEdit={NOOP}
        onSendAnyway={NOOP}
      />,
    );
    expect(screen.getByTestId("copa-send-anyway").textContent).toBe(
      "Send anyway",
    );
  });

  it("fires onEdit when 'Edit details' is clicked", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    const onSendAnyway = vi.fn();
    render(
      <CopaVerdictPanel
        state={{ kind: "NO_MATCH", intent: "save-payee" }}
        enteredName="Entered"
        onAccept={NOOP}
        onEdit={onEdit}
        onSendAnyway={onSendAnyway}
      />,
    );
    await user.click(screen.getByTestId("copa-edit"));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onSendAnyway).not.toHaveBeenCalled();
  });

  it("fires onSendAnyway when 'Save anyway' is clicked", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    const onSendAnyway = vi.fn();
    render(
      <CopaVerdictPanel
        state={{ kind: "NO_MATCH", intent: "save-payee" }}
        enteredName="Entered"
        onAccept={NOOP}
        onEdit={onEdit}
        onSendAnyway={onSendAnyway}
      />,
    );
    await user.click(screen.getByTestId("copa-send-anyway"));
    expect(onSendAnyway).toHaveBeenCalledTimes(1);
    expect(onEdit).not.toHaveBeenCalled();
  });
});
