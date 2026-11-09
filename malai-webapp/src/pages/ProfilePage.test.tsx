import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { UserResponse } from "@/api/generated/types.gen";
import { ThemeProvider } from "@/theming/useTheme";
import { ProfilePage } from "./ProfilePage";

const signoutSpy = vi.fn();
const useSignoutMock = vi.fn(() => ({ signout: signoutSpy, isPending: false }));
vi.mock("@/auth/useSignout", () => ({
  useSignout: () => useSignoutMock(),
}));

type Listener = (event: { matches: boolean }) => void;

function mockMatchMedia(isDesktop: boolean) {
  const listeners = new Set<Listener>();
  const impl = (query: string): MediaQueryList =>
    ({
      matches: query.includes("min-width: 768px") ? isDesktop : false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: (_t: string, l: Listener) => listeners.add(l),
      removeEventListener: (_t: string, l: Listener) => listeners.delete(l),
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
  vi.stubGlobal("matchMedia", impl);
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: impl,
  });
}

const USER: UserResponse = {
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  roles: ["CUSTOMER"],
};

function OutletStub({ user }: { user: UserResponse }) {
  return <Outlet context={{ user }} />;
}

function SettingsStub() {
  return <h1>Settings landing</h1>;
}

function renderAt(path: string, user: UserResponse = USER) {
  return render(
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      storageKey="malai-mode"
    >
      <ThemeProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route element={<OutletStub user={user} />}>
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/settings" element={<SettingsStub />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </NextThemesProvider>,
  );
}

beforeEach(() => {
  signoutSpy.mockReset();
  useSignoutMock.mockReset();
  useSignoutMock.mockImplementation(() => ({
    signout: signoutSpy,
    isPending: false,
  }));
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  document.documentElement.classList.remove("light", "dark");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ProfilePage (mobile)", () => {
  beforeEach(() => {
    mockMatchMedia(false);
  });

  it("renders an h1 'Profile' focused on mount", () => {
    renderAt("/profile");
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Profile");
    expect(heading).toHaveAttribute("tabindex", "-1");
    expect(heading).toHaveFocus();
  });

  it("renders the identity block with display name and email", () => {
    renderAt("/profile");
    expect(screen.getByTestId("profile-display-name")).toHaveTextContent(
      "Ada Lovelace",
    );
    expect(screen.getByTestId("profile-email")).toHaveTextContent(
      "ada@example.com",
    );
  });

  it("renders the inline ThemeSwitcher in the Appearance section", () => {
    renderAt("/profile");
    expect(screen.getByTestId("appearance-section")).toBeInTheDocument();
    expect(screen.getByTestId("inline-theme-switcher")).toBeInTheDocument();
    expect(screen.getByTestId("inline-theme-item-malai")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("renders the Mode toggle in the Appearance section", () => {
    renderAt("/profile");
    expect(screen.getByTestId("mode-toggle")).toBeInTheDocument();
    for (const id of ["light", "dark", "system"] as const) {
      expect(screen.getByTestId(`mode-toggle-item-${id}`)).toBeInTheDocument();
    }
  });

  it("renders a Settings link to /settings", () => {
    renderAt("/profile");
    const link = screen.getByTestId("profile-settings-link");
    expect(link).toHaveAttribute("href", "/settings");
  });

  it("renders a Sandbox link to /sandbox", () => {
    renderAt("/profile");
    const link = screen.getByTestId("profile-sandbox-link");
    expect(link).toHaveAttribute("href", "/sandbox");
    expect(link).toHaveTextContent("Sandbox");
  });

  it("offers an Ops link as a peer of Sandbox to a user whose roles offer Ops", () => {
    renderAt("/profile", { ...USER, roles: ["CUSTOMER", "ADMIN"] });
    const link = screen.getByTestId("profile-ops-link");
    expect(link).toHaveAttribute("href", "/ops");
    expect(link).toHaveTextContent("Ops");
  });

  it("withholds the Ops link from a customer-only user", () => {
    renderAt("/profile");
    expect(screen.queryByTestId("profile-ops-link")).not.toBeInTheDocument();
  });

  it("offers an Exec link as a peer of Ops to a user whose roles offer Exec", () => {
    renderAt("/profile", { ...USER, roles: ["CUSTOMER", "ADMIN"] });
    const link = screen.getByTestId("profile-exec-link");
    expect(link).toHaveAttribute("href", "/exec");
    expect(link).toHaveTextContent("Exec");
  });

  it("withholds the Exec link from a customer-only user", () => {
    renderAt("/profile");
    expect(screen.queryByTestId("profile-exec-link")).not.toBeInTheDocument();
  });

  it("invokes useSignout().signout when the Sign out button is activated", async () => {
    const user = userEvent.setup();
    renderAt("/profile");
    await user.click(screen.getByTestId("profile-signout"));
    expect(signoutSpy).toHaveBeenCalledTimes(1);
  });

  it("disables the Sign out button while signout is pending", () => {
    useSignoutMock.mockImplementation(() => ({
      signout: signoutSpy,
      isPending: true,
    }));
    renderAt("/profile");
    expect(screen.getByTestId("profile-signout")).toBeDisabled();
  });
});

describe("ProfilePage (desktop)", () => {
  beforeEach(() => {
    mockMatchMedia(true);
  });

  it("redirects to /settings on desktop", () => {
    renderAt("/profile");
    expect(
      screen.getByRole("heading", { level: 1, name: "Settings landing" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("profile-display-name"),
    ).not.toBeInTheDocument();
  });
});
