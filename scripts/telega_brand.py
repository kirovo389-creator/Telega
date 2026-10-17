#!/usr/bin/env python3
"""Telega branding, package, adaptive icons (black + white plane)."""
from pathlib import Path
import re, json, copy, subprocess, sys

NEW_PKG, OLD_PKG = "org.telega.messenger", "org.telegram.messenger"

# Adaptive XML
Path("TMessagesProj/src/main/res/mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    Path(f"TMessagesProj/src/main/res/mipmap-anydpi-v26/{name}").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/telega_icon_bg" />
    <foreground android:drawable="@drawable/telega_icon_fg" />
    <monochrome android:drawable="@drawable/icon_plane" />
</adaptive-icon>
''',
        encoding="utf-8",
    )

Path("TMessagesProj/src/main/res/drawable/icon_background.xml").write_text(
    '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FF000000" />
</shape>
''',
    encoding="utf-8",
)
Path("TMessagesProj/src/main/res/drawable/icon_background_round.xml").write_text(
    '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="#FF000000" />
</shape>
''',
    encoding="utf-8",
)

Path("TMessagesProj/src/main/res/drawable/telega_icon_bg.xml").write_text(
    '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FF000000" />
</shape>
''',
    encoding="utf-8",
)
Path("TMessagesProj/src/main/res/drawable/telega_icon_fg.xml").write_text(
    '''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/icon_plane"
    android:insetLeft="18%"
    android:insetTop="18%"
    android:insetRight="18%"
    android:insetBottom="18%" />
''',
    encoding="utf-8",
)

# Install pillow if needed
def ensure_pil():
    try:
        from PIL import Image, ImageDraw  # noqa: F401
        return
    except ImportError:
        pass
    for cmd in (
        [sys.executable, "-m", "pip", "install", "--user", "pillow"],
        [sys.executable, "-m", "pip", "install", "pillow"],
        ["pip3", "install", "--user", "pillow"],
    ):
        try:
            subprocess.check_call(cmd)
            from PIL import Image, ImageDraw  # noqa: F401
            return
        except Exception as e:
            print("pip try failed:", cmd, e)
    # last resort: apt
    try:
        subprocess.check_call(["sudo", "apt-get", "update", "-qq"])
        subprocess.check_call(["sudo", "apt-get", "install", "-y", "-qq", "python3-pil"])
    except Exception as e:
        print("apt install failed", e)
        raise

ensure_pil()
from PIL import Image, ImageDraw

def make_plane(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    cx = cy = size // 2
    s = size * 0.34
    pts = [
        (cx + s * 0.62, cy),
        (cx - s * 0.55, cy - s * 0.48),
        (cx - s * 0.12, cy),
        (cx - s * 0.55, cy + s * 0.48),
    ]
    d.polygon(pts, fill=(255, 255, 255, 255))
    notch = [
        (cx - s * 0.12, cy),
        (cx + s * 0.08, cy + s * 0.04),
        (cx - s * 0.12, cy + s * 0.18),
    ]
    d.polygon(notch, fill=(0, 0, 0, 255))
    return img

dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dens_name, sz in dens.items():
    icon = make_plane(sz)
    for folder, names in [
        (f"TMessagesProj/src/main/res/mipmap-{dens_name}", ["ic_launcher.png", "ic_launcher_round.png"]),
        (f"TMessagesProj/src/main/res/drawable-{dens_name}", ["ic_launcher_dr.png"]),
    ]:
        Path(folder).mkdir(parents=True, exist_ok=True)
        for n in names:
            icon.save(f"{folder}/{n}")
        wp = Path(folder) / "ic_launcher_dr.webp"
        if wp.exists():
            wp.unlink()
print("Generated black+plane icons")

for p in Path(".").rglob("google-services.json"):
    if "build" in p.parts:
        continue
    data = json.loads(p.read_text(encoding="utf-8"))
    clients = data.get("client", [])
    existing, base = set(), None
    for c in clients:
        pkg = c.get("client_info", {}).get("android_client_info", {}).get("package_name")
        if pkg:
            existing.add(pkg)
        if pkg == OLD_PKG:
            base = c
    if NEW_PKG not in existing and base is not None:
        extra = copy.deepcopy(base)
        extra["client_info"]["android_client_info"]["package_name"] = NEW_PKG
        clients.append(extra)
        data["client"] = clients
        p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

for p in [
    Path("TMessagesProj_App/src/main/AndroidManifest.xml"),
    Path("TMessagesProj/src/main/AndroidManifest.xml"),
]:
    if not p.exists():
        continue
    t = p.read_text(encoding="utf-8")
    t2 = t.replace(f"{OLD_PKG}.provider", f"{NEW_PKG}.provider")
    t2 = t2.replace(f"{OLD_PKG}.passthrough", f"{NEW_PKG}.passthrough")
    t2 = t2.replace(f"{OLD_PKG}.channel_content", f"{NEW_PKG}.channel_content")
    t2 = t2.replace(f"{OLD_PKG}.notification", f"{NEW_PKG}.notification")
    t2 = t2.replace('android:label="Telegram"', 'android:label="Telega"')
    p.write_text(t2, encoding="utf-8")

nfiles = 0
for p in Path("TMessagesProj/src/main/res").rglob("strings.xml"):
    t = p.read_text(encoding="utf-8")
    ph = {}

    def protect(m):
        k = f"__P{len(ph)}__"
        ph[k] = m.group(0)
        return k

    t2 = re.sub(r"https?://[^\s<\"']+", protect, t)
    t2 = re.sub(r"\btelegram\.(org|me|dog|desktop)\b", protect, t2, flags=re.I)
    t2 = re.sub(r"\bt\.me\b", protect, t2)
    t2 = re.sub(r"\bTelegram\b", "Telega", t2)
    t2 = re.sub(r"\btelegram\b", "telega", t2)
    t2 = re.sub(
        r'(<string\s+name="(?:AppName|app_name|app_name_short)"[^>]*>)[^<]*(</string>)',
        r"\1Telega\2",
        t2,
    )
    for k, v in ph.items():
        t2 = t2.replace(k, v)
    if t2 != t:
        p.write_text(t2, encoding="utf-8")
        nfiles += 1
print(f"Branded {nfiles} strings.xml files")
