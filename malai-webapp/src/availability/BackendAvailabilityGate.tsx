import type { ReactNode } from "react";
import { ServicePausedPage } from "@/pages/ServicePausedPage";
import { useBackendAvailability } from "./useBackendAvailability";

type Props = {
  children: ReactNode;
};

export function BackendAvailabilityGate({ children }: Props) {
  const { status, rechecking, recheck } = useBackendAvailability();

  if (status === "unreachable") {
    return <ServicePausedPage rechecking={rechecking} onRetry={recheck} />;
  }

  return <>{children}</>;
}
