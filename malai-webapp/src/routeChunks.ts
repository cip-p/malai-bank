import { lazy, type ComponentType, type LazyExoticComponent } from "react";

type NamedModule<K extends string> = Record<K, ComponentType>;

export function lazyNamed<K extends string>(
  load: () => Promise<NamedModule<K>>,
  name: K,
): LazyExoticComponent<ComponentType> {
  return lazy(() => load().then((module) => ({ default: module[name] })));
}

type EntryLoader = () => Promise<unknown>;

const ENTRY_LOADERS: Record<string, EntryLoader> = {
  login: () => import("@/pages/LoginPage"),
  register: () => import("@/pages/RegisterPage"),
  verify: () => import("@/pages/VerifyPage"),
  accounts: () => import("@/pages/AccountDetailPage"),
  cards: () => import("@/pages/CardsPage"),
  move: () => import("@/pages/move/TransferPage"),
  profile: () => import("@/pages/ProfilePage"),
  settings: () => import("@/pages/SettingsPage"),
  sandbox: () => import("@/pages/sandbox/CounterpartiesPage"),
  ops: () => import("@/pages/ops/WorklistPage"),
  exec: () => import("@/pages/exec/ExecDashboardPage"),
};

export function entrySegmentOf(pathname: string): string {
  return pathname.split("/").find((segment) => segment !== "") ?? "";
}

export function warmEntryChunk(pathname: string): Promise<unknown> | null {
  const load = ENTRY_LOADERS[entrySegmentOf(pathname)];
  return load ? load() : null;
}
