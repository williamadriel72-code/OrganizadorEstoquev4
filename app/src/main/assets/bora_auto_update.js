(() => {
  const SUPA_URL = 'https://rlgsbtolosxyymosidns.supabase.co';
  const SUPA_KEY = 'sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde';
  const LATEST_URL = SUPA_URL + '/functions/v1/latest-app-version';
  const FALLBACK_BUILD_CODE = 50022;
  let updateRunning = false;

  function currentCode() {
    try {
      if (window.AndroidApp && typeof AndroidApp.getAppVersionCode === 'function') {
        return Number(AndroidApp.getAppVersionCode()) || FALLBACK_BUILD_CODE;
      }
    } catch (_) {}
    return FALLBACK_BUILD_CODE;
  }

  function notify(text) {
    try {
      const status = document.getElementById('status');
      if (status && text) status.textContent = text;
    } catch (_) {}
  }

  async function checkForNativeUpdate(manual = false) {
    if (updateRunning) return;
    if (!window.AndroidApp || typeof AndroidApp.downloadAndInstallUpdate !== 'function') return;
    try {
      const response = await fetch(LATEST_URL, {
        method: 'GET',
        cache: 'no-store',
        headers: { 'Accept': 'application/json', 'apikey': SUPA_KEY }
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const u = await response.json();
      const remoteCode = Number(u?.version_code || 0);
      if (!remoteCode || remoteCode <= currentCode()) return;
      const url = String(u?.download_url || '').trim();
      if (!/^https:\/\//i.test(url)) throw new Error('Link de atualização inválido.');
      updateRunning = true;
      if (manual) notify('Baixando atualização estrutural do APK...');
      AndroidApp.downloadAndInstallUpdate(url, String(u.version_name || remoteCode));
    } catch (_) {
      if (manual) notify('Não foi possível verificar atualização estrutural agora.');
    }
  }

  window.checkBoraUpdateNow = () => checkForNativeUpdate(true);
  window.onNativeUpdateError = () => { updateRunning = false; };
  setTimeout(() => checkForNativeUpdate(false), 1500);
})();
