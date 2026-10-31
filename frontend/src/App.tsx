import { useEffect, useMemo, useState, type FormEvent } from 'react';
import './App.css';
import { CustomerWorkspace, RequestWorkspace } from './CustomerWorkspace';

type View = 'Overview' | 'Customers' | 'Service Requests' | 'Scheduling' | 'Work Orders';
type Json = Record<string, unknown>;
type Slot = { technicianId: string; technicianName?: string; startsAt: string; endsAt: string };
const API = '/api/v1';

async function call<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...(options?.body ? { 'Content-Type': 'application/json' } : {}), ...options?.headers },
  });
  const raw = await response.text();
  let payload: unknown;
  try { payload = raw ? JSON.parse(raw) : null; } catch { payload = raw; }
  if (!response.ok) {
    const detail = payload && typeof payload === 'object'
      ? String((payload as Json).detail ?? (payload as Json).message ?? (payload as Json).error ?? '')
      : String(payload ?? '');
    throw new Error(`${response.status} ${response.statusText}${detail ? ` — ${detail}` : ''}`);
  }
  return payload as T;
}

function formatDate(value: unknown): string {
  if (typeof value !== 'string' || !value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('en-PH', { dateStyle: 'medium', timeStyle: 'short' });
}
function formatVal(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
function keyValue(data: Json, labels?: Record<string, string>) {
  return Object.entries(data).filter(([key]) => !key.startsWith('_')).map(([key, value]) => (
    <div className="detail-line" key={key}>
      <span>{labels?.[key] ?? key.replace(/([A-Z])/g, ' $1').replace(/^./, x => x.toUpperCase())}</span>
      <strong>{/At$/.test(key) ? formatDate(value) : formatVal(value)}</strong>
    </div>
  ));
}

const menu: { group: string; items: { label: View; icon: string }[] }[] = [
  { group: 'WORKSPACE', items: [{ label: 'Overview', icon: '◫' }, { label: 'Customers', icon: '♙' }, { label: 'Service Requests', icon: '▤' }] },
  { group: 'OPERATIONS', items: [{ label: 'Scheduling', icon: '▦' }, { label: 'Work Orders', icon: '▧' }] },
];

function App() {
  const [view, setView] = useState<View>('Overview');
  const [health, setHealth] = useState<'checking' | 'online' | 'offline'>('checking');
  const [notice, setNotice] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [customerId, setCustomerId] = useState('');
  const [workOrderId, setWorkOrderId] = useState('');
  const [workOrder, setWorkOrder] = useState<Json | null>(null);
  const [history, setHistory] = useState<unknown>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [category, setCategory] = useState('REFRIGERATOR');
  const [date, setDate] = useState(() => { const d = new Date(); d.setDate(d.getDate() + 1); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; });
  const [duration, setDuration] = useState(60);
  const availableCount = slots.length;

  useEffect(() => {
    fetch('/actuator/health').then(r => setHealth(r.ok ? 'online' : 'offline')).catch(() => setHealth('offline'));
  }, []);

  const dateLabel = useMemo(() => new Date().toLocaleDateString('en-PH', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }), []);
  async function run<T>(action: () => Promise<T>, onSuccess: (value: T) => void, successText?: string) {
    setBusy(true); setNotice(null);
    try { const data = await action(); onSuccess(data); if (successText) setNotice({ kind: 'success', text: successText }); }
    catch (e) { setNotice({ kind: 'error', text: e instanceof Error ? e.message : 'Request failed' }); }
    finally { setBusy(false); }
  }
  function findWorkOrder(e: FormEvent) { e.preventDefault(); if (!workOrderId.trim()) return; void run(async () => {
    const id = encodeURIComponent(workOrderId.trim());
    const [record, events] = await Promise.all([call<Json>(`/work-orders/${id}`), call<unknown>(`/work-orders/${id}/history`)]);
    return { record, events };
  }, ({record,events}) => { setWorkOrder(record); setHistory(events); }, 'Work order and history loaded.'); }
  function findSlots(e: FormEvent) { e.preventDefault(); void run(() => call<Slot[]>(`/availability?${new URLSearchParams({ category: category.trim(), date, durationMinutes: String(duration), stepMinutes: '30' })}`), setSlots, 'Availability refreshed from PostgreSQL.'); }

  const title = view === 'Overview' ? 'Operations overview' : view;
  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div className="brand"><div className="brand-mark">S<span>·</span></div><div><strong>SERVIQ<span> AI</span></strong><small>OPERATIONS PLATFORM</small></div></div>
        <div className="workspace"><span className="workspace-symbol">◇</span><div><strong>Service Operations</strong><small>Command workspace</small></div><span className="chevron">⌄</span></div>
        <nav>{menu.map(group => <div className="nav-group" key={group.group}><div className="group-label">{group.group}</div>{group.items.map(item => <button key={item.label} className={`nav-item ${view === item.label ? 'selected' : ''}`} onClick={() => { setView(item.label); setNotice(null); }}><span className="nav-icon">{item.icon}</span>{item.label}{view === item.label && <span className="nav-marker" />}</button>)}</div>)}</nav>
        <div className="sidebar-bottom"><div className="help-dot">✧</div><div><strong>Development workspace</strong><small>Connected to local Spring Boot</small></div></div>
        <div className="profile"><div className="avatar">SA</div><div><strong>Serviq Administrator</strong><small>Local development</small></div><span>⋯</span></div>
      </aside>
      <main className="main">
        <header className="topbar"><span className="crumb">Workspace <span>/</span> {view}</span><div className="top-actions"><span className={`health-dot ${health}`}/><span className="health-label">API {health}</span><span className="top-divider"/><span className="env-pill">DEV ENVIRONMENT</span><span className="circle-user">SA</span></div></header>
        <div className="content">
          <div className="page-heading"><div><div className="eyebrow">SERVIQ / {view.toUpperCase()}</div><h1>{title}</h1><p>{view === 'Overview' ? 'Your service workflows, connected in one place.' : 'Live operations powered by your Spring Boot services.'}</p></div><div className="heading-right"><span className="today">◷ &nbsp;{dateLabel}</span></div></div>
          {notice && <div className={`notice ${notice.kind}`} role="status"><span>{notice.kind === 'success' ? '✓' : '!'}</span>{notice.text}<button onClick={() => setNotice(null)} aria-label="Dismiss">×</button></div>}

          {view === 'Overview' && <>
            <section className="hero"><div><div className="hero-kicker"><span/> THE OPERATIONS HUB</div><h2>Every service.<br/><em>In perfect sync.</em></h2><p>Manage customer requests, technician availability, and work orders through your real backend workflows.</p><button className="primary-btn" onClick={() => setView('Scheduling')}>Explore availability <span>↗</span></button></div><div className="hero-art" aria-hidden="true"><div className="orb orb-one"/><div className="orb orb-two"/><div className="orb orb-three"/><div className="orb-core">S</div><span className="orbit-label a">CUSTOMERS</span><span className="orbit-label b">TECHNICIANS</span><span className="orbit-label c">OPERATIONS</span></div></section>
            <div className="section-title"><div><h3>Workspace health</h3><p>Live connectivity and implementation status</p></div><span className="minor-tag">LIVE SYSTEM</span></div>
            <div className="stat-grid">
              <div className="stat-card"><div className="stat-top"><span>Backend connection</span><span className="stat-symbol">↗</span></div><strong>{health === 'online' ? 'Online' : health === 'checking' ? 'Checking' : 'Offline'}</strong><small>Spring Boot actuator health</small><div className="stat-foot"><span className={`tiny-dot ${health}`}/>Verified from /actuator/health</div></div>
              <div className="stat-card"><div className="stat-top"><span>Availability results</span><span className="stat-symbol">▦</span></div><strong>{slots.length ? slots.length : '—'}</strong><small>Slots in most recent search</small><div className="stat-foot">{slots.length ? 'Live query results' : 'Run a scheduling search'}</div></div>
              <div className="stat-card"><div className="stat-top"><span>Work order</span><span className="stat-symbol">▧</span></div><strong className="status-card-text">{workOrder ? formatVal(workOrder.status) : 'Not loaded'}</strong><small>{workOrder ? `#${formatVal(workOrder.workOrderNumber)}` : 'Lookup a work order to view'}</small><div className="stat-foot">No simulated KPI data</div></div>
            </div>
            <div className="section-title second"><div><h3>Quick actions</h3><p>Continue a service workflow</p></div></div>
            <div className="quick-grid">{([{ name: 'Find a customer', sub: 'Look up a customer by ID', icon: '♙', target: 'Customers' },{ name: 'Open service request', sub: 'Retrieve or create a service request', icon: '▤', target: 'Service Requests' },{ name: 'Check technician slots', sub: 'Find available appointment windows', icon: '▦', target: 'Scheduling' },{ name: 'Review work order', sub: 'Inspect work and status history', icon: '▧', target: 'Work Orders' }] as {name:string;sub:string;icon:string;target:View}[]).map(item => <button key={item.name} className="quick-action" onClick={() => setView(item.target)}><span className="quick-icon">{item.icon}</span><span><strong>{item.name}</strong><small>{item.sub}</small></span><span className="quick-arrow">↗</span></button>)}</div>
          </>}

          {view === 'Customers' && <CustomerWorkspace selectedId={customerId} onSelect={setCustomerId} />}
          {view === 'Service Requests' && <RequestWorkspace selectedId={customerId} onSelect={setCustomerId} />}

          {view === 'Scheduling' && <><div className="two-col scheduling"><section className="panel"><div className="panel-heading"><span className="panel-icon">▦</span><div><h3>Find available technicians</h3><p>Search working hours, skills, time off, and bookings</p></div></div><form className="form-stack" onSubmit={findSlots}><label>Equipment category<input required value={category} onChange={e=>setCategory(e.target.value)} placeholder="REFRIGERATOR"/></label><div className="form-row"><label>Service date<input required type="date" value={date} onChange={e=>setDate(e.target.value)}/></label><label>Duration (minutes)<select value={duration} onChange={e=>setDuration(Number(e.target.value))}><option value={30}>30 minutes</option><option value={60}>60 minutes</option><option value={90}>90 minutes</option><option value={120}>120 minutes</option></select></label></div><button className="primary-btn" disabled={busy}>Search availability <span>↗</span></button></form></section><section className="panel scheduling-explain"><div className="panel-heading"><span className="panel-icon">✦</span><div><h3>Scheduling rules</h3><p>Calculated in your Spring Boot backend</p></div></div>{['Only active technicians with matching skills','Inside each technician’s local working hours','Excludes absences and active booking assignments','Booking must still revalidate before reservation'].map((s,i)=><div key={s} className="rule"><span>{i+1}</span>{s}</div>)}</section></div><section className="panel result-panel"><div className="panel-heading"><span className="panel-icon">◷</span><div><h3>Available appointment windows</h3><p>{availableCount ? `${availableCount} results returned` : 'Results appear after you search'}</p></div></div>{availableCount ? <div className="slots-table"><div className="table-header"><span>TECHNICIAN</span><span>START (LOCAL DISPLAY)</span><span>END (LOCAL DISPLAY)</span></div>{slots.map((slot,i)=><div className="slot-row" key={`${slot.technicianId}-${slot.startsAt}-${i}`}><span><strong>{slot.technicianName || 'Technician'}</strong><small>{slot.technicianId}</small></span><span>{formatDate(slot.startsAt)}</span><span>{formatDate(slot.endsAt)}</span></div>)}</div>:<EmptyState icon="▦" text="No slots loaded. Run a search to retrieve real availability."/>}</section></>}

          {view === 'Work Orders' && <div className="two-col"><section className="panel"><div className="panel-heading"><span className="panel-icon">⌕</span><div><h3>Work order lookup</h3><p>Retrieve a work order and its audit trail</p></div></div><form className="form-stack" onSubmit={findWorkOrder}><label>Work order UUID<input required value={workOrderId} onChange={e=>setWorkOrderId(e.target.value)} placeholder="fe5139b4-a827-4525-9660-88b14392b7b5"/></label><button className="primary-btn" disabled={busy}>Retrieve work order <span>→</span></button></form>{workOrder && <div className="details separated">{keyValue(workOrder)}</div>}</section><section className="panel"><div className="panel-heading"><span className="panel-icon">◷</span><div><h3>Status history</h3><p>Auditable transitions stored in PostgreSQL</p></div></div>{history ? <div className="history-view">{Array.isArray(history) ? history.map((item,i) => <div className="history-item" key={i}><span className="history-bullet"/><div className="details">{item && typeof item === 'object' ? keyValue(item as Json) : formatVal(item)}</div></div>) : <pre>{JSON.stringify(history,null,2)}</pre>}</div> : <EmptyState icon="◷" text="Retrieve a work order to inspect its recorded history."/>}</section></div>}
          <footer>© SERVIQ AI <span>•</span> Local development interface <span>•</span> All operational data comes from the backend</footer>
        </div>
      </main>
    </div>
  );
}
function EmptyState({icon,text}:{icon:string;text:string}) { return <div className="empty-state"><div>{icon}</div><p>{text}</p></div>; }
export default App;
