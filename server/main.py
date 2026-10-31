import os
import sys
import subprocess
import re
import json
from pathlib import Path
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import Response, JSONResponse, FileResponse
from pydantic import BaseModel

app = FastAPI(title="CleanYourTV Web API", version="2.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
TV_PY = PROJECT_ROOT / "tools" / "tv.py"
PYTHON_EXE = PROJECT_ROOT / "tools" / "venv" / "Scripts" / "python.exe"
if not PYTHON_EXE.exists():
    PYTHON_EXE = Path(sys.executable)

CURRENT_TV_HOST = "192.168.1.22"

KNOWN_DEVICES = [
    {"name": "TCL 65Q6C QD-Mini LED", "ip": "192.168.1.22", "brand": "TCL", "port": 5555},
    {"name": "OnePlus 55U1", "ip": "192.168.1.29", "brand": "OnePlus", "port": 5555},
]

PACKAGE_CATALOG = {
    # P0: Telemetry & Spyware & Ad-Networks
    "com.android.adservices.api": {"priority": "P0", "title": "Android Ad Services API", "desc": "Google Privacy Sandbox ad-targeting tracking backend"},
    "com.android.ondevicepersonalization.services": {"priority": "P0", "title": "On-Device Personalization", "desc": "Ad profiling and personalized recommendation collector"},
    "com.android.federatedcompute.services": {"priority": "P0", "title": "Federated Compute Services", "desc": "Background telemetry aggregation and analytics service"},
    "com.google.android.feedback": {"priority": "P0", "title": "Google Feedback", "desc": "Crash report and background diagnostics uploader"},
    "com.android.tv.feedbackconsent": {"priority": "P0", "title": "TV Feedback Consent", "desc": "OEM telemetry consent prompt framework"},
    "com.tcl.logkit": {"priority": "P0", "title": "TCL LogKit", "desc": "TCL system telemetry dumper and logger"},
    "com.heytap.mcs": {"priority": "P0", "title": "HeyTap MCS Service", "desc": "OnePlus/OPPO cloud messaging and analytics telemetry"},
    "com.oneplus.oobe.tv": {"priority": "P0", "title": "OnePlus Setup Wizard Telemetry", "desc": "Background out-of-box user telemetry service"},
    "android.autoinstalls.config.OnePlus": {"priority": "P0", "title": "OnePlus Auto-Installer Config", "desc": "Automatic bloatware downloading trigger"},
    
    # P1: OEM Promotional Overlays & Sponsored Ads
    "com.tcl.messagebox": {"priority": "P1", "title": "TCL Message Box", "desc": "Marketing pop-ups and promotional overlay messages"},
    "com.tcl.esticker": {"priority": "P1", "title": "TCL E-Sticker", "desc": "Store display promotional badge banner overlay"},
    "com.tcl.exhibit": {"priority": "P1", "title": "TCL Exhibit Mode", "desc": "Retail demo advertisements and video loop popups"},
    "com.tcl.tv.tclhome_passive": {"priority": "P1", "title": "TCL Home Passive Service", "desc": "Promotional banner aggregator"},
    "com.tcl.waterfall.overseas": {"priority": "P1", "title": "TCL Waterfall Overseas", "desc": "TCL Home screen ad carousel feeds"},
    "com.tcl.channelplus": {"priority": "P1", "title": "TCL Channel Plus", "desc": "FAST ad-supported promotional video channels"},
    "com.tcl.usercenter": {"priority": "P1", "title": "TCL User Center", "desc": "TCL account sign-in prompt and profile ads"},
    "com.tcl.guard": {"priority": "P1", "title": "TCL Safety Guard", "desc": "Fake memory booster that displays ad alerts"},
    "com.oneplus.tv.imagewall": {"priority": "P1", "title": "OnePlus Image Wall", "desc": "Lockscreen promotional picture displays"},
    "com.oneplus.tv.btspeaker": {"priority": "P1", "title": "OnePlus Bluetooth Speaker Mode", "desc": "Unused advertising promo activity"},
    "com.google.android.tvrecommendations": {"priority": "P1", "title": "Google TV Recommendations", "desc": "Sponsored movie trailers and paid app ads row"},

    # P2: Preloaded Bloatware & Obsolete System Services
    "com.tcl.ocean.instructions": {"priority": "P2", "title": "TCL User Instructions", "desc": "Pre-installed manual taking up memory"},
    "com.tcl.repairguide": {"priority": "P2", "title": "TCL Repair Guide", "desc": "Preloaded static HTML repair guide"},
    "com.android.printspooler": {"priority": "P2", "title": "Android Print Spooler", "desc": "Printer spooler service completely unused on TVs"},
    "com.google.android.syncadapters.calendar": {"priority": "P2", "title": "Google Calendar Sync", "desc": "Unneeded Google Calendar sync service for TV"},
    "com.android.providers.calendar": {"priority": "P2", "title": "Calendar Provider", "desc": "Storage provider for calendar on television"},
    "com.google.android.onetimeinitializer": {"priority": "P2", "title": "One-Time Initializer", "desc": "Legacy setup service that runs repeatedly in background"},
    "com.tcl.hearaid": {"priority": "P2", "title": "TCL HearAid Service", "desc": "Unused audio test bloatware"},
    "com.tcl.t_solo": {"priority": "P2", "title": "TCL T-Solo Demo", "desc": "Retail shop demonstration package"},
    "com.tcl.UpdatePeripheral": {"priority": "P2", "title": "TCL Peripheral Updater", "desc": "Vendor accessory promo listener"},
    "com.tcl.ui_mediaCenter": {"priority": "P2", "title": "TCL Media Center UI", "desc": "Outdated local media browser replaced by modern players"},
    "com.google.android.videos": {"priority": "P2", "title": "Google Play Movies / TV", "desc": "Old Play Movies storefront replaced by Google TV tab"},
    "com.google.android.play.games": {"priority": "P2", "title": "Google Play Games", "desc": "Legacy Google Play Games background sync"},
    "com.eros.now": {"priority": "P2", "title": "Eros Now", "desc": "Pre-installed subscription video streaming bloatware"},
    "net.oneplus.weather": {"priority": "P2", "title": "OnePlus Weather", "desc": "Built-in weather app with advertising background ping"},
    "com.oneplus.tv.userguide": {"priority": "P2", "title": "OnePlus User Guide", "desc": "Static user manual app taking up storage"},
    "com.google.android.boot.appsplashscreen": {"priority": "P2", "title": "Google App Splash Screen", "desc": "Unnecessary boot animation delay overlay"},

    # P3: Stock Ad-Filled Launcher & SetupWraith
    "com.google.android.apps.tv.launcherx": {"priority": "P3", "title": "Google TV Stock Launcher", "desc": "Ad-heavy stock launcher with un-skippable sponsored video ads"},
    "com.google.android.tvlauncher": {"priority": "P3", "title": "Android TV Stock Launcher", "desc": "Standard Android TV launcher with ad banner rows"},
    "com.google.android.tungsten.setupwraith": {"priority": "P3", "title": "Google SetupWraith", "desc": "Google TV account onboarding overlay and recommendation sync"},
    "com.oneplus.tv.launcher": {"priority": "P3", "title": "OnePlus TV Launcher", "desc": "OnePlus OxygenOS TV stock launcher with OxygenPlay ads"},
    "com.google.android.leanbacklauncher.partnercustomizer": {"priority": "P3", "title": "Partner Customizer", "desc": "Sponsored app injection engine into stock launcher"},

    # P4: Optional / Ecosystem Apps
    "com.tcl.smartalexa": {"priority": "P4", "title": "TCL Alexa Integration", "desc": "Alexa smart assistant skill bridge for TV"},
    "oneplus.smarthome": {"priority": "P4", "title": "OnePlus Smart Home", "desc": "IoT smart device dashboard on TV"},
    "com.tcl.browser": {"priority": "P4", "title": "TCL Built-in Browser", "desc": "Laggy OEM web browser with sponsored bookmark icons"},
    "com.tcl.hotelmenu": {"priority": "P4", "title": "TCL Hotel Menu", "desc": "Commercial hospitality mode configuration"},
}

PROTECTED_PREFIXES = (
    "com.android.systemui",
    "com.android.settings",
    "com.android.tv.settings",
    "com.android.shell",
    "com.android.phone",
    "com.google.android.gms",
    "com.android.vending",
    "com.google.android.permissioncontroller",
    "com.google.android.inputmethod",
    "com.tcl.tv",
    "com.oneplus.tv.android.livetv",
    "com.tcl.suspension",
    "com.android.bluetooth",
    "android",
)

def run_tv_command(cmd: str, host: str = None) -> str:
    target_host = host or CURRENT_TV_HOST
    
    # 1. First try adb CLI
    try:
        proc = subprocess.run(
            ["adb", "-s", f"{target_host}:5555", "shell", cmd],
            capture_output=True,
            text=True,
            timeout=8
        )
        if proc.returncode == 0 and proc.stdout:
            return proc.stdout.strip()
    except Exception:
        pass

    # 2. Try connect first then adb
    try:
        subprocess.run(["adb", "connect", f"{target_host}:5555"], capture_output=True, timeout=4)
        proc = subprocess.run(
            ["adb", "-s", f"{target_host}:5555", "shell", cmd],
            capture_output=True,
            text=True,
            timeout=8
        )
        if proc.returncode == 0:
            return proc.stdout.strip()
    except Exception:
        pass

    # 3. Fallback to tools/tv.py
    env = os.environ.copy()
    env["TV_HOST"] = target_host
    try:
        proc = subprocess.run(
            [str(PYTHON_EXE), str(TV_PY), cmd],
            capture_output=True,
            text=True,
            env=env,
            timeout=8
        )
        return proc.stdout.strip()
    except Exception as e:
        raise HTTPException(status_code=504, detail=f"TV ({target_host}) is unreachable. Check power and Wi-Fi.")

class ConnectRequest(BaseModel):
    ip: str
    port: int = 5555

class DebloatRequest(BaseModel):
    packages: List[str]
    host: Optional[str] = None

class KeyRequest(BaseModel):
    key: str
    host: Optional[str] = None

@app.get("/api/devices/saved")
def get_saved_devices():
    return {
        "current_ip": CURRENT_TV_HOST,
        "saved": KNOWN_DEVICES
    }

@app.post("/api/connect")
def connect_device(req: ConnectRequest):
    global CURRENT_TV_HOST
    CURRENT_TV_HOST = req.ip
    
    # Ensure adb connect
    subprocess.run(["adb", "connect", f"{req.ip}:{req.port}"], capture_output=True, timeout=5)
    
    raw_probe = run_tv_command("getprop ro.product.model", host=req.ip)
    if not raw_probe:
        raise HTTPException(status_code=400, detail=f"Unable to connect to TV at {req.ip}. Please wake up the TV or enable ADB.")
        
    mfr = run_tv_command("getprop ro.product.manufacturer", host=req.ip)
    rel = run_tv_command("getprop ro.build.version.release", host=req.ip)
    sdk = run_tv_command("getprop ro.build.version.sdk", host=req.ip)
    bid = run_tv_command("getprop ro.build.display.id", host=req.ip)
        
    return {
        "status": "connected",
        "ip": req.ip,
        "model": raw_probe,
        "manufacturer": mfr or "Smart TV",
        "android_version": rel or "14",
        "sdk": sdk or "34",
        "build_id": bid or "release-keys",
    }

@app.get("/api/audit")
def audit_tv(host: Optional[str] = None):
    target_host = host or CURRENT_TV_HOST
    
    # 1. Get installed packages
    all_pkg_raw = run_tv_command("pm list packages", host=target_host)
    all_pkgs = set()
    for line in all_pkg_raw.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            all_pkgs.add(line.replace("package:", "").strip())
            
    # 2. Get disabled packages
    disabled_raw = run_tv_command("pm list packages -d", host=target_host)
    disabled_pkgs = set()
    for line in disabled_raw.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            disabled_pkgs.add(line.replace("package:", "").strip())
            
    # 3. Categorize packages into P0 - P4
    categorized = {
        "P0": [],
        "P1": [],
        "P2": [],
        "P3": [],
        "P4": [],
        "Protected": []
    }
    
    total_bloat_detected = 0
    total_bloat_disabled = 0
    
    for pkg in sorted(all_pkgs):
        is_disabled = pkg in disabled_pkgs
        is_protected = any(pkg == p or pkg.startswith(p + ".") for p in PROTECTED_PREFIXES)
        
        info = PACKAGE_CATALOG.get(pkg)
        if info:
            priority = info["priority"]
            total_bloat_detected += 1
            if is_disabled:
                total_bloat_disabled += 1
            categorized[priority].append({
                "package": pkg,
                "title": info["title"],
                "description": info["desc"],
                "priority": priority,
                "disabled": is_disabled,
                "protected": False
            })
        elif is_protected:
            categorized["Protected"].append({
                "package": pkg,
                "title": "System Essential",
                "description": "Android core service required for TV to boot and function",
                "priority": "Protected",
                "disabled": is_disabled,
                "protected": True
            })
            
    # Calculate debloat score (0% to 100%)
    if total_bloat_detected > 0:
        score = int((total_bloat_disabled / total_bloat_detected) * 100)
    else:
        score = 100
        
    return {
        "host": target_host,
        "debloat_score": score,
        "total_packages": len(all_pkgs),
        "total_bloat_detected": total_bloat_detected,
        "total_bloat_disabled": total_bloat_disabled,
        "is_debloated": score >= 75,
        "categories": categorized
    }

@app.post("/api/debloat")
def debloat_packages(req: DebloatRequest):
    target_host = req.host or CURRENT_TV_HOST
    results = []
    for pkg in req.packages:
        if any(pkg == p or pkg.startswith(p + ".") for p in PROTECTED_PREFIXES):
            results.append({"package": pkg, "status": "skipped", "reason": "Package is strictly protected"})
            continue
            
        out = run_tv_command(f"pm disable-user --user 0 {pkg}", host=target_host)
        results.append({"package": pkg, "status": "disabled", "output": out})
        
    return {"host": target_host, "results": results}

@app.post("/api/restore")
def restore_packages(req: DebloatRequest):
    target_host = req.host or CURRENT_TV_HOST
    results = []
    for pkg in req.packages:
        out = run_tv_command(f"pm enable {pkg}", host=target_host)
        results.append({"package": pkg, "status": "enabled", "output": out})
        
    return {"host": target_host, "results": results}

@app.get("/api/screenshot")
def get_screenshot(host: Optional[str] = None):
    target_host = host or CURRENT_TV_HOST
    shot_path = PROJECT_ROOT / "shots" / f"web_live_{target_host.replace('.', '_')}.png"
    shot_path.parent.mkdir(exist_ok=True)
    
    # Capture via adb shell screencap
    try:
        subprocess.run(["adb", "-s", f"{target_host}:5555", "shell", "screencap", "-p", "/data/local/tmp/shot.png"], timeout=6)
        subprocess.run(["adb", "-s", f"{target_host}:5555", "pull", "/data/local/tmp/shot.png", str(shot_path)], timeout=6)
    except Exception:
        pass
        
    if not shot_path.exists():
        env = os.environ.copy()
        env["TV_HOST"] = target_host
        try:
            subprocess.run([str(PYTHON_EXE), str(TV_PY), "--screenshot", str(shot_path)], capture_output=True, env=env, timeout=10)
        except Exception:
            pass
            
    if shot_path.exists():
        with open(shot_path, "rb") as f:
            data = f.read()
        return Response(content=data, media_type="image/png")
    else:
        raise HTTPException(status_code=500, detail="Failed to capture screenshot")

@app.post("/api/remote/key")
def send_key(req: KeyRequest):
    target_host = req.host or CURRENT_TV_HOST
    key_map = {
        "up": "DPAD_UP",
        "down": "DPAD_DOWN",
        "left": "DPAD_LEFT",
        "right": "DPAD_RIGHT",
        "select": "DPAD_CENTER",
        "home": "HOME",
        "back": "BACK",
        "menu": "MENU",
        "volume_up": "VOLUME_UP",
        "volume_down": "VOLUME_DOWN",
        "mute": "VOLUME_MUTE"
    }
    key_code = key_map.get(req.key.lower(), req.key.upper())
    out = run_tv_command(f"input keyevent KEYCODE_{key_code}", host=target_host)
    return {"key": key_code, "output": out}

WEB_DIST = PROJECT_ROOT / "web" / "dist"
if WEB_DIST.exists():
    app.mount("/assets", StaticFiles(directory=str(WEB_DIST / "assets")), name="assets")

    @app.get("/{full_path:path}")
    def serve_frontend(full_path: str):
        file_path = WEB_DIST / full_path
        if file_path.is_file():
            return FileResponse(file_path)
        return FileResponse(WEB_DIST / "index.html")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)
