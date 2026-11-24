import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

// Safe to decode without verifying: getUser() below has already validated the token.
function jwtClaims(authorization: string): Record<string, unknown> {
  try {
    const token = authorization.slice("Bearer ".length);
    const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = part + "=".repeat((4 - (part.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return {};
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed." }, 405);

  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return jsonResponse({ error: "Unauthorized." }, 401);

  let payload: Record<string, unknown>;
  try {
    payload = await request.json();
  } catch {
    return jsonResponse({ error: "Invalid request." }, 400);
  }
  const action = payload.action;
  if (typeof action !== "string") return jsonResponse({ error: "Invalid request." }, 400);

  // 1. Authenticate the caller from their own token.
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userResult, error: userError } = await userClient.auth.getUser();
  const caller = userResult?.user;
  if (userError || !caller) return jsonResponse({ error: "Unauthorized." }, 401);

  // 2. Superadmin role lives in app_metadata, which only the service role can write.
  const isSuperadmin = caller.app_metadata?.role === "superadmin";
  const mfaVerified = jwtClaims(authorization).aal === "aal2";

  // Lets the app decide whether to show the admin UI and/or prompt for the authenticator code.
  if (action === "check_role") {
    return jsonResponse({ is_admin: isSuperadmin, mfa_verified: mfaVerified });
  }
  if (!isSuperadmin) return jsonResponse({ error: "Forbidden." }, 403);
  if (!mfaVerified) return jsonResponse({ error: "MFA required.", mfa_required: true }, 403);

  const serviceClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const log = async (event: string, details: Record<string, unknown>) => {
    const { error } = await serviceClient
      .from("admin_debug_logs")
      .insert({ actor_id: caller.id, event, details });
    if (error) throw error;
  };

  const targetId = (): string | null =>
    typeof payload.user_id === "string" ? payload.user_id : null;

  try {
    if (action === "list_users") {
      const { data, error } = await serviceClient.auth.admin.listUsers({ page: 1, perPage: 100 });
      if (error) throw error;
      const authUsers = data.users.filter((u) => u.app_metadata?.role !== "superadmin");
      const ids = authUsers.map((u) => u.id);
      const { data: profiles, error: profilesError } = ids.length
        ? await serviceClient.from("profiles").select("id,full_name,is_active").in("id", ids)
        : { data: [], error: null };
      if (profilesError) throw profilesError;
      const byId = new Map((profiles ?? []).map((p) => [p.id, p]));
      return jsonResponse({
        users: authUsers.map((u) => {
          const p = byId.get(u.id);
          return {
            id: u.id,
            email: u.email ?? "",
            full_name: p?.full_name ?? "",
            course: "",
            role: "user",
            is_active: p?.is_active ?? !u.banned_until,
          };
        }),
      });
    }

    if (action === "set_user_active") {
      const userId = targetId();
      const isActive = payload.is_active;
      if (!userId || typeof isActive !== "boolean") return jsonResponse({ error: "Invalid request." }, 400);
      if (userId === caller.id) return jsonResponse({ error: "You cannot change your own account." }, 400);

      const { error: profileError } = await serviceClient
        .from("profiles")
        .update({ is_active: isActive })
        .eq("id", userId);
      if (profileError) throw profileError;

      const { error: authError } = await serviceClient.auth.admin.updateUserById(userId, {
        ban_duration: isActive ? "none" : "876000h",
      });
      if (authError) {
        await serviceClient.from("profiles").update({ is_active: !isActive }).eq("id", userId);
        throw authError;
      }
      await log(isActive ? "user_restored" : "user_archived", { target_user_id: userId });
      return jsonResponse({ success: true });
    }

    if (action === "send_password_reset") {
      const userId = targetId();
      if (!userId) return jsonResponse({ error: "Invalid request." }, 400);
      const { data, error } = await serviceClient.auth.admin.getUserById(userId);
      if (error || !data.user?.email) throw error ?? new Error("User has no email");
      const { error: resetError } = await serviceClient.auth.resetPasswordForEmail(data.user.email);
      if (resetError) throw resetError;
      await log("password_reset_sent", { target_user_id: userId });
      return jsonResponse({ success: true });
    }

    if (action === "set_user_password") {
      const userId = targetId();
      const newPassword = payload.new_password;
      if (!userId || typeof newPassword !== "string") return jsonResponse({ error: "Invalid request." }, 400);
      if (newPassword.length < 8) return jsonResponse({ error: "Password must be at least 8 characters." }, 400);
      if (userId === caller.id) return jsonResponse({ error: "You cannot change your own password here." }, 400);
      const { error } = await serviceClient.auth.admin.updateUserById(userId, { password: newPassword });
      if (error) throw error;
      // Never log the password itself.
      await log("password_set", { target_user_id: userId });
      return jsonResponse({ success: true });
    }

    if (action === "delete_user") {
      // Panel feedback: admins must not delete accounts that hold user data.
      return jsonResponse({ error: "Account deletion is disabled. Archive the account instead." }, 403);
    }

    if (action === "debug_logs") {
      const { data, error } = await serviceClient
        .from("admin_debug_logs")
        .select("actor_id,event,details,created_at")
        .order("created_at", { ascending: false })
        .limit(100);
      if (error) throw error;
      return jsonResponse({ logs: data ?? [] });
    }

    return jsonResponse({ error: "Unknown action." }, 400);
  } catch (error) {
    console.error("Admin function request failed", error);
    return jsonResponse({ error: "Unable to complete the admin request." }, 500);
  }
});