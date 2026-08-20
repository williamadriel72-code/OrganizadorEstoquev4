from pathlib import Path
import re

worker = Path('app/src/main/java/com/organizador/estoque/BackgroundUpdateWorker.kt')
worker_kt = worker.read_text(encoding='utf-8')
worker_kt = worker_kt.replace('import android.app.DevicePolicyManager\n', 'import android.app.admin.DevicePolicyManager\n')
worker.write_text(worker_kt, encoding='utf-8')

gradle = Path('app/build.gradle.kts')
gradle_text = gradle.read_text(encoding='utf-8')
gradle_text = gradle_text.replace(
    'implementation("androidx.work:work-runtime:2.11.2")',
    'implementation("androidx.work:work-runtime-ktx:2.11.2")'
)
gradle.write_text(gradle_text, encoding='utf-8')

launcher = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')
kt = launcher.read_text(encoding='utf-8')

if 'import android.Manifest' not in kt:
    kt = kt.replace('import android.annotation.SuppressLint\n', 'import android.Manifest\nimport android.annotation.SuppressLint\n', 1)

if 'UpdateScheduler.ensureScheduled(applicationContext)' not in kt:
    kt = kt.replace(
        '        prepareBipSound()\n',
        '        prepareBipSound()\n        UpdateScheduler.ensureScheduled(applicationContext)\n        requestUpdateNotificationPermission()\n',
        1,
    )

if 'private fun requestUpdateNotificationPermission()' not in kt:
    marker = '    private fun prepareBipSound() {'
    method = '''    private fun requestUpdateNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9102)
        }
    }

'''
    if marker not in kt:
        raise SystemExit('Não foi possível localizar prepareBipSound em LauncherActivity.kt')
    kt = kt.replace(marker, method + marker, 1)

launcher.write_text(kt, encoding='utf-8')

app_js = Path('app/src/main/assets/app.js')
js = app_js.read_text(encoding='utf-8')
pattern = re.compile(r"^function apkUrl\(path\).*$", re.M)
replacement = "function apkUrl(path){const raw=String(path||'').trim();if(/^https:\\/\\//i.test(raw))return raw;return`${SUPABASE_URL}/storage/v1/object/public/app-updates/${raw.split('/').map(encodeURIComponent).join('/')}`}"
js, count = pattern.subn(replacement, js, count=1)
if count != 1:
    raise SystemExit('Não foi possível atualizar apkUrl em app.js')
app_js.write_text(js, encoding='utf-8')

print('Atualização automática preparada: WorkManager + endpoint de versão + URL direta ou Storage.')
