export function sortByNextDate<
  T extends { nextDate: string | null; name: string },
>(items: T[]): T[] {
  const dated: T[] = [];
  const nulls: T[] = [];
  for (const item of items) {
    if (item.nextDate === null) {
      nulls.push(item);
    } else {
      dated.push(item);
    }
  }
  dated.sort((a, b) => {
    if (a.nextDate === b.nextDate) return a.name.localeCompare(b.name);
    return (a.nextDate as string) < (b.nextDate as string) ? -1 : 1;
  });
  nulls.sort((a, b) => a.name.localeCompare(b.name));
  return [...dated, ...nulls];
}
