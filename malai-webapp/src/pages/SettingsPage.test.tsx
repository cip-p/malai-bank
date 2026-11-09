import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import type {
  AddressV2Response,
  UserResponse,
} from "@/api/generated/types.gen";
import type { UseCurrentAddressResult } from "@/account-holder/useCurrentAddress";
import { ThemeProvider } from "@/theming/useTheme";
import { SettingsPage } from "./SettingsPage";

const updateUserSpy = vi.fn();
const useUpdateUserMock = vi.fn(() => ({
  updateUser: updateUserSpy,
  isPending: false,
}));
vi.mock("@/auth/useUpdateUser", () => ({
  useUpdateUser: () => useUpdateUserMock(),
}));

const ADDRESS: AddressV2Response = {
  line1: "12 Maple Grove",
  line2: "Flat 4",
  postTown: "Bristol",
  postCode: "BS1 4QR",
  countryCode: "GB",
};

const useCurrentAddressMock = vi.fn<() => UseCurrentAddressResult>(() => ({
  address: ADDRESS,
  isPending: false,
  isError: false,
  error: null,
}));
vi.mock("@/account-holder/useCurrentAddress", () => ({
  useCurrentAddress: () => useCurrentAddressMock(),
}));

const USER: UserResponse = {
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  dateOfBirth: "1815-12-10",
  roles: ["CUSTOMER"],
};

function OutletStub({ user }: { user: UserResponse }) {
  return <Outlet context={{ user }} />;
}

function renderWith(user: UserResponse = USER) {
  return render(
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      storageKey="malai-mode"
    >
      <ThemeProvider>
        <MemoryRouter initialEntries={["/settings"]}>
          <Routes>
            <Route element={<OutletStub user={user} />}>
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </NextThemesProvider>,
  );
}

beforeEach(() => {
  updateUserSpy.mockReset();
  useUpdateUserMock.mockReset();
  useUpdateUserMock.mockImplementation(() => ({
    updateUser: updateUserSpy,
    isPending: false,
  }));
  useCurrentAddressMock.mockReset();
  useCurrentAddressMock.mockImplementation(() => ({
    address: ADDRESS,
    isPending: false,
    isError: false,
    error: null,
  }));
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  document.documentElement.classList.remove("light", "dark");
});

describe("SettingsPage", () => {
  it("renders an <h1> 'Account settings' focused on mount", () => {
    renderWith();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Account settings");
    expect(heading).toHaveAttribute("tabindex", "-1");
    expect(heading).toHaveFocus();
  });

  it("renders an <h2> 'Profile' below the page heading", () => {
    renderWith();
    expect(
      screen.getByRole("heading", { level: 2, name: "Profile" }),
    ).toBeInTheDocument();
  });

  it("renders firstName + lastName inputs prefilled from the user", () => {
    renderWith();
    const firstName = screen.getByLabelText("First name") as HTMLInputElement;
    const lastName = screen.getByLabelText("Last name") as HTMLInputElement;
    expect(firstName.value).toBe("Ada");
    expect(lastName.value).toBe("Lovelace");
  });

  it("renders the email as read-only with 'Contact support to change'", () => {
    renderWith();
    const term = screen.getByText("Email");
    const row = term.closest("div");
    expect(row).not.toBeNull();
    expect(row).toHaveTextContent("ada@example.com");
    expect(row).toHaveTextContent("Contact support to change");
    expect(
      screen.queryByRole("textbox", { name: /^email$/i }),
    ).not.toBeInTheDocument();
  });

  it("formats the dateOfBirth as 'd MMM yyyy' (UK)", () => {
    renderWith();
    const term = screen.getByText("Date of birth");
    const row = term.closest("div");
    expect(row).not.toBeNull();
    expect(row).toHaveTextContent("10 Dec 1815");
    expect(row).toHaveTextContent("Contact support to change");
  });

  it("renders the address read-only with 'Contact support to change'", () => {
    renderWith();
    const term = screen.getByText("Address");
    const row = term.closest("div");
    expect(row).not.toBeNull();
    expect(row).toHaveTextContent("12 Maple Grove");
    expect(row).toHaveTextContent("Flat 4");
    expect(row).toHaveTextContent("Bristol");
    expect(row).toHaveTextContent("BS1 4QR");
    expect(row).toHaveTextContent("Contact support to change");
    expect(
      screen.queryByRole("textbox", { name: /address/i }),
    ).not.toBeInTheDocument();
  });

  it("omits the address row when no current address is available", () => {
    useCurrentAddressMock.mockImplementation(() => ({
      address: null,
      isPending: false,
      isError: false,
      error: null,
    }));
    renderWith();
    expect(screen.queryByText("Address")).not.toBeInTheDocument();
  });

  it("disables Save while the form is pristine", () => {
    renderWith();
    const save = screen.getByRole("button", { name: "Save" });
    expect(save).toBeDisabled();
  });

  it("enables Save once the user edits a field", async () => {
    const user = userEvent.setup();
    renderWith();
    const firstName = screen.getByLabelText("First name");
    await user.clear(firstName);
    await user.type(firstName, "Ada-New");
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("calls useUpdateUser().updateUser on submit with the form values", async () => {
    const user = userEvent.setup();
    renderWith();
    const firstName = screen.getByLabelText("First name");
    await user.clear(firstName);
    await user.type(firstName, "Ada-New");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(updateUserSpy).toHaveBeenCalledWith({
      firstName: "Ada-New",
      lastName: "Lovelace",
    });
  });

  it("shows 'Saving…' and disables Save while the mutation is pending", () => {
    useUpdateUserMock.mockImplementation(() => ({
      updateUser: updateUserSpy,
      isPending: true,
    }));
    renderWith();
    const save = screen.getByRole("button", { name: "Saving…" });
    expect(save).toBeDisabled();
  });

  it("falls back to empty string when user.firstName is absent", () => {
    renderWith({
      lastName: "Lovelace",
      email: "ada@example.com",
      roles: ["CUSTOMER"],
    });
    const firstName = screen.getByLabelText("First name") as HTMLInputElement;
    expect(firstName.value).toBe("");
  });

  it("omits the date-of-birth row when user.dateOfBirth is absent", () => {
    renderWith({
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",
      roles: ["CUSTOMER"],
    });
    expect(screen.queryByText("Date of birth")).not.toBeInTheDocument();
  });
});

describe("SettingsPage Security section (stub)", () => {
  it("renders an <h2> 'Security' with 'Coming soon.' body", () => {
    renderWith();
    expect(
      screen.getByRole("heading", { level: 2, name: "Security" }),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("settings-security-coming-soon"),
    ).toHaveTextContent("Coming soon.");
  });

  it("does not render a change-password form", () => {
    renderWith();
    expect(screen.queryByLabelText("Current password")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /update password/i }),
    ).not.toBeInTheDocument();
  });
});

describe("SettingsPage Appearance section", () => {
  it("renders an <h2> 'Appearance' with InlineThemeSwitcher and ModeToggle", () => {
    renderWith();
    expect(
      screen.getByRole("heading", { level: 2, name: "Appearance" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("inline-theme-switcher")).toBeInTheDocument();
    expect(screen.getByTestId("mode-toggle")).toBeInTheDocument();
  });

  it("switching theme on SettingsPage updates the active theme", async () => {
    const user = userEvent.setup();
    renderWith();
    expect(screen.getByTestId("inline-theme-item-malai")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    await user.click(screen.getByTestId("inline-theme-item-linden"));
    expect(screen.getByTestId("inline-theme-item-linden")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(document.documentElement.getAttribute("data-theme")).toBe("linden");
  });
});
