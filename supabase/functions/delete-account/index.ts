import { createClient } from "@supabase/supabase-js";
import { createDeleteAccountHandler } from "./handler.ts";

const handler = createDeleteAccountHandler({
  getEnv: (name) => Deno.env.get(name),
  fetch: (input, init) => fetch(input, init),
  createClient: (url, key) =>
    createClient(url, key, {
      auth: { persistSession: false, autoRefreshToken: false },
    }),
  logError: (message) => console.error(message),
});

Deno.serve(handler);
