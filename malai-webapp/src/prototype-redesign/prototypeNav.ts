import { useSearchParams } from "react-router-dom";

export type PrototypeScreen = "login" | "home" | "pay";

export function usePrototypeNav() {
  const [searchParams, setSearchParams] = useSearchParams();

  const screen = (searchParams.get("screen") ?? "login") as PrototypeScreen;
  const accountUid = searchParams.get("account");

  function navigate(next: PrototypeScreen, account?: string | null) {
    const params = new URLSearchParams(searchParams);
    params.set("screen", next);
    if (account === null) params.delete("account");
    if (account) params.set("account", account);
    setSearchParams(params, { replace: true });
  }

  return { screen, accountUid, navigate };
}

export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function greetingFor(hour: number): string {
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}
