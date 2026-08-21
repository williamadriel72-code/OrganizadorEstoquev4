from pathlib import Path
import re

index_path=Path('app/src/main/assets/index.html')
app_path=Path('app/src/main/assets/app.js')
css_path=Path('app/src/main/assets/styles.css')
html=index_path.read_text(encoding='utf-8')
js=app_path.read_text(encoding='utf-8')
css=css_path.read_text(encoding='utf-8')

# Login 100% numérico, sem e-mail visível.
auth=r'''<section id="auth" class="login">
  <div class="loginbox">
    <div class="brand"><div class="brandicon">📦</div><h1>AWS</h1><p>Gestão de Estoque</p></div>
    <div class="authcard">
      <div class="tabs"><button id="tabLogin" class="tab active">Entrar</button><button id="tabSignup" class="tab hidden">Primeiro acesso</button></div>
      <form id="loginForm">
        <div class="field"><label>Usuário</label><input id="loginUser" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="username" maxlength="20" placeholder="Somente números" required></div>
        <div class="field"><label>Senha</label><input id="loginPass" type="password" inputmode="numeric" pattern="[0-9]*" autocomplete="current-password" maxlength="20" placeholder="Somente números" required></div>
        <label class="rememberline"><input id="keepLogin" type="checkbox" checked><span><b>Manter conectado neste aparelho</b><small>Entra automaticamente ao abrir o APK. Sua senha não é salva.</small></span></label>
        <button class="primary" type="submit">Entrar</button>
      </form>
      <form id="signupForm" class="hidden">
        <div class="field"><label>Nome completo</label><input id="signupName" autocomplete="name" maxlength="120" placeholder="Nome do administrador" required></div>
        <div class="field"><label>Usuário</label><input id="signupUser" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="20" placeholder="Somente números" required></div>
        <div class="field"><label>Senha</label><input id="signupPass" type="password" inputmode="numeric" pattern="[0-9]*" minlength="6" maxlength="20" placeholder="Mínimo 6 números" required></div>
        <button class="primary" type="submit">Criar primeiro administrador</button>
      </form>
      <div class="note">🔢 Usuário e senha funcionam somente com números.</div>
      <div style="font-size:9px;line-height:1.3;color:#7184a6;text-align:center;margin-top:7px;opacity:.58">Desenvolvido por Adriel William Silva</div>
    </div>
  </div>
</section>'''
html,n=re.subn(r'<section id="auth" class="login">.*?</section>\s*\n\s*<section id="app"',auth+'\n\n<section id="app"',html,count=1,flags=re.S)
if n!=1: raise SystemExit('Bloco de login não encontrado.')

# Card de usuários no painel.
if 'id="usersCard"' not in html:
    marker='<button id="updatesCard" class="action hidden" data-view-open="updates">'
    pos=html.find(marker)
    if pos<0: raise SystemExit('Card de atualizações não encontrado.')
    card='<button id="usersCard" class="action hidden" data-view-open="users"><div class="icon">👥</div><h3>Usuários</h3><p>Criar, editar, bloquear e redefinir senha.</p></button>\n        '
    html=html[:pos]+card+html[pos:]

# Tela administrativa de usuários.
if '<section id="users"' not in html:
    users=r'''
    <section id="users" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Usuários</h2><small>Acesso por usuário e senha numéricos</small></div></div>
      <div class="panel">
        <h3 id="usrFormTitle">Novo usuário</h3>
        <input id="usrId" type="hidden">
        <div class="formgrid">
          <div class="full"><label>Nome completo</label><input id="usrName" maxlength="120" placeholder="Nome do usuário"></div>
          <div><label>Usuário</label><input id="usrLogin" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="20" placeholder="Somente números"></div>
          <div><label>Senha</label><input id="usrPin" type="password" inputmode="numeric" pattern="[0-9]*" minlength="6" maxlength="20" placeholder="6 a 20 números"></div>
          <div><label>Perfil</label><select id="usrRole"><option value="funcionario">Funcionário</option><option value="gerente">Gerente</option><option value="administrador">Administrador</option></select></div>
          <label class="checkline"><input id="usrActive" type="checkbox" checked><span><b>Usuário ativo</b><br><small>Desative para bloquear o acesso.</small></span></label>
        </div>
        <div class="actions"><button id="usrSave" class="purple">＋ Criar usuário</button><button id="usrCancel" class="secondary hidden">Cancelar edição</button></div>
        <div id="usrStatus" class="status"></div>
      </div>
      <div class="panel"><h3>Usuários cadastrados</h3><div id="usersList"><div class="empty">Carregando...</div></div></div>
    </section>
'''
    marker='    <section id="updates" class="hidden">'
    if marker not in html: raise SystemExit('Seção de atualizações não encontrada.')
    html=html.replace(marker,users+'\n'+marker,1)

# Utilidades e autenticação numérica.
helper=r'''const NUMERIC_USERS_URL=`${SUPABASE_URL}/functions/v1/numeric-users`;
function digits(v){return String(v??'').replace(/\D+/g,'')}
function technicalEmail(login){return `u${digits(login)}@login.awsestoque.app`}
function bindDigits(id){const el=$(id);if(el)el.addEventListener('input',()=>{const v=digits(el.value);if(el.value!==v)el.value=v})}
async function numericUserApi(action,payload={},withAuth=true){
 const headers={'Content-Type':'application/json','apikey':SUPABASE_KEY};
 if(withAuth){const{data:{session}}=await db.auth.getSession();if(!session)throw Error('Sessão expirada.');headers.Authorization=`Bearer ${session.access_token}`}
 const r=await fetch(NUMERIC_USERS_URL,{method:'POST',headers,body:JSON.stringify({action,...payload})});
 let d={};try{d=await r.json()}catch(_){ }
 if(!r.ok)throw Error(d.error||'Falha ao gerenciar usuários.');return d
}
async function refreshBootstrapState(){try{const d=await numericUserApi('status',{},false);$('tabSignup')?.classList.toggle('hidden',!d.needs_bootstrap);if(!d.needs_bootstrap&&!$('signupForm')?.classList.contains('hidden'))setMode(false)}catch(e){console.warn(e)}}
'''
if 'const NUMERIC_USERS_URL=' not in js:
    pos=js.find('function setMode(')
    if pos<0: raise SystemExit('setMode não encontrado.')
    js=js[:pos]+helper+js[pos:]

# Troca login/cadastro antigos por fluxo numérico.
auth_js=r'''function setMode(signup){$('loginForm').classList.toggle('hidden',signup);$('signupForm').classList.toggle('hidden',!signup);$('tabLogin').classList.toggle('active',!signup);$('tabSignup').classList.toggle('active',signup)}
$('tabLogin').onclick=()=>setMode(false);$('tabSignup').onclick=()=>setMode(true);
$('loginForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter,login=digits($('loginUser').value),pin=digits($('loginPass').value);$('loginUser').value=login;$('loginPass').value=pin;if(!login)return toast('Digite o usuário numérico.','err');if(pin.length<6)return toast('A senha deve ter pelo menos 6 números.','err');const keep=$('keepLogin')?.checked!==false;try{const n=window.AndroidApp;if(n&&n.setKeepLogin)n.setKeepLogin(keep);if(!keep&&n&&n.clearPersistentAuth)n.clearPersistentAuth()}catch(_){}b.disabled=true;b.textContent='Entrando...';const{error}=await db.auth.signInWithPassword({email:technicalEmail(login),password:pin});b.disabled=false;b.textContent='Entrar';if(error)return toast('Usuário ou senha inválidos.','err');$('loginPass').value='';await start()};
$('signupForm').onsubmit=async e=>{e.preventDefault();const b=e.submitter,nome=$('signupName').value.trim(),login=digits($('signupUser').value),pin=digits($('signupPass').value);$('signupUser').value=login;$('signupPass').value=pin;if(!nome)return toast('Informe o nome.','err');if(!login)return toast('Informe o usuário numérico.','err');if(pin.length<6)return toast('A senha deve ter pelo menos 6 números.','err');b.disabled=true;b.textContent='Criando...';try{await numericUserApi('bootstrap',{nome,login,pin},false);const{error}=await db.auth.signInWithPassword({email:technicalEmail(login),password:pin});if(error)throw error;setMode(false);await start();toast('Administrador criado.','ok')}catch(err){toast(err.message||'Erro ao criar administrador.','err')}finally{b.disabled=false;b.textContent='Criar primeiro administrador'}};'''
pat=re.compile(r"function setMode\(signup\).*?\$\('signupForm'\)\.onsubmit=async e=>\{.*?\};",re.S)
js,n=pat.subn(lambda _:auth_js,js,count=1)
if n!=1: raise SystemExit('Handlers de autenticação não encontrados.')

# Perfil deve existir de verdade: sessão antiga sem perfil é encerrada.
js=js.replace(".select('nome,papel,filial_id,ativo')", ".select('nome,papel,filial_id,ativo,login_numero')")
old="profile=data||{nome:session.user.email.split('@')[0],papel:'funcionario',filial_id:null,ativo:true};\n if(profile.ativo===false){await db.auth.signOut();return toast('Usuário desativado.','err')}"
new="if(error||!data){await db.auth.signOut();profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden');await refreshBootstrapState();return toast('Acesso não cadastrado.','err')}\n profile=data;\n if(profile.ativo===false){await db.auth.signOut();profile=null;currentUser=null;$('app').classList.add('hidden');$('auth').classList.remove('hidden');return toast('Usuário desativado.','err')}"
if old in js: js=js.replace(old,new,1)
else:
    js,n=re.subn(r"profile=data\|\|\{.*?\};\s*if\(profile\.ativo===false\)\{.*?\}",new,js,count=1,flags=re.S)
    if n!=1: raise SystemExit('Validação de perfil não encontrada.')

# Mostra gestão somente para administrador.
needle="$('updatesCard').classList.toggle('hidden',profile.papel!=='administrador');"
if needle in js and "$('usersCard').classList.toggle" not in js:
    js=js.replace(needle,needle+"\n $('usersCard').classList.toggle('hidden',profile.papel!=='administrador');",1)

# Navegação inclui usuários.
js=js.replace("for(const id of['home','search','imports','updates'])", "for(const id of['home','search','imports','updates','users'])")
js=js.replace("if(v==='updates')loadLatestVersion()", "if(v==='updates')loadLatestVersion();if(v==='users')loadNumericUsers()")

# Administração de usuários.
users_js=r'''
let numericUsers=[];
function roleLabel(v){return({administrador:'Administrador',gerente:'Gerente',funcionario:'Funcionário'})[v]||v}
function resetUserForm(){if(!$('usrId'))return;$('usrId').value='';$('usrName').value='';$('usrLogin').value='';$('usrPin').value='';$('usrRole').value='funcionario';$('usrActive').checked=true;$('usrFormTitle').textContent='Novo usuário';$('usrSave').textContent='＋ Criar usuário';$('usrCancel').classList.add('hidden');$('usrPin').placeholder='6 a 20 números'}
function renderNumericUsers(){if(!$('usersList'))return;if(!numericUsers.length){$('usersList').innerHTML='<div class="empty">Nenhum usuário cadastrado.</div>';return}$('usersList').innerHTML=numericUsers.map(u=>`<div class="userrow"><div class="userinfo"><b>${esc(u.nome||'Usuário')}</b><small>Usuário ${esc(u.login_numero||'—')} · ${roleLabel(u.papel)}</small><span class="userstate ${u.ativo?'on':'off'}">${u.ativo?'ATIVO':'INATIVO'}</span></div><div class="useractions"><button class="secondary" onclick="editNumericUser('${u.usuario_id}')">Editar</button><button class="dangerbtn" onclick="deleteNumericUser('${u.usuario_id}')">Excluir</button></div></div>`).join('')}
async function loadNumericUsers(){if(profile?.papel!=='administrador')return;try{const d=await numericUserApi('list');numericUsers=d.users||[];renderNumericUsers()}catch(e){$('usersList').innerHTML=`<div class="empty">${esc(e.message||'Erro ao carregar usuários.')}</div>`}}
window.editNumericUser=id=>{const u=numericUsers.find(x=>x.usuario_id===id);if(!u)return;$('usrId').value=u.usuario_id;$('usrName').value=u.nome||'';$('usrLogin').value=u.login_numero||'';$('usrPin').value='';$('usrPin').placeholder='Deixe vazio para manter a senha';$('usrRole').value=u.papel;$('usrActive').checked=!!u.ativo;$('usrFormTitle').textContent='Editar usuário';$('usrSave').textContent='✓ Salvar alterações';$('usrCancel').classList.remove('hidden');window.scrollTo({top:0,behavior:'smooth'})};
window.deleteNumericUser=async id=>{const u=numericUsers.find(x=>x.usuario_id===id);if(!u||!confirm(`Excluir ${u.nome}?`))return;try{await numericUserApi('delete',{usuario_id:id});toast('Usuário excluído.','ok');resetUserForm();await loadNumericUsers()}catch(e){toast(e.message||'Erro ao excluir.','err')}};
if($('usrCancel'))$('usrCancel').onclick=resetUserForm;
if($('usrSave'))$('usrSave').onclick=async()=>{const id=$('usrId').value,nome=$('usrName').value.trim(),login=digits($('usrLogin').value),pin=digits($('usrPin').value),papel=$('usrRole').value,ativo=$('usrActive').checked;$('usrLogin').value=login;$('usrPin').value=pin;if(!nome)return toast('Informe o nome.','err');if(!login)return toast('Informe o usuário numérico.','err');if(!id&&pin.length<6)return toast('A senha precisa ter pelo menos 6 números.','err');if(id&&pin&&pin.length<6)return toast('A nova senha precisa ter pelo menos 6 números.','err');const b=$('usrSave');b.disabled=true;b.textContent='Salvando...';try{if(id){await numericUserApi('update',{usuario_id:id,nome,login,papel,ativo});if(pin)await numericUserApi('reset_pin',{usuario_id:id,pin})}else{await numericUserApi('create',{nome,login,pin,papel,ativo})}toast(id?'Usuário atualizado.':'Usuário criado.','ok');resetUserForm();await loadNumericUsers()}catch(e){toast(e.message||'Erro ao salvar usuário.','err')}finally{b.disabled=false;if(!$('usrId').value)b.textContent='＋ Criar usuário'}};
['loginUser','loginPass','signupUser','signupPass','usrLogin','usrPin'].forEach(bindDigits);
'''
if 'let numericUsers=[];' not in js:
    pos=js.find('function parseProgress(')
    if pos<0: raise SystemExit('Ponto de inserção da gestão de usuários não encontrado.')
    js=js[:pos]+users_js+'\n'+js[pos:]

# Voltar do Android inclui a nova tela.
js=js.replace("!$('updates').classList.contains('hidden')", "!$('updates').classList.contains('hidden')||!$('users').classList.contains('hidden')")

# Atualiza estado de primeiro acesso antes da inicialização final.
last=js.rfind('start();')
if last<0: raise SystemExit('start final não encontrado.')
if 'refreshBootstrapState();\nstart();' not in js[last-80:last+80]:
    js=js[:last]+'refreshBootstrapState();\n'+js[last:]

if '.userrow{' not in css:
    css+='''\n.userrow{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 0;border-bottom:1px solid rgba(255,255,255,.08)}.userrow:last-child{border-bottom:0}.userinfo{display:flex;flex-direction:column;gap:3px;min-width:0}.userinfo b{font-size:14px}.userinfo small{color:#8fa2c3}.userstate{display:inline-flex;width:max-content;font-size:9px;font-weight:800;padding:3px 7px;border-radius:999px}.userstate.on{background:rgba(34,197,94,.15);color:#7ee2a1}.userstate.off{background:rgba(239,68,68,.15);color:#ff9a9a}.useractions{display:flex;gap:6px;flex-wrap:wrap;justify-content:flex-end}.dangerbtn{border:1px solid rgba(239,68,68,.4);background:rgba(239,68,68,.1);color:#ff9a9a;border-radius:10px;padding:9px 11px;font-weight:700}@media(max-width:520px){.userrow{align-items:flex-start;flex-direction:column}.useractions{width:100%}.useractions button{flex:1}}\n'''

checks={
 'login_user':'id="loginUser"' in html,
 'numeric_keyboard':'inputmode="numeric"' in html,
 'users_screen':'<section id="users"' in html,
 'numeric_api':'const NUMERIC_USERS_URL=' in js,
 'technical_email':'login.awsestoque.app' in js,
 'users_logic':'let numericUsers=[];' in js,
 'profile_guard':"Acesso não cadastrado." in js,
}
missing=[k for k,v in checks.items() if not v]
if missing: raise SystemExit('Sistema numérico incompleto: '+', '.join(missing))

index_path.write_text(html,encoding='utf-8')
app_path.write_text(js,encoding='utf-8')
css_path.write_text(css,encoding='utf-8')
print('Usuarios numericos preparados: login, primeiro administrador e gestao completa.')
