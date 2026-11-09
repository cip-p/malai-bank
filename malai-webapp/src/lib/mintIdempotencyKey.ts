export function mintIdempotencyKey(): string {
  return crypto.randomUUID();
}
