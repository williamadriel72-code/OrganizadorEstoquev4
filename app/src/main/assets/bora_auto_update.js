(() => {
  const BUILD_CODE = 50017;
  let updateRunning = false;

  function setUpdateStatus(text) {
    try {
      const el = document.getElementById('loginMsg');
      if (el && !document.getElementById('login')?.classList.contains('hidden')) el.textContent = text || '';
    } catch (_) {}
  }

  function resolveApkUrl(path) {
    const p = String(path || '').trim();
    if (/^https:\/\//i.test(p)) return p;
    return `${U}/storage/v1/object/public/app-updates/${p.split('/').map(encodeURIComponent).join('/')}`;
  }

  async function checkForUpdate() {
    if (updateRunning) return;
    if (!window.AndroidApp || typeof AndroidApp.downloadAndInstallUpdate !== 'function') return;
    try {
      const r = await api({ action: 'check_update', currentCode: BUILD_CODE });
      const u = r?.update;
      if (!u || Number(u.version_code || 0) <= BUILD_CODE) return;
      const url = resolveApkUrl(u.apk_path);
      if (!url) return;
      updateRunning = true;
      setUpdateStatus(`Atualizando para ${u.version_name || u.version_code}...`);
      AndroidApp.downloadAndInstallUpdate(url, String(u.version_name || u.version_code));
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
    setUpdateStatus(message || 'Não foi possível atualizar agora. O aplicativo continuará funcionando normalmente.');
  };

  setTimeout(checkForUpdate, 1200);
  setInterval(checkForUpdate, 30 * 60 * 1000);
})();
