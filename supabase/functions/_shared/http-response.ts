/** Build the webhook's text response without violating 204's null-body rule. */
export function plainTextResponse(text: string, status: number): Response {
  return new Response(status === 204 ? null : text, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "text/plain; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
