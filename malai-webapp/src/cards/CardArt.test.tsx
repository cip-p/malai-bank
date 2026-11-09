import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { CardArt, SCHEME_MARK } from "./CardArt";

describe("CardArt", () => {
  it("renders the masked PAN in dotted groups", () => {
    render(
      <CardArt
        variant="DEBIT"
        status="ACTIVE"
        pan="************1234"
        expiry="10/29"
      />,
    );
    const pan = screen.getByTestId("card-art-pan");
    const groups = Array.from(pan.children).map((c) => c.textContent);
    expect(groups).toEqual(["••••", "••••", "••••", "1234"]);
  });

  it("renders the variant label and the scheme mark", () => {
    render(
      <CardArt
        variant="CREDIT"
        status="ACTIVE"
        pan="************9876"
        expiry="10/29"
      />,
    );
    expect(screen.getByTestId("card-art-variant")).toHaveTextContent("Credit");
    expect(screen.getByTestId("card-art")).toHaveTextContent(SCHEME_MARK);
  });

  it("renders expiry, and the CVV only when provided", () => {
    const { rerender } = render(
      <CardArt
        variant="DEBIT"
        status="ACTIVE"
        pan="9904320000012341"
        expiry="10/29"
      />,
    );
    expect(screen.getByTestId("card-art-expiry")).toHaveTextContent("10/29");
    expect(screen.queryByTestId("card-art-cvv")).toBeNull();

    rerender(
      <CardArt
        variant="DEBIT"
        status="ACTIVE"
        pan="9904320000012341"
        expiry="10/29"
        cvv="123"
      />,
    );
    expect(screen.getByTestId("card-art-cvv")).toHaveTextContent("123");
  });

  it("renders the cardholder name when provided", () => {
    render(
      <CardArt
        variant="DEBIT"
        status="ACTIVE"
        pan="************1234"
        expiry="10/29"
        cardholderName="Ada Lovelace"
      />,
    );
    expect(screen.getByTestId("card-art-name")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("shows a frozen overlay only when the card is FROZEN", () => {
    const { rerender } = render(
      <CardArt
        variant="DEBIT"
        status="ACTIVE"
        pan="************1234"
        expiry="10/29"
      />,
    );
    expect(screen.queryByTestId("card-art-frozen-overlay")).toBeNull();

    rerender(
      <CardArt
        variant="DEBIT"
        status="FROZEN"
        pan="************1234"
        expiry="10/29"
      />,
    );
    expect(screen.getByTestId("card-art-frozen-overlay")).toHaveTextContent(
      "Frozen",
    );
  });

  it("marks the art with the card status for cancelled styling", () => {
    render(
      <CardArt
        variant="DEBIT"
        status="CANCELLED"
        pan="************1234"
        expiry="10/29"
      />,
    );
    expect(screen.getByTestId("card-art")).toHaveAttribute(
      "data-status",
      "CANCELLED",
    );
  });
});
