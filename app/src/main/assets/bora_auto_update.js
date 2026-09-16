(() => {
  const SUPA_URL = 'https://rlgsbtolosxyymosidns.supabase.co';
  const SUPA_KEY = 'sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde';
  const LATEST_URL = SUPA_URL + '/functions/v1/latest-app-version';
  const FALLBACK_BUILD_CODE = 50020;
  let updateRunning = false;

  function currentCode() {
    try {
      if (window.AndroidApp && typeof AndroidApp.getAppVersionCode === 'function') {
        return Number(AndroidApp.getAppVersionCode()) || FALLBACK_BUILD_CODE;
      }
    } catch (_) {}
    return FALLBACK_BUILD_CODE;
  }

  function showNotice(text) {
    try {
      let el = document.getElementById('boraUpdateNotice');
      if (!el) {
        el = document.createElement('div');
        el.id = 'boraUpdateNotice';
        el.style.cssText = 'position:fixed;left:12px;right:12px;bottom:112px;z-index:99999;background:#111827;color:#fff;border:1px solid #334155;border-radius:12px;padding:11px 13px;text-align:center;font:600 12px system-ui;box-shadow:0 10px 30px #0008';
        document.body.appendChild(el);
      }
      el.textContent = text || '';
      el.style.display = text ? 'block' : 'none';
      clearTimeout(window.__boraUpdateNoticeTimer);
      if (text) window.__boraUpdateNoticeTimer = setTimeout(() => { el.style.display = 'none'; }, 5000);
    } catch (_) {}
  }

  function setUpdateStatus(text) {
    try {
      const el = document.getElementById('loginMsg');
      if (el && !document.getElementById('login')?.classList.contains('hidden')) el.textContent = text || '';
    } catch (_) {}
    if (text) showNotice(text);
  }

  function makeButton(id, className, text = 'Atualizar app') {
    if (document.getElementById(id)) return null;
    const b = document.createElement('button');
    b.id = id;
    b.type = 'button';
    b.textContent = text;
    b.className = className;
    b.onclick = () => checkForUpdate(true);
    return b;
  }

  function installButtons() {
    try {
      // Garante que nenhuma versão antiga do botão permaneça no cabeçalho.
      document.getElementById('boraUpdateTop')?.remove();

      const bottom = document.querySelector('#app .bottomin');
      const sync = document.getElementById('sync');
      const bottomBtn = makeButton('boraUpdateBottom', 'btn updateBottom', 'Atualizar app');
      if (bottom && bottomBtn) {
        if (sync) bottom.insertBefore(bottomBtn, sync);
        else bottom.appendChild(bottomBtn);
      }

      const loginBox = document.querySelector('#login .loginbox');
      const loginMsg = document.getElementById('loginMsg');
      const loginBtn = makeButton('boraUpdateLogin', 'btn ghost', 'Atualizar app');
      if (loginBox && loginBtn) {
        loginBtn.style.cssText = 'width:100%;margin-top:9px;border:1px solid #334155;background:#172033;color:#fff;border-radius:12px;padding:11px 14px;font-weight:850';
        if (loginMsg) loginBox.insertBefore(loginBtn, loginMsg);
        else loginBox.appendChild(loginBtn);
      }
    } catch (_) {}
  }

  async function checkForUpdate(manual = false) {
    if (updateRunning) {
      if (manual) setUpdateStatus('Uma atualização já está em andamento.');
      return;
    }
    if (!window.AndroidApp || typeof AndroidApp.downloadAndInstallUpdate !== 'function') {
      if (manual) setUpdateStatus('Atualização disponível somente dentro do APK.');
      return;
    }
    try {
      if (manual) setUpdateStatus('Procurando atualização...');
      const response = await fetch(LATEST_URL, {
        method: 'GET',
        cache: 'no-store',
        headers: { 'Accept': 'application/json', 'apikey': SUPA_KEY }
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const u = await response.json();
      const remoteCode = Number(u?.version_code || 0);
      if (!remoteCode || remoteCode <= currentCode()) {
        if (manual) setUpdateStatus('Seu aplicativo já está na versão mais recente.');
        return;
      }
      const url = String(u?.download_url || '').trim();
      if (!/^https:\/\//i.test(url)) throw new Error('Link de atualização inválido.');
      updateRunning = true;
      setUpdateStatus(`Nova versão ${u.version_name || remoteCode} encontrada. Baixando...`);
      AndroidApp.downloadAndInstallUpdate(url, String(u.version_name || remoteCode));
    } catch (e) {
      console.warn('Falha ao verificar atualização automática', e);
      if (manual) setUpdateStatus('Não foi possível verificar a atualização agora.');
    }
  }

  window.checkBoraUpdateNow = () => checkForUpdate(true);
  window.onNativeUpdateProgress = (percent, downloaded, total, state) => {
    const pct = Number(percent);
    setUpdateStatus(`${state || 'Atualizando aplicativo...'}${pct >= 0 ? ` ${pct}%` : ''}`);
  };
  window.onNativeUpdateReady = () => setUpdateStatus('Atualização pronta. Abrindo instalador...');
  window.onNativeUpdatePermissionRequired = () => setUpdateStatus('Autorize a instalação desta atualização para continuar.');
  window.onNativeUpdateError = (message) => {
    updateRunning = false;
    setUpdateStatus(message || 'Não foi possível atualizar agora.');
  };

  installButtons();
  setTimeout(installButtons, 500);
  setTimeout(() => checkForUpdate(false), 1200);
  setInterval(() => checkForUpdate(false), 15 * 60 * 1000);
})();
