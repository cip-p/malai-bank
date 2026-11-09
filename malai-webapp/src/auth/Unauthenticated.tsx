import { Suspense } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { landingPath } from "@/surfaces/surfaceOffering";
import { useUser } from "./useUser";

export function Unauthenticated() {
  const { status, user } = useUser();

  if (status === "unknown") {
    return <LoadingSession />;
  }

  if (status === "authenticated") {
    return <Navigate to={landingPath(user?.roles ?? [])} replace />;
  }

  return (
    <Suspense fallback={<LoadingSession />}>
      <Outlet />
    </Suspense>
  );
}

function LoadingSession() {
  return (
    <main className="flex h-full items-center justify-center" aria-busy="true">
      <div className="text-muted-foreground">Loading…</div>
    </main>
  );
}
