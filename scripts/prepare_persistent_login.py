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

# 1) Interface: opção para manter a sessão salva.
if 'id="keepLogin"' not in html:
    password_pattern = re.compile(r'(<div class="field"><label>Senha</label><input id="loginPass"[^>]*></div>)')
    remember = r'''\1<label class="rememberline"><input id="keepLogin" type="checkbox" checked><span><b>Manter conectado neste aparelho</b><small>Entra automaticamente ao abrir o APK. Sua senha não é salva.</small></span></label>'''
    html, n = password_pattern.subn(remember, html, count=1)
    if n != 1:
        raise SystemExit('Não foi possível inserir a opção Manter conectado.')

if '.rememberline{' not in css:
    css += '\n.rememberline{display:flex;align-items:flex-start;gap:10px;margin:12px 0 15px;padding:11px 12px;border:1px solid rgba(139,92,246,.35);border-radius:13px;background:rgba(255,255,255,.045);color:#dce5f8}.rememberline input{width:18px;height:18px;margin:2px 0 0;accent-color:#8b5cf6;flex:0 0 auto}.rememberline span{display:block;line-height:1.35}.rememberline b{display:block;font-size:12px}.rememberline small{display:block;margin-top:3px;color:#8fa2c3;font-size:10px}\n'

# 2) Supabase: usa uma camada de armazenamento que persiste no Android quando solicitado.
if 'const authStorage=' not in js:
    auth_client = r'''const authStorage={
 getItem(key){try{const native=window.AndroidApp;const keep=native&&native.getKeepLogin?native.getKeepLogin():true;if(!keep)return sessionStorage.getItem(key);let value=native&&native.getPersistentAuth?native.getPersistentAuth(key):'';if(!value)value=localStorage.getItem(key)||'';if(value&&native&&native.setPersistentAuth)native.setPersistentAuth(key,value);return value||null}catch(e){console.warn('Falha ao ler sessão persistente',e);return localStorage.getItem(key)}},
 setItem(key,value){try{const native=window.AndroidApp;const keep=native&&native.getKeepLogin?native.getKeepLogin():true;if(keep){localStorage.setItem(key,value);if(native&&native.setPersistentAuth)native.setPersistentAuth(key,value)}else{sessionStorage.setItem(key,value);localStorage.removeItem(key);if(native&&native.removePersistentAuth)native.removePersistentAuth(key)}}catch(e){console.warn('Falha ao salvar sessão persistente',e);localStorage.setItem(key,value)}},
 removeItem(key){try{sessionStorage.removeItem(key);localStorage.removeItem(key);const native=window.AndroidApp;if(native&&native.removePersistentAuth)native.removePersistentAuth(key)}catch(e){console.warn('Falha ao remover sessão persistente',e)}}
};
const db=supabase.createClient(SUPABASE_URL,SUPABASE_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,storage:authStorage}});'''
    client_pattern = re.compile(r"const db=supabase\.createClient\(SUPABASE_URL,SUPABASE_KEY,\{auth:\{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true(?:,storage:[^}]*)?\}\}\);")
    js, n = client_pattern.subn(lambda _: auth_client, js, count=1)
    if n != 1:
        raise SystemExit('Cliente Supabase não encontrado para aplicar persistência.')

# Login: define a preferência ANTES do signIn, para o token já nascer no storage correto.
if "const keep=$('keepLogin')" not in js:
    login_pattern = re.compile(r"\$\('loginForm'\)\.onsubmit=async e=>\{.*?await start\(\)\};", re.S)
    login_handler = "$('loginForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter;const keep=$('keepLogin')?.checked!==false;try{const native=window.AndroidApp;if(native&&native.setKeepLogin)native.setKeepLogin(keep);if(!keep&&native&&native.clearPersistentAuth)native.clearPersistentAuth()}catch(_){}b.disabled=true;b.textContent='Entrando...';const{error}=await db.auth.signInWithPassword({email:$('loginEmail').value.trim(),password:$('loginPass').value});b.disabled=false;b.textContent='Entrar';if(error)return toast(error.message,'err');await start()};"
    js, n = login_pattern.subn(lambda _: login_handler, js, count=1)
    if n != 1:
        raise SystemExit('Handler de login não encontrado.')

# Logout explícito sempre remove a sessão salva.
if 'clearPersistentAuth' not in re.search(r"\$\('logout'\)\.onclick=async\(\)=>\{.*?\};", js, re.S).group(0) if re.search(r"\$\('logout'\)\.onclick=async\(\)=>\{.*?\};", js, re.S) else '':
    logout_pattern = re.compile(r"\$\('logout'\)\.onclick=async\(\)=>\{.*?\};", re.S)
    logout_handler = "$('logout').onclick=async()=>{await db.auth.signOut();try{const native=window.AndroidApp;if(native&&native.clearPersistentAuth)native.clearPersistentAuth()}catch(_){}profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden')};"
    js, n = logout_pattern.subn(lambda _: logout_handler, js, count=1)
    if n != 1:
        raise SystemExit('Handler de logout não encontrado.')

# Checkbox reflete a preferência salva no aparelho. O padrão é ligado.
if "$('keepLogin').checked=" not in js:
    init_line = "try{if($('keepLogin')){$('keepLogin').checked=window.AndroidApp&&AndroidApp.getKeepLogin?AndroidApp.getKeepLogin():true}}catch(_){}\n"
    anchor = 'db.auth.onAuthStateChange'
    if anchor not in js:
        raise SystemExit('Inicialização de autenticação não encontrada.')
    js = js.replace(anchor, init_line + anchor, 1)

# 3) Android: SharedPreferences privado para a sessão. A senha nunca é gravada.
if 'aws_auth_prefs' not in kt:
    kt, n = re.subn(
        r'(\s+private lateinit var webView: WebView\n)',
        r'\1    private val authPrefs by lazy { getSharedPreferences("aws_auth_prefs", MODE_PRIVATE) }\n',
        kt,
        count=1
    )
    if n != 1:
        raise SystemExit('Campo WebView não encontrado no LauncherActivity.')

if 'fun getKeepLogin()' not in kt:
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
    marker = '    private inner class AndroidBridge {\n'
    if marker not in kt:
        raise SystemExit('AndroidBridge não encontrado.')
    kt = kt.replace(marker, native_methods, 1)

# Verificação final da transformação.
checks = {
    'checkbox': 'id="keepLogin"' in html,
    'storage-js': 'const authStorage=' in js,
    'login-js': "const keep=$('keepLogin')" in js,
    'prefs-native': 'aws_auth_prefs' in kt,
    'bridge-native': 'fun getPersistentAuth(key: String)' in kt,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('Login persistente incompleto: ' + ', '.join(missing))

index_path.write_text(html, encoding='utf-8')
app_path.write_text(js, encoding='utf-8')
css_path.write_text(css, encoding='utf-8')
kotlin_path.write_text(kt, encoding='utf-8')
print('Login persistente validado: opção visual, sessão Supabase e armazenamento Android prontos.')
