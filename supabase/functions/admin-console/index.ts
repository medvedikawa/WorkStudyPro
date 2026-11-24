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
    if (action === "get_algorithm_config") {
      const { data, error } = await serviceClient
        .from("app_config")
        .select("value,updated_at")
        .eq("key", "algorithm_weights")
        .maybeSingle();
      if (error) throw error;
      return jsonResponse({
        config: data?.value ?? {
          weights: {
            deadline_proximity: 35,
            task_performance: 20,
            group_work: 15,
            laboratory: 10,
            assignment: 10,
            work_shift_collision: 10,
          },
          thresholds: { moderate_load: 60, high_load: 85 },
        },
        updated_at: data?.updated_at ?? null,
      });
    }

    if (action === "publish_algorithm_config") {
      const config = payload.config;
      if (!config || typeof config !== "object" || Array.isArray(config)) {
        return jsonResponse({ error: "Invalid algorithm configuration." }, 400);
      }
      const candidate = config as Record<string, unknown>;
      const weights = candidate.weights;
      const thresholds = candidate.thresholds;
      if (!weights || typeof weights !== "object" || Array.isArray(weights)
          || !thresholds || typeof thresholds !== "object" || Array.isArray(thresholds)) {
        return jsonResponse({ error: "Invalid algorithm configuration." }, 400);
      }
      const weightValues = weights as Record<string, unknown>;
      const thresholdValues = thresholds as Record<string, unknown>;
      const weightKeys = [
        "deadline_proximity", "task_performance", "group_work", "laboratory",
        "assignment", "work_shift_collision",
      ];
      const parsedWeights: Record<string, number> = {};
      for (const key of weightKeys) {
        const value = weightValues[key];
        if (typeof value !== "number" || !Number.isInteger(value) || value < 0 || value > 100) {
          return jsonResponse({ error: "Each algorithm weight must be a whole number from 0 to 100." }, 400);
        }
        parsedWeights[key] = value;
      }
      if (Object.values(parsedWeights).reduce((total, value) => total + value, 0) !== 100) {
        return jsonResponse({ error: "Algorithm weights must total 100." }, 400);
      }
      const moderateLoad = thresholdValues.moderate_load;
      const highLoad = thresholdValues.high_load;
      if (typeof moderateLoad !== "number" || !Number.isInteger(moderateLoad)
          || moderateLoad < 10 || moderateLoad > 95
          || typeof highLoad !== "number" || !Number.isInteger(highLoad)
          || highLoad < 20 || highLoad > 100 || moderateLoad >= highLoad) {
        return jsonResponse({ error: "Load thresholds must be valid and increasing." }, 400);
      }

      const publishedConfig = {
        weights: parsedWeights,
        thresholds: { moderate_load: moderateLoad, high_load: highLoad },
      };
      const { data, error } = await serviceClient
        .from("app_config")
        .upsert({
          key: "algorithm_weights",
          value: publishedConfig,
          updated_at: new Date().toISOString(),
        }, { onConflict: "key" })
        .select("updated_at")
        .single();
      if (error) throw error;
      let auditLogged = true;
      try {
        await log("algorithm_weights_published", {
          weights: parsedWeights,
          thresholds: publishedConfig.thresholds,
          updated_at: data.updated_at,
        });
      } catch (auditError) {
        auditLogged = false;
        console.error("Algorithm settings were saved, but audit logging failed.", auditError);
      }
      return jsonResponse({
        success: true,
        config: publishedConfig,
        updated_at: data.updated_at,
        audit_logged: auditLogged,
      });
    }

    if (action === "list_users") {
      const { data, error } = await serviceClient.auth.admin.listUsers({ page: 1, perPage: 100 });
      if (error) throw error;
      const authUsers = data.users.filter((u) => u.app_metadata?.role !== "superadmin");
      const ids = authUsers.map((u) => u.id);
      const { data: profiles, error: profilesError } = ids.length
        ? await serviceClient.from("profiles").select("id,full_name,course,is_active").in("id", ids)
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
            course: p?.course ?? "",
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
      await log(isActive ? "user_reactivated" : "user_deactivated", { target_user_id: userId });
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
      const userId = targetId();
      if (!userId) return jsonResponse({ error: "Invalid request." }, 400);
      if (userId === caller.id) return jsonResponse({ error: "You cannot delete your own account." }, 400);
      const { error } = await serviceClient.auth.admin.deleteUser(userId);
      if (error) throw error;
      await log("user_deleted", { target_user_id: userId });
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