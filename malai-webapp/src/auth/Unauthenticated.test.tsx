import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { Unauthenticated } from "./Unauthenticated";

const useUserMock = vi.fn();
vi.mock("@/auth/useUser", () => ({ useUser: () => useUserMock() }));

function renderAt(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<Unauthenticated />}>
          <Route path="/login" element={<div>login outlet</div>} />
        </Route>
        <Route path="/" element={<div>home</div>} />
        <Route path="/ops" element={<div>ops home</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("Unauthenticated layout route", () => {
  beforeEach(() => {
    useUserMock.mockReset();
  });

  it("renders a busy skeleton while the user query is pending", () => {
    useUserMock.mockReturnValue({ user: null, status: "unknown" });
    renderAt("/login");
    const main = screen.getByRole("main");
    expect(main).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("login outlet")).not.toBeInTheDocument();
    expect(screen.queryByText("home")).not.toBeInTheDocument();
  });

  it("redirects a customer user to Banking when already authenticated", () => {
    useUserMock.mockReturnValue({
      user: { firstName: "Ada", lastName: "Lovelace", roles: ["CUSTOMER"] },
      status: "authenticated",
    });
    renderAt("/login");
    expect(screen.getByText("home")).toBeInTheDocument();
    expect(screen.queryByText("login outlet")).not.toBeInTheDocument();
  });

  it("lands an admin-only user on Ops, the first offered surface", () => {
    useUserMock.mockReturnValue({
      user: { firstName: "Sam", lastName: "Reviewer", roles: ["ADMIN"] },
      status: "authenticated",
    });
    renderAt("/login");
    expect(screen.getByText("ops home")).toBeInTheDocument();
    expect(screen.queryByText("login outlet")).not.toBeInTheDocument();
  });

  it("lands a user with no offered surface on the Banking root, where the Access page renders", () => {
    useUserMock.mockReturnValue({
      user: { firstName: "Sam", lastName: "Agent", roles: ["SUPPORT_AGENT"] },
      status: "authenticated",
    });
    renderAt("/login");
    expect(screen.getByText("home")).toBeInTheDocument();
  });

  it("renders the outlet when the user is unauthenticated", () => {
    useUserMock.mockReturnValue({ user: null, status: "unauthenticated" });
    renderAt("/login");
    expect(screen.getByText("login outlet")).toBeInTheDocument();
  });
});
