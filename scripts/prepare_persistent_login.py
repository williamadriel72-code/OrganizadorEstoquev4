from pathlib import Path
import re

index_path = Path('app/src/main/assets/index.html')
app_path = Path('app/src/main/assets/app.js')
css_path = Path('app/src/main/assets/styles.css')
kotlin_path = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')

html = index_path.read_text(encoding='utf-8')
js = app_path.read_text(encoding='utf-8')
css = css_path.read_text(encoding='utf-8')
kt = kotlin_path.read_text(encoding='utf-8')

# 1) Opção visual no login.
if 'id="keepLogin"' not in html:
    password_field = '<div class="field"><label>Senha</label><input id="loginPass" type="password" placeholder="Digite sua senha" required></div>'
    remember = password_field + '<label class="rememberline"><input id="keepLogin" type="checkbox" checked><span><b>Manter conectado neste aparelho</b><small>Entra automaticamente ao abrir o APK. Sua senha não é salva.</small></span></label>'
    if password_field not in html:
        raise SystemExit('Campo de senha do login não encontrado.')
    html = html.replace(password_field, remember, 1)

if '.rememberline{' not in css:
    css += '\n.rememberline{display:flex;align-items:flex-start;gap:10px;margin:12px 0 15px;padding:11px 12px;border:1px solid rgba(139,92,246,.35);border-radius:13px;background:rgba(255,255,255,.045);color:#dce5f8}.rememberline input{width:18px;height:18px;margin:2px 0 0;accent-color:#8b5cf6;flex:0 0 auto}.rememberline span{display:block;line-height:1.35}.rememberline b{display:block;font-size:12px}.rememberline small{display:block;margin-top:3px;color:#8fa2c3;font-size:10px}\n'

# 2) Sessão do Supabase usa armazenamento nativo quando "Manter conectado" estiver ativo.
old_client = "const db=supabase.createClient(SUPABASE_URL,SUPABASE_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}});"
auth_client = r'''const authStorage={
 getItem(key){try{const native=window.AndroidApp;const keep=native?.getKeepLogin?native.getKeepLogin():true;if(!keep)return sessionStorage.getItem(key);let value=native?.getPersistentAuth?native.getPersistentAuth(key):'';if(!value)value=localStorage.getItem(key)||'';if(value&&native?.setPersistentAuth)native.setPersistentAuth(key,value);return value||null}catch(e){console.warn('Falha ao ler sessão persistente',e);return localStorage.getItem(key)}},
 setItem(key,value){try{const native=window.AndroidApp;const keep=native?.getKeepLogin?native.getKeepLogin():true;if(keep){localStorage.setItem(key,value);native?.setPersistentAuth?.(key,value)}else{sessionStorage.setItem(key,value);localStorage.removeItem(key);native?.removePersistentAuth?.(key)}}catch(e){console.warn('Falha ao salvar sessão persistente',e);localStorage.setItem(key,value)}},
 removeItem(key){try{sessionStorage.removeItem(key);localStorage.removeItem(key);window.AndroidApp?.removePersistentAuth?.(key)}catch(e){console.warn('Falha ao remover sessão persistente',e)}}
};
const db=supabase.createClient(SUPABASE_URL,SUPABASE_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,storage:authStorage}});'''
if old_client not in js:
    raise SystemExit('Criação do cliente Supabase não encontrada.')
js = js.replace(old_client, auth_client, 1)

old_login = "$('loginForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter;b.disabled=true;b.textContent='Entrando...';const{error}=await db.auth.signInWithPassword({email:$('loginEmail').value.trim(),password:$('loginPass').value});b.disabled=false;b.textContent='Entrar';if(error)return toast(error.message,'err');await start()};"
new_login = "$('loginForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter;const keep=$('keepLogin')?.checked!==false;try{window.AndroidApp?.setKeepLogin?.(keep);if(!keep)window.AndroidApp?.clearPersistentAuth?.()}catch(_){}b.disabled=true;b.textContent='Entrando...';const{error}=await db.auth.signInWithPassword({email:$('loginEmail').value.trim(),password:$('loginPass').value});b.disabled=false;b.textContent='Entrar';if(error)return toast(error.message,'err');await start()};"
if old_login not in js:
    raise SystemExit('Handler de login não encontrado.')
js = js.replace(old_login, new_login, 1)

old_logout = "$('logout').onclick=async()=>{await db.auth.signOut();profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden')};"
new_logout = "$('logout').onclick=async()=>{await db.auth.signOut();try{window.AndroidApp?.clearPersistentAuth?.()}catch(_){}profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden')};"
if old_logout not in js:
    raise SystemExit('Handler de logout não encontrado.')
js = js.replace(old_logout, new_logout, 1)

# Inicializa o checkbox com a preferência nativa. Padrão: marcado.
marker = "db.auth.onAuthStateChange(e=>{if(e==='SIGNED_OUT'){$('app').classList.add('hidden');$('auth').classList.remove('hidden')}});start();"
replacement = "try{if($('keepLogin'))$('keepLogin').checked=window.AndroidApp?.getKeepLogin?AndroidApp.getKeepLogin():true}catch(_){}\n" + marker
if marker not in js:
    raise SystemExit('Inicialização final do app não encontrada.')
js = js.replace(marker, replacement, 1)

# 3) Armazenamento nativo privado do Android. Não guarda a senha; guarda somente o token de sessão Supabase.
if 'aws_auth_prefs' not in kt:
    kt = kt.replace(
        '    private lateinit var webView: WebView\n',
        '    private lateinit var webView: WebView\n    private val authPrefs by lazy { getSharedPreferences("aws_auth_prefs", MODE_PRIVATE) }\n',
        1
    )

if 'fun getKeepLogin()' not in kt:
    bridge_marker = '    private inner class AndroidBridge {\n'
    native_methods = r'''    private inner class AndroidBridge {
        private fun validAuthKey(key: String): Boolean =
            key.startsWith("sb-") && key.length in 4..256

        @JavascriptInterface
        fun getKeepLogin(): Boolean = authPrefs.getBoolean("keep_login", true)

        @JavascriptInterface
        fun setKeepLogin(enabled: Boolean) {
            authPrefs.edit().putBoolean("keep_login", enabled).apply()
            if (!enabled) clearPersistentAuth()
        }

        @JavascriptInterface
        fun getPersistentAuth(key: String): String {
            if (!validAuthKey(key) || !getKeepLogin()) return ""
            return authPrefs.getString("auth:$key", "") ?: ""
        }

        @JavascriptInterface
        fun setPersistentAuth(key: String, value: String) {
            if (!validAuthKey(key) || !getKeepLogin()) return
            authPrefs.edit().putString("auth:$key", value).apply()
        }

        @JavascriptInterface
        fun removePersistentAuth(key: String) {
            if (!validAuthKey(key)) return
            authPrefs.edit().remove("auth:$key").apply()
        }

        @JavascriptInterface
        fun clearPersistentAuth() {
            val editor = authPrefs.edit()
            authPrefs.all.keys.filter { it.startsWith("auth:") }.forEach { editor.remove(it) }
            editor.apply()
        }

'''
    if bridge_marker not in kt:
        raise SystemExit('AndroidBridge não encontrado.')
    kt = kt.replace(bridge_marker, native_methods, 1)

index_path.write_text(html, encoding='utf-8')
app_path.write_text(js, encoding='utf-8')
css_path.write_text(css, encoding='utf-8')
kotlin_path.write_text(kt, encoding='utf-8')
print('Login persistente preparado: sessão Supabase nativa opcional, sem armazenar senha.')
