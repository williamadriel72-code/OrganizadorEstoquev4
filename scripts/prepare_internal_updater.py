from pathlib import Path
import re

path = Path('app/src/main/assets/app.js')
js = path.read_text(encoding='utf-8')

replacement = r'''function fmtUpdateBytes(v){const n=Number(v||0);if(!n)return'0 MB';return(n/1048576).toLocaleString('pt-BR',{minimumFractionDigits:1,maximumFractionDigits:1})+' MB'}
function setUpdateButton(text,disabled=true){const b=$('updateNow');if(!b)return;b.textContent=text;b.disabled=disabled}
$('updateNow').onclick=()=>{
 if(!latestUpdate)return;
 const url=apkUrl(latestUpdate.apk_path);
 if(window.AndroidApp?.downloadAndInstallUpdate){
  setUpdateButton('PREPARANDO...',true);
  $('updateInfo').textContent=`Preparando a versão ${latestUpdate.version_name}...`;
  AndroidApp.downloadAndInstallUpdate(url,String(latestUpdate.version_name||'nova-versao'));
  return;
 }
 if(window.AndroidApp?.openUpdateUrl)AndroidApp.openUpdateUrl(url);else window.open(url,'_blank');
};
$('updateLater').onclick=()=>$('updateOverlay').classList.add('hidden');
window.onNativeUpdateProgress=(percent,downloaded,total,state)=>{
 const pct=Number(percent);
 const amount=total>0?`${fmtUpdateBytes(downloaded)} de ${fmtUpdateBytes(total)}`:fmtUpdateBytes(downloaded);
 $('updateInfo').textContent=`${state||'Baixando atualização...'} ${pct>=0?pct+'% · ':''}${amount}`;
 setUpdateButton(pct>=0?`BAIXANDO ${pct}%`:'BAIXANDO...',true);
};
window.onNativeUpdateReady=()=>{
 $('updateInfo').textContent='Download concluído e verificado. Abrindo o instalador do Android...';
 setUpdateButton('ABRINDO INSTALAÇÃO...',true);
};
window.onNativeUpdatePermissionRequired=()=>{
 $('updateInfo').textContent='APK baixado. Autorize este aplicativo a instalar atualizações; ao voltar, a instalação continuará automaticamente.';
 setUpdateButton('AGUARDANDO PERMISSÃO...',true);
};
window.onNativeUpdateError=m=>{
 setUpdateButton('TENTAR NOVAMENTE',false);
 toast(m||'Não foi possível concluir a atualização.','err');
 if(m)$('updateInfo').textContent='Falha na atualização: '+m;
};'''

pattern = re.compile(
    r"\$\('updateNow'\)\.onclick=.*?window\.onNativeUpdateError=.*?;(?=\n\nasync function loadLatestVersion)",
    re.S,
)

js, count = pattern.subn(lambda _: replacement, js, count=1)
if count != 1:
    raise SystemExit('Não foi possível localizar o bloco do atualizador em app.js')

path.write_text(js, encoding='utf-8')
print('Atualizador interno preparado com progresso, retry e instalação nativa.')
