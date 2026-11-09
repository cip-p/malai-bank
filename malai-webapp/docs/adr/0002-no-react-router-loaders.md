# No React Router loaders; components own data via TanStack Query

React Router 7 ships with a data-router API (route `loader` functions that block navigation until data resolves). We do not use it — every protected page subscribes to its data via TanStack Query hooks from inside the rendered component, suspending or rendering a skeleton while data loads. Navigation is instant; data resolves after the route renders.

Combining loaders with TanStack Query forces every page to split its data flow across two coordinated pieces (the loader prefetches, the component re-reads) and forces the auth gate to be applied in both places. Trading that complexity for "no skeleton flash on navigation" is not worth it — modern banking UIs all show transition skeletons, and the consistency of one read pattern across the app outweighs the polish.

See also: ADR-0001 (TanStack Query as the source of auth state).
