import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  FEATURE_HINT_STORAGE_KEY,
  FeatureHintBanner,
  hintDestinations,
  readFeatureHintDismissed,
} from "./FeatureHintBanner";

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  window.localStorage.clear();
});

describe("hintDestinations", () => {
  it("names the Sandbox alone when no staff surface is offered", () => {
    expect(hintDestinations(["CUSTOMER"])).toBe("the Sandbox");
  });

  it("names the single offered staff view alongside the Sandbox", () => {
    expect(hintDestinations(["MANAGER"])).toBe(
      "the Exec dashboard and the Sandbox",
    );
  });

  it("names both staff views alongside the Sandbox", () => {
    expect(hintDestinations(["CUSTOMER", "ADMIN"])).toBe(
      "the Ops review worklist, the Exec dashboard, and the Sandbox",
    );
  });
});

describe("readFeatureHintDismissed", () => {
  it("is false when nothing is stored", () => {
    expect(readFeatureHintDismissed()).toBe(false);
  });

  it("is true once the dismissal flag is stored", () => {
    window.localStorage.setItem(FEATURE_HINT_STORAGE_KEY, "true");
    expect(readFeatureHintDismissed()).toBe(true);
  });

  it("is false for any other stored value", () => {
    window.localStorage.setItem(FEATURE_HINT_STORAGE_KEY, "nope");
    expect(readFeatureHintDismissed()).toBe(false);
  });

  it("is false when localStorage throws", () => {
    const getItem = vi
      .spyOn(Storage.prototype, "getItem")
      .mockImplementation(() => {
        throw new Error("denied");
      });
    expect(readFeatureHintDismissed()).toBe(false);
    getItem.mockRestore();
  });
});

describe("FeatureHintBanner", () => {
  it("points desktop readers at the menu and mobile readers at the Profile tab", () => {
    render(
      <FeatureHintBanner roles={["CUSTOMER", "ADMIN"]} onDismiss={vi.fn()} />,
    );

    const desktop = screen.getByTestId("feature-hint-location-desktop");
    expect(desktop).toHaveTextContent("The menu under your name, top right,");
    expect(desktop).toHaveClass("hidden");
    expect(desktop).toHaveClass("md:inline");

    const mobile = screen.getByTestId("feature-hint-location-mobile");
    expect(mobile).toHaveTextContent("The Profile tab");
    expect(mobile).toHaveClass("md:hidden");
  });

  it("names the surfaces the roles actually unlock", () => {
    render(
      <FeatureHintBanner roles={["CUSTOMER", "ADMIN"]} onDismiss={vi.fn()} />,
    );
    expect(screen.getByTestId("feature-hint-banner")).toHaveTextContent(
      "also opens the Ops review worklist, the Exec dashboard, and the Sandbox",
    );
  });

  it("omits the staff views for a customer who is not offered them", () => {
    render(<FeatureHintBanner roles={["CUSTOMER"]} onDismiss={vi.fn()} />);
    const banner = screen.getByTestId("feature-hint-banner");
    expect(banner).toHaveTextContent("also opens the Sandbox");
    expect(banner).not.toHaveTextContent("Ops");
    expect(banner).not.toHaveTextContent("Exec");
  });

  it("holds the Sandbox detail back on the narrowest screens", () => {
    render(<FeatureHintBanner roles={["CUSTOMER"]} onDismiss={vi.fn()} />);
    const detail = screen.getByText(
      "for simulating inbound payments and card activity",
      { exact: false, selector: "span" },
    );
    expect(detail).toHaveClass("hidden");
    expect(detail).toHaveClass("sm:inline");
  });

  it("calls onDismiss when the close control is pressed", async () => {
    const onDismiss = vi.fn();
    render(<FeatureHintBanner roles={["CUSTOMER"]} onDismiss={onDismiss} />);

    await userEvent.click(
      screen.getByRole("button", { name: "Dismiss this tip" }),
    );

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
