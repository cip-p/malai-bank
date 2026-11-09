import { Suspense } from "react";
import { Outlet, useOutletContext } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { AppShell } from "@/components/AppShell";
import { RouteFallback } from "@/components/RouteFallback";
import { AccessPage } from "@/pages/AccessPage";
import { isSurfaceOffered } from "./surfaceOffering";

type SessionOutletContext = { user: UserResponse };

export function BankingSurface() {
  const { user } = useOutletContext<SessionOutletContext>();

  if (!isSurfaceOffered(user.roles, "banking")) {
    return <AccessPage roles={user.roles} />;
  }

  return (
    <AppShell user={user}>
      <Suspense fallback={<RouteFallback />}>
        <Outlet context={{ user }} />
      </Suspense>
    </AppShell>
  );
}
