#!/usr/bin/env python3
"""اختبار التطبيق على محاكي أندرويد حقيقي في GitHub Actions.
يتحكم بالتطبيق عبر واجهة تصحيح WebView (CDP)، ويلتقط صوراً، ويفحص الإشعارات والودجت والبيانات بعد إعادة التشغيل."""
import json, os, re, subprocess, sys, time, urllib.request
from websocket import create_connection

OUT = 'ci-shots'; os.makedirs(OUT, exist_ok=True)
PKG = 'com.mohasbak.app'
R = []
def sh(c, timeout=60):
    try: return subprocess.run(c, shell=True, capture_output=True, text=True, timeout=timeout).stdout
    except Exception as e: return f'ERR {e}'
def shot(n): sh(f'adb exec-out screencap -p > {OUT}/{n}.png')
def check(n, c, d=''):
    line = ('✔ ' if c else '✘ ') + n + ('' if c else f' — {str(d)[:300]}'); R.append(line); print(line, flush=True)

class Page:
    def __init__(self):
        sock = None
        for _ in range(40):
            m = re.search(r'@(webview_devtools_remote_\d+)', sh('adb shell cat /proc/net/unix'))
            if m: sock = m.group(1); break
            time.sleep(2)
        if not sock: raise RuntimeError('no webview devtools socket')
        sh('adb forward --remove-all'); sh(f'adb forward tcp:9222 localabstract:{sock}')
        for _ in range(20):
            try:
                pages = json.load(urllib.request.urlopen('http://127.0.0.1:9222/json', timeout=5))
                pages = [p for p in pages if p.get('type') == 'page' and p.get('webSocketDebuggerUrl')]
                if pages: break
            except Exception: pass
            time.sleep(2)
        self.ws = create_connection(pages[0]['webSocketDebuggerUrl'], timeout=40, suppress_origin=True)
        self.mid = 0
    def ev(self, expr):
        self.mid += 1
        self.ws.send(json.dumps({'id': self.mid, 'method': 'Runtime.evaluate', 'params': {'expression': expr, 'awaitPromise': True, 'returnByValue': True}}))
        while True:
            r = json.loads(self.ws.recv())
            if r.get('id') == self.mid:
                res = r.get('result', {})
                if 'exceptionDetails' in res: return 'EXC: ' + json.dumps(res['exceptionDetails'])[:300]
                return res.get('result', {}).get('value')
    def wait(self, expr, t=20):
        for _ in range(t * 2):
            v = self.ev(expr)
            if v and not (isinstance(v, str) and v.startswith('EXC')): return v
            time.sleep(0.5)
        return None

def launch():
    sh(f'adb shell am start -W -n {PKG}/.MainActivity', 90)

try:
    sh(f'adb shell pm grant {PKG} android.permission.POST_NOTIFICATIONS')
    sh('adb logcat -c')
    launch(); time.sleep(10); shot('01-launch')
    pg = Page()
    check('التطبيق يعمل داخل أندرويد (Capacitor)', pg.wait("window.Capacitor && Capacitor.isNativePlatform() && !!window.__app"))
    check('الجسر الأصلي مفعّل', pg.ev("window.__native && window.__native.on") is True)
    pg.ev("document.querySelector('[data-act=load-demo]').click(); true"); time.sleep(4); shot('02-home')
    tb = pg.wait("document.querySelector('.today-box') && document.querySelector('.today-box').innerText")
    check('الرئيسية: مسموح تصرف اليوم ظاهر', tb and 'مسموح' in tb, tb)
    time.sleep(3)
    n = pg.ev("Capacitor.Plugins.LocalNotifications.getPending().then(r => r.notifications.length)")
    check('الإشعارات مجدولة في نظام أندرويد', isinstance(n, int) and n >= 10, n)
    titles = pg.ev("Capacitor.Plugins.LocalNotifications.getPending().then(r => r.notifications.map(x => x.title).slice(0, 6).join(' | '))")
    check('عناوين الإشعارات صحيحة', titles and ('بكرة يُخصم' in titles or 'سجّلت' in titles), titles)
    prefs = sh(f'adb shell run-as {PKG} cat shared_prefs/mohasbak_widget.xml')
    check('بيانات الودجت محفوظة للنظام', 'مسموح' in prefs and 'يتبقى من الراتب' in prefs, prefs[:300])
    aw = sh('adb shell dumpsys appwidget')
    check('الودجت مسجّل في أندرويد', 'MohasbakWidget' in aw, aw[:200])
    # تسجيل مصروف سريع
    pg.ev("quickSpend(); true"); time.sleep(1.5); shot('03-quick-spend')
    pg.ev("document.querySelector('#q_amt').value = '45'; document.querySelector('.sheet-foot [data-act=submit-form]').click(); true"); time.sleep(2)
    tb2 = pg.ev("document.querySelector('.today-box').innerText")
    check('تسجيل مصروف 45 يحدّث مسموح اليوم', tb2 and 'صرفت اليوم 45' in tb2, tb2)
    time.sleep(2)
    prefs2 = sh(f'adb shell run-as {PKG} cat shared_prefs/mohasbak_widget.xml')
    check('الودجت تحدّث بعد المصروف', prefs2 != prefs, prefs2[:200])
    # إشعار حقيقي
    pg.ev("window.__nativeTestNotif(); true"); time.sleep(10)
    dn = sh('adb shell dumpsys notification --noredact')
    check('وصل إشعار حقيقي في أندرويد', PKG in dn and 'هكذا ستصلك' in dn, '')
    sh('adb shell cmd statusbar expand-notifications'); time.sleep(2); shot('04-notification'); sh('adb shell cmd statusbar collapse'); time.sleep(1)
    # زر الرجوع
    pg.ev("document.querySelector('.fab').click(); true"); time.sleep(1)
    sh('adb shell input keyevent 4'); time.sleep(1.5)
    check('زر الرجوع يغلق النافذة ولا يخرج', pg.ev("!document.querySelector('.sheet')") is True and PKG in sh('adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"'))
    pg.ev("window.__app.go('forecast'); true"); time.sleep(1); shot('05-forecast')
    sh('adb shell input keyevent 4'); time.sleep(1)
    check('الرجوع من التوقع يعود للرئيسية', pg.ev("window.__app.ui.view") == 'home')
    bio = pg.ev("Capacitor.Plugins.MohasbakNative.canBiometric().then(r => JSON.stringify(r))")
    check('إضافة البصمة تعمل (بدون انهيار)', bio and 'available' in bio, bio)
    # زر + في الودجت (محاكاة النية التي يرسلها)
    sh('adb shell input keyevent 3'); time.sleep(2)
    sh(f'adb shell am start -n {PKG}/.MainActivity --es mohasbak_action quick'); time.sleep(4)
    check('زر + في الودجت يفتح تسجيل مصروف', pg.wait("!!document.querySelector('#q_amt')", 10), '')
    shot('06-widget-plus'); pg.ev("closeSheet(); true")
    # البيانات بعد إغلاق التطبيق بالكامل
    sh(f'adb shell am force-stop {PKG}'); time.sleep(2); launch(); time.sleep(8)
    pg = Page()
    cnt = pg.wait("window.__app && window.__app.state && window.__app.state.items.filter(i => i.type === 'daily').length")
    check('البيانات باقية بعد إغلاق التطبيق وفتحه', cnt == 1, cnt)
    shot('07-after-restart')
    pg.ev("window.__app.go('options'); true"); time.sleep(1.5); shot('08-options')
    check('الإعدادات: قسم التنبيهات والجوال', 'التنبيهات والجوال' in (pg.ev("document.querySelector('#main').innerText") or ''))
except Exception as e:
    check('تنفيذ الاختبار', False, repr(e))
finally:
    log = sh('adb logcat -d -v brief')
    open(f'{OUT}/logcat.txt', 'w').write(log[-200000:])
    check('لا انهيار في التطبيق (FATAL)', 'FATAL EXCEPTION' not in log, re.findall(r'FATAL EXCEPTION.{0,400}', log, re.S)[:1])
    js_err = [l for l in log.splitlines() if 'chromium' in l.lower() and ('Uncaught' in l)]
    check('لا أخطاء JavaScript في WebView', not js_err, js_err[:3])
    ok = sum(1 for l in R if l.startswith('✔'))
    R.append(f'النتيجة: {ok}/{len(R)}')
    open(f'{OUT}/results.txt', 'w').write('\n'.join(R) + '\n')
    print(R[-1])
