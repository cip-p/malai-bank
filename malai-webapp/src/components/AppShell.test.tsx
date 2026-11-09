import { type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { ThemeProvider } from "@/theming/useTheme";
import { pageTitleStore, usePageTitle } from "@/lib/usePageTitle";
import { FEATURE_HINT_STORAGE_KEY } from "./FeatureHintBanner";
import { AppShell } from "./AppShell";

const signoutSpy = vi.fn();
const useSignoutMock = vi.fn(() => ({ signout: signoutSpy, isPending: false }));
vi.mock("@/auth/useSignout", () => ({
  useSignout: () => useSignoutMock(),
}));

const USER: UserResponse = {
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  roles: ["CUSTOMER"],
};

beforeEach(() => {
  window.localStorage.clear();
  signoutSpy.mockReset();
  useSignoutMock.mockReset();
  useSignoutMock.mockImplementation(() => ({
    signout: signoutSpy,
    isPending: false,
  }));
  pageTitleStore.setState({ title: "" });
});

function renderAt(
  path: string,
  child: ReactNode = <div>content</div>,
  user: UserResponse = USER,
) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppShell user={user}>{child}</AppShell>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function TitleSetter({ title }: { title: string }) {
  usePageTitle(title);
  return null;
}

describe("AppShell desktop top nav", () => {
  it("renders the desktop primary nav with Accounts, Cards, and Move money links only", () => {
    renderAt("/");
    const nav = screen.getByTestId("primary-nav-desktop");
    expect(nav).toHaveClass("hidden");
    expect(nav).toHaveClass("md:block");
    const list = within(nav).getByRole("list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(3);
    expect(
      within(items[0]).getByRole("link", { name: "Accounts" }),
    ).toHaveAttribute("href", "/");
    expect(
      within(items[1]).getByRole("link", { name: "Cards" }),
    ).toHaveAttribute("href", "/cards");
    expect(
      within(items[2]).getByRole("link", { name: "Move money" }),
    ).toHaveAttribute("href", "/move");
  });

  it("does not render a Profile link in the desktop top nav", () => {
    renderAt("/");
    const nav = screen.getByTestId("primary-nav-desktop");
    expect(
      within(nav).queryByRole("link", { name: /^Profile$/ }),
    ).not.toBeInTheDocument();
  });

  it("marks the Accounts link aria-current=page when on /", () => {
    renderAt("/");
    expect(screen.getByTestId("primary-nav-link-accounts")).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByTestId("primary-nav-link-move")).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("marks the Accounts link aria-current=page when on /accounts/:uid", () => {
    renderAt("/accounts/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    expect(screen.getByTestId("primary-nav-link-accounts")).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("marks the Move money link aria-current=page on every /move/* sub-route", () => {
    for (const path of [
      "/move",
      "/move/send",
      "/move/payees",
      "/move/direct-debits",
      "/move/standing-orders",
    ]) {
      const { unmount } = renderAt(path);
      expect(screen.getByTestId("primary-nav-link-move")).toHaveAttribute(
        "aria-current",
        "page",
      );
      unmount();
    }
  });

  it("does not render a Pay link in v1", () => {
    renderAt("/");
    expect(
      screen.queryByRole("link", { name: /^Pay$/i }),
    ).not.toBeInTheDocument();
  });

  it("does not render a mobile hamburger trigger", () => {
    renderAt("/");
    expect(
      screen.queryByTestId("primary-nav-mobile-trigger"),
    ).not.toBeInTheDocument();
  });
});

describe("AppShell mobile bottom tab bar", () => {
  it("renders a bottom tab bar with four tabs (Accounts, Cards, Move money, Profile)", () => {
    renderAt("/");
    const bar = screen.getByTestId("bottom-tab-bar");
    expect(bar).toHaveClass("md:hidden");
    const list = within(bar).getByRole("list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(4);
    expect(
      within(items[0]).getByRole("link", { name: "Accounts" }),
    ).toHaveAttribute("href", "/");
    expect(
      within(items[1]).getByRole("link", { name: "Cards" }),
    ).toHaveAttribute("href", "/cards");
    expect(
      within(items[2]).getByRole("link", { name: "Move money" }),
    ).toHaveAttribute("href", "/move");
    expect(
      within(items[3]).getByRole("link", { name: "Profile" }),
    ).toHaveAttribute("href", "/profile");
  });

  it("fixes the bottom tab bar to the viewport bottom", () => {
    renderAt("/");
    const bar = screen.getByTestId("bottom-tab-bar");
    expect(bar).toHaveClass("fixed");
    expect(bar).toHaveClass("bottom-0");
  });

  it("reserves bottom-safe-area padding on the bar", () => {
    renderAt("/");
    const bar = screen.getByTestId("bottom-tab-bar");
    expect(bar.className).toContain("safe-area-inset-bottom");
  });

  it("marks the Accounts tab aria-current=page when on /", () => {
    renderAt("/");
    expect(screen.getByTestId("bottom-tab-accounts")).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByTestId("bottom-tab-move")).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByTestId("bottom-tab-profile")).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("marks the Move money tab aria-current=page on /move/* sub-routes", () => {
    renderAt("/move/payees");
    expect(screen.getByTestId("bottom-tab-move")).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("marks the Profile tab aria-current=page on /profile", () => {
    renderAt("/profile");
    expect(screen.getByTestId("bottom-tab-profile")).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("reserves bottom padding on main so content does not collide with the bar", () => {
    renderAt("/");
    const main = screen.getByRole("main");
    expect(main.className).toMatch(/pb-/);
    expect(main.className).toContain("md:pb-0");
  });
});

describe("AppShell desktop header user dropdown", () => {
  it("renders the header only at md+ breakpoint", () => {
    renderAt("/");
    const header = screen.getByTestId("app-header");
    expect(header).toHaveClass("hidden");
    expect(header).toHaveClass("md:block");
  });

  it("renders the HeaderWordmark as a Link to /", () => {
    renderAt("/");
    const link = screen.getByTestId("app-header-wordmark");
    expect(link).toHaveAttribute("href", "/");
    expect(within(link).getByTestId("header-wordmark")).toBeInTheDocument();
  });

  it("renders the ThemeSwitcher next to the user menu", () => {
    renderAt("/");
    expect(screen.getByTestId("theme-switcher-trigger")).toBeInTheDocument();
  });

  it("renders the display name on the dropdown trigger", () => {
    renderAt("/");
    const trigger = screen.getByTestId("user-menu-trigger");
    expect(trigger).toHaveTextContent("Ada Lovelace");
    expect(within(trigger).getByTestId("display-name")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("does not render an inline Sign out button (the menu owns it)", () => {
    renderAt("/");
    const triggers = screen.queryAllByRole("button", { name: /Sign out/i });
    expect(triggers).toHaveLength(0);
  });

  it("reveals Settings, Sandbox, and Sign out items when the menu is opened", async () => {
    const user = userEvent.setup();
    renderAt("/");
    const trigger = screen.getByTestId("user-menu-trigger");
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    await user.click(trigger);

    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const settings = await screen.findByRole("menuitem", { name: "Settings" });
    expect(settings).toHaveAttribute("href", "/settings");
    const sandbox = screen.getByRole("menuitem", { name: "Sandbox" });
    expect(sandbox).toHaveAttribute("href", "/sandbox");
    expect(
      screen.getByRole("menuitem", { name: "Sign out" }),
    ).toBeInTheDocument();
  });

  it("orders the user-menu items Settings → Sandbox → Sign out", async () => {
    const user = userEvent.setup();
    renderAt("/");
    await user.click(screen.getByTestId("user-menu-trigger"));
    const items = await screen.findAllByRole("menuitem");
    expect(items.map((i) => i.textContent)).toEqual([
      "Settings",
      "Sandbox",
      "Sign out",
    ]);
  });

  it("offers Ops and Exec rows as peers of Sandbox to a user whose roles offer them", async () => {
    const user = userEvent.setup();
    renderAt("/", <div>content</div>, {
      ...USER,
      roles: ["CUSTOMER", "ADMIN"],
    });
    await user.click(screen.getByTestId("user-menu-trigger"));
    const ops = await screen.findByTestId("user-menu-item-ops");
    expect(ops).toHaveAttribute("href", "/ops");
    expect(screen.getByTestId("user-menu-item-exec")).toHaveAttribute(
      "href",
      "/exec",
    );
    const items = screen.getAllByRole("menuitem");
    expect(items.map((i) => i.textContent)).toEqual([
      "Settings",
      "Sandbox",
      "Ops",
      "Exec",
      "Sign out",
    ]);
  });

  it("withholds the Ops and Exec rows from a customer-only user", async () => {
    const user = userEvent.setup();
    renderAt("/");
    await user.click(screen.getByTestId("user-menu-trigger"));
    await screen.findByRole("menuitem", { name: "Settings" });
    expect(screen.queryByTestId("user-menu-item-ops")).not.toBeInTheDocument();
    expect(screen.queryByTestId("user-menu-item-exec")).not.toBeInTheDocument();
  });

  it("invokes signout when the Sign out item is activated", async () => {
    const user = userEvent.setup();
    renderAt("/");
    await user.click(screen.getByTestId("user-menu-trigger"));
    const signoutItem = await screen.findByRole("menuitem", {
      name: "Sign out",
    });
    await user.click(signoutItem);
    expect(signoutSpy).toHaveBeenCalledTimes(1);
  });

  it("disables the Sign out item while signout is pending", async () => {
    useSignoutMock.mockImplementation(() => ({
      signout: signoutSpy,
      isPending: true,
    }));
    const user = userEvent.setup();
    renderAt("/");
    await user.click(screen.getByTestId("user-menu-trigger"));
    const signoutItem = await screen.findByRole("menuitem", {
      name: "Sign out",
    });
    expect(signoutItem).toHaveAttribute("aria-disabled", "true");
  });
});

describe("AppShell mobile sticky top bar", () => {
  it("renders the mobile top bar only below the md breakpoint", () => {
    renderAt("/");
    const bar = screen.getByTestId("app-mobile-top-bar");
    expect(bar).toHaveClass("md:hidden");
    expect(bar).toHaveClass("sticky");
    expect(bar).toHaveClass("top-0");
  });

  it("reserves top-safe-area padding on the bar", () => {
    renderAt("/");
    const bar = screen.getByTestId("app-mobile-top-bar");
    expect(bar.className).toContain("safe-area-inset-top");
  });

  it("renders the HeaderWordmark on the left, linking to /", () => {
    renderAt("/");
    const bar = screen.getByTestId("app-mobile-top-bar");
    const link = within(bar).getByTestId("mobile-top-bar-wordmark");
    expect(link).toHaveAttribute("href", "/");
    expect(within(link).getByTestId("header-wordmark")).toBeInTheDocument();
  });

  it("renders the active page title from the store", () => {
    renderAt("/", <TitleSetter title="Accounts" />);
    const bar = screen.getByTestId("app-mobile-top-bar");
    expect(within(bar).getByTestId("mobile-top-bar-title")).toHaveTextContent(
      "Accounts",
    );
  });

  it("renders an empty title slot when no page has set one", () => {
    renderAt("/");
    const bar = screen.getByTestId("app-mobile-top-bar");
    const title = within(bar).getByTestId("mobile-top-bar-title");
    expect(title).toBeInTheDocument();
    expect(title).toHaveTextContent("");
  });

  it("does not duplicate the user-menu, theme-switcher, or top-nav links", () => {
    renderAt("/");
    const bar = screen.getByTestId("app-mobile-top-bar");
    expect(
      within(bar).queryByTestId("user-menu-trigger"),
    ).not.toBeInTheDocument();
    expect(
      within(bar).queryByTestId("theme-switcher-trigger"),
    ).not.toBeInTheDocument();
    expect(
      within(bar).queryByTestId("primary-nav-link-accounts"),
    ).not.toBeInTheDocument();
    expect(
      within(bar).queryByTestId("primary-nav-link-move"),
    ).not.toBeInTheDocument();
  });
});

describe("AppShell feature hint banner", () => {
  it("shows the banner above the header when nothing is stored", () => {
    renderAt("/");
    const banner = screen.getByTestId("feature-hint-banner");
    expect(banner).toBeInTheDocument();
    expect(
      banner.compareDocumentPosition(screen.getByTestId("app-header")) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("highlights the user menu trigger while the banner is up", () => {
    renderAt("/");
    expect(screen.getByTestId("user-menu-trigger")).toHaveAttribute(
      "data-hinted",
      "true",
    );
  });

  it("hides the banner and stores the dismissal when the close control is pressed", async () => {
    const user = userEvent.setup();
    renderAt("/");

    await user.click(screen.getByTestId("feature-hint-dismiss"));

    expect(screen.queryByTestId("feature-hint-banner")).not.toBeInTheDocument();
    expect(window.localStorage.getItem(FEATURE_HINT_STORAGE_KEY)).toBe("true");
    expect(screen.getByTestId("user-menu-trigger")).not.toHaveAttribute(
      "data-hinted",
    );
  });

  it("stays hidden on a later render once the dismissal is stored", () => {
    window.localStorage.setItem(FEATURE_HINT_STORAGE_KEY, "true");
    renderAt("/");
    expect(screen.queryByTestId("feature-hint-banner")).not.toBeInTheDocument();
  });
});
