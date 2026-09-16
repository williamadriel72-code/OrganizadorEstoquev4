(() => {
  const SUPA_URL = 'https://rlgsbtolosxyymosidns.supabase.co';
  const SUPA_KEY = 'sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde';
  const LATEST_URL = SUPA_URL + '/functions/v1/latest-app-version';
  const FALLBACK_BUILD_CODE = 50018;
  let updateRunning = false;

  function currentCode() {
    try {
      if (window.AndroidApp && typeof AndroidApp.getAppVersionCode === 'function') {
        return Number(AndroidApp.getAppVersionCode()) || FALLBACK_BUILD_CODE;
      }
    } catch (_) {}
    return FALLBACK_BUILD_CODE;
  }

  function setUpdateStatus(text) {
    try {
      const el = document.getElementById('loginMsg');
      if (el && !document.getElementById('login')?.classList.contains('hidden')) {
        el.textContent = text || '';
      }
    } catch (_) {}
  }

  async function checkForUpdate() {
    if (updateRunning) return;
    if (!window.AndroidApp || typeof AndroidApp.downloadAndInstallUpdate !== 'function') return;
    try {
      const response = await fetch(LATEST_URL, {
        method: 'GET',
        cache: 'no-store',
        headers: {
          'Accept': 'application/json',
          'apikey': SUPA_KEY
        }
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const u = await response.json();
      const remoteCode = Number(u?.version_code || 0);
      if (!remoteCode || remoteCode <= currentCode()) return;
      const url = String(u?.download_url || '').trim();
      if (!/^https:\/\//i.test(url)) return;
      updateRunning = true;
      setUpdateStatus(`Atualizando para ${u.version_name || remoteCode}...`);
      AndroidApp.downloadAndInstallUpdate(url, String(u.version_name || remoteCode));
    } catch (e) {
      console.warn('Falha ao verificar atualização automática', e);
    }
  }

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

  setTimeout(checkForUpdate, 1200);
  setInterval(checkForUpdate, 15 * 60 * 1000);
})();
