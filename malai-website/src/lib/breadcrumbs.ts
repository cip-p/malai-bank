import { footer } from "../content/footer";
import { nav } from "../content/nav";

function normalise(path: string): string {
  const trimmed = path.replace(/\/+$/, "");
  return trimmed === "" ? "/" : trimmed;
}

function isInternal(href: string): boolean {
  return href.startsWith("/") && !href.startsWith("//");
}

const labelsByPath: ReadonlyMap<string, string> = new Map(
  [...nav.anchors, ...footer.columns.flatMap((column) => column.links)]
    .filter((link) => isInternal(link.href))
    .map((link) => [normalise(link.href), link.label] as const),
);

export function breadcrumbLabelFor(pathname: string): string | undefined {
  const path = normalise(pathname);
  return path === "/" ? undefined : labelsByPath.get(path);
}
