from pathlib import Path
import re

index_path=Path('app/src/main/assets/index.html')
app_path=Path('app/src/main/assets/app.js')
css_path=Path('app/src/main/assets/styles.css')
kotlin_path=Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')
html=index_path.read_text(encoding='utf-8')
js=app_path.read_text(encoding='utf-8')
css=css_path.read_text(encoding='utf-8')
kt=kotlin_path.read_text(encoding='utf-8')

# Opção visual.
if 'id="keepLogin"' not in html:
    pat=re.compile(r'(<div class="field"><label>Senha</label><input id="loginPass"[^>]*></div>)')
    html,n=pat.subn(r'''\1<label class="rememberline"><input id="keepLogin" type="checkbox" checked><span><b>Manter conectado neste aparelho</b><small>Entra automaticamente ao abrir o APK. Sua senha não é salva.</small></span></label>''',html,count=1)
    if n!=1: raise SystemExit('Campo de senha não encontrado para inserir Manter conectado.')
if '.rememberline{' not in css:
    css+='\n.rememberline{display:flex;align-items:flex-start;gap:10px;margin:12px 0 15px;padding:11px 12px;border:1px solid rgba(139,92,246,.35);border-radius:13px;background:rgba(255,255,255,.045);color:#dce5f8}.rememberline input{width:18px;height:18px;margin:2px 0 0;accent-color:#8b5cf6;flex:0 0 auto}.rememberline span{display:block;line-height:1.35}.rememberline b{display:block;font-size:12px}.rememberline small{display:block;margin-top:3px;color:#8fa2c3;font-size:10px}\n'

# Storage da sessão: SharedPreferences quando marcado; sessionStorage quando desmarcado.
if 'const authStorage=' not in js:
    storage=r'''const authStorage={
 getItem(key){try{const n=window.AndroidApp,keep=n&&n.getKeepLogin?n.getKeepLogin():true;if(!keep)return sessionStorage.getItem(key);let v=n&&n.getPersistentAuth?n.getPersistentAuth(key):'';if(!v)v=localStorage.getItem(key)||'';if(v&&n&&n.setPersistentAuth)n.setPersistentAuth(key,v);return v||null}catch(e){return localStorage.getItem(key)}},
 setItem(key,value){try{const n=window.AndroidApp,keep=n&&n.getKeepLogin?n.getKeepLogin():true;if(keep){localStorage.setItem(key,value);if(n&&n.setPersistentAuth)n.setPersistentAuth(key,value)}else{sessionStorage.setItem(key,value);localStorage.removeItem(key);if(n&&n.removePersistentAuth)n.removePersistentAuth(key)}}catch(e){localStorage.setItem(key,value)}},
 removeItem(key){try{sessionStorage.removeItem(key);localStorage.removeItem(key);const n=window.AndroidApp;if(n&&n.removePersistentAuth)n.removePersistentAuth(key)}catch(e){}}
};
const db=supabase.createClient(SUPABASE_URL,SUPABASE_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,storage:authStorage}});'''
    client=re.compile(r"const db=supabase\.createClient\(SUPABASE_URL,SUPABASE_KEY,\{auth:\{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true\}\}\);")
    js,n=client.subn(lambda _:storage,js,count=1)
    if n!=1: raise SystemExit('Cliente Supabase não encontrado para login persistente.')

# Login define a preferência antes de autenticar.
if "const keep=$('keepLogin')" not in js:
    pat=re.compile(r"\$\('loginForm'\)\.onsubmit=async e=>\{.*?await start\(\)\};",re.S)
    handler="$('loginForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter;const keep=$('keepLogin')?.checked!==false;try{const n=window.AndroidApp;if(n&&n.setKeepLogin)n.setKeepLogin(keep);if(!keep&&n&&n.clearPersistentAuth)n.clearPersistentAuth()}catch(_){}b.disabled=true;b.textContent='Entrando...';const{error}=await db.auth.signInWithPassword({email:$('loginEmail').value.trim(),password:$('loginPass').value});b.disabled=false;b.textContent='Entrar';if(error)return toast(error.message,'err');await start()};"
    js,n=pat.subn(lambda _:handler,js,count=1)
    if n!=1: raise SystemExit('Handler de login não encontrado.')

# Sair remove a sessão persistida.
logout_pat=re.compile(r"\$\('logout'\)\.onclick=async\(\)=>\{.*?\};",re.S)
m=logout_pat.search(js)
if not m: raise SystemExit('Handler de logout não encontrado.')
if 'clearPersistentAuth' not in m.group(0):
    handler="$('logout').onclick=async()=>{await db.auth.signOut();try{const n=window.AndroidApp;if(n&&n.clearPersistentAuth)n.clearPersistentAuth()}catch(_){}profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden')};"
    js=js[:m.start()]+handler+js[m.end():]

# Checkbox: insere imediatamente antes da última chamada start(); sem depender do callback antigo.
if "$('keepLogin').checked=" not in js:
    init="try{if($('keepLogin')){$('keepLogin').checked=window.AndroidApp&&AndroidApp.getKeepLogin?AndroidApp.getKeepLogin():true}}catch(_){}\n"
    pos=js.rfind('start();')
    if pos<0: raise SystemExit('Chamada final start() não encontrada.')
    js=js[:pos]+init+js[pos:]

# Android: armazenamento privado de sessão; senha nunca é gravada.
if 'aws_auth_prefs' not in kt:
    kt,n=re.subn(r'(\s+private lateinit var webView: WebView\n)',r'\1    private val authPrefs by lazy { getSharedPreferences("aws_auth_prefs", MODE_PRIVATE) }\n',kt,count=1)
    if n!=1: raise SystemExit('WebView não encontrada no LauncherActivity.')

if 'fun getKeepLogin()' not in kt:
    marker='    private inner class AndroidBridge {\n'
    if marker not in kt: raise SystemExit('AndroidBridge não encontrado.')
    methods=r'''    private inner class AndroidBridge {
        private fun validAuthKey(key: String): Boolean = key.startsWith("sb-") && key.length in 4..256

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
            if (validAuthKey(key)) authPrefs.edit().remove("auth:$key").apply()
        }

        @JavascriptInterface
        fun clearPersistentAuth() {
            val e=authPrefs.edit()
            authPrefs.all.keys.filter { it.startsWith("auth:") }.forEach { e.remove(it) }
            e.apply()
        }

'''
    kt=kt.replace(marker,methods,1)

checks={
 'checkbox':'id="keepLogin"' in html,
 'storage':'const authStorage=' in js,
 'login':"const keep=$('keepLogin')" in js,
 'native':'fun getPersistentAuth(key: String)' in kt,
 'prefs':'aws_auth_prefs' in kt,
}
missing=[k for k,v in checks.items() if not v]
if missing: raise SystemExit('Login persistente incompleto: '+', '.join(missing))

index_path.write_text(html,encoding='utf-8')
app_path.write_text(js,encoding='utf-8')
css_path.write_text(css,encoding='utf-8')
kotlin_path.write_text(kt,encoding='utf-8')
print('Login persistente validado sem depender da inicialização antiga.')
