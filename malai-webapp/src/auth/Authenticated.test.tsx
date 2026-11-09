import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  MemoryRouter,
  Route,
  Routes,
  useOutletContext,
} from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { Authenticated } from "./Authenticated";

const useUserMock = vi.fn();
vi.mock("@/auth/useUser", () => ({ useUser: () => useUserMock() }));

const USER: UserResponse = {
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  roles: ["CUSTOMER"],
};

function ContextProbe() {
  const { user } = useOutletContext<{ user: UserResponse }>();
  return <div>session outlet for {user.email}</div>;
}

function renderAt(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<Authenticated />}>
          <Route path="/" element={<ContextProbe />} />
        </Route>
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("Authenticated session guard", () => {
  beforeEach(() => {
    useUserMock.mockReset();
  });

  it("renders a busy skeleton while the session is unknown", () => {
    useUserMock.mockReturnValue({ user: null, status: "unknown" });
    renderAt("/");
    expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText(/session outlet/)).not.toBeInTheDocument();
  });

  it("redirects to login when signed out", () => {
    useUserMock.mockReturnValue({ user: null, status: "unauthenticated" });
    renderAt("/");
    expect(screen.getByText("login page")).toBeInTheDocument();
  });

  it("renders the outlet with the user in context and no surface chrome", () => {
    useUserMock.mockReturnValue({ user: USER, status: "authenticated" });
    renderAt("/");
    expect(
      screen.getByText("session outlet for ada@example.com"),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("app-header")).not.toBeInTheDocument();
    expect(screen.queryByTestId("bottom-tab-bar")).not.toBeInTheDocument();
  });
});
