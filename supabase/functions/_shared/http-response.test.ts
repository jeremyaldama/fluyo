import { assertEquals } from "jsr:@std/assert@1";
import { plainTextResponse } from "./http-response.ts";

Deno.test("Pub/Sub 204 ACK has a null body and is constructible", async () => {
  const response = plainTextResponse("", 204);
  assertEquals(response.status, 204);
  assertEquals(response.body, null);
  assertEquals(await response.text(), "");
});

Deno.test("non-204 text responses preserve their safe body", async () => {
  const response = plainTextResponse("retry", 503);
  assertEquals(response.status, 503);
  assertEquals(await response.text(), "retry");
});
