import React, { useState, useEffect } from 'react';
import { 
  Tv, 
  ShieldAlert, 
  CheckCircle2, 
  XCircle, 
  RefreshCw, 
  Sliders, 
  Terminal, 
  Radio, 
  Play, 
  RotateCcw, 
  Gamepad2, 
  ChevronRight, 
  Search, 
  Filter, 
  Camera, 
  Maximize2,
  Lock,
  ArrowUp,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Home,
  CornerDownLeft,
  Volume2,
  VolumeX
} from 'lucide-react';

const PRIORITY_BADGES = {
  P0: { label: 'P0 • Telemetry & Spyware', color: 'bg-rose-500/15 text-rose-400 border-rose-500/30' },
  P1: { label: 'P1 • OEM Promos & Overlays', color: 'bg-amber-500/15 text-amber-400 border-amber-500/30' },
  P2: { label: 'P2 • Preloaded Bloat & Demos', color: 'bg-sky-500/15 text-sky-400 border-sky-500/30' },
  P3: { label: 'P3 • Stock Ad Launcher', color: 'bg-purple-500/15 text-purple-400 border-purple-500/30' },
  P4: { label: 'P4 • Optional / Ecosystem', color: 'bg-zinc-500/15 text-zinc-400 border-zinc-500/30' },
  Protected: { label: 'Protected • Core Android', color: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30' },
};

export default function App() {
  const [savedDevices, setSavedDevices] = useState([]);
  const [selectedIp, setSelectedIp] = useState('192.168.1.22');
  const [customIp, setCustomIp] = useState('');
  const [connecting, setConnecting] = useState(false);
  const [deviceInfo, setDeviceInfo] = useState(null);
  
  const [auditing, setAuditing] = useState(false);
  const [auditData, setAuditData] = useState(null);
  const [selectedPriority, setSelectedPriority] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  
  const [actionLoading, setActionLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [screenshotKey, setScreenshotKey] = useState(Date.now());
  const [showRemote, setShowRemote] = useState(false);

  useEffect(() => {
    fetch('/api/devices/saved')
      .then(res => res.json())
      .then(data => {
        setSavedDevices(data.saved || []);
        if (data.current_ip) setSelectedIp(data.current_ip);
      })
      .catch(console.error);
  }, []);

  const handleConnect = async (ipToConnect) => {
    const targetIp = ipToConnect || customIp || selectedIp;
    setConnecting(true);
    setStatusMessage(`Connecting to TV at ${targetIp}...`);
    try {
      const res = await fetch('/api/connect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ip: targetIp, port: 5555 })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Connection failed');
      setDeviceInfo(data);
      setSelectedIp(targetIp);
      setStatusMessage(`Connected to ${data.model} (${data.manufacturer})`);
      runAudit(targetIp);
    } catch (err) {
      setStatusMessage(`Error: ${err.message}`);
    } finally {
      setConnecting(false);
    }
  };

  const runAudit = async (targetHost) => {
    setAuditing(true);
    try {
      const res = await fetch(`/api/audit?host=${targetHost || selectedIp}`);
      const data = await res.json();
      setAuditData(data);
    } catch (err) {
      setStatusMessage(`Audit failed: ${err.message}`);
    } finally {
      setAuditing(false);
    }
  };

  const togglePackage = async (pkgName, isCurrentlyDisabled) => {
    setActionLoading(true);
    const endpoint = isCurrentlyDisabled ? '/api/restore' : '/api/debloat';
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ packages: [pkgName], host: selectedIp })
      });
      const data = await res.json();
      setStatusMessage(`${isCurrentlyDisabled ? 'Restored' : 'Disabled'} ${pkgName}`);
      runAudit(selectedIp);
    } catch (err) {
      setStatusMessage(`Failed action on ${pkgName}: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const batchAction = async (priority, action) => {
    if (!auditData || !auditData.categories[priority]) return;
    const pkgs = auditData.categories[priority]
      .filter(p => !p.protected)
      .map(p => p.package);
      
    if (pkgs.length === 0) return;
    
    setActionLoading(true);
    setStatusMessage(`${action === 'debloat' ? 'Debloating' : 'Restoring'} all ${priority} packages...`);
    try {
      const endpoint = action === 'debloat' ? '/api/debloat' : '/api/restore';
      await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ packages: pkgs, host: selectedIp })
      });
      setStatusMessage(`Successfully processed ${pkgs.length} packages in ${priority}`);
      runAudit(selectedIp);
    } catch (err) {
      setStatusMessage(`Batch action failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const sendRemoteKey = async (key) => {
    try {
      await fetch('/api/remote/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key, host: selectedIp })
      });
      setScreenshotKey(Date.now());
    } catch (err) {
      console.error(err);
    }
  };

  // Compile package list with filters
  const allFilteredPackages = [];
  if (auditData) {
    const prioritiesToInclude = selectedPriority === 'ALL' 
      ? ['P0', 'P1', 'P2', 'P3', 'P4', 'Protected'] 
      : [selectedPriority];

    for (const prio of prioritiesToInclude) {
      const items = auditData.categories[prio] || [];
      for (const item of items) {
        if (!searchQuery || item.package.toLowerCase().includes(searchQuery.toLowerCase()) || (item.title && item.title.toLowerCase().includes(searchQuery.toLowerCase()))) {
          allFilteredPackages.push(item);
        }
      }
    }
  }

  return (
    <div className="min-h-screen bg-[#090b10] text-slate-100 flex flex-col font-sans selection:bg-blue-500/30">
      {/* Top Navbar */}
      <header className="border-b border-slate-800/80 bg-[#0c1017]/90 backdrop-blur sticky top-0 z-30 px-6 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400 font-bold text-sm tracking-wider">
            CTV
          </div>
          <div>
            <h1 className="text-sm font-semibold tracking-wide text-slate-100 flex items-center gap-2">
              CleanYourTV
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">v2.0 Web</span>
            </h1>
            <p className="text-[11px] text-slate-400">Non-Root Android TV & Google TV Debloater</p>
          </div>
        </div>

        {/* Quick TV Selector & Connect */}
        <div className="flex items-center gap-3">
          <div className="flex items-center bg-slate-900/80 border border-slate-800 rounded-lg p-1">
            {savedDevices.map(d => (
              <button
                key={d.ip}
                onClick={() => { setSelectedIp(d.ip); handleConnect(d.ip); }}
                className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${
                  selectedIp === d.ip && deviceInfo
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {d.name.split(' ')[0]} ({d.ip})
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <input 
              type="text" 
              placeholder="Custom IP (e.g. 192.168.1.50)"
              value={customIp}
              onChange={e => setCustomIp(e.target.value)}
              className="bg-slate-900 border border-slate-800 text-xs px-3 py-1.5 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500 w-44 font-mono"
            />
            <button
              onClick={() => handleConnect(customIp || selectedIp)}
              disabled={connecting}
              className="bg-blue-600 hover:bg-blue-500 text-white text-xs px-3.5 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5 shadow-sm disabled:opacity-50"
            >
              {connecting ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Radio className="w-3.5 h-3.5" />}
              {connecting ? 'Connecting...' : 'Connect'}
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 grid grid-cols-12 gap-6">
        
        {/* Left Col: Device Specs, Debloat Status & Remote Viewfinder */}
        <div className="col-span-12 lg:col-span-4 space-y-6">
          
          {/* Device Card */}
          <div className="bg-[#0f141c] border border-slate-800/80 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800/60 mb-4">
              <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold flex items-center gap-1.5">
                <Tv className="w-3.5 h-3.5 text-blue-400" /> Target Television
              </span>
              {deviceInfo ? (
                <span className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" /> Online
                </span>
              ) : (
                <span className="text-[11px] text-slate-500">Not Connected</span>
              )}
            </div>

            {deviceInfo ? (
              <div className="space-y-2.5 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Model:</span>
                  <span className="font-semibold text-slate-200">{deviceInfo.model}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Manufacturer:</span>
                  <span className="font-mono text-slate-300">{deviceInfo.manufacturer}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Android Version:</span>
                  <span className="font-mono text-slate-300">{deviceInfo.android_version} (SDK {deviceInfo.sdk})</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">IP Endpoint:</span>
                  <span className="font-mono text-blue-400">{deviceInfo.ip}:5555</span>
                </div>
                <div className="pt-2 border-t border-slate-800/60 flex justify-between items-center">
                  <span className="text-slate-400">Build:</span>
                  <span className="font-mono text-[11px] text-slate-400 truncate max-w-[180px]">{deviceInfo.build_id}</span>
                </div>
              </div>
            ) : (
              <div className="py-6 text-center text-xs text-slate-500">
                Connect your TV above to view hardware specifications and scan debloat status.
              </div>
            )}
          </div>

          {/* Debloat Health Metric */}
          {auditData && (
            <div className="bg-[#0f141c] border border-slate-800/80 rounded-xl p-5 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold">Debloat Health Score</span>
                <button 
                  onClick={() => runAudit(selectedIp)}
                  disabled={auditing}
                  className="text-slate-400 hover:text-slate-200 text-xs flex items-center gap-1 transition"
                >
                  <RefreshCw className={`w-3 h-3 ${auditing ? 'animate-spin' : ''}`} /> Scan
                </button>
              </div>

              <div className="flex items-baseline justify-between mb-2">
                <span className={`text-4xl font-extrabold tracking-tight ${
                  auditData.debloat_score >= 80 ? 'text-emerald-400' :
                  auditData.debloat_score >= 50 ? 'text-amber-400' : 'text-rose-400'
                }`}>
                  {auditData.debloat_score}%
                </span>
                <span className="text-xs text-slate-400 font-medium">
                  {auditData.total_bloat_disabled} of {auditData.total_bloat_detected} bloatware neutralized
                </span>
              </div>

              {/* Progress bar */}
              <div className="w-full bg-slate-800 rounded-full h-2 mb-4 overflow-hidden">
                <div 
                  className={`h-full transition-all duration-500 ${
                    auditData.debloat_score >= 80 ? 'bg-emerald-500' :
                    auditData.debloat_score >= 50 ? 'bg-amber-500' : 'bg-rose-500'
                  }`}
                  style={{ width: `${auditData.debloat_score}%` }}
                />
              </div>

              {/* Status verdict */}
              <div className={`p-3 rounded-lg text-xs flex items-start gap-2.5 ${
                auditData.is_debloated 
                  ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20'
                  : 'bg-rose-500/10 text-rose-300 border border-rose-500/20'
              }`}>
                {auditData.is_debloated ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                ) : (
                  <ShieldAlert className="w-4 h-4 text-rose-400 mt-0.5 shrink-0" />
                )}
                <div>
                  <div className="font-semibold">{auditData.is_debloated ? 'Clean & Optimized' : 'Action Required: Active Bloatware'}</div>
                  <div className="text-[11px] text-slate-400 mt-0.5">
                    {auditData.is_debloated 
                      ? 'Ad services and OEM overlays have been safely disabled.' 
                      : 'Telemetry, promotional overlays, and background ad services are currently draining CPU & RAM.'}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Live Viewfinder & D-Pad Remote */}
          <div className="bg-[#0f141c] border border-slate-800/80 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800/60 mb-3">
              <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold flex items-center gap-1.5">
                <Camera className="w-3.5 h-3.5 text-blue-400" /> Live TV Viewfinder
              </span>
              <button
                onClick={() => setScreenshotKey(Date.now())}
                className="text-xs text-blue-400 hover:text-blue-300 transition flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" /> Refresh
              </button>
            </div>

            <div className="relative aspect-video bg-black rounded-lg overflow-hidden border border-slate-800/80 flex items-center justify-center">
              {deviceInfo ? (
                <img 
                  src={`/api/screenshot?host=${selectedIp}&t=${screenshotKey}`}
                  alt="TV Screen"
                  className="w-full h-full object-contain"
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
              ) : (
                <span className="text-xs text-slate-600">Connect TV to view screen</span>
              )}
            </div>

            {/* D-Pad Remote Control Toggle */}
            <div className="mt-3">
              <button
                onClick={() => setShowRemote(!showRemote)}
                className="w-full py-1.5 text-xs text-slate-400 hover:text-slate-200 border border-slate-800 rounded-lg flex items-center justify-center gap-1.5 transition"
              >
                <Gamepad2 className="w-3.5 h-3.5 text-blue-400" /> {showRemote ? 'Hide Remote' : 'Show Virtual D-Pad Remote'}
              </button>

              {showRemote && (
                <div className="mt-3 p-3 bg-slate-900/60 rounded-xl border border-slate-800/80 flex flex-col items-center gap-2">
                  <button onClick={() => sendRemoteKey('up')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg"><ArrowUp className="w-4 h-4" /></button>
                  <div className="flex items-center gap-2">
                    <button onClick={() => sendRemoteKey('left')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg"><ArrowLeft className="w-4 h-4" /></button>
                    <button onClick={() => sendRemoteKey('select')} className="px-3 py-2 bg-blue-600 hover:bg-blue-500 text-xs font-bold rounded-lg">OK</button>
                    <button onClick={() => sendRemoteKey('right')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg"><ArrowRight className="w-4 h-4" /></button>
                  </div>
                  <button onClick={() => sendRemoteKey('down')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg"><ArrowDown className="w-4 h-4" /></button>
                  
                  <div className="flex items-center gap-2 mt-2 pt-2 border-t border-slate-800 w-full justify-center">
                    <button onClick={() => sendRemoteKey('back')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg" title="Back"><CornerDownLeft className="w-3.5 h-3.5" /></button>
                    <button onClick={() => sendRemoteKey('home')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg" title="Home"><Home className="w-3.5 h-3.5" /></button>
                    <button onClick={() => sendRemoteKey('volume_up')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg" title="Vol +"><Volume2 className="w-3.5 h-3.5" /></button>
                    <button onClick={() => sendRemoteKey('volume_down')} className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg" title="Vol -"><VolumeX className="w-3.5 h-3.5" /></button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Col: Debloat Categorization Table (P0 - P4) */}
        <div className="col-span-12 lg:col-span-8 space-y-4">
          
          {/* Status banner */}
          {statusMessage && (
            <div className="p-3 bg-blue-500/10 border border-blue-500/20 text-blue-300 rounded-xl text-xs flex items-center justify-between">
              <span>{statusMessage}</span>
              <button onClick={() => setStatusMessage('')} className="text-slate-400 hover:text-slate-200">✕</button>
            </div>
          )}

          {/* Filter Bar & Search */}
          <div className="bg-[#0f141c] border border-slate-800/80 rounded-xl p-4 shadow-sm flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-1.5 flex-wrap">
              {['ALL', 'P0', 'P1', 'P2', 'P3', 'P4', 'Protected'].map(prio => (
                <button
                  key={prio}
                  onClick={() => setSelectedPriority(prio)}
                  className={`px-2.5 py-1 text-xs rounded-lg font-medium transition ${
                    selectedPriority === prio
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
                  }`}
                >
                  {prio}
                  {auditData && prio !== 'ALL' && (
                    <span className="ml-1.5 text-[10px] opacity-70">
                      ({(auditData.categories[prio] || []).length})
                    </span>
                  )}
                </button>
              ))}
            </div>

            <div className="relative min-w-[220px]">
              <Search className="w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-400" />
              <input 
                type="text" 
                placeholder="Search packages..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="w-full bg-slate-900 border border-slate-800 rounded-lg pl-8 pr-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          {/* Priority Quick Action Bar */}
          {selectedPriority !== 'ALL' && selectedPriority !== 'Protected' && (
            <div className="p-3 bg-slate-900/60 border border-slate-800 rounded-xl flex items-center justify-between">
              <span className="text-xs text-slate-300">
                Batch actions for <strong>{selectedPriority}</strong> ({PRIORITY_BADGES[selectedPriority]?.label}):
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => batchAction(selectedPriority, 'debloat')}
                  disabled={actionLoading}
                  className="px-3 py-1 bg-rose-600/20 hover:bg-rose-600/30 text-rose-400 border border-rose-500/30 rounded-lg text-xs font-medium transition"
                >
                  Disable All {selectedPriority}
                </button>
                <button
                  onClick={() => batchAction(selectedPriority, 'restore')}
                  disabled={actionLoading}
                  className="px-3 py-1 bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 rounded-lg text-xs font-medium transition"
                >
                  Restore All {selectedPriority}
                </button>
              </div>
            </div>
          )}

          {/* Package Items List */}
          <div className="bg-[#0f141c] border border-slate-800/80 rounded-xl overflow-hidden shadow-sm divide-y divide-slate-800/60">
            {allFilteredPackages.length > 0 ? (
              allFilteredPackages.map(item => {
                const badge = PRIORITY_BADGES[item.priority] || PRIORITY_BADGES.P4;
                return (
                  <div key={item.package} className="p-4 flex items-center justify-between hover:bg-slate-900/40 transition">
                    <div className="space-y-1 pr-4">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`text-[10px] font-semibold px-2 py-0.5 rounded border ${badge.color}`}>
                          {item.priority}
                        </span>
                        <span className="font-semibold text-xs text-slate-100">{item.title}</span>
                        {item.disabled && (
                          <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700">
                            Disabled
                          </span>
                        )}
                        {item.protected && (
                          <span className="text-[10px] flex items-center gap-1 font-mono px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            <Lock className="w-2.5 h-2.5" /> Protected
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] font-mono text-slate-400">{item.package}</div>
                      <div className="text-xs text-slate-400">{item.description}</div>
                    </div>

                    {/* Action Toggle */}
                    <div className="shrink-0">
                      {item.protected ? (
                        <span className="text-[11px] text-slate-500 italic px-2">Core Service</span>
                      ) : (
                        <button
                          onClick={() => togglePackage(item.package, item.disabled)}
                          disabled={actionLoading}
                          className={`text-xs px-3.5 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5 shadow-sm ${
                            item.disabled 
                              ? 'bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700' 
                              : 'bg-rose-600/15 hover:bg-rose-600/25 text-rose-400 border border-rose-500/30'
                          }`}
                        >
                          {item.disabled ? (
                            <>
                              <RotateCcw className="w-3 h-3 text-slate-400" /> Restore
                            </>
                          ) : (
                            <>
                              <XCircle className="w-3 h-3 text-rose-400" /> Disable
                            </>
                          )}
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="py-12 text-center text-xs text-slate-500">
                {auditData ? 'No packages match the selected criteria.' : 'Connect your TV above to view audit and classification.'}
              </div>
            )}
          </div>
        </div>

      </main>
    </div>
  );
}
