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

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userResult, error: userError } = await userClient.auth.getUser();
  const caller = userResult.user;
  if (userError || !caller) return jsonResponse({ error: "Unauthorized." }, 401);

  const serviceClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: callerProfile, error: roleError } = await serviceClient
    .from("profiles")
    .select("role,is_active")
    .eq("id", caller.id)
    .maybeSingle();
  if (roleError) {
    console.error("Admin role lookup failed", roleError);
    return jsonResponse({ error: "Unable to verify access." }, 500);
  }
  const isAdmin = callerProfile?.role === "admin" && callerProfile.is_active !== false;
  if (action === "check_role") return jsonResponse({ is_admin: isAdmin });
  if (!isAdmin) return jsonResponse({ error: "Forbidden." }, 403);

  try {
    if (action === "list_users") {
      const { data, error } = await serviceClient.auth.admin.listUsers({ page: 1, perPage: 100 });
      if (error) throw error;
      const authUsers = data.users;
      const ids = authUsers.map((user) => user.id);
      const { data: profiles, error: profilesError } = ids.length
        ? await serviceClient.from("profiles").select("id,full_name,course,role,is_active").in("id", ids)
        : { data: [], error: null };
      if (profilesError) throw profilesError;
      const byId = new Map((profiles ?? []).map((profile) => [profile.id, profile]));
      return jsonResponse({
        users: authUsers.map((user) => {
          const profile = byId.get(user.id);
          return {
            id: user.id,
            email: user.email ?? "",
            full_name: profile?.full_name ?? "",
            course: profile?.course ?? "",
            role: profile?.role ?? "user",
            is_active: profile?.is_active ?? !user.banned_until,
          };
        }),
      });
    }

    if (action === "set_user_active") {
      const userId = payload.user_id;
      const isActive = payload.is_active;
      if (typeof userId !== "string" || typeof isActive !== "boolean") {
        return jsonResponse({ error: "Invalid request." }, 400);
      }
      if (!isActive && userId === caller.id) {
        return jsonResponse({ error: "You cannot deactivate your own account." }, 400);
      }

      const { error: profileError } = await serviceClient
        .from("profiles")
        .upsert({ id: userId, is_active: isActive }, { onConflict: "id" });
      if (profileError) throw profileError;

      const { error: authError } = await serviceClient.auth.admin.updateUserById(userId, {
        ban_duration: isActive ? "none" : "876000h",
      });
      if (authError) {
        await serviceClient.from("profiles").update({ is_active: !isActive }).eq("id", userId);
        throw authError;
      }

      const { error: logError } = await serviceClient.from("admin_debug_logs").insert({
        actor_id: caller.id,
        event: isActive ? "user_reactivated" : "user_deactivated",
        details: { target_user_id: userId },
      });
      if (logError) throw logError;
      return jsonResponse({ success: true });
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
