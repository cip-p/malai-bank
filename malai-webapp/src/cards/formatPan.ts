const GROUP_SIZE = 4;

export function formatPanGroups(pan: string): string[] {
  const dotted = pan.replace(/\*/g, "•");
  const groups: string[] = [];
  for (let i = 0; i < dotted.length; i += GROUP_SIZE) {
    groups.push(dotted.slice(i, i + GROUP_SIZE));
  }
  return groups;
}

export function lastFourOf(maskedPan: string): string {
  return maskedPan.slice(-4);
}
