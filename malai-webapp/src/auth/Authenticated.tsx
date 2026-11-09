import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useUser } from "./useUser";

export function Authenticated() {
  const { status, user } = useUser();
  const location = useLocation();

  if (status === "unknown") {
    return (
      <main
        className="flex h-full items-center justify-center"
        aria-busy="true"
      >
        <div className="text-muted-foreground">Loading…</div>
      </main>
    );
  }

  if (status === "unauthenticated" || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet context={{ user }} />;
}
