#!/usr/bin/env python3
"""يُشغَّل في GitHub Actions بعد `npx cap add android`: ينسخ الكود الأصلي (الودجت، الإضافة، MainActivity)،
ويعدّل AndroidManifest وbuild.gradle (البصمة + التوقيع الثابت)، ويضع أيقونة التطبيق وشاشة البداية."""
import pathlib, re, shutil
from PIL import Image

HERE = pathlib.Path(__file__).parent
APP = HERE.parent / 'android' / 'app'
MAIN = APP / 'src' / 'main'
JAVA = MAIN / 'java' / 'com' / 'mohasbak' / 'app'

# 1) كود Java
JAVA.mkdir(parents=True, exist_ok=True)
for f in (HERE / 'java').glob('*.java'):
    shutil.copy(f, JAVA / f.name)

# 2) الموارد
for f in (HERE / 'res').rglob('*'):
    if f.is_file():
        dst = MAIN / 'res' / f.relative_to(HERE / 'res')
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dst)

# 3) AndroidManifest: مستقبل الودجت
mf = MAIN / 'AndroidManifest.xml'
s = mf.read_text(encoding='utf-8')
if 'MohasbakWidget' not in s:
    recv = '''
        <receiver android:name=".MohasbakWidget" android:exported="false" android:label="محاسبك المالي">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/mohasbak_widget_info" />
        </receiver>
    </application>'''
    s = s.replace('</application>', recv, 1)
mf.write_text(s, encoding='utf-8')

# 4) build.gradle: مكتبة البصمة + توقيع ثابت لنسخ الاختبار
bg = APP / 'build.gradle'
g = bg.read_text(encoding='utf-8')
if 'androidx.biometric' not in g:
    g = re.sub(r'dependencies\s*\{', 'dependencies {\n    implementation "androidx.biometric:biometric:1.1.0"', g, count=1)
if 'signingConfigs' not in g:
    g += '''
android {
    signingConfigs {
        mohasbakTest {
            storeFile file('../../keystore/test.keystore')
            storePassword 'mohasbak-test'
            keyAlias 'mohasbak'
            keyPassword 'mohasbak-test'
        }
    }
    buildTypes {
        debug { signingConfig signingConfigs.mohasbakTest }
        release { signingConfig signingConfigs.mohasbakTest }
    }
}
'''
bg.write_text(g, encoding='utf-8')

# 5) الأيقونة وشاشة البداية
icon = Image.open(HERE.parent / 'www' / 'icon-512.png').convert('RGBA')
sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
shutil.rmtree(MAIN / 'res' / 'mipmap-anydpi-v26', ignore_errors=True)
for d, px in sizes.items():
    out = MAIN / 'res' / f'mipmap-{d}'
    out.mkdir(parents=True, exist_ok=True)
    im = icon.resize((px, px), Image.LANCZOS)
    for name in ('ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'):
        im.save(out / name)
for sp in (MAIN / 'res').glob('drawable*/splash.png'):
    w, h = Image.open(sp).size
    bg_ = Image.new('RGBA', (w, h), (242, 245, 244, 255))
    side = max(48, min(w, h) // 4)
    bg_.paste(icon.resize((side, side), Image.LANCZOS), ((w - side) // 2, (h - side) // 2), icon.resize((side, side), Image.LANCZOS))
    bg_.convert('RGB').save(sp)
print('applied native overlay')
