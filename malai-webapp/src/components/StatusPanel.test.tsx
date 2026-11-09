import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusPanel } from "./StatusPanel";

describe("StatusPanel", () => {
  it("renders the title alone when there is nothing more to say", () => {
    render(<StatusPanel testId="panel" title="No cards yet" />);
    const panel = screen.getByTestId("panel");
    expect(panel).toHaveTextContent(/^No cards yet$/);
    expect(panel).not.toHaveAttribute("role");
  });

  it("announces a failure with its recovery line and action", () => {
    render(
      <StatusPanel
        testId="panel"
        role="alert"
        title="We couldn’t load your accounts"
        body="Please try again in a moment."
        action={<button type="button">Try again</button>}
      />,
    );
    const panel = screen.getByRole("alert");
    expect(panel).toHaveTextContent("We couldn’t load your accounts");
    expect(panel).toHaveTextContent("Please try again in a moment.");
    expect(
      screen.getByRole("button", { name: "Try again" }),
    ).toBeInTheDocument();
  });
});
