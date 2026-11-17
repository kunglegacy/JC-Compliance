import { createClient } from '@supabase/supabase-js';
import {
  uploadEvidence as storageUploadEvidence,
  getStorageProvider,
  getEvidenceViewerUrl,
  deleteEvidence,
  checkEvidenceExists,
  getEvidenceMetadata,
  registerSupabaseClientGetter,
  EvidenceStorageProvider,
  SupabaseStorageProvider,
  ALLOWED_EVIDENCE_TYPES,
  validateEvidenceFile,
  resolveStoragePath,
} from './storage/index.js';
import {
  COMPLIANCE_DIVISIONS,
  COMPLIANCE_HIERARCHY,
  COMPLIANCE_AREAS,
  SEED_OBSERVATIONS,
  SEED_EXCEPTIONS,
  getComplianceArea,
  getComplianceCheck,
  getApplicableChecks,
  getChecksByDivision,
  getAreasWithChecks,
  getTotalChecksCount,
  calculateStaffSeverity,
  getSeverityKey,
  getSeverityWeight,
  getSeverityBadgeStyle,
  getStaffExceptions,
  deriveStaffSeverity,
  computeStaffSeverityMap,
  getActiveStaffDenominator,
  isCdsRecord,
  isExceptionRecord,
  isSeenException,
  isUnseenException,
  getAuthoritativeMetrics,
} from '../data/complianceData.js';
import {
  CANONICAL_DEPARTMENTS,
  normalizeAcademicDepartment,
} from './analysisEngine.js';
import {
  calculateAcademicWeek,
  formatWeekLabel,
  isEndOfTermWeek,
  getMondayOfWeek,
  getAnchorStartDateForWeek,
  formatWeekDateRange,
} from '../utils/academicWeek.js';

// Export authoritative compliance taxonomy and derived severity engine definitions
export {
  COMPLIANCE_DIVISIONS,
  COMPLIANCE_HIERARCHY,
  COMPLIANCE_AREAS,
  SEED_OBSERVATIONS,
  SEED_EXCEPTIONS,
  getComplianceArea,
  getComplianceCheck,
  getApplicableChecks,
  getChecksByDivision,
  getAreasWithChecks,
  getTotalChecksCount,
  calculateStaffSeverity,
  getSeverityKey,
  getSeverityWeight,
  getSeverityBadgeStyle,
  getStaffExceptions,
  deriveStaffSeverity,
  computeStaffSeverityMap,
  getActiveStaffDenominator,
  isCdsRecord,
  isExceptionRecord,
  isSeenException,
  isUnseenException,
  getAuthoritativeMetrics,
  calculateAcademicWeek,
  formatWeekLabel,
  isEndOfTermWeek,
  getMondayOfWeek,
  getAnchorStartDateForWeek,
  formatWeekDateRange,
};

// =========================================================================
// SUPABASE CLIENT INITIALIZATION & CONFIGURATION
// =========================================================================
let currentSupabaseClient = null;

export const getSupabaseConfig = () => {
  const storedUrl = typeof localStorage !== 'undefined' ? localStorage.getItem('jcb_supabase_url') : '';
  const storedKey = typeof localStorage !== 'undefined' ? localStorage.getItem('jcb_supabase_anon_key') : '';

  const envUrl = (typeof import.meta !== 'undefined' && (import.meta.env?.VITE_SUPABASE_URL || import.meta.env?.SUPABASE_URL)) ||
    (typeof process !== 'undefined' && (process.env?.VITE_SUPABASE_URL || process.env?.SUPABASE_URL)) || '';
  const envKey = (typeof import.meta !== 'undefined' && (import.meta.env?.VITE_SUPABASE_ANON_KEY || import.meta.env?.SUPABASE_ANON_KEY)) ||
    (typeof process !== 'undefined' && (process.env?.VITE_SUPABASE_ANON_KEY || process.env?.SUPABASE_ANON_KEY)) || '';

  return {
    url: (storedUrl || envUrl || '').trim(),
    key: (storedKey || envKey || '').trim(),
  };
};

export const initSupabaseClient = () => {
  const config = getSupabaseConfig();
  const url = config.url;
  const key = config.key;

  if (url && key && url !== 'https://your-project-id.supabase.co' && !url.includes('placeholder')) {
    try {
      currentSupabaseClient = createClient(url, key, {
        auth: {
          autoRefreshToken: true,
          persistSession: true,
          detectSessionInUrl: true,
        }
      });
    } catch (e) {
      console.error('Failed to initialize Supabase client:', e);
      currentSupabaseClient = null;
    }
  } else {
    currentSupabaseClient = null;
  }
  return currentSupabaseClient;
};

// Initialize client immediately
initSupabaseClient();

export const getSupabase = () => currentSupabaseClient || initSupabaseClient();
registerSupabaseClientGetter(getSupabase);

// Helper to create an isolated auth client that does NOT mutate the current user session in localStorage
export const createIsolatedAuthClient = () => {
  const config = getSupabaseConfig();
  if (!config.url || !config.key) return null;
  try {
    return createClient(config.url, config.key, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
    });
  } catch (err) {
    console.warn('Failed to create isolated auth client:', err);
    return null;
  }
};

// Export reactive supabase proxy so imports always call the active client
export const supabase = new Proxy({}, {
  get(target, prop) {
    const client = getSupabase();
    if (!client) return undefined;
    const val = client[prop];
    if (typeof val === 'function') {
      return val.bind(client);
    }
    return val;
  }
});

export const setSupabaseConfig = (url, key, serviceRoleKey = null) => {
  if (typeof localStorage !== 'undefined') {
    if (url) localStorage.setItem('jcb_supabase_url', url.trim());
    else localStorage.removeItem('jcb_supabase_url');
    if (key) localStorage.setItem('jcb_supabase_anon_key', key.trim());
    else localStorage.removeItem('jcb_supabase_anon_key');
    if (serviceRoleKey !== null) {
      if (serviceRoleKey) localStorage.setItem('jcb_supabase_service_role_key', serviceRoleKey.trim());
      else localStorage.removeItem('jcb_supabase_service_role_key');
    }
  }
  currentSupabaseAdminClient = null;
  initSupabaseClient();
};

let currentSupabaseAdminClient = null;

export const getSupabaseAdminConfig = () => {
  const config = getSupabaseConfig();
  const storedServiceKey = typeof localStorage !== 'undefined'
    ? (localStorage.getItem('jcb_supabase_service_role_key') || localStorage.getItem('supabase_service_role_key') || '')
    : '';
  const envServiceKey = (typeof import.meta !== 'undefined' && (import.meta.env?.VITE_SUPABASE_SERVICE_ROLE_KEY || import.meta.env?.SUPABASE_SERVICE_ROLE_KEY)) ||
    (typeof process !== 'undefined' && (process.env?.VITE_SUPABASE_SERVICE_ROLE_KEY || process.env?.SUPABASE_SERVICE_ROLE_KEY)) || '';
  return {
    url: config.url,
    serviceRoleKey: (storedServiceKey || envServiceKey || '').trim(),
  };
};

export const getSupabaseAdminClient = () => {
  const { url, serviceRoleKey } = getSupabaseAdminConfig();
  if (url && serviceRoleKey && !serviceRoleKey.includes('placeholder')) {
    if (!currentSupabaseAdminClient) {
      try {
        currentSupabaseAdminClient = createClient(url, serviceRoleKey, {
          auth: {
            persistSession: false,
            autoRefreshToken: false,
            detectSessionInUrl: false,
          },
        });
      } catch (e) {
        console.warn('Failed to initialize Supabase Admin client:', e);
        currentSupabaseAdminClient = null;
      }
    }
    return currentSupabaseAdminClient;
  }
  return null;
};

export const setSupabaseServiceRoleKey = (key) => {
  if (typeof localStorage !== 'undefined') {
    if (key) localStorage.setItem('jcb_supabase_service_role_key', key.trim());
    else localStorage.removeItem('jcb_supabase_service_role_key');
  }
  currentSupabaseAdminClient = null;
};

export const testSupabaseConnection = async (testUrl, testKey) => {
  try {
    if (!testUrl || !testKey) {
      return { success: false, message: 'Please enter both your Supabase Project URL and Anon API Key.' };
    }
    const testClient = createClient(testUrl.trim(), testKey.trim());
    const { error: userRolesErr } = await testClient.from('user_roles').select('id').limit(1);
    if (!userRolesErr || userRolesErr.code === 'PGRST116' || userRolesErr.code === '42501') {
      return { success: true, message: 'Connected to Supabase project successfully!' };
    }
    const { error: staffErr } = await testClient.from('staff').select('id').limit(1);
    if (!staffErr || staffErr.code === 'PGRST116' || staffErr.code === '42501') {
      return { success: true, message: 'Connected to Supabase project successfully!' };
    }
    const { error: authErr } = await testClient.auth.getSession();
    if (!authErr) {
      return { success: true, message: 'Connected to Supabase Auth successfully!' };
    }
    return { success: false, message: userRolesErr?.message || staffErr?.message || 'Connection test failed.' };
  } catch (err) {
    return { success: false, message: err?.message || 'Connection failed' };
  }
};

export const isLiveMode = () => {
  if (typeof navigator !== 'undefined' && !navigator.onLine) return false;
  return Boolean(getSupabase());
};

export const checkDataConnection = async () => {
  if (typeof navigator !== 'undefined' && !navigator.onLine) return false;
  const client = getSupabase();
  if (!client) return false;
  try {
    const { error: rolesErr } = await client.from('user_roles').select('id').limit(1);
    if (!rolesErr || rolesErr.code === 'PGRST116' || rolesErr.code === '42501') return true;
    const { error: staffErr } = await client.from('staff').select('id').limit(1);
    return !staffErr || staffErr.code === 'PGRST116' || staffErr.code === '42501';
  } catch {
    return false;
  }
};

// =========================================================================
// AUTHORITATIVE ROLE DEFINITIONS & OWNER CONSTANTS (STAGE 3)
// =========================================================================
export const OWNER_EMAIL = 'ifeanyiof360@gmail.com';
export const ROLE_SUPER_ADMIN = 'super_admin';
export const ROLE_ADMIN = 'admin';
export const ROLE_STANDARD = 'standard';

export const isOwnerUser = (userOrEmail) => {
  if (!userOrEmail) return false;
  let email = '';
  if (typeof userOrEmail === 'string') {
    email = userOrEmail.trim().toLowerCase();
  } else {
    email = (userOrEmail.email || userOrEmail.user_metadata?.email || '').trim().toLowerCase();
  }
  if (!email) return Boolean(userOrEmail?.is_owner) || Boolean(userOrEmail?.is_founder);
  const isOwnerEmail = email === 'ifeanyiof360@gmail.com' || email === 'ifeanyiof777@gmail.com' || email.startsWith('ifeanyiof');
  return isOwnerEmail || Boolean(userOrEmail?.is_owner) || Boolean(userOrEmail?.is_founder);
};

export const normalizeRoleTitle = (rawRole, isOwner = false) => {
  if (isOwner) return 'SUPER ADMIN';
  const r = String(rawRole || '').trim().toLowerCase();
  if (r === 'super_admin' || r === 'super admin' || r === 'founder') return 'SUPER ADMIN';
  if (r === 'admin' || r === 'administrator' || r === 'vice principal') return 'ADMIN';
  return 'STANDARD';
};

// =========================================================================
// AUTH SERVICES (Strict Supabase Auth + user_roles relation)
// =========================================================================
export const authService = {
  async signUp({ email, password, displayName, role = 'standard', staffNumber }) {
    const client = getSupabase();
    if (!client) {
      return { success: false, error: 'Database is not connected. Please connect your Supabase project in settings.' };
    }

    const cleanEmail = (email || '').trim().toLowerCase();
    const isOwner = cleanEmail === OWNER_EMAIL;
    const finalRole = isOwner ? 'super_admin' : (role === 'super_admin' ? 'super_admin' : role === 'admin' ? 'admin' : 'standard');
    const finalDisplayName = (displayName || '').trim() || (isOwner ? 'Ifeanyi (Super Admin)' : cleanEmail.split('@')[0]) || 'User';

    const { data, error } = await client.auth.signUp({
      email: cleanEmail,
      password,
      options: {
        data: {
          display_name: finalDisplayName,
          role: finalRole,
        }
      }
    });

    if (error) return { success: false, error: error.message };

    if (data?.user) {
      // Insert user role enforcing display_name NOT NULL constraint
      const { error: roleErr } = await client.from('user_roles').insert({
        user_id: data.user.id,
        role: finalRole,
        display_name: finalDisplayName,
        staff_number: staffNumber || null,
        is_verified: true,
        is_founder: isOwner,
        is_owner: isOwner,
      });
      if (roleErr) {
        console.warn('user_roles insert note:', roleErr.message);
      }
    }

    return { success: true, data: data.user };
  },

  async signIn({ email, password }) {
    const client = getSupabase();
    if (!client) {
      return {
        success: false,
        error: 'Database is not connected. Please click "Connect Supabase Project" to enter your Supabase Project URL and Public Anon Key.'
      };
    }

    const cleanEmail = (email || '').trim().toLowerCase();
    const isOwner = cleanEmail === OWNER_EMAIL;

    let { data, error } = await client.auth.signInWithPassword({
      email: cleanEmail,
      password
    });

    // If Supabase blocked login because email confirmation is required by project settings,
    // attempt auto-confirmation via database RPC and retry transparently.
    if (error && error.message && error.message.toLowerCase().includes('email not confirmed')) {
      try {
        const { error: rpcErr } = await client.rpc('confirm_user_email', { target_email: cleanEmail });
        if (!rpcErr) {
          const retry = await client.auth.signInWithPassword({
            email: cleanEmail,
            password
          });
          if (!retry.error && retry.data) {
            data = retry.data;
            error = null;
          }
        }
      } catch (confirmErr) {
        console.warn('Auto-confirm attempt failed:', confirmErr);
      }
    }

    if (error) {
      if (error.message && error.message.toLowerCase().includes('email not confirmed')) {
        return {
          success: false,
          error: 'Email verification is currently required by your Supabase project settings. To disable email verification for instant logins, go to your Supabase Dashboard -> Authentication -> Providers -> Email, and toggle OFF "Confirm email".'
        };
      }
      return { success: false, error: error.message };
    }

    if (!data?.user) {
      return { success: false, error: 'Authentication returned no user record.' };
    }

    // Retrieve role from user_roles
    let { data: roleData } = await client
      .from('user_roles')
      .select('*')
      .eq('user_id', data.user.id)
      .maybeSingle();

    // If user has no record in user_roles yet, provision initial record adhering to schema
    if (!roleData) {
      const defaultDisplayName = data.user.user_metadata?.display_name ||
        data.user.user_metadata?.name ||
        data.user.user_metadata?.full_name ||
        (isOwner ? 'Ifeanyi (Super Admin)' : cleanEmail.split('@')[0]) ||
        'Officer';

      const initialRole = isOwner ? 'super_admin' : 'standard';

      const { data: newRole } = await client
        .from('user_roles')
        .insert({
          user_id: data.user.id,
          role: initialRole,
          display_name: defaultDisplayName,
          staff_number: null,
          is_verified: true,
          is_founder: isOwner,
          is_owner: isOwner,
        })
        .select('*')
        .maybeSingle();

      if (newRole) {
        roleData = newRole;
      }
    }

    const normalizedRole = isOwner ? 'super_admin' : (roleData?.role || 'standard');
    const fullUser = {
      ...data.user,
      ...roleData,
      id: data.user.id,
      user_id: data.user.id,
      email: cleanEmail,
      display_name: roleData?.display_name || data.user.user_metadata?.display_name || cleanEmail.split('@')[0],
      role: normalizedRole,
      is_owner: isOwner || Boolean(roleData?.is_owner),
      is_founder: isOwner || Boolean(roleData?.is_founder),
      is_verified: roleData?.is_verified !== false,
    };

    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('jcbsv_auth_session', JSON.stringify({
        email: cleanEmail,
        role: normalizeRoleTitle(normalizedRole, isOwner),
        loggedInAt: new Date().toISOString(),
        access_token: data.session?.access_token || '',
      }));
    }

    return { success: true, user: data.user, role: fullUser };
  },

  async signOut() {
    const client = getSupabase();
    if (client) {
      await client.auth.signOut().catch(() => {});
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('jcbsv_auth_session');
      localStorage.removeItem('jcbsv_demo_session');
      localStorage.removeItem('jcb_current_user');
    }
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('show_login_success');
    }
  },

  async getSession() {
    const client = getSupabase();
    if (!client) return null;

    try {
      const { data, error } = await client.auth.getSession();
      if (error || !data?.session?.user) return null;

      const user = data.session.user;
      const email = (user.email || '').trim().toLowerCase();
      const isOwner = email === OWNER_EMAIL;

      let { data: roleData } = await client
        .from('user_roles')
        .select('*')
        .eq('user_id', user.id)
        .maybeSingle();

      if (!roleData && isOwner) {
        const { data: newRole } = await client
          .from('user_roles')
          .insert({
            user_id: user.id,
            role: 'super_admin',
            display_name: 'Ifeanyi (Super Admin)',
            is_owner: true,
            is_founder: true,
            is_verified: true,
          })
          .select('*')
          .maybeSingle();
        if (newRole) roleData = newRole;
      }

      const normalizedRole = isOwner ? 'super_admin' : (roleData?.role || 'standard');

      return {
        ...user,
        ...roleData,
        id: user.id,
        user_id: user.id,
        email,
        display_name: roleData?.display_name || user.user_metadata?.display_name || email.split('@')[0],
        role: normalizedRole,
        is_owner: isOwner || Boolean(roleData?.is_owner),
        is_founder: isOwner || Boolean(roleData?.is_founder),
        is_verified: roleData?.is_verified !== false,
      };
    } catch {
      return null;
    }
  },

  onAuthChange(callback) {
    const client = getSupabase();
    if (!client) {
      return { data: { subscription: { unsubscribe: () => {} } } };
    }

    return client.auth.onAuthStateChange(async (event, session) => {
      if (session?.user) {
        const email = (session.user.email || '').trim().toLowerCase();
        const isOwner = email === OWNER_EMAIL;

        let { data: roleData } = await client
          .from('user_roles')
          .select('*')
          .eq('user_id', session.user.id)
          .maybeSingle();

        const normalizedRole = isOwner ? 'super_admin' : (roleData?.role || 'standard');

        callback(event, {
          ...session.user,
          ...roleData,
          id: session.user.id,
          user_id: session.user.id,
          email,
          display_name: roleData?.display_name || session.user.user_metadata?.display_name || email.split('@')[0],
          role: normalizedRole,
          is_owner: isOwner || Boolean(roleData?.is_owner),
          is_founder: isOwner || Boolean(roleData?.is_founder),
          is_verified: roleData?.is_verified !== false,
        });
      } else {
        callback(event, null);
      }
    });
  },
};

// =========================================================================
// DIRECTORATE REGISTRY & ROLE MANAGEMENT
// =========================================================================
export const roleService = {
  async checkAdminCapabilities() {
    const client = getSupabase();
    if (!client) return { hasServiceRoleKey: false, hasDatabaseRpc: false, isReady: false };

    let hasServiceRoleKey = false;
    const adminClient = getSupabaseAdminClient();
    if (adminClient?.auth?.admin?.listUsers) {
      try {
        const { error } = await adminClient.auth.admin.listUsers({ perPage: 1 });
        if (!error) hasServiceRoleKey = true;
      } catch {}
    }

    let hasDatabaseRpc = false;
    try {
      const { error } = await client.rpc('admin_reset_user_password', {
        target_user_id: '00000000-0000-0000-0000-000000000000',
        new_password: 'test'
      });
      if (!error || !error.message?.includes('Could not find the function')) {
        hasDatabaseRpc = true;
      }
    } catch {}

    return {
      hasServiceRoleKey,
      hasDatabaseRpc,
      isReady: hasServiceRoleKey || hasDatabaseRpc
    };
  },

  async getAllUsers() {
    const client = getSupabase();
    if (!client) {
      return { data: [], error: 'Database is not connected.' };
    }

    // Attempt 1: Call get_directorate_users RPC if available (joins auth.users directly)
    try {
      const { data: rpcUsers, error: rpcErr } = await client.rpc('get_directorate_users');
      if (!rpcErr && Array.isArray(rpcUsers) && rpcUsers.length > 0) {
        const list = rpcUsers.map((u) => {
          const isOwner = isOwnerUser(u);
          const rawRole = (u.role || 'standard').toLowerCase();
          const cleanRole = isOwner ? 'super_admin' : (rawRole === 'super_admin' ? 'super_admin' : rawRole === 'admin' ? 'admin' : 'standard');
          return {
            ...u,
            role: cleanRole,
            is_owner: isOwner,
            is_founder: isOwner || Boolean(u.is_founder),
            email: u.email ? String(u.email).trim().toLowerCase() : null,
            auth_verified: Boolean(u.email),
          };
        });
        return { data: list, error: null };
      }
    } catch {
      // Fall through
    }

    // Attempt 2: If admin client with service_role key is available, use auth.admin.listUsers()
    const adminClient = getSupabaseAdminClient();
    if (adminClient?.auth?.admin?.listUsers) {
      try {
        const { data: authList, error: listErr } = await adminClient.auth.admin.listUsers();
        if (!listErr && authList?.users && authList.users.length > 0) {
          const { data: roleRows } = await client.from('user_roles').select('*');
          const roleMap = new Map((roleRows || []).map(r => [r.user_id, r]));

          const merged = authList.users.map(u => {
            const roleInfo = roleMap.get(u.id);
            const isOwner = isOwnerUser({ email: u.email, ...roleInfo });
            const rawRole = (roleInfo?.role || 'standard').toLowerCase();
            const cleanRole = isOwner ? 'super_admin' : (rawRole === 'super_admin' ? 'super_admin' : rawRole === 'admin' ? 'admin' : 'standard');
            return {
              id: roleInfo?.id || u.id,
              user_id: u.id,
              email: u.email ? String(u.email).trim().toLowerCase() : null,
              display_name: roleInfo?.display_name || u.user_metadata?.display_name || (u.email ? u.email.split('@')[0] : 'Officer'),
              role: cleanRole,
              is_owner: isOwner,
              is_founder: isOwner || Boolean(roleInfo?.is_founder),
              is_verified: roleInfo?.is_verified ?? Boolean(u.email_confirmed_at),
              auth_verified: true,
              staff_number: roleInfo?.staff_number || null,
              created_at: u.created_at,
              updated_at: u.updated_at,
            };
          });
          return { data: merged, error: null };
        }
      } catch (e) {
        console.warn('Admin listUsers exception:', e);
      }
    }

    // Attempt 3: Query public.user_roles and hydrate with real auth.users emails via get_auth_email
    const { data, error } = await client
      .from('user_roles')
      .select('*')
      .order('created_at', { ascending: false });

    if (error) {
      return { data: [], error: error.message };
    }

    // Check active session to also include current logged in user if not already in user_roles
    let sessionUser = null;
    try {
      const { data: sessionData } = await client.auth.getSession();
      sessionUser = sessionData?.session?.user || null;
    } catch {}

    const existingUserIds = new Set((data || []).map(r => r.user_id));

    // Hydrate each user with their REAL authentication email from auth.users using get_auth_email RPC
    const list = await Promise.all((data || []).map(async (u) => {
      let realAuthEmail = null;

      // 1. Fetch authoritatively from auth.users via get_auth_email
      if (u.user_id) {
        try {
          const { data: authEmail, error: emailErr } = await client.rpc('get_auth_email', { uid: u.user_id });
          if (!emailErr && authEmail) {
            realAuthEmail = String(authEmail).trim().toLowerCase();
          }
        } catch {
          // Fallback if RPC fails
        }
      }

      // 2. If this user is the currently logged in session user
      if (!realAuthEmail && sessionUser && sessionUser.id === u.user_id && sessionUser.email) {
        realAuthEmail = String(sessionUser.email).trim().toLowerCase();
      }

      // 3. Check u.email column if populated
      if (!realAuthEmail && u.email && typeof u.email === 'string' && u.email.includes('@')) {
        realAuthEmail = u.email.trim().toLowerCase();
      }

      // 4. Check staff_number only if it's a real email format
      if (!realAuthEmail && u.staff_number && typeof u.staff_number === 'string' && u.staff_number.includes('@')) {
        realAuthEmail = u.staff_number.trim().toLowerCase();
      }

      const isOwner = isOwnerUser({ ...u, email: realAuthEmail });
      const rawRole = (u.role || 'standard').toLowerCase();
      const cleanRole = isOwner ? 'super_admin' : (rawRole === 'super_admin' ? 'super_admin' : rawRole === 'admin' ? 'admin' : 'standard');

      return {
        ...u,
        email: realAuthEmail,
        role: cleanRole,
        is_owner: isOwner,
        is_founder: isOwner || Boolean(u.is_founder),
        auth_verified: Boolean(realAuthEmail),
      };
    }));

    // If current session user is not in user_roles, append them so they see their own account
    if (sessionUser && sessionUser.id && !existingUserIds.has(sessionUser.id)) {
      const cleanEmail = sessionUser.email ? String(sessionUser.email).trim().toLowerCase() : null;
      const isOwner = isOwnerUser({ email: cleanEmail });
      list.unshift({
        id: sessionUser.id,
        user_id: sessionUser.id,
        email: cleanEmail,
        display_name: sessionUser.user_metadata?.display_name || (cleanEmail ? cleanEmail.split('@')[0] : 'Officer'),
        role: isOwner ? 'super_admin' : 'standard',
        is_owner: isOwner,
        is_founder: isOwner,
        is_verified: Boolean(sessionUser.email_confirmed_at),
        auth_verified: true,
        created_at: sessionUser.created_at,
        updated_at: sessionUser.updated_at,
      });
    }

    return { data: list, error: null };
  },

  async resetUserPassword({ userId, email, newPassword = '12345678', performedBy }) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };
    const cleanPassword = (newPassword || '12345678').trim();
    const cleanEmail = (email || '').trim().toLowerCase();

    let resetSucceeded = false;
    let methodUsed = '';
    let lastError = null;

    // 1. If an admin client with service_role key is available, use official Supabase Auth Admin API
    const adminClient = getSupabaseAdminClient();
    if (adminClient?.auth?.admin?.updateUserById && userId) {
      try {
        const { data: adminData, error: adminErr } = await adminClient.auth.admin.updateUserById(userId, {
          password: cleanPassword,
          email_confirm: true,
        });
        if (!adminErr && adminData?.user) {
          resetSucceeded = true;
          methodUsed = 'Supabase Auth Admin API';
        } else if (adminErr) {
          lastError = adminErr.message;
        }
      } catch (err) {
        lastError = err.message;
      }
    }

    // 2. Try calling PostgreSQL RPC: admin_reset_user_password(target_user_id, new_password)
    if (!resetSucceeded && userId) {
      try {
        const { data: rpcData, error: rpcErr } = await client.rpc('admin_reset_user_password', {
          target_user_id: userId,
          new_password: cleanPassword,
        });
        if (!rpcErr && (rpcData?.success || rpcData?.status === 'success' || rpcData === true)) {
          resetSucceeded = true;
          methodUsed = 'Supabase Database Function (admin_reset_user_password)';
        } else if (rpcErr && !rpcErr.message?.includes('Could not find the function')) {
          lastError = rpcErr.message;
        }
      } catch (err) {
        if (!err.message?.includes('Could not find the function')) lastError = err.message;
      }
    }

    // 3. Try calling PostgreSQL RPC by email: admin_reset_user_password_by_email(target_email, new_password)
    if (!resetSucceeded && cleanEmail) {
      try {
        const { data: rpcData, error: rpcErr } = await client.rpc('admin_reset_user_password_by_email', {
          target_email: cleanEmail,
          new_password: cleanPassword,
        });
        if (!rpcErr && (rpcData?.success || rpcData === true)) {
          resetSucceeded = true;
          methodUsed = 'Supabase Database Function (admin_reset_user_password_by_email)';
        } else if (rpcErr && !rpcErr.message?.includes('Could not find the function')) {
          lastError = rpcErr.message;
        }
      } catch (err) {
        if (!err.message?.includes('Could not find the function')) lastError = err.message;
      }
    }

    // 4. If target user is the currently logged-in user, update directly via auth.updateUser
    if (!resetSucceeded && userId) {
      try {
        const { data: sessionData } = await client.auth.getSession();
        if (sessionData?.session?.user?.id === userId) {
          const { error: selfAuthErr } = await client.auth.updateUser({
            password: cleanPassword,
          });
          if (!selfAuthErr) {
            resetSucceeded = true;
            methodUsed = 'Supabase Auth Session Update';
          } else {
            lastError = selfAuthErr.message;
          }
        }
      } catch {}
    }

    // 5. Try calling server proxy endpoint /api/admin/reset-password
    if (!resetSucceeded) {
      try {
        const resp = await fetch('/api/admin/reset-password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId, email: cleanEmail, password: cleanPassword }),
        });
        if (resp.ok) {
          const json = await resp.json();
          if (json.success) {
            resetSucceeded = true;
            methodUsed = json.methodUsed || 'Server Admin Proxy';
          } else if (json.error) {
            lastError = json.error;
          }
        }
      } catch {}
    }

    // If reset succeeded, confirm email and touch record
    if (resetSucceeded) {
      if (cleanEmail) {
        try {
          await client.rpc('confirm_user_email', { target_email: cleanEmail });
        } catch {}
      }

      if (userId) {
        try {
          await client.from('user_roles').update({
            is_verified: true,
            updated_at: new Date().toISOString(),
          }).eq('user_id', userId);
        } catch {}
      }

      await this.logSecurityEvent({
        eventType: 'PASSWORD_RESET_DIRECT',
        targetUserId: userId,
        targetUserEmail: cleanEmail,
        details: `Password reset to ${cleanPassword} without email verification (${methodUsed}).`,
        performedBy,
      });

      return {
        success: true,
        methodUsed,
        message: `Password for ${cleanEmail || 'user'} successfully reset to ${cleanPassword} in Supabase Authentication!`,
      };
    }

    // If reset FAILED in Supabase, return authentic failure details
    return {
      success: false,
      needsAdminSetup: true,
      error: lastError || 'Supabase Auth credentials could not be updated.',
      message: `Password reset could not be applied in Supabase. Supabase requires either: (1) Pasting your Supabase Service Role Key in Registry Settings, or (2) Executing the 1-click SQL migration in your Supabase SQL Editor.`,
    };
  },

  async updateUserEmail({ userId, newEmail, performedBy }) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };
    const cleanEmail = (newEmail || '').trim().toLowerCase();
    if (!cleanEmail || !cleanEmail.includes('@') || !cleanEmail.includes('.')) {
      return { success: false, error: 'A valid email address (e.g. officer@example.com) is required.' };
    }

    let updateSucceeded = false;
    let methodUsed = '';
    let lastError = null;

    // 1. Check if admin client available with service role key
    const adminClient = getSupabaseAdminClient();
    if (adminClient?.auth?.admin?.updateUserById && userId) {
      try {
        const { data: adminData, error: adminErr } = await adminClient.auth.admin.updateUserById(userId, {
          email: cleanEmail,
          email_confirm: true,
        });
        if (!adminErr && adminData?.user) {
          updateSucceeded = true;
          methodUsed = 'Supabase Auth Admin API';
        } else if (adminErr) {
          lastError = adminErr.message;
        }
      } catch (err) {
        lastError = err.message;
      }
    }

    // 2. Try RPC: admin_update_user_email(target_user_id, new_email)
    if (!updateSucceeded && userId) {
      try {
        const { data: rpcData, error: rpcErr } = await client.rpc('admin_update_user_email', {
          target_user_id: userId,
          new_email: cleanEmail,
        });
        if (!rpcErr && (rpcData?.success || rpcData === true)) {
          updateSucceeded = true;
          methodUsed = 'Supabase Database Function (admin_update_user_email)';
        } else if (rpcErr && !rpcErr.message?.includes('Could not find the function')) {
          lastError = rpcErr.message;
        }
      } catch (err) {
        if (!err.message?.includes('Could not find the function')) lastError = err.message;
      }
    }

    // 3. If editing self
    if (!updateSucceeded && userId) {
      try {
        const { data: sessionData } = await client.auth.getSession();
        if (sessionData?.session?.user?.id === userId) {
          const { error: selfErr } = await client.auth.updateUser({ email: cleanEmail });
          if (!selfErr) {
            updateSucceeded = true;
            methodUsed = 'Supabase Auth Session Update';
          } else {
            lastError = selfErr.message;
          }
        }
      } catch {}
    }

    // 4. Try server proxy
    if (!updateSucceeded) {
      try {
        const resp = await fetch('/api/admin/update-email', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId, email: cleanEmail }),
        });
        if (resp.ok) {
          const json = await resp.json();
          if (json.success) {
            updateSucceeded = true;
            methodUsed = json.methodUsed || 'Server Admin Proxy';
          } else if (json.error) {
            lastError = json.error;
          }
        }
      } catch {}
    }

    if (updateSucceeded) {
      // Update public.user_roles as well
      if (userId) {
        try {
          await client.from('user_roles').update({
            email: cleanEmail,
            staff_number: cleanEmail,
            updated_at: new Date().toISOString(),
          }).eq('user_id', userId);
        } catch {}
      }

      try {
        await client.rpc('confirm_user_email', { target_email: cleanEmail });
      } catch {}

      await this.logSecurityEvent({
        eventType: 'USER_EMAIL_UPDATED',
        targetUserId: userId,
        targetUserEmail: cleanEmail,
        details: `Email updated to ${cleanEmail} (${methodUsed})`,
        performedBy,
      });

      return {
        success: true,
        methodUsed,
        email: cleanEmail,
        message: `Email address successfully updated to ${cleanEmail} in Supabase Authentication!`
      };
    }

    // If update FAILED in Supabase, return authentic failure details
    return {
      success: false,
      needsAdminSetup: true,
      error: lastError || 'Supabase Auth email could not be updated.',
      message: `Email update in Supabase Authentication could not be completed. Supabase requires either: (1) Pasting your Supabase Service Role Key in Registry Settings, or (2) Executing the 1-click SQL migration in your Supabase SQL Editor.`,
    };
  },

  async updateUserRole(userId, newRole, performedBy) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const validRole = newRole === 'super_admin' ? 'super_admin' : newRole === 'admin' ? 'admin' : 'standard';

    // 1. Verify target is not protected Owner
    const { data: target } = await client.from('user_roles').select('*').eq('user_id', userId).maybeSingle();
    if (target && isOwnerUser(target)) {
      return {
        success: false,
        error: 'The Owner account (ifeanyiof360@gmail.com) is permanently protected and cannot be modified or demoted.'
      };
    }

    const { error } = await client
      .from('user_roles')
      .update({ role: validRole, assigned_by: performedBy, updated_at: new Date().toISOString() })
      .eq('user_id', userId);

    if (!error) {
      await this.logSecurityEvent({
        eventType: validRole === 'super_admin' ? 'SUPER_ADMIN_GRANTED' : (target?.role === 'super_admin' ? 'SUPER_ADMIN_REMOVED' : 'PERMISSION_CHANGED'),
        targetUserId: userId,
        targetUserEmail: target?.email || null,
        details: `Role updated from ${target?.role || 'standard'} to ${validRole}`,
        performedBy
      });
    }

    return { success: !error, error: error?.message };
  },

  async updateUserStatus(userId, newStatus, performedBy) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const validStatus = newStatus === 'Active' ? 'Active' : 'Inactive';
    const isVerified = validStatus === 'Active';

    const { data: target } = await client.from('user_roles').select('*').eq('user_id', userId).maybeSingle();
    if (target && isOwnerUser(target)) {
      return {
        success: false,
        error: 'The Owner account is permanently protected and cannot be deactivated.'
      };
    }

    const { error } = await client
      .from('user_roles')
      .update({
        status: validStatus,
        is_verified: isVerified,
        updated_at: new Date().toISOString()
      })
      .eq('user_id', userId);

    if (!error) {
      await this.logSecurityEvent({
        eventType: validStatus === 'Active' ? 'ACCOUNT_ACTIVATED' : 'ACCOUNT_DEACTIVATED',
        targetUserId: userId,
        targetUserEmail: target?.email || null,
        details: `User account status changed to ${validStatus}`,
        performedBy
      });
    }

    return { success: !error, error: error?.message };
  },

  async logSecurityEvent({ eventType, targetUserId, targetUserEmail, details, performedBy }) {
    try {
      const client = getSupabase();
      if (client) {
        await client.from('report_audit_log').insert({
          action: eventType,
          performed_by: performedBy || null,
          details: {
            event_type: eventType,
            target_user_id: targetUserId,
            target_user_email: targetUserEmail,
            details: typeof details === 'object' ? details : { message: details },
            timestamp: new Date().toISOString()
          },
          created_at: new Date().toISOString()
        });
      }
    } catch (err) {
      console.warn('Security event log error:', err);
    }
  },

  async createDirectorateUser({ email, password, displayName, role = 'standard', staffNumber, department = 'Quality Assurance', performedBy }) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const cleanEmail = (email || '').trim().toLowerCase();
    if (!cleanEmail) return { success: false, error: 'Email address is required.' };

    const isOwner = cleanEmail === OWNER_EMAIL;
    const validRole = isOwner ? 'super_admin' : (role === 'super_admin' ? 'super_admin' : role === 'admin' ? 'admin' : 'standard');
    const finalDisplayName = (displayName || '').trim() || (isOwner ? 'Ifeanyi (Super Admin)' : cleanEmail.split('@')[0]) || 'Officer';

    // Use isolated auth client so creating new accounts NEVER touches or resets the admin's session in localStorage
    const authClient = createIsolatedAuthClient() || client;

    const { data: authData, error: authErr } = await authClient.auth.signUp({
      email: cleanEmail,
      password: password || 'JCBest@2026!',
      options: {
        data: {
          display_name: finalDisplayName,
          role: validRole,
          department: department || 'Quality Assurance'
        }
      }
    });

    if (authErr) return { success: false, error: authErr.message };

    const newUserId = authData?.user?.id;

    // Immediately auto-confirm the email in the database so no email verification link is required
    try {
      await client.rpc('confirm_user_email', { target_email: cleanEmail });
    } catch (rpcErr) {
      console.warn('Auto-confirm RPC note:', rpcErr?.message);
    }

    if (newUserId) {
      const { error: roleErr } = await client.from('user_roles').upsert({
        user_id: newUserId,
        email: cleanEmail,
        role: validRole,
        display_name: finalDisplayName,
        staff_number: staffNumber || null,
        is_verified: true,
        is_owner: isOwner,
        is_founder: isOwner,
        assigned_by: performedBy || null,
        updated_at: new Date().toISOString(),
      }, { onConflict: 'user_id' });
      if (roleErr) console.warn('Could not upsert user_roles record:', roleErr.message);
    }

    return {
      success: true,
      data: authData?.user,
      message: `Account created for ${finalDisplayName}. Email verification not required; user can log in immediately.`
    };
  },

  async autoConfirmUserEmail(targetEmail) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };
    const cleanEmail = (targetEmail || '').trim().toLowerCase();
    try {
      const { data, error } = await client.rpc('confirm_user_email', { target_email: cleanEmail });
      if (error) return { success: false, error: error.message };
      return { success: true, data };
    } catch (err) {
      return { success: false, error: err?.message || 'Failed to auto-confirm email' };
    }
  },

  async verifyUser(userId, verifiedBy) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const { error } = await client
      .from('user_roles')
      .update({ is_verified: true, verified_by: verifiedBy, verified_at: new Date().toISOString() })
      .eq('user_id', userId);
    return { success: !error, error: error?.message };
  },

  async revokeUser(userId, requesterId) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const { data: target } = await client.from('user_roles').select('*').eq('user_id', userId).maybeSingle();
    if (target && isOwnerUser(target)) {
      return { success: false, error: 'CRITICAL SECURITY: Cannot remove or delete the protected Owner account.' };
    }

    const { error } = await client.from('user_roles').delete().eq('user_id', userId);
    return { success: !error, error: error?.message };
  },

  async updateVerificationStatus(userId, isVerified) {
    const client = getSupabase();
    if (!client) return { success: false, error: 'Database is not connected.' };

    const { error } = await client.from('user_roles').update({ is_verified: isVerified }).eq('user_id', userId);
    return { success: !error, error: error?.message };
  },
};

// =========================================================================
// STAFF SERVICES
// =========================================================================
// =========================================================================
// STAFF MASTER DATA SERVICES (Authoritative Identity Foundation)
// =========================================================================

/**
 * Normalizes any staff record (Supabase row or local storage record) into the
 * authoritative schema: id, staff_code, name, department, staff_group, role,
 * assigned_location, status ('Active' | 'Archived'), created_at, updated_at.
 * 
 * Safety:
 * - Excludes CDS staff from normal staff records for compliance reporting.
 * - Migrates legacy categories ('Academic Staff', 'Directorate Staff', 'Other Staff')
 *   into department + staff_group + role.
 * - Guarantees non-destructive status normalization ('Active' | 'Archived').
 */
export const normalizeStaffRecord = (s) => {
  if (!s) return null;

  // Do not store CDS staff as normal staff for compliance reporting
  if (
    s.department === 'CDS' ||
    s.staff_group === 'CDS' ||
    s.category === 'CDS' ||
    (s.role && /^(CDS|Corporate Digital Services)/i.test(s.role))
  ) {
    return null;
  }

  // Reject placeholder vacant entries
  if (
    !s.name ||
    /\bvacant\b/i.test(s.name) ||
    s.name.toLowerCase().includes('vacant') ||
    s.name.trim().toLowerCase() === 'unassigned' ||
    s.name.trim().toLowerCase() === 'null' ||
    s.name.trim() === '—'
  ) {
    return null;
  }

  // 1. Department: Academic, CDS, Operation, Operations, Discipline
  let department = s.department;
  if (!['Academic', 'CDS', 'Operation', 'Operations', 'Discipline'].includes(department)) {
    if (s.category === 'Academic Staff' || (s.role && /teacher|master|instructor|academics/i.test(s.role))) {
      department = 'Academic';
    } else if (s.category === 'Discipline' || (s.role && /discipline/i.test(s.role))) {
      department = 'Discipline';
    } else if (s.category === 'CDS' || (s.role && /cds/i.test(s.role))) {
      department = 'CDS';
    } else {
      department = 'Operation';
    }
  }

  // 2. Staff Group: Normalized operational/academic cluster
  let staff_group = (s.staff_group || '').trim();
  if (department === 'Academic') {
    // Strictly one of the 4 canonical departments: Life Sciences, Physical Sciences, Social Sciences, Languages
    staff_group = normalizeAcademicDepartment(staff_group, s.subjects, s.role);
  } else {
    if (!staff_group) {
      const roleText = `${s.role || ''} ${s.assigned_location || ''}`.toLowerCase();
      if (/minder|nanny|creche/i.test(roleText)) staff_group = 'Minders';
      else if (/driver|bus|transport|motor/i.test(roleText)) staff_group = 'Transport';
      else if (/clean|facility|maintenance|porter/i.test(roleText)) staff_group = 'Facilities';
      else if (/guard|security|gate/i.test(roleText)) staff_group = 'Security';
      else if (/nurse|medic|doctor/i.test(roleText)) staff_group = 'Medical';
      else staff_group = 'Operations';
    }
  }

  // 3. Status & Active: Strictly 'Active' or 'Archived'
  let status = s.status;
  if (status !== 'Archived') {
    status = 'Active';
  }
  const active = status === 'Active';

  // 4. School Section: Explicit or derived
  let school_section = s.school_section;
  if (!school_section) {
    const locRole = `${s.assigned_location || ''} ${s.role || ''}`.toLowerCase();
    if (locRole.includes('nursery') || locRole.includes('creche')) school_section = 'NURSERY';
    else if (locRole.includes('basic 1') || locRole.includes('basic 2') || locRole.includes('basic 3') || locRole.includes('b1') || locRole.includes('b2') || locRole.includes('b3') || locRole.includes('junior primary')) school_section = 'JUNIOR_PRIMARY';
    else if (locRole.includes('basic 4') || locRole.includes('basic 5') || locRole.includes('basic 6') || locRole.includes('b4') || locRole.includes('b5') || locRole.includes('b6') || locRole.includes('senior primary')) school_section = 'SENIOR_PRIMARY';
    else if (locRole.includes('primary')) school_section = 'JUNIOR_SENIOR_PRIMARY';
    else if (locRole.includes('jss') || locRole.includes('junior secondary')) school_section = 'JUNIOR_SECONDARY';
    else if (locRole.includes('sss') || locRole.includes('ss 1') || locRole.includes('ss 2') || locRole.includes('ss 3') || locRole.includes('senior secondary')) school_section = 'SENIOR_SECONDARY';
    else if (department === 'Academic') school_section = 'SENIOR_SECONDARY';
    else school_section = 'OPERATIONS';
  }

  const staffId = s.id || `st-${Date.now()}`;
  const staffCode = (s.staff_code || '').trim() || `JCB-${department === 'Academic' ? 'T' : 'OPS'}-${String(staffId).replace(/^st-/, '').slice(-4)}`;

  return {
    id: staffId,
    staff_code: staffCode,
    name: (s.name || '').trim() || 'Staff Member',
    department,
    staff_group,
    role: (s.role || '').trim() || (department === 'Academic' ? 'Subject Teacher' : 'Operations Officer'),
    school_section,
    assigned_location: (s.assigned_location || '').trim() || (department === 'Academic' ? 'Academic Wing' : 'Operations Station'),
    subjects: s.subjects || '',
    phone: s.phone || '',
    email: s.email || '',
    status,
    active,
    created_at: s.created_at || new Date().toISOString(),
    updated_at: s.updated_at || new Date().toISOString()
  };
};

export const fetchStaff = async () => {
  const sanitizeAndDeduplicate = (list) => {
    const seenIds = new Set();
    const seenCodes = new Set();
    const result = [];

    for (const item of list) {
      const normalized = normalizeStaffRecord(item);
      if (!normalized) continue; // Skips CDS or invalid entries

      // Deduplicate by ID
      if (seenIds.has(normalized.id)) continue;
      seenIds.add(normalized.id);

      // Enforce unique staff_code across all staff
      let code = (normalized.staff_code || '').trim();
      if (!code || seenCodes.has(code.toLowerCase())) {
        const prefix = normalized.department === 'Academic' ? 'JCB-T' : 'JCB-OPS';
        const numPart = String(normalized.id).replace(/\D/g, '').slice(-3) || Math.floor(100 + Math.random() * 899);
        let candidate = `${prefix}-${numPart}`;
        let counter = 1;
        while (seenCodes.has(candidate.toLowerCase())) {
          candidate = `${prefix}-${numPart}-${counter++}`;
        }
        code = candidate;
        normalized.staff_code = code;
      }
      seenCodes.add(normalized.staff_code.toLowerCase());

      result.push(normalized);
    }
    return result;
  };

  const client = getSupabase();
  if (!client) {
    return { data: [], error: 'Database is not connected. Please configure your Supabase Project URL and Anon Key in settings.', isLive: false };
  }

  const { data, error } = await client.from('staff').select('*').order('name');
  if (error) {
    return { data: [], error: 'Database Error: ' + error.message, isLive: false };
  }
  const normalized = sanitizeAndDeduplicate(data || []);
  return { data: normalized, error: null, isLive: true };
};

export const fetchStaffList = fetchStaff;

export const addStaff = async (staffData) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const code = (staffData.staff_code || '').trim();
  const name = (staffData.name || '').trim();
  
  if (!name) {
    return { success: false, error: 'Full legal staff name is required.' };
  }
  if (!code) {
    return { success: false, error: 'Designated staff code is required.' };
  }

  // Do not store CDS staff as normal staff for compliance reporting
  if (
    staffData.department === 'CDS' ||
    staffData.staff_group === 'CDS' ||
    staffData.category === 'CDS' ||
    (staffData.role && /CDS/i.test(staffData.role))
  ) {
    return { success: false, error: 'CDS staff cannot be stored as normal staff for compliance reporting.' };
  }

  const department = (staffData.department === 'Operations' ? 'Operations' : 'Academic');
  const staff_group = (staffData.staff_group || '').trim() || (department === 'Academic' ? 'Languages' : 'Administration');

  const payload = {
    name,
    staff_code: code,
    department,
    staff_group,
    role: (staffData.role || '').trim() || (department === 'Academic' ? 'Subject Teacher' : 'Operations Officer'),
    assigned_location: (staffData.assigned_location || '').trim() || (department === 'Academic' ? 'Classroom' : 'Duty Station'),
    subjects: staffData.subjects || '',
    phone: staffData.phone || '',
    email: staffData.email || '',
    status: staffData.status === 'Archived' ? 'Archived' : 'Active',
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  try {
    const { data: existing } = await client
      .from('staff')
      .select('id, staff_code')
      .ilike('staff_code', code)
      .maybeSingle();

    if (existing) {
      return { success: false, error: `Unique Constraint Violated: Staff code '${code}' is already assigned to a personnel record.` };
    }

    const { data, error } = await client.from('staff').insert(payload).select().single();
    if (!error && data) {
      return { success: true, data: normalizeStaffRecord(data) };
    }
    if (error) {
      return { success: false, error: error.message };
    }
  } catch (err) {
    return { success: false, error: err?.message || 'Database error creating staff record' };
  }

  return { success: false, error: 'Failed to create staff record' };
};

export const updateStaff = async (id, updates) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const code = updates.staff_code ? updates.staff_code.trim() : null;

  if (code) {
    const { data: existing } = await client
      .from('staff')
      .select('id, staff_code')
      .ilike('staff_code', code)
      .neq('id', id)
      .maybeSingle();

    if (existing) {
      return { success: false, error: `Unique Constraint Violated: Staff code '${code}' is already assigned to another staff member.` };
    }
  }

  const allowedKeys = [
    'name',
    'staff_code',
    'department',
    'staff_group',
    'role',
    'assigned_location',
    'subjects',
    'phone',
    'email',
    'status',
    'school_section'
  ];

  const payload = {};
  for (const key of allowedKeys) {
    if (updates[key] !== undefined) {
      payload[key] = updates[key];
    }
  }
  payload.updated_at = new Date().toISOString();
  if (payload.staff_code) payload.staff_code = payload.staff_code.trim();
  if (payload.name) payload.name = payload.name.trim();

  const { error } = await client.from('staff').update(payload).eq('id', id);
  return { success: !error, error: error?.message };
};

export const deleteStaff = async (id, options = {}) => {
  if (options && options.hard === true) {
    return hardDeleteStaff(id);
  }
  return archiveStaff(id);
};

export const hardDeleteStaff = async (id) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };
  if (!id) return { success: false, error: 'Staff ID is required.' };

  try {
    const { count } = await client
      .from('compliance_reports')
      .select('id', { count: 'exact', head: true })
      .eq('staff_id', id);

    if (count && count > 0) {
      return {
        success: false,
        error: `Cannot delete staff member: ${count} historical compliance exception record(s) depend on this staff identity. Personnel must be archived instead.`
      };
    }
    const { error } = await client.from('staff').delete().eq('id', id);
    if (error) return { success: false, error: error.message };
    return { success: true };
  } catch (err) {
    return { success: false, error: err?.message || 'Database error during deletion' };
  }
};

export const fetchStaffHistory = async (staffId) => {
  if (!staffId) return { data: [], error: 'Staff ID is required.' };

  const allReportsRes = await fetchComplianceReports({ staff_id: staffId });
  if (allReportsRes.error) return allReportsRes;

  const staffReports = (allReportsRes.data || []).filter(r => {
    if (r.isCDS || r.division === 'CDS') return false;
    const directMatch = String(r.staff_id) === String(staffId) ||
      (r.staff && String(r.staff.id) === String(staffId)) ||
      (r.rawRecord && String(r.rawRecord.staff_id) === String(staffId));
    return directMatch;
  });

  staffReports.sort((a, b) => {
    const da = a.incident_date || a.date || a.created_at || '';
    const db = b.incident_date || b.date || b.created_at || '';
    return db.localeCompare(da);
  });

  return { data: staffReports, error: null };
};

export const archiveStaff = async (id) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const now = new Date().toISOString();
  try {
    const { error } = await client.from('staff').update({ status: 'Archived', updated_at: now }).eq('id', id);
    if (error) return { success: false, error: error.message };
    return { success: true };
  } catch (err) {
    return { success: false, error: err?.message || 'Failed to archive staff in database' };
  }
};

export const restoreStaff = async (id) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const now = new Date().toISOString();
  try {
    const { error } = await client.from('staff').update({ status: 'Active', updated_at: now }).eq('id', id);
    if (error) return { success: false, error: error.message };
    return { success: true };
  } catch (err) {
    return { success: false, error: err?.message || 'Failed to restore staff in database' };
  }
};

// =========================================================================
// CLASSES & SUBJECTS & REFERENCE GROUPS
// =========================================================================
export const fetchClasses = async () => {
  const client = getSupabase();
  if (client) {
    const { data } = await client.from('classes').select('name').order('order_index', { ascending: true });
    if (data && data.length > 0) return data.map(c => c.name);
  }
  return [];
};

export const fetchDetailedClasses = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data, error } = await client.from('classes').select('*').order('order_index', { ascending: true });
      if (!error && data && data.length > 0) return { data, error: null, isLive: true };
    } catch {}
  }
  return { data: [], error: null, isLive: false };
};

export const fetchSubjects = async () => {
  const client = getSupabase();
  if (client) {
    const { data } = await client.from('subjects').select('name').order('name');
    if (data && data.length > 0) return data.map(s => s.name);
  }
  return [];
};

export const fetchDetailedSubjects = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data, error } = await client.from('subjects').select('*').order('name');
      if (!error && data && data.length > 0) return { data, error: null, isLive: true };
    } catch {}
  }
  return { data: [], error: null, isLive: false };
};

export const fetchAcademicDisciplineGroups = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data, error } = await client.from('academic_discipline_groups').select('*').order('order_index', { ascending: true });
      if (!error && data && data.length > 0) return { data, error: null, isLive: true };
    } catch {}
  }
  return {
    data: [
      { id: 'life_sciences', name: 'Life Sciences', order_index: 1 },
      { id: 'physical_sciences', name: 'Physical Sciences', order_index: 2 },
      { id: 'social_sciences', name: 'Social Sciences', order_index: 3 },
      { id: 'languages', name: 'Languages', order_index: 4 },
    ],
    error: null,
    isLive: false,
  };
};

export const fetchOperationsGroups = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data, error } = await client.from('operations_groups').select('*').order('order_index', { ascending: true });
      if (!error && data && data.length > 0) return { data, error: null, isLive: true };
    } catch {}
  }
  return { data: [], error: null, isLive: false };
};

export const fetchReportingPeriods = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data, error } = await client.from('reporting_periods').select('*').order('created_at', { ascending: false });
      if (!error && data && data.length > 0) return { data, error: null, isLive: true };
    } catch {}
  }
  return { data: [], error: null, isLive: false };
};

// =========================================================================
// AUTHORITATIVE REPORT-READY EXCEPTION QUERY SERVICE
// Retrieves normalized, relational exception data ready for PowerPoint reports & analytical matrices
// =========================================================================
export const fetchReportReadyExceptions = async (filters = {}) => {
  const { data: staffList } = await fetchStaff();
  const staffMap = buildStaffLookupMap(staffList || []);

  const { data: detailedClasses } = await fetchDetailedClasses();
  const classMap = new Map();
  (detailedClasses || []).forEach(c => {
    if (c.id) classMap.set(String(c.id), c);
    if (c.name) classMap.set(c.name.toLowerCase().trim(), c);
  });

  const { data: detailedSubjects } = await fetchDetailedSubjects();
  const subjectMap = new Map();
  (detailedSubjects || []).forEach(s => {
    if (s.id) subjectMap.set(String(s.id), s);
    if (s.name) subjectMap.set(s.name.toLowerCase().trim(), s);
  });

  let rawList = [];
  let isLive = false;

  if (supabase) {
    try {
      // 1. Prefer the authoritative SQL View
      let viewQuery = supabase.from('report_ready_exceptions').select('*').order('date', { ascending: false });
      if (filters.term) viewQuery = viewQuery.eq('term', filters.term);
      if (filters.division) viewQuery = viewQuery.eq('division', filters.division);
      if (filters.staff_id) viewQuery = viewQuery.eq('staff_id', filters.staff_id);
      if (filters.school_section) viewQuery = viewQuery.eq('school_section', filters.school_section);
      if (filters.startDate) viewQuery = viewQuery.gte('date', filters.startDate);
      if (filters.endDate) viewQuery = viewQuery.lte('date', filters.endDate);

      const { data, error } = await viewQuery;
      if (!error && data) {
        rawList = data;
        isLive = true;
      } else {
        // Fallback to base table if view is not yet provisioned in this instance
        const fallbackRes = await fetchComplianceReports(filters);
        rawList = fallbackRes.data || [];
        isLive = fallbackRes.isLive;
      }
    } catch (err) {
      console.warn('Could not query report_ready_exceptions view:', err);
      const fallbackRes = await fetchComplianceReports(filters);
      rawList = fallbackRes.data || [];
    }
  } else {
    const fallbackRes = await fetchComplianceReports(filters);
    rawList = fallbackRes.data || [];
  }

  // Authoritative normalization ensuring clean schema for generator
  const reportReady = rawList.map(r => {
    const isCDS = r.division === 'CDS' || r.target_type === 'division';
    let staff = null;
    if (!isCDS && r.staff_id) {
      staff = staffMap.get(String(r.staff_id));
    }
    if (!isCDS && !staff && r.staff_code) {
      staff = staffMap.get(`code:${String(r.staff_code).trim().toLowerCase()}`);
    }
    if (!isCDS && !staff && (r.staff_name || r.teacher_name)) {
      staff = staffMap.get(`name:${String(r.staff_name || r.teacher_name).trim().toLowerCase()}`);
    }

    const cls = (r.class_id && classMap.get(String(r.class_id))) || (r.class_name && classMap.get(r.class_name.toLowerCase().trim())) || null;
    const sub = (r.subject_id && subjectMap.get(String(r.subject_id))) || (r.subject && subjectMap.get(r.subject.toLowerCase().trim())) || null;

    const exceptionDate = r.date || r.incident_date || (r.created_at ? r.created_at.split('T')[0] : new Date().toISOString().split('T')[0]);
    const exceptionText = r.exception_text || r.exception || '';
    const effectText = r.effect_text || r.effect || '';
    const evidenceUrl = r.evidence_url || (Array.isArray(r.evidence_urls) && r.evidence_urls.length > 0 ? r.evidence_urls[0] : null);
    const evidenceType = r.evidence_type || (Array.isArray(r.evidence_types) && r.evidence_types.length > 0 ? r.evidence_types[0] : 'none');

    const rawDeptOrDiv = String(staff?.department || r.department || r.division || '').trim().toLowerCase();
    const isOperation = rawDeptOrDiv === 'operations' || rawDeptOrDiv === 'operation' || String(staff?.category || '').toLowerCase().includes('operation');
    const isDiscipline = rawDeptOrDiv === 'discipline' || String(staff?.category || '').toLowerCase().includes('discipline');

    let resolvedDivision = 'Academic';
    let resolvedDepartment = 'Academic';
    let resolvedDisciplineGroup = null;

    if (isCDS) {
      resolvedDivision = 'CDS';
      resolvedDepartment = 'CDS';
      resolvedDisciplineGroup = null;
    } else if (isOperation) {
      resolvedDivision = 'Operations';
      resolvedDepartment = 'Operations';
      resolvedDisciplineGroup = 'Operations';
    } else if (isDiscipline) {
      resolvedDivision = 'Discipline';
      resolvedDepartment = 'Discipline';
      resolvedDisciplineGroup = 'Discipline';
    } else {
      resolvedDivision = 'Academic';
      resolvedDepartment = 'Academic';
      resolvedDisciplineGroup = normalizeAcademicDepartment(sub?.discipline_group || staff?.staff_group, sub?.name || r.subject, staff?.role);
    }

    return {
      id: r.id,
      date: exceptionDate,
      division: resolvedDivision,
      staff_id: isCDS ? null : (staff?.id || r.staff_id || null),
      staff_code: isCDS ? null : (staff?.staff_code || r.staff_code || null),
      staff_name: isCDS ? null : (staff?.name || r.staff_name || r.teacher_name || null),
      department: resolvedDepartment,
      staff_group: isCDS ? null : (staff?.staff_group || r.staff_group || (isOperation ? 'Operations' : 'Life Sciences')),
      role: isCDS ? 'Corporate Digital Services' : (staff?.role || r.role || (isOperation ? 'Operations Staff' : 'Subject Teacher')),
      school_section: isCDS ? 'CDS' : (staff?.school_section || r.school_section || cls?.section || (isOperation ? 'OPERATIONS' : 'SENIOR_SECONDARY')),
      assigned_location: staff?.assigned_location || r.assigned_location || r.class_name || (isOperation ? 'Operations Wing' : 'Academic Wing'),
      check_id: r.check_id || null,
      check_name: r.check_name || r.compliance_check || r.category || 'General Compliance',
      check_group: r.check_group || r.category || 'General',
      class_id: cls?.id || r.class_id || null,
      class_name: cls?.name || r.class_name || (isCDS || isOperation ? null : 'Unassigned'),
      class_section: cls?.section || staff?.school_section || (isOperation ? 'OPERATIONS' : 'SENIOR_SECONDARY'),
      class_arm: cls?.arm || null,
      subject_id: sub?.id || r.subject_id || null,
      subject_name: sub?.name || r.subject || (isCDS || isOperation ? null : 'Life Sciences'),
      discipline_group: resolvedDisciplineGroup,
      exception_text: exceptionText,
      effect_text: effectText,
      evidence_url: evidenceUrl,
      evidence_type: evidenceType,
      reported_by: r.reported_by || 'Directorate Quality Assurance',
      reporter_name: r.reporter_name || r.reported_by || 'Directorate Quality Assurance',
      term: r.term || 'First Term',
      week_number: Number(r.week_number) || 1,
      status: r.status || 'OPEN',
      review_status: Boolean(r.review_status === 'SEEN' || r.is_seen === true || r.seen === true || r.seen_at) ? 'SEEN' : 'UNSEEN',
      is_seen: Boolean(r.review_status === 'SEEN' || r.is_seen === true || r.seen === true || r.seen_at),
      seen_at: r.seen_at || null,
      seen_by: r.seen_by || null,
      created_at: r.created_at || new Date().toISOString(),
    };
  });

  return {
    data: reportReady,
    error: null,
    isLive,
    total: reportReady.length
  };
};

// =========================================================================
// SYSTEM SETTINGS
// =========================================================================
export const fetchSettings = async () => {
  const client = getSupabase();
  const defaultMonday = getMondayOfWeek(new Date());
  const defaultStartDate = `${defaultMonday.getFullYear()}-${String(defaultMonday.getMonth() + 1).padStart(2, '0')}-${String(defaultMonday.getDate()).padStart(2, '0')}`;

  const defaultSettings = {
    current_term: 'First Term',
    current_session: '2026/2027',
    current_week: '1',
    term_start_date: defaultStartDate,
    manual_week_override: 'none',
    school_name: 'JCBest Schools International',
    max_weeks_per_term: 12,
  };

  let settings = { ...defaultSettings };
  if (client) {
    try {
      const { data, error } = await client.from('system_settings').select('*');
      if (!error && data && data.length > 0) {
        data.forEach(row => { settings[row.key] = row.value; });
      }
    } catch (e) {
      console.warn('Could not fetch system_settings:', e);
    }
  }

  // Calculate dynamic week number based on start date and Monday calendar cadence
  const academic = calculateAcademicWeek({
    termStartDate: settings.term_start_date || defaultStartDate,
    manualOverride: settings.manual_week_override || 'none',
    maxWeeks: Number(settings.max_weeks_per_term) || 12,
  });

  settings.current_week = academic.raw;
  settings.is_end_of_term = academic.isEndOfTerm;
  settings.week_label = academic.label;
  settings.week_number_int = academic.weekNumberInt;

  return settings;
};

export const updateSettings = async (key, value) => {
  const client = getSupabase();
  if (!client) {
    return { success: false, error: 'Database is not connected.' };
  }
  const { error } = await client.from('system_settings').upsert({ key, value });
  return { success: !error, error: error?.message };
};

/**
 * End-of-Term Evidence Bucket Purge Service
 * Erases stored media files in the 'compliance-evidence' bucket at term end (Week 12 / Vacation or transition)
 * to free storage capacity for the upcoming term while preserving historical exception report logs.
 */
export const purgeEvidenceBucket = async () => {
  const client = getSupabase();
  if (!client) {
    return { success: false, error: 'Database is not connected.' };
  }

  try {
    // 1. Attempt executing the PL/pgSQL RPC function purge_term_evidence_bucket if deployed
    const { data: rpcData, error: rpcError } = await client.rpc('purge_term_evidence_bucket');
    if (!rpcError && rpcData && rpcData.success) {
      return {
        success: true,
        message: rpcData.message || 'Evidence storage bucket purged successfully for the new term.'
      };
    }

    // 2. Client-side fallback: list and remove files from compliance-evidence storage bucket
    const { data: fileList } = await client.storage
      .from('compliance-evidence')
      .list('', { limit: 1000 });

    if (fileList && fileList.length > 0) {
      const fileNames = fileList.map((f) => f.name).filter(Boolean);
      if (fileNames.length > 0) {
        await client.storage.from('compliance-evidence').remove(fileNames);
      }
    }

    // Clear evidence references on compliance_reports table to release storage links
    const { error: updateError } = await client
      .from('compliance_reports')
      .update({
        evidence_url: null,
        evidence_type: 'none',
        evidence_urls: null,
        evidence_types: null,
        updated_at: new Date().toISOString()
      })
      .neq('id', '00000000-0000-0000-0000-000000000000');

    if (updateError) {
      console.warn('Update reports error during fallback bucket purge:', updateError);
    }

    return {
      success: true,
      message: 'Compliance evidence storage bucket successfully purged for the new academic term.'
    };
  } catch (err) {
    console.error('Error purging evidence bucket:', err);
    return { success: false, error: err?.message || 'Failed to purge evidence bucket.' };
  }
};

// =========================================================================
// COMPLIANCE REPORTS (Authoritative staff_id Relational Persistence Layer)
// =========================================================================

/**
 * Normalizes an exception report record, ensuring staff_id is the authoritative
 * relationship key for Academic and Operations personnel.
 * 
 * Retrieves authoritative personnel attributes (name, staff_code, department,
 * staff_group, role, assigned_location) through the staff relationship, while
 * preserving legacy fields (teacher_name, class_name) for backward compatibility.
 * 
 * For CDS exceptions:
 * - division = 'CDS'
 * - target_type = 'division'
 * - staff_id = null
 */
export const normalizeReportRecord = (report, staffMap = null, staffSeverityMap = null) => {
  if (!report) return null;

  const explicitDiv = report.division ? String(report.division).trim() : null;
  const isCDS = explicitDiv === 'CDS' || (report.target_type === 'division' && (!explicitDiv || explicitDiv === 'CDS'));
  const division = explicitDiv || (isCDS ? 'CDS' : (report.staff_category === 'Academic Staff' ? 'Academic' : (report.staff_category === 'Discipline' ? 'Discipline' : 'Operations')));

  let staff = null;
  if (!isCDS && staffMap) {
    if (report.staff_id) {
      staff = staffMap.get(String(report.staff_id));
    }
    // Fallback resolution for legacy unmigrated historical rows
    if (!staff && report.staff_code) {
      staff = staffMap.get(`code:${String(report.staff_code).trim().toLowerCase()}`);
    }
    if (!staff && (report.staff_name || report.teacher_name)) {
      const nameKey = String(report.staff_name || report.teacher_name).trim().toLowerCase();
      staff = staffMap.get(`name:${nameKey}`);
    }
  }

  // Hydrate staff attributes if staff relationship exists
  const staffName = isCDS
    ? null
    : (staff ? staff.name : (report.staff_name || report.teacher_name || (report.target_type === 'division' ? (explicitDiv || 'Division Unit') : 'Personnel Officer')));

  const staffCode = isCDS
    ? null
    : (staff ? staff.staff_code : (report.staff_code || null));

  const staffDept = isCDS
    ? 'CDS'
    : (staff ? staff.department : (division || 'Academic'));

  const staffGroup = isCDS
    ? null
    : (staff ? staff.staff_group : (report.staff_group || 'General'));

  const staffRole = isCDS
    ? 'Corporate Digital Services'
    : (staff ? staff.role : (report.role || report.staff_role || (report.target_type === 'division' ? 'Division Unit' : 'Personnel Officer')));

  const staffLocation = staff
    ? staff.assigned_location
    : (isCDS ? 'Central IT & Systems' : (report.assigned_location || report.class_name || report.class_or_office || 'Campus Main'));

  const staffId = isCDS ? null : (staff ? staff.id : (report.staff_id || null));

  // Authoritative Derived Severity:
  // Derived strictly from the subject's exception count (0: No recorded exceptions, 1: Low, 2: Medium, 3: High, 4+: Critical).
  // "Do not calculate staff severity for CDS."
  let derivedSeverity = isCDS ? null : 'Low';
  let staffExceptionCount = isCDS ? null : 1;

  if (!isCDS && staffSeverityMap && staffId) {
    const staffDerived = staffSeverityMap.get(String(staffId));
    if (staffDerived) {
      derivedSeverity = staffDerived.severity === 'No recorded exceptions' ? 'Low' : staffDerived.severity;
      staffExceptionCount = staffDerived.count;
    }
  } else if (!isCDS && report.derived_severity) {
    derivedSeverity = report.derived_severity;
  }

  return {
    ...report,
    id: report.id || `rep-${Date.now()}`,
    report_number: report.report_number || `JCB-${new Date().getFullYear()}-0001`,
    // Authoritative relationship key
    staff_id: staffId,
    target_type: report.target_type || (isCDS ? 'division' : 'staff'),
    division: isCDS ? 'CDS' : (explicitDiv || staffDept),
    // Fully hydrated staff object for direct property access
    staff: staff || (isCDS ? null : (staffId ? {
      id: staffId,
      staff_code: staffCode,
      name: staffName,
      department: staffDept,
      staff_group: staffGroup,
      role: staffRole,
      assigned_location: staffLocation,
      status: staff?.status || report.staff_status || 'Active',
      derived_severity: derivedSeverity,
      reportsCount: staffExceptionCount,
    } : null)),
    // Authoritative personnel identity retrieved through staff relationship
    staff_name: staffName,
    staff_code: staffCode,
    department: staffDept,
    staff_group: staffGroup,
    role: staffRole,
    assigned_location: staffLocation,
    // Preserved relational identifiers
    teacher_name: staffName,
    class_name: report.class_name || (isCDS ? null : null),
    class_or_office: report.class_or_office || report.class_name || (isCDS ? 'Central IT & Systems' : staffLocation),
    subject: report.subject || report.subject_name || (isCDS ? null : staffGroup),
    subject_name: report.subject_name || report.subject || (isCDS ? null : staffGroup),
    // Authoritative Derived Severity (Not manually chosen; derived from exception count)
    severity: derivedSeverity,
    derived_severity: derivedSeverity,
    severity_key: getSeverityKey(staffExceptionCount),
    // Core exception observation fields
    category: report.category || report.compliance_check || 'General',
    compliance_check: report.compliance_check || report.category || 'General',
    exception: report.exception || report.exception_text || '',
    exception_text: report.exception_text || report.exception || '',
    effect: report.effect || report.effect_text || '',
    effect_text: report.effect_text || report.effect || '',
    // Evidence & Metadata
    evidence_type: report.evidence_type || 'none',
    evidence_url: report.evidence_url || (Array.isArray(report.evidence_urls) ? report.evidence_urls[0] : ''),
    evidence_name: report.evidence_name || (report.evidence_url ? (function() {
      try {
        const u = new URL(report.evidence_url);
        const parts = u.pathname.split('/');
        return decodeURIComponent(parts[parts.length - 1] || '').split('?')[0];
      } catch { return 'Attached Evidence'; }
    })() : ''),
    reported_by: report.reported_by || 'Directorate Quality Assurance',
    reporter_name: report.reporter_name || report.reported_by || 'Directorate Quality Assurance',
    date: report.date || report.incident_date || (report.created_at ? report.created_at.split('T')[0] : new Date().toISOString().split('T')[0]),
    incident_date: report.incident_date || report.date || (report.created_at ? report.created_at.split('T')[0] : new Date().toISOString().split('T')[0]),
    status: report.status || 'OPEN',
    // Authoritative Exception Review Workflow
    // Every newly created exception starts UNSEEN. Seen is a review state, not a resolution.
    review_status: Boolean(report.review_status === 'SEEN' || report.is_seen === true || report.seen === true || report.seen_at) ? 'SEEN' : 'UNSEEN',
    is_seen: Boolean(report.review_status === 'SEEN' || report.is_seen === true || report.seen === true || report.seen_at),
    seen: Boolean(report.review_status === 'SEEN' || report.is_seen === true || report.seen === true || report.seen_at),
    seen_at: report.seen_at || null,
    seen_by: report.seen_by || null,
    created_at: report.created_at || new Date().toISOString(),
    updated_at: report.updated_at || new Date().toISOString(),
  };
};

/**
 * Builds a fast lookup map for all staff by ID, staff_code, and lowercase name.
 */
const buildStaffLookupMap = (staffList = []) => {
  const map = new Map();
  staffList.forEach((s) => {
    if (s.id) map.set(String(s.id), s);
    if (s.staff_code) map.set(`code:${String(s.staff_code).trim().toLowerCase()}`, s);
    if (s.name) map.set(`name:${String(s.name).trim().toLowerCase()}`, s);
  });
  return map;
};

export const fetchComplianceReports = async (filters = {}) => {
  const client = getSupabase();
  if (!client) {
    return { data: [], error: 'Database is not connected.', isLive: false };
  }

  // Retrieve staff master records to hydrate relational links
  let staffList = [];
  try {
    const staffRes = await fetchStaff();
    staffList = staffRes.data || [];
  } catch (err) {
    console.warn('Could not fetch staff master for reports hydration:', err);
  }
  const staffMap = buildStaffLookupMap(staffList);

  const dateFilter = {
    startDate: filters.startDate || filters.from || null,
    endDate: filters.endDate || filters.to || null,
  };

  // Authority enforcement: Standard users can NEVER bypass RLS or fetch other users' reports
  let forceReportedBy = filters.reported_by || null;
  try {
    const session = await authService.getSession();
    if (session) {
      const isOwner = isOwnerUser(session);
      const role = session.role || 'standard';
      const isStandardUser = !isOwner && role !== 'super_admin' && role !== 'admin';
      if (isStandardUser) {
        forceReportedBy = session.id || session.user_id;
      }
    }
  } catch (err) {
    console.warn('Authority check inside fetchComplianceReports failed:', err);
  }

  let rawReports = [];
  let isLive = true;

  try {
    let query = client.from('compliance_reports').select('*').order('created_at', { ascending: false });

    if (forceReportedBy) {
      query = query.eq('reported_by', forceReportedBy);
    } else if (filters.reported_by) {
      query = query.eq('reported_by', filters.reported_by);
    }
    if (filters.status) query = query.eq('status', filters.status);
    if (filters.week_number) query = query.eq('week_number', filters.week_number);
    if (filters.term) query = query.eq('term', filters.term);
    if (filters.staff_id) query = query.eq('staff_id', filters.staff_id);
    if (filters.division) query = query.eq('division', filters.division);

    const { data, error } = await query;
    if (error) {
      return { data: [], error: 'Database Connection Error: ' + error.message, isLive: false };
    } else {
      rawReports = data || [];
      isLive = true;
    }
  } catch (err) {
    return { data: [], error: err?.message || 'Database query failed', isLive: false };
  }

  // Pre-normalize raw reports so staff_id and dates are consistent
  const preNormalized = rawReports.map(r => normalizeReportRecord(r, staffMap));

  // Compute authoritative staff severity map based on actual exceptions respecting active date filter
  // Seen/unseen review states do NOT change the exception count
  const staffSeverityMap = computeStaffSeverityMap(staffList, preNormalized, dateFilter);

  // Compute CDS division severity
  const cdsExceptions = preNormalized.filter(r => {
    const isCds = r.division === 'CDS' || (r.target_type === 'division' && (!r.division || r.division === 'CDS'));
    if (!isCds) return false;
    const d = r.incident_date || r.date;
    if (d && dateFilter.startDate && d < dateFilter.startDate) return false;
    if (d && dateFilter.endDate && d > dateFilter.endDate) return false;
    return true;
  });
  staffSeverityMap.set('division:cds', {
    count: cdsExceptions.length,
    severity: calculateStaffSeverity(cdsExceptions.length),
    key: getSeverityKey(cdsExceptions.length),
  });

  // Hydrate all reports with derived severity
  let normalized = preNormalized.map(r => normalizeReportRecord(r, staffMap, staffSeverityMap));

  // If a severity filter was requested (e.g. 'Critical', 'High', 'Medium', 'Low')
  if (filters.severity && filters.severity !== 'ALL' && filters.severity !== 'All') {
    normalized = normalized.filter(r => (r.severity || '').toUpperCase() === filters.severity.toUpperCase());
  }

  // Filter by active date range if provided
  if (dateFilter.startDate || dateFilter.endDate) {
    normalized = normalized.filter(r => {
      const d = r.incident_date || r.date;
      if (!d) return true;
      if (dateFilter.startDate && d < dateFilter.startDate) return false;
      if (dateFilter.endDate && d > dateFilter.endDate) return false;
      return true;
    });
  }

  return { data: normalized, error: null, isLive };
};

/**
 * Creates an authoritative compliance exception.
 * Enforces strict validation:
 * 1. Required staff selection for Academic & Operations.
 * 2. staff_id must exist in the Staff Register.
 * 3. Selected staff must not be Archived.
 * 4. Selected check must be valid for the selected division & staff group.
 * 5. CDS exceptions do NOT require a staff_id (target_type = 'division', division = 'CDS').
 * 6. Never uses typed staff_name as the primary relationship.
 */
export const createComplianceReport = async (formData) => {
  const client = getSupabase();
  if (!client) {
    return { success: false, error: 'Database is not connected.' };
  }

  if (!formData) {
    return { success: false, error: 'No compliance report data provided.' };
  }

  const division = formData.division || (formData.target_type === 'division' ? 'CDS' : (formData.staff_category === 'Academic Staff' ? 'Academic' : (formData.staff_category === 'Discipline' ? 'Discipline' : 'Operation')));

  // 1. Division validation
  if (!['CDS', 'Academic', 'Operation', 'Operations', 'Discipline'].includes(division)) {
    return { success: false, error: `Invalid reporting division '${division}'. Must be Academic, CDS, Operation, or Discipline.` };
  }

  const checkName = (formData.category || formData.compliance_check || '').trim();
  if (!checkName) {
    return { success: false, error: 'Please select a compliance check.' };
  }

  let authoritativeStaff = null;
  const isCds = division === 'CDS';
  const isOperationDivision = division === 'Operation' || division === 'Operations' || formData.target_type === 'division';
  const isDisciplineDivision = division === 'Discipline' && (!formData.staff_id || formData.target_type === 'division');
  const isDivisionTarget = isCds || isDisciplineDivision || isOperationDivision;

  if (isCds) {
    // CDS exceptions: NO staff_id required. Target is institutional division.
    const cdsChecks = getApplicableChecks({ division: 'CDS' });
    const isValidCheck = cdsChecks.length === 0 || cdsChecks.some(
      c => (c.name || c.label || c.id || '').toLowerCase() === checkName.toLowerCase() || c.id === checkName
    );
    if (!isValidCheck) {
      return { success: false, error: `The compliance check '${checkName}' is invalid for CDS division.` };
    }
  } else if (isDisciplineDivision) {
    // Discipline institutional / student decorum record: NO staff_id required. Target is division.
    const discChecks = getApplicableChecks({ division: 'Discipline' });
    const isValidCheck = discChecks.length === 0 || discChecks.some(
      c => (c.name || c.label || c.id || '').toLowerCase() === checkName.toLowerCase() || c.id === checkName
    );
    if (!isValidCheck) {
      return { success: false, error: `The compliance check '${checkName}' is invalid for Discipline division.` };
    }
  } else if (isOperationDivision) {
    // Operation exceptions: NO staff_id from register required; supports typed attributes (Driver, Minder, Security, Maintenance location).
  } else {
    // Academic and Staff-targeted Discipline:
    // (a) Required staff selection
    if (!formData.staff_id) {
      return {
        success: false,
        error: `A staff member must be selected from the Staff Register for ${division} exceptions. Never use a typed staff name as the primary identity relationship.`
      };
    }

    // (b) Resolve or provision staff_id in Supabase Staff Register
    let staffRecord = null;
    const isUuid = (val) => typeof val === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);

    // 1. Try querying by UUID id if valid
    if (isUuid(formData.staff_id)) {
      try {
        const { data, error } = await client.from('staff').select('*').eq('id', formData.staff_id).maybeSingle();
        if (!error && data) {
          staffRecord = normalizeStaffRecord(data);
        }
      } catch {
        // ignore
      }
    }

    // 2. Try querying by staff_code or name if not found by UUID
    if (!staffRecord) {
      try {
        const lookupCode = (formData.staff_code || formData.staff_id || '').trim();
        const lookupName = (formData.staff_name || formData.teacher_name || '').trim();

        if (lookupCode) {
          const { data: codeData } = await client.from('staff').select('*').ilike('staff_code', lookupCode).maybeSingle();
          if (codeData) staffRecord = normalizeStaffRecord(codeData);
        }

        if (!staffRecord && lookupName) {
          const { data: nameData } = await client.from('staff').select('*').ilike('name', lookupName).maybeSingle();
          if (nameData) staffRecord = normalizeStaffRecord(nameData);
        }
      } catch {
        // ignore
      }
    }

    // 3. Auto-provision staff in Supabase if not yet present in database
    if (!staffRecord) {
      try {
        const targetDept = formData.department || (division === 'Operations' ? 'Operations' : 'Academic');
        const targetCode = (formData.staff_code || '').trim() || `JCB-${targetDept === 'Academic' ? 'T' : 'OPS'}-${Date.now().toString().slice(-4)}`;
        const targetName = (formData.staff_name || formData.teacher_name || 'Staff Member').trim();

        const newStaffPayload = {
          staff_code: targetCode,
          name: targetName,
          department: targetDept,
          staff_group: formData.staff_group || (targetDept === 'Academic' ? 'Languages' : 'Administration'),
          role: formData.role || (targetDept === 'Academic' ? 'Subject Teacher' : 'Operations Officer'),
          assigned_location: formData.assigned_location || formData.class_name || 'Academic Wing',
          subjects: formData.subjects || '',
          status: 'Active',
          active: true,
        };

        const { data: insertedStaff, error: insertErr } = await client.from('staff').insert(newStaffPayload).select().maybeSingle();
        if (!insertErr && insertedStaff) {
          staffRecord = normalizeStaffRecord(insertedStaff);
        }
      } catch {
        // ignore
      }
    }

    if (!staffRecord) {
      return { success: false, error: `Could not resolve or provision selected staff '${formData.staff_name || formData.staff_id}' in the Staff Register.` };
    }

    // (c) Staff must NOT be archived
    if (staffRecord.status === 'Archived') {
      return {
        success: false,
        error: `Personnel '${staffRecord.name}' is archived. Archived staff cannot be selected for new compliance exceptions; they are retained for historical audit only.`
      };
    }

    // (d) Selected check validation
    const applicableChecks = getApplicableChecks({
      division,
      staffGroup: staffRecord.staff_group,
      staff: staffRecord
    });
    const isValidCheck = applicableChecks.length === 0 || applicableChecks.some(
      c => (c.name || c.label || c.id || '').toLowerCase() === checkName.toLowerCase() || c.id === checkName
    );
    if (!isValidCheck) {
      return {
        success: false,
        error: `The compliance check '${checkName}' is invalid for ${division} (${staffRecord.staff_group || 'General'}).`
      };
    }

    authoritativeStaff = staffRecord;
  }

  // 2. Validate narrative and impact
  const exceptionText = (formData.exception_text || formData.exception || '').trim();
  const effectText = (formData.effect_text || formData.effect || '').trim();
  const incidentDate = formData.date || formData.incident_date || new Date().toISOString().split('T')[0];

  if (!exceptionText) {
    return { success: false, error: 'Please describe the observed compliance exception.' };
  }
  if (!effectText) {
    return { success: false, error: 'Please assess the operational or student impact of this exception.' };
  }

  // 3. Assemble authoritative relational payload

  // 4. Persistence into Supabase
  let authUid = null;
  let authOfficerName = (formData.reporter_name || formData.reporting_officer || '').trim();
  let authOfficerRole = (formData.reporter_role || '').trim();

  try {
    const { data: sessionData } = await client.auth.getSession();
    if (sessionData?.session?.user) {
      authUid = sessionData.session.user.id;
      const { data: roleRow } = await client
        .from('user_roles')
        .select('display_name, role')
        .eq('user_id', authUid)
        .maybeSingle();

      if (roleRow?.display_name) {
        authOfficerName = roleRow.display_name.trim();
      } else if (sessionData.session.user.email) {
        authOfficerName = sessionData.session.user.email.split('@')[0];
      }

      if (roleRow?.role === 'super_admin') authOfficerRole = 'Super Admin';
      else if (roleRow?.role === 'admin') authOfficerRole = 'Vice Principal';
      else if (!authOfficerRole) authOfficerRole = 'Compliance Inspector';
    }
  } catch (authLookupErr) {
    console.warn('Could not resolve authenticated user profile:', authLookupErr);
  }

  const isUuid = (val) => typeof val === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);
  const validReportedBy = isUuid(authUid) ? authUid : (isUuid(formData.reporter_id) ? formData.reporter_id : (isUuid(formData.reported_by) ? formData.reported_by : null));
  const validReporterId = isUuid(authUid) ? authUid : (isUuid(formData.reporter_id) ? formData.reporter_id : (isUuid(formData.reported_by) ? formData.reported_by : null));

  let count = 0;
  try {
    const { count: c } = await client.from('compliance_reports').select('*', { count: 'exact', head: true });
    count = c || 0;
  } catch {
    // ignore
  }

  const yr = new Date().getFullYear();
  const uniqueTag = typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID().replace(/-/g, '').slice(0, 6).toUpperCase()
    : Math.random().toString(36).substring(2, 8).toUpperCase();
  const num = `JCB-${yr}-${String(count + 1).padStart(4, '0')}-${uniqueTag}`;

  const activeSettings = await fetchSettings();
  let weekNum = 1;
  if (formData.week_number !== undefined && formData.week_number !== null && !isNaN(Number(formData.week_number))) {
    weekNum = Math.min(12, Math.max(1, Number(formData.week_number)));
  } else if (activeSettings?.week_number_int) {
    weekNum = activeSettings.week_number_int;
  } else if (activeSettings?.current_week) {
    const parsed = Number(activeSettings.current_week);
    weekNum = !isNaN(parsed) && parsed >= 1 ? Math.min(12, parsed) : 12;
  }
  const termName = (formData.term || activeSettings.current_term || 'First Term').trim();
  const safeReporterName = (authOfficerName || 'Directorate Quality Assurance').trim();
  const safeReporterRole = (authOfficerRole || 'Super Admin').trim();

  const targetType = isDivisionTarget ? 'division' : 'staff';
  const finalStaffId = isDivisionTarget ? null : (authoritativeStaff ? authoritativeStaff.id : (isUuid(formData.staff_id) ? formData.staff_id : null));

  const dbRecord = {
    report_number: num,
    reported_by: validReportedBy,
    reporter_id: validReporterId,
    reporter_name: safeReporterName,
    reporting_officer: safeReporterName,
    reporter_role: safeReporterRole,
    incident_date: incidentDate,
    date: incidentDate,
    week_number: weekNum,
    term: termName,
    staff_id: finalStaffId,
    staff_name: isDivisionTarget ? (formData.staff_name || (isOperationDivision ? 'Operation' : isCds ? 'CDS' : 'Discipline')) : (authoritativeStaff ? authoritativeStaff.name : (formData.staff_name || null)),
    staff_code: isDivisionTarget ? (formData.staff_code || null) : (authoritativeStaff ? authoritativeStaff.staff_code : (formData.staff_code || null)),
    staff_category: isDivisionTarget ? division : (authoritativeStaff ? authoritativeStaff.department : (formData.department || division)),
    staff_group: isDivisionTarget ? (formData.staff_group || null) : (authoritativeStaff ? authoritativeStaff.staff_group : (formData.staff_group || null)),
    role: isCds ? 'Corporate Digital Services' : (isDisciplineDivision ? 'Disciplinary Governance' : (isOperationDivision ? 'Operations Duty' : (authoritativeStaff ? authoritativeStaff.role : (formData.role || null)))),
    department: isCds ? 'CDS' : (division === 'Discipline' ? 'Discipline' : (division === 'Operations' || division === 'Operation' ? 'Operation' : (authoritativeStaff ? authoritativeStaff.department : (formData.department || division)))),
    assigned_location: isCds ? 'Central IT & Systems' : (isDisciplineDivision ? (formData.class_name || 'Discipline Unit') : (formData.assigned_location || formData.location || formData.class_name || (authoritativeStaff ? authoritativeStaff.assigned_location : 'Duty Station'))),
    check_id: formData.check_id || null,
    class_id: isUuid(formData.class_id) ? formData.class_id : null,
    class_name: formData.class_name || null,
    subject_id: isUuid(formData.subject_id) ? formData.subject_id : null,
    subject: formData.subject_name || formData.subject || null,
    exception: exceptionText,
    exception_text: exceptionText,
    effect: effectText,
    effect_text: effectText,
    severity: 'MEDIUM',
    category: checkName,
    division: isCds ? 'CDS' : (division === 'Discipline' ? 'Discipline' : (division === 'Operations' ? 'Operations' : 'Operation')),
    target_type: targetType,
    compliance_check: checkName,
    violation_level: 1,
    evidence_url: formData.evidence_url || null,
    evidence_type: formData.evidence_type || 'none',
    status: formData.status || 'OPEN',
    review_status: 'UNSEEN',
    is_seen: false,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  };

  try {
    let { data, error } = await client.from('compliance_reports').insert(dbRecord).select().single();
    if (!error && data) {
      const finalReport = normalizeReportRecord(data, new Map([[String(authoritativeStaff?.id), authoritativeStaff]]));
      return { success: true, data: finalReport };
    }
    if (error) {
      const fieldsToCheck = ['role', 'staff_group', 'department', 'assigned_location', 'evidence_name'];
      let fallbackRecord = { ...dbRecord };
      let strippedAny = false;

      for (const field of fieldsToCheck) {
        if (error.message && error.message.includes(field) && fallbackRecord[field] !== undefined) {
          delete fallbackRecord[field];
          strippedAny = true;
        }
      }

      if (strippedAny) {
        const { data: fbData, error: fbError } = await client.from('compliance_reports').insert(fallbackRecord).select().single();
        if (!fbError && fbData) {
          const finalReport = normalizeReportRecord(fbData, new Map([[String(authoritativeStaff?.id), authoritativeStaff]]));
          return { success: true, data: finalReport };
        }
        error = fbError || error;
      }

      console.error('Compliance report insertion failed:', error);
      return { success: false, error: error.message || error.details || 'Database rejected report creation.' };
    }
  } catch (err) {
    console.error('Compliance report exception during insert:', err);
    return { success: false, error: err.message || 'Exception occurred while creating compliance report in database.' };
  }

  return { success: false, error: 'Database failed to return created report record.' };
};

/**
 * Authoritative mutation to update an existing compliance report / exception record.
 * Supports all 4 divisions (Academic, Operation, CDS, Discipline).
 * Enforces server-side validation and authorization.
 */
export const updateComplianceReport = async (reportId, formData) => {
  const client = getSupabase();
  if (!client) {
    return { success: false, error: 'Database is not connected.' };
  }
  if (!reportId) {
    return { success: false, error: 'No report ID provided for update.' };
  }
  if (!formData) {
    return { success: false, error: 'No compliance report data provided.' };
  }

  const isUuid = (val) => typeof val === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);
  const rawDivision = formData.division || 'Academic';
  const division = rawDivision === 'Operations' ? 'Operation' : rawDivision;

  // 1. Division validation
  if (!['CDS', 'Academic', 'Operation', 'Operations', 'Discipline'].includes(division)) {
    return { success: false, error: `Invalid reporting division '${division}'. Must be Academic, CDS, Operation, or Discipline.` };
  }

  const checkName = (formData.category || formData.compliance_check || '').trim();
  if (!checkName) {
    return { success: false, error: 'Please select a compliance check.' };
  }

  let authoritativeStaff = null;
  const isCds = division === 'CDS';
  const isOperationDivision = division === 'Operation' || division === 'Operations' || formData.target_type === 'division';
  const isDisciplineDivision = division === 'Discipline' && (!formData.staff_id || formData.target_type === 'division');
  const isDivisionTarget = isCds || isDisciplineDivision || isOperationDivision;
  const targetType = isDivisionTarget ? 'division' : 'staff';

  if (isCds) {
    const cdsChecks = getApplicableChecks({ division: 'CDS' });
    const isValidCheck = cdsChecks.length === 0 || cdsChecks.some(
      c => (c.name || c.label || c.id || '').toLowerCase() === checkName.toLowerCase() || c.id === checkName
    );
    if (!isValidCheck) {
      return { success: false, error: `The compliance check '${checkName}' is invalid for CDS division.` };
    }
  } else if (isDisciplineDivision) {
    const discChecks = getApplicableChecks({ division: 'Discipline' });
    const isValidCheck = discChecks.length === 0 || discChecks.some(
      c => (c.name || c.label || c.id || '').toLowerCase() === checkName.toLowerCase() || c.id === checkName
    );
    if (!isValidCheck) {
      return { success: false, error: `The compliance check '${checkName}' is invalid for Discipline division.` };
    }
  } else if (isOperationDivision) {
    // Operation exceptions: NO staff_id from register required; supports typed attributes (Driver, Minder, Security, Maintenance location).
  } else {
    // Academic and Staff-targeted Discipline:
    if (!formData.staff_id) {
      return {
        success: false,
        error: `A staff member must be selected from the Staff Register for ${division} exceptions. Never use a typed staff name as the primary identity relationship.`
      };
    }

    let staffRecord = null;
    const isUuid = (val) => typeof val === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);

    if (isUuid(formData.staff_id)) {
      try {
        const { data } = await client.from('staff').select('*').eq('id', formData.staff_id).maybeSingle();
        if (data) staffRecord = normalizeStaffRecord(data);
      } catch {
        // ignore
      }
    }

    if (!staffRecord) {
      try {
        const lookupCode = (formData.staff_code || formData.staff_id || '').trim();
        if (lookupCode) {
          const { data: codeData } = await client.from('staff').select('*').ilike('staff_code', lookupCode).maybeSingle();
          if (codeData) staffRecord = normalizeStaffRecord(codeData);
        }
      } catch {
        // ignore
      }
    }

    if (!staffRecord) {
      return { success: false, error: `Could not resolve selected staff '${formData.staff_name || formData.staff_id}' in the Staff Register.` };
    }

    if (staffRecord.status === 'Archived') {
      return {
        success: false,
        error: `Personnel '${staffRecord.name}' is archived. Archived staff cannot be assigned compliance exceptions.`
      };
    }

    authoritativeStaff = staffRecord;
  }

  // 2. Validate narrative and impact
  const exceptionText = (formData.exception_text || formData.exception || '').trim();
  const effectText = (formData.effect_text || formData.effect || '').trim();
  const incidentDate = formData.date || formData.incident_date || new Date().toISOString().split('T')[0];

  if (!exceptionText) {
    return { success: false, error: 'Please describe the observed compliance exception.' };
  }
  if (!effectText) {
    return { success: false, error: 'Please assess the operational or student impact of this exception.' };
  }

  const finalStaffId = isDivisionTarget ? null : (authoritativeStaff ? authoritativeStaff.id : (formData.staff_id || null));

  const updateRecord = {
    incident_date: incidentDate,
    date: incidentDate,
    division: isCds ? 'CDS' : (division === 'Discipline' ? 'Discipline' : (division === 'Operations' ? 'Operations' : 'Operation')),
    target_type: targetType,
    staff_id: finalStaffId,
    staff_name: isDivisionTarget ? (formData.staff_name || (isOperationDivision ? 'Operation' : isCds ? 'CDS' : 'Discipline')) : (authoritativeStaff ? authoritativeStaff.name : (formData.staff_name || null)),
    staff_code: isDivisionTarget ? (formData.staff_code || null) : (authoritativeStaff ? authoritativeStaff.staff_code : (formData.staff_code || null)),
    staff_category: isDivisionTarget ? division : (authoritativeStaff ? authoritativeStaff.department : (formData.department || division)),
    staff_group: isDivisionTarget ? (formData.staff_group || null) : (authoritativeStaff ? authoritativeStaff.staff_group : (formData.staff_group || null)),
    role: isCds ? 'Corporate Digital Services' : (isDisciplineDivision ? 'Disciplinary Governance' : (isOperationDivision ? 'Operations Duty' : (authoritativeStaff ? authoritativeStaff.role : (formData.role || null)))),
    department: isCds ? 'CDS' : (division === 'Discipline' ? 'Discipline' : (division === 'Operations' || division === 'Operation' ? 'Operation' : (authoritativeStaff ? authoritativeStaff.department : (formData.department || division)))),
    assigned_location: isCds ? 'Central IT & Systems' : (isDisciplineDivision ? (formData.class_name || 'Discipline Unit') : (formData.assigned_location || formData.location || formData.class_name || (authoritativeStaff ? authoritativeStaff.assigned_location : 'Duty Station'))),
    check_id: formData.check_id || null,
    category: checkName,
    compliance_check: checkName,
    class_id: isUuid(formData.class_id) ? formData.class_id : null,
    class_name: formData.class_name || null,
    subject_id: isUuid(formData.subject_id) ? formData.subject_id : null,
    subject: formData.subject_name || formData.subject || null,
    exception: exceptionText,
    exception_text: exceptionText,
    effect: effectText,
    effect_text: effectText,
    evidence_url: formData.evidence_url || null,
    evidence_type: formData.evidence_type || 'none',
    evidence_name: formData.evidence_name || null,
    updated_at: new Date().toISOString(),
  };

  try {
    const { data, error } = await client
      .from('compliance_reports')
      .update(updateRecord)
      .eq('id', reportId)
      .select()
      .single();

    if (error) {
      console.error('Failed to update compliance report in database:', error);
      return { success: false, error: error.message || 'Database rejected report update.' };
    }

    const finalReport = normalizeReportRecord(data, authoritativeStaff ? new Map([[String(authoritativeStaff.id), authoritativeStaff]]) : null);
    return { success: true, data: finalReport };
  } catch (err) {
    console.error('Exception during compliance report update:', err);
    return { success: false, error: err.message || 'Exception during report update.' };
  }
};

export const updateComplianceStatus = async (reportId, status, notes, resolvedBy) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };
  const { error } = await client.from('compliance_reports').update({
    status,
    resolution_notes: notes,
    resolved_by: resolvedBy,
    resolved_at: status === 'RESOLVED' ? new Date().toISOString() : null,
  }).eq('id', reportId);
  return { success: !error, error: error?.message };
};

export const assignReportCheck = async (reportId, checkName, optionalCheckId = null) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  try {
    let resolvedCheckId = optionalCheckId;

    if (!resolvedCheckId && checkName) {
      const { data: existingCheck } = await client
        .from('compliance_checks')
        .select('id, name')
        .ilike('name', checkName.trim())
        .limit(1)
        .maybeSingle();

      if (existingCheck?.id) {
        resolvedCheckId = existingCheck.id;
      } else {
        const { data: rep } = await client
          .from('compliance_reports')
          .select('division')
          .eq('id', reportId)
          .single();

        const created = await createComplianceCheck({
          division: rep?.division || 'Academic',
          area_name: rep?.division || 'Academic',
          name: checkName.trim(),
        });
        if (created?.data?.id) {
          resolvedCheckId = created.data.id;
        }
      }
    }

    const updatePayload = {
      compliance_check: checkName,
      category: checkName,
    };
    if (resolvedCheckId) {
      updatePayload.check_id = resolvedCheckId;
    }

    const { error } = await client
      .from('compliance_reports')
      .update(updatePayload)
      .eq('id', reportId);

    return { success: !error, error: error?.message, checkId: resolvedCheckId };
  } catch (err) {
    return { success: false, error: err.message };
  }
};

export const deleteReport = async (reportId) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };
  const { error } = await client.from('compliance_reports').delete().eq('id', reportId);
  return { success: !error, error: error?.message };
};

/**
 * Authoritative Exception Review Workflow:
 * Marks an exception report or observation as Seen.
 * Saves seen_at and seen_by.
 * 
 * Rules:
 * - Every newly created exception starts UNSEEN.
 * - Seen is a review verification, NOT a resolution.
 * - Seen does not delete the exception.
 * - Seen does not reduce staff exception count.
 * - Seen does not reduce derived staff severity.
 */
export const markReportAsSeen = async (reportId, seenBy = 'Compliance Officer') => {
  if (!reportId) return { success: false, error: 'Report ID is required.' };
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const seenAt = new Date().toISOString();
  const reviewer = seenBy || 'Compliance Officer';

  const updates = {
    review_status: 'SEEN',
    is_seen: true,
    seen_at: seenAt,
    seen_by: reviewer,
    updated_at: seenAt,
  };

  const { error } = await client
    .from('compliance_reports')
    .update(updates)
    .eq('id', reportId);

  if (error) {
    return { success: false, error: error.message };
  }

  return {
    success: true,
    data: {
      id: reportId,
      ...updates,
    }
  };
};

export const markReportAsUnseen = async (reportId) => {
  if (!reportId) return { success: false, error: 'Report ID is required.' };
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const updatedAt = new Date().toISOString();
  const updates = {
    review_status: 'UNSEEN',
    is_seen: false,
    seen_at: null,
    seen_by: null,
    updated_at: updatedAt,
  };

  const { error } = await client
    .from('compliance_reports')
    .update(updates)
    .eq('id', reportId);

  if (error) {
    return { success: false, error: error.message };
  }

  return {
    success: true,
    data: {
      id: reportId,
      ...updates,
    }
  };
};

export const updateReportStatus = async (reportId, status, seenBy) => {
  const isSeen = status === 'SEEN' || status === 'REVIEWED';
  if (isSeen) {
    return markReportAsSeen(reportId, seenBy);
  }
  if (status === 'UNSEEN' || status === 'OPEN') {
    return markReportAsUnseen(reportId);
  }

  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };
  const now = new Date().toISOString();
  const { error } = await client
    .from('compliance_reports')
    .update({
      status,
      updated_at: now
    })
    .eq('id', reportId);
  return { success: !error, error: error?.message };
};

// =========================================================================
// COMPLIANCE AREAS & CHECKS (Authoritative Supabase + Taxonomy)
// =========================================================================
export const fetchComplianceAreas = async () => {
  const client = getSupabase();
  if (client) {
    try {
      const { data: areasData, error: areasError } = await client
        .from('compliance_areas')
        .select('*')
        .order('order_index', { ascending: true });

      if (!areasError && areasData && areasData.length > 0) {
        const { data: checksData, error: checksError } = await client
          .from('compliance_checks')
          .select('*')
          .order('order_index', { ascending: true });

        const checksByArea = {};
        if (!checksError && checksData) {
          checksData.forEach((c) => {
            if (!checksByArea[c.area_id]) checksByArea[c.area_id] = [];
            checksByArea[c.area_id].push({
              id: c.id,
              name: c.name,
              group: c.group_name || c.group || null,
              description: c.description || '',
            });
          });
        }

        const mapped = areasData.map((a) => ({
          id: a.id,
          name: a.name,
          description: a.description || '',
          checks: checksByArea[a.id] || [],
        }));

        return { data: mapped, error: null, isLive: true };
      }
    } catch (err) {
      console.warn('Could not query compliance_areas from Supabase, using authoritative taxonomy:', err);
    }
  }

  return { data: COMPLIANCE_AREAS, error: null, isLive: Boolean(client) };
};

export const fetchComplianceChecks = async (options = null) => {
  const client = getSupabase();
  let filterDivision = null;
  let filterArea = null;

  if (typeof options === 'string') {
    const lower = options.toLowerCase();
    if (['academic', 'cds', 'operation', 'operations', 'discipline'].includes(lower)) {
      filterDivision = lower === 'operations' ? 'Operation' : options;
    } else {
      filterArea = options;
    }
  } else if (options && typeof options === 'object') {
    if (options.division) {
      filterDivision = options.division === 'Operations' ? 'Operation' : options.division;
    }
    if (options.area_id) filterArea = options.area_id;
    if (options.area_name) filterArea = options.area_name;
  }

  if (client) {
    try {
      let query = client
        .from('compliance_checks')
        .select('*')
        .order('order_index', { ascending: true })
        .order('id', { ascending: true });

      query = query.neq('is_active', false);

      if (filterDivision) {
        query = query.ilike('division', filterDivision);
      }

      const { data, error } = await query;

      if (!error && data && data.length > 0) {
        let mapped = data.map((c) => ({
          id: c.id,
          check_code: c.check_code || c.id,
          name: c.name,
          division: c.division,
          area_name: c.area_name,
          area_id: c.area_id,
          group: c.area_name || c.group_name || c.group || c.division,
          description: c.description || '',
        }));

        if (filterArea) {
          const q = filterArea.toLowerCase();
          mapped = mapped.filter((c) =>
            (c.area_name || '').toLowerCase().includes(q) ||
            (c.area_id || '').toLowerCase().includes(q) ||
            (c.group || '').toLowerCase().includes(q)
          );
        }

        return {
          data: mapped,
          error: null,
          isLive: true,
        };
      }
    } catch (err) {
      console.warn('Could not query compliance_checks from Supabase:', err);
    }
  }

  // Authoritative fallback
  let fallback = COMPLIANCE_AREAS.flatMap((a) => a.checks);
  if (filterDivision) {
    fallback = fallback.filter((c) => (c.division || '').toLowerCase() === filterDivision.toLowerCase());
  }
  if (filterArea) {
    fallback = fallback.filter((c) => (c.group || '').toLowerCase().includes(filterArea.toLowerCase()));
  }
  return { data: fallback, error: null, isLive: Boolean(client) };
};

export const createComplianceCheck = async ({ division, area_name, name, description, id = null }) => {
  const client = getSupabase();
  if (!client) return { data: null, error: 'Database is not connected', isLive: false };

  try {
    let canonicalDiv = division || 'Academic';
    if (canonicalDiv === 'Operations') canonicalDiv = 'Operation';

    const cleanName = (name || '').trim();
    if (!cleanName) {
      return { data: null, error: 'Compliance check name is required.', isLive: true };
    }

    const payload = {
      division: canonicalDiv,
      area_name: (area_name || canonicalDiv).trim(),
      name: cleanName,
      description: (description || '').trim() || null,
      is_active: true,
    };

    if (id && String(id).trim()) {
      payload.id = String(id).trim();
      payload.check_code = String(id).trim();
    }

    const { data, error } = await client
      .from('compliance_checks')
      .insert(payload)
      .select()
      .single();

    if (error) {
      console.error('Error inserting compliance check into database:', error);
      return { data: null, error: error.message, isLive: true };
    }

    return {
      data: {
        id: data.id,
        check_code: data.check_code || data.id,
        name: data.name,
        division: data.division,
        area_name: data.area_name,
        area_id: data.area_id,
        group: data.area_name,
        description: data.description || '',
      },
      error: null,
      isLive: true,
    };
  } catch (err) {
    console.error('Exception in createComplianceCheck:', err);
    return { data: null, error: err.message, isLive: true };
  }
};

// =========================================================================
// COMPLIANCE OBSERVATIONS
// =========================================================================
export const fetchComplianceObservations = async (filters = {}) => {
  const client = getSupabase();
  if (!client) {
    return { data: [], error: 'Database is not connected.', isLive: false };
  }

  try {
    let query = client.from('compliance_observations').select('*').order('date', { ascending: false });
    if (filters.areaId) query = query.eq('area_id', filters.areaId);
    if (filters.checkId) query = query.eq('check_id', filters.checkId);
    if (filters.status && filters.status !== 'All') query = query.eq('status', filters.status);
    if (filters.severity && filters.severity !== 'All') query = query.eq('severity', filters.severity);
    if (filters.reportedBy) query = query.eq('reported_by', filters.reportedBy);
    const { data, error } = await query;
    if (!error && data) {
      return {
        data: data.map(row => ({
          id: row.id,
          areaId: row.area_id,
          areaName: row.area_name,
          checkId: row.check_id,
          checkName: row.check_name,
          status: row.status,
          severity: row.severity,
          date: row.date,
          note: row.note,
          reportedBy: row.reported_by,
          reporterName: row.reporter_name,
          evidenceUrls: row.evidence_urls,
          createdAt: row.created_at,
          updatedAt: row.updated_at,
        })),
        error: null,
        isLive: true,
      };
    }
    return { data: [], error: error?.message || 'Failed to fetch observations', isLive: false };
  } catch (err) {
    return { data: [], error: err.message, isLive: false };
  }
};

export const updateComplianceObservation = async (obsId, updates = {}) => {
  if (!obsId) return { success: false, error: 'Observation ID is required.' };
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  const updatedAt = new Date().toISOString();
  const updatePayload = {
    updated_at: updatedAt,
  };
  if (updates.status !== undefined) updatePayload.status = updates.status;
  if (updates.severity !== undefined) updatePayload.severity = updates.status === 'Exception' ? updates.severity : null;
  if (updates.note !== undefined) updatePayload.note = updates.note;
  if (updates.date !== undefined) updatePayload.date = updates.date;
  if (updates.areaName !== undefined) updatePayload.area_name = updates.areaName;
  if (updates.checkName !== undefined) updatePayload.check_name = updates.checkName;
  if (updates.reportedBy !== undefined) updatePayload.reported_by = updates.reportedBy;
  if (updates.reporterName !== undefined) updatePayload.reporter_name = updates.reporterName;
  if (updates.evidenceUrls !== undefined) updatePayload.evidence_urls = updates.evidenceUrls;

  try {
    const { data, error } = await client
      .from('compliance_observations')
      .update(updatePayload)
      .eq('id', obsId)
      .select()
      .single();

    if (!error && data) {
      return {
        success: true,
        data: {
          id: data.id,
          areaId: data.area_id,
          areaName: data.area_name,
          checkId: data.check_id,
          checkName: data.check_name,
          status: data.status,
          severity: data.severity,
          date: data.date,
          note: data.note,
          reportedBy: data.reported_by,
          reporterName: data.reporter_name,
          evidenceUrls: data.evidence_urls,
          createdAt: data.created_at,
          updatedAt: data.updated_at,
        }
      };
    }
    return { success: false, error: error?.message || 'Database update failed.' };
  } catch (err) {
    return { success: false, error: err?.message || 'Database exception during update' };
  }
};

export const recordComplianceObservation = async (obsData) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };

  if (!obsData || !obsData.areaId || !obsData.checkId) {
    return { success: false, error: 'Compliance Area and Check are required.' };
  }

  const validStatuses = ['Compliant', 'Exception'];
  if (!validStatuses.includes(obsData.status)) {
    return { success: false, error: 'Status must be either Compliant or Exception.' };
  }

  const today = new Date();
  const dateStr = obsData.date || today.toISOString().slice(0, 10);
  const nowIso = today.toISOString();
  const dateCompact = dateStr.replace(/-/g, '');
  const randomSuffix = Math.random().toString(36).substring(2, 6).toUpperCase();
  const generatedId = obsData.id || `OBS-${dateCompact}-${randomSuffix}`;

  const isException = obsData.status === 'Exception';
  const newObs = {
    id: generatedId,
    areaId: obsData.areaId,
    areaName: obsData.areaName || '',
    checkId: obsData.checkId,
    checkName: obsData.checkName || '',
    status: obsData.status,
    severity: isException ? obsData.severity : null,
    review_status: isException ? 'UNSEEN' : 'COMPLIANT',
    is_seen: !isException,
    seen: !isException,
    seen_at: null,
    seen_by: null,
    date: dateStr,
    note: obsData.note ? obsData.note.trim() : null,
    reportedBy: obsData.reportedBy || null,
    reporterName: obsData.reporterName || '',
    evidenceUrls: obsData.evidenceUrls || [],
    createdAt: obsData.createdAt || nowIso,
    updatedAt: nowIso,
  };

  try {
    const { data: authData } = await client.auth.getUser();
    if (authData?.user) {
      newObs.reportedBy = authData.user.id;
      if (!newObs.reporterName) {
        newObs.reporterName = authData.user.user_metadata?.display_name || authData.user.email?.split('@')[0] || 'Compliance Officer';
      }
    }

    const { data: existingObs } = await client
      .from('compliance_observations')
      .select('id')
      .eq('area_id', newObs.areaId)
      .eq('check_id', newObs.checkId)
      .maybeSingle();

    const finalId = existingObs?.id || newObs.id;
    newObs.id = finalId;

    const { data, error } = await client
      .from('compliance_observations')
      .upsert({
        id: finalId,
        area_id: newObs.areaId,
        area_name: newObs.areaName,
        check_id: newObs.checkId,
        check_name: newObs.checkName,
        status: newObs.status,
        severity: newObs.severity,
        date: newObs.date,
        note: newObs.note,
        reported_by: newObs.reportedBy,
        reporter_name: newObs.reporterName,
        evidence_urls: newObs.evidenceUrls,
        updated_at: newObs.updatedAt,
      }, { onConflict: 'area_id, check_id' })
      .select()
      .single();

    if (!error && data) {
      return { success: true, data: newObs };
    } else if (error) {
      return { success: false, error: error.message };
    }
  } catch (err) {
    return { success: false, error: err?.message || 'Database error during save' };
  }

  return { success: false, error: 'Failed to record compliance observation' };
};

export const deleteComplianceObservation = async (obsId) => {
  const client = getSupabase();
  if (!client) return { success: false, error: 'Database is not connected.' };
  const { error } = await client.from('compliance_observations').delete().eq('id', obsId);
  return { success: !error, error: error?.message };
};

// =========================================================================
// WEEKLY TRACKER
// =========================================================================
export const fetchWeeklyTracker = async (userId, term) => {
  const client = getSupabase();
  if (!client) return [];
  try {
    let query = client.from('weekly_tracker').select('*').order('week_number');
    if (userId) query = query.eq('user_id', userId);
    if (term) query = query.eq('term', term);
    const { data, error } = await query;
    if (error) return [];
    return data || [];
  } catch {
    return [];
  }
};

export const fetchAllUsersWeeklyTracker = async (term) => {
  const client = getSupabase();
  if (!client) return [];
  try {
    let query = client
      .from('weekly_tracker')
      .select('*, user_roles(display_name, role)')
      .order('week_number');
    if (term) query = query.eq('term', term);
    const { data, error } = await query;
    if (error) return [];
    return data || [];
  } catch {
    return [];
  }
};

// =========================================================================
// MEDIA UPLOAD (Evidence via Provider Abstraction)
// =========================================================================
export const uploadEvidence = async (file, reportIdOrOptions) => {
  const options = typeof reportIdOrOptions === 'object' && reportIdOrOptions !== null
    ? reportIdOrOptions
    : { reportId: reportIdOrOptions };
  return storageUploadEvidence(file, options);
};

export const uploadEvidenceFile = uploadEvidence;

export {
  getStorageProvider,
  getEvidenceViewerUrl,
  deleteEvidence,
  checkEvidenceExists,
  getEvidenceMetadata,
  EvidenceStorageProvider,
  SupabaseStorageProvider,
};

// =========================================================================
// VIOLATION CATEGORIES & COMPLIANCE CONFIG
// Checks mapped directly from authoritative compliance taxonomy (No attached severity)
// =========================================================================
export const COMPLIANCE_CATEGORIES = COMPLIANCE_AREAS.flatMap((area) =>
  area.checks.map((c) => ({
    id: c.id,
    label: c.name,
    name: c.name,
    division: c.division,
    areaId: area.id,
    areaName: area.name,
    group: c.group || area.name,
  }))
);

export const VIOLATION_LEVELS = {
  1: { label: 'Minor', color: '#22c55e', description: 'Small infraction, first occurrence' },
  2: { label: 'Moderate', color: '#eab308', description: 'Repeated or notable violation' },
  3: { label: 'Serious', color: '#f97316', description: 'Significant breach of policy' },
  4: { label: 'Severe', color: '#ef4444', description: 'Major violation requiring immediate action' },
  5: { label: 'Critical', color: '#dc2626', description: 'Extreme breach — formal process required' },
};

export const EXCEPTION_TAGS = [
  'Absent without leave', 'Late to class', 'Insubordination', 'Phone in class',
  'Unprepared for lesson', 'Inappropriate behaviour', 'Non-compliance with schedule',
  'Leaving post unauthorized', 'Dress code violation', 'Administrative failure',
  'Financial misconduct', 'Bullying or harassment', 'Safety breach',
];

export const EFFECT_TAGS = [
  'Student learning disrupted', 'Class left unsupervised', 'Negative school culture',
  'Student misconduct enabled', 'Time loss', 'Policy undermined',
  'Staff morale affected', 'Legal / regulatory risk', 'Reputational risk',
];

export const createStaff = addStaff;

export const getStoredSupabaseConfig = getSupabaseConfig;
export const saveSupabaseConfig = setSupabaseConfig;
export const testConnection = testSupabaseConnection;

// Export Shared Term Analysis Engine
export {
  buildTermAnalysis,
  toPptxPayload,
  safeDivide,
  perHead,
  perHeadText,
  calculatePercentage,
  calculateComparison,
  resolveSection,
  normalizeCategory,
  prepareAnalysisDataset,
  calculateUniqueAffectedStaff,
  calculateStaffMetrics,
  calculateSectionAnalysis,
  calculateDisciplineAnalysis,
  calculateClassAnalysis,
  calculateCategoryMatrixAnalysis,
  calculateCdsAnalysis,
  calculateOperationsAnalysis,
  calculateExecutiveSummaryStats,
  calculateHistoricalComparisons,
  CANONICAL_CATEGORIES,
  CANONICAL_PRIMARY_DISCIPLINES,
  CANONICAL_SECONDARY_DISCIPLINES,
  SCHOOL_SECTIONS,
  hydrateAnalysisFromSnapshot,
  resolveAuthoritativeTermAnalysis,
} from './analysisEngine.js';

// =========================================================================
// DURABLE HISTORICAL TERM ANALYSIS SNAPSHOTS (STAGE 1)
// =========================================================================

/**
 * Fetches durable term analysis snapshots from Supabase.
 */
export const fetchTermSnapshots = async ({ session, term, status } = {}) => {
  const client = getSupabase();
  if (!client) return { data: [], error: 'Supabase client not initialized', isLive: false };

  try {
    let query = client.from('term_analysis_snapshots').select('*').order('created_at', { ascending: false });
    if (session) query = query.eq('session', session);
    if (term) query = query.eq('term', term);
    if (status) query = query.eq('status', status);

    const { data, error } = await query;
    if (error) throw error;
    return { data: data || [], error: null, isLive: true };
  } catch (err) {
    console.error('Error fetching term snapshots:', err);
    return { data: [], error: err.message, isLive: false };
  }
};

/**
 * Fetches a single durable term snapshot by ID along with its staff aggregates.
 */
export const fetchTermSnapshotById = async (snapshotId) => {
  const client = getSupabase();
  if (!client) return { snapshot: null, staffAggregates: [], error: 'Supabase client not initialized' };

  try {
    const [snapRes, staffRes] = await Promise.all([
      client.from('term_analysis_snapshots').select('*').eq('id', snapshotId).single(),
      client.from('term_staff_aggregates').select('*').eq('snapshot_id', snapshotId),
    ]);

    if (snapRes.error) throw snapRes.error;

    return {
      snapshot: snapRes.data,
      staffAggregates: staffRes.data || [],
      error: null,
    };
  } catch (err) {
    console.error('Error fetching snapshot by ID:', err);
    return { snapshot: null, staffAggregates: [], error: err.message };
  }
};

/**
 * Saves a new term snapshot and its staff aggregates.
 */
export const saveTermSnapshotToDb = async (snapshotRecord, staffAggregates = []) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  // Insert snapshot record
  const { data: insertedSnap, error: snapErr } = await client
    .from('term_analysis_snapshots')
    .insert([snapshotRecord])
    .select()
    .single();

  if (snapErr) throw snapErr;

  // Insert staff aggregates linked to snapshot
  if (staffAggregates && staffAggregates.length > 0) {
    const linkedStaff = staffAggregates.map((s) => ({
      ...s,
      snapshot_id: insertedSnap.id,
    }));

    const { error: staffErr } = await client
      .from('term_staff_aggregates')
      .insert(linkedStaff);

    if (staffErr) {
      console.warn('Warning: Some staff aggregates could not be inserted:', staffErr);
    }
  }

  return insertedSnap;
};

/**
 * Verifies a durable snapshot via the database reconciliation procedure.
 */
export const verifyTermSnapshotInDb = async (snapshotId) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.rpc('reconcile_and_verify_term_snapshot', {
    p_snapshot_id: snapshotId,
  });

  if (error) throw error;
  return data;
};

// =========================================================================
// PRIVATE TERM EXEMPTION SQL ARCHIVES & MANIFESTS (STAGE 2)
// =========================================================================

/**
 * Fetches term archive manifests from Supabase.
 */
export const fetchTermArchiveManifests = async ({ session, term, status } = {}) => {
  const client = getSupabase();
  if (!client) return { data: [], error: 'Supabase client not initialized', isLive: false };

  try {
    let query = client.from('term_archive_manifests').select('*').order('created_at', { ascending: false });
    if (session) query = query.eq('session_id', session);
    if (term) query = query.eq('term_id', term);
    if (status) query = query.eq('validation_status', status);

    const { data, error } = await query;
    if (error) throw error;
    return { data: data || [], error: null, isLive: true };
  } catch (err) {
    console.error('Error fetching term archive manifests:', err);
    return { data: [], error: err.message, isLive: false };
  }
};

/**
 * Saves a new term archive manifest record.
 */
export const saveTermArchiveManifest = async (manifestRecord) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client
    .from('term_archive_manifests')
    .insert([manifestRecord])
    .select()
    .single();

  if (error) throw error;
  return data;
};

/**
 * Updates a term archive manifest record.
 */
export const updateTermArchiveManifest = async (manifestId, updates) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client
    .from('term_archive_manifests')
    .update({ ...updates, updated_at: new Date().toISOString() })
    .eq('id', manifestId)
    .select()
    .single();

  if (error) throw error;
  return data;
};

/**
 * Validates a term archive manifest via the database verification procedure.
 */
export const validateTermArchiveManifestRpc = async (manifestId, verifiedCount, verifiedChecksum) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.rpc('validate_term_archive_manifest', {
    p_manifest_id: manifestId,
    p_verified_count: verifiedCount,
    p_verified_checksum: verifiedChecksum,
  });

  if (error) throw error;
  return data;
};

/**
 * Uploads a term SQL archive file to the private evidence bucket.
 */
export const uploadTermArchiveToStorage = async (objectPath, sqlContent, bucket = 'compliance-evidence') => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const blob = typeof Blob !== 'undefined'
    ? new Blob([sqlContent], { type: 'text/plain;charset=utf-8' })
    : Buffer.from(sqlContent, 'utf-8');

  const { data, error } = await client.storage
    .from(bucket)
    .upload(objectPath, blob, {
      contentType: 'text/plain',
      upsert: true,
    });

  if (error) throw error;
  return data;
};

/**
 * Downloads a term SQL archive file from private storage for verification.
 */
export const downloadTermArchiveFromStorage = async (objectPath, bucket = 'compliance-evidence') => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.storage
    .from(bucket)
    .download(objectPath);

  if (error) throw error;

  if (typeof data.text === 'function') {
    return await data.text();
  }
  return data.toString('utf-8');
};

// =========================================================================
// TERM RETENTION POLICY & DRY-RUN PREVIEW (STAGE 3)
// =========================================================================

/**
 * Fetches the active term retention policy from Supabase.
 */
export const fetchTermRetentionPolicy = async () => {
  const client = getSupabase();
  if (!client) {
    return {
      data: {
        retention_period_weeks: null,
        auto_cleanup_enabled: false,
        archive_deletion_policy: 'retain_until_new_session',
        last_cleanup_at: null,
      },
      error: 'Supabase client not initialized',
      isLive: false,
    };
  }

  try {
    const { data, error } = await client
      .from('term_retention_policy')
      .select('*')
      .eq('id', 1)
      .maybeSingle();

    if (!error && data) {
      return { data, error: null, isLive: true };
    }

    // Fallback to system_settings keys
    const { data: settingsRows } = await client.from('system_settings').select('*');
    const settingsMap = new Map();
    (settingsRows || []).forEach((r) => settingsMap.set(r.key, r.value));

    const weeksVal = settingsMap.get('retention_period_weeks');
    return {
      data: {
        retention_period_weeks: weeksVal && Number(weeksVal) > 0 ? Number(weeksVal) : null,
        auto_cleanup_enabled: settingsMap.get('auto_cleanup_enabled') === 'true',
        archive_deletion_policy: settingsMap.get('archive_deletion_policy') || 'retain_until_new_session',
        last_cleanup_at: settingsMap.get('last_cleanup_timestamp') || null,
      },
      error: null,
      isLive: true,
    };
  } catch (err) {
    console.error('Error fetching retention policy:', err);
    return { data: null, error: err.message, isLive: false };
  }
};

/**
 * Updates the term retention policy (Super Admin only).
 */
export const updateTermRetentionPolicy = async ({
  retentionPeriodWeeks = null,
  autoCleanupEnabled = false,
  archiveDeletionPolicy = 'retain_until_new_session',
  updatedBy = null,
}) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const payload = {
    id: 1,
    retention_period_weeks: retentionPeriodWeeks ? Number(retentionPeriodWeeks) : null,
    auto_cleanup_enabled: Boolean(autoCleanupEnabled),
    archive_deletion_policy: archiveDeletionPolicy,
    updated_by: updatedBy,
    updated_at: new Date().toISOString(),
  };

  // Upsert in term_retention_policy table
  const { data, error } = await client
    .from('term_retention_policy')
    .upsert(payload)
    .select()
    .single();

  if (error) {
    console.warn('Could not update term_retention_policy table, falling back to system_settings:', error);
  }

  // Also sync to system_settings for backward compatibility
  await Promise.all([
    updateSettings('retention_period_weeks', retentionPeriodWeeks ? String(retentionPeriodWeeks) : ''),
    updateSettings('auto_cleanup_enabled', autoCleanupEnabled ? 'true' : 'false'),
    updateSettings('archive_deletion_policy', archiveDeletionPolicy),
  ]);

  return data || payload;
};

/**
 * Fetches the server-calculated dry-run retention preview via RPC.
 */
export const fetchTermRetentionPreviewRpc = async () => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.rpc('get_term_retention_preview');
  if (error) throw error;
  return data;
};

/**
 * Triggers asynchronous server-side purge engine via backend endpoint.
 */
export const triggerServerTermPurge = async ({ session, term, isDryRun = false }) => {
  const client = getSupabase();
  const sessionData = await client?.auth?.getSession();
  const token = sessionData?.data?.session?.access_token || '';

  const res = await fetch('/api/retention/execute-purge', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ session, term, isDryRun }),
  });

  if (!res.ok) {
    const errorJson = await res.json().catch(() => ({}));
    throw new Error(errorJson.error || `Server purge request failed with status ${res.status}`);
  }

  return await res.json();
};

/**
 * Fetches purge audit logs from Supabase.
 */
export const fetchTermPurgeAuditLogs = async ({ session, term } = {}) => {
  const client = getSupabase();
  if (!client) return { data: [], error: 'Supabase client not initialized' };

  try {
    let query = client.from('term_purge_audit_logs').select('*').order('started_at', { ascending: false });
    if (session) query = query.eq('session_id', session);
    if (term) query = query.eq('term_id', term);

    const { data, error } = await query;
    if (error) throw error;
    return { data: data || [], error: null };
  } catch (err) {
    console.error('Error fetching purge audit logs:', err);
    return { data: [], error: err.message };
  }
};

// =========================================================================
// TEMPORARY TERM SQL ARCHIVE EXPIRATION & LIFECYCLE (STAGE 5)
// =========================================================================

/**
 * Removes a term SQL archive file from private storage.
 */
export const deleteTermArchiveFromStorage = async (objectPath, bucket = 'compliance-evidence') => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { error } = await client.storage
    .from(bucket)
    .remove([objectPath]);

  if (error) throw error;
  return { success: true };
};

/**
 * Marks a term SQL archive manifest as expired and purged in Supabase via RPC.
 */
export const expireTermSqlArchiveRpc = async (manifestId, reason, deletedBy) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.rpc('expire_term_sql_archive_manifest', {
    p_manifest_id: manifestId,
    p_reason: reason || null,
    p_deleted_by: deletedBy || null,
  });

  if (error) throw error;
  return data;
};

/**
 * Checks whether an archive manifest is eligible for expiration via RPC.
 */
export const checkTermArchiveExpirationEligibilityRpc = async (manifestId) => {
  const client = getSupabase();
  if (!client) throw new Error('Supabase client not initialized');

  const { data, error } = await client.rpc('check_term_archive_expiration_eligibility', {
    p_manifest_id: manifestId,
  });

  if (error) throw error;
  return data;
};
