import { useCallback, useEffect, useState, type FormEvent } from 'react';

const API = '/api/v1';
type Entity = Record<string, unknown>;
type Page<T> = { items: T[]; page: number; totalItems: number; totalPages: number };
export type Address = { addressId: string; addressLabel?: string; addressLine1: string; city: string; countryCode: string };
export type Asset = { assetId: string; category: string; manufacturer?: string; modelNumber?: string };
export type RequestRecord = { requestId: string; issueSummary: string; requestStatus: string; priority: string; createdAt: string; customerId: string };

async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch(`${API}${path}`, { method, headers: body === undefined ? undefined : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let data: unknown;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) {
    const detail = data && typeof data === 'object' ? String((data as Entity).detail ?? (data as Entity).message ?? (data as Entity).error ?? '') : String(data ?? '');
    throw new Error(`HTTP ${res.status}${detail ? `: ${detail}` : ''}`);
  }
  return data as T;
}

function useCustomerRecords(customerId: string) {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [requests, setRequests] = useState<RequestRecord[]>([]);
  const [requestPage, setRequestPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const reload = useCallback(async (page = 0) => {
    if (!customerId) { setAddresses([]); setAssets([]); setRequests([]); return; }
    setLoading(true); setError('');
    try {
      const id = encodeURIComponent(customerId);
      const [a, b, r] = await Promise.all([
        api<Page<Address>>(`/customers/${id}/addresses?size=100`),
        api<Page<Asset>>(`/customers/${id}/assets?size=100`),
        api<Page<RequestRecord>>(`/service-requests/customer/${id}?page=${page}&size=20`),
      ]);
      setAddresses(a.items); setAssets(b.items); setRequests(r.items);
      setRequestPage(r.page); setTotalPages(r.totalPages);
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load records'); }
    finally { setLoading(false); }
  }, [customerId]);
  useEffect(() => { void reload(0); }, [reload]);
  return { addresses, assets, requests, requestPage, totalPages, loading, error, reload };
}

const short = (id: string) => id.slice(0, 8);
const empty = (s: string) => <div className="ws-empty">{s}</div>;

export function CustomerWorkspace({ selectedId, onSelect }: { selectedId: string; onSelect: (id: string) => void }) {
  const [lookup, setLookup] = useState(selectedId);
  const [customer, setCustomer] = useState<Entity | null>(null);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const records = useCustomerRecords(customer ? String(customer.customerId) : '');
  useEffect(() => { if (selectedId) setLookup(selectedId); }, [selectedId]);
  async function find(e?: FormEvent, id = lookup) {
    e?.preventDefault(); if (!id.trim()) return;
    setBusy(true); setError(''); setMessage('');
    try { const data = await api<Entity>(`/customers/${encodeURIComponent(id.trim())}`); setCustomer(data); onSelect(String(data.customerId)); }
    catch (e) { setCustomer(null); setError(e instanceof Error ? e.message : 'Customer not found'); }
    finally { setBusy(false); }
  }
  async function register(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setMessage('');
    try {
      const data = await api<Entity>('/customers', 'POST', { fullName: fullName.trim(), emailAddress: email.trim() || null, phoneNumber: phone.trim() || null });
      setCustomer(data); const id = String(data.customerId); setLookup(id); onSelect(id);
      setMessage('Customer registered and selected.'); setFullName(''); setEmail(''); setPhone('');
    } catch(e) { setError(e instanceof Error ? e.message : 'Registration failed'); }
    finally { setBusy(false); }
  }
  return <div className="ws-stack">
    <div className="ws-columns">
      <section className="panel"><div className="panel-heading"><span className="panel-icon">⌕</span><div><h3>Find a customer</h3><p>Use an existing customer ID</p></div></div>
        <form className="inline-form" onSubmit={e => void find(e)}><input aria-label="Customer UUID" value={lookup} onChange={e=>setLookup(e.target.value)} placeholder="Customer UUID" required /><button className="secondary-btn" disabled={busy}>Find</button></form>
        <p className="ws-help">The API does not yet support a global customer list. Search by ID or register a customer below.</p>
        {customer && <div className="ws-profile"><div className="ws-avatar">{String(customer.fullName ?? '?').slice(0, 1).toUpperCase()}</div><div><h3>{String(customer.fullName ?? '')}</h3><p>{String(customer.emailAddress ?? 'No email')} · {String(customer.phoneNumber ?? 'No phone')}</p><code>{String(customer.customerId)}</code></div></div>}
      </section>
      <section className="panel"><div className="panel-heading"><span className="panel-icon">＋</span><div><h3>Register customer</h3><p>Create a PostgreSQL-backed profile</p></div></div>
        <form className="form-stack" onSubmit={e=>void register(e)}><label>Full name<input value={fullName} maxLength={150} onChange={e=>setFullName(e.target.value)} required placeholder="Maria Santos" /></label><label>Email (optional)<input type="email" value={email} onChange={e=>setEmail(e.target.value)} /></label><label>Phone (optional)<input value={phone} onChange={e=>setPhone(e.target.value)} /></label><button className="primary-btn" disabled={busy}>Create customer →</button></form>
      </section>
    </div>
    {error && <div className="notice error" role="alert">{error}</div>}{message && <div className="notice success" role="status">{message}</div>}
    {customer && <section className="panel"><div className="ws-heading"><div><h3>Customer records</h3><p>Addresses, registered assets and service history</p></div><button className="secondary-btn ws-refresh" disabled={records.loading} onClick={()=>void records.reload(records.requestPage)}>Refresh</button></div>
      {records.error && <p className="ws-error">{records.error}</p>}
      <div className="ws-columns"><div><h4>Addresses <span>{records.addresses.length}</span></h4>{records.addresses.length ? records.addresses.map(a=><div className="ws-list-row" key={a.addressId}><div><strong>{a.addressLabel || 'Service address'}</strong><small>{a.addressLine1}, {a.city} · {a.countryCode}</small></div><code>{short(a.addressId)}</code></div>) : empty('No addresses registered')}</div><div><h4>Equipment <span>{records.assets.length}</span></h4>{records.assets.length ? records.assets.map(a=><div className="ws-list-row" key={a.assetId}><div><strong>{a.category}</strong><small>{[a.manufacturer,a.modelNumber].filter(Boolean).join(' ') || 'No model details'}</small></div><code>{short(a.assetId)}</code></div>) : empty('No equipment registered')}</div></div>
      <div className="ws-request-section"><h4>Service requests <span>{records.requests.length} on this page</span></h4>{records.requests.length ? records.requests.map(r=><div className="ws-list-row" key={r.requestId}><div><strong>{r.issueSummary}</strong><small>{r.priority} · {new Date(r.createdAt).toLocaleDateString('en-PH')}</small></div><span className="ws-status">{r.requestStatus}</span></div>) : empty('No service requests for this customer')}
      {records.totalPages > 1 && <div className="ws-pages"><button disabled={records.loading || records.requestPage===0} onClick={()=>void records.reload(records.requestPage-1)}>Previous</button><span>Page {records.requestPage+1} of {records.totalPages}</span><button disabled={records.loading || records.requestPage+1>=records.totalPages} onClick={()=>void records.reload(records.requestPage+1)}>Next</button></div>}</div>
    </section>}
  </div>;
}

export function RequestWorkspace({ selectedId, onSelect }: { selectedId: string; onSelect: (id: string) => void }) {
  const [lookup, setLookup] = useState(selectedId);
  const [customer, setCustomer] = useState<Entity | null>(null);
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('NORMAL');
  const [addressId, setAddressId] = useState('');
  const [assetId, setAssetId] = useState('');
  const [current, setCurrent] = useState<RequestRecord | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const records = useCustomerRecords(customer ? String(customer.customerId) : '');
  useEffect(()=>{if(selectedId) setLookup(selectedId)},[selectedId]);
  useEffect(()=>{setAddressId(records.addresses[0]?.addressId ?? ''); setAssetId('')},[records.addresses]);
  async function loadCustomer(e:FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { const c = await api<Entity>(`/customers/${encodeURIComponent(lookup.trim())}`); setCustomer(c); onSelect(String(c.customerId)); setCurrent(null); }
    catch(e){ setCustomer(null); setError(e instanceof Error ? e.message : 'Customer not found'); }
    finally { setBusy(false); }
  }
  async function create(e:FormEvent) {
    e.preventDefault(); if (!customer || !addressId) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await api<RequestRecord>('/service-requests','POST',{ customerId:customer.customerId, addressId, assetId:assetId || null, issueSummary:summary.trim(), issueDescription:description.trim(), priority, intakeChannel:'STAFF' });
      setCurrent(result); setSummary(''); setDescription(''); setNotice(`Request ${short(result.requestId)} created.`); await records.reload(0);
    } catch(e) { setError(e instanceof Error ? e.message : 'Creation failed'); }
    finally { setBusy(false); }
  }
  return <div className="ws-stack">
    <section className="panel"><div className="ws-heading"><div><h3>Customer context</h3><p>Load a customer to select their registered address and equipment</p></div>{customer && <span className="ws-status">{String(customer.fullName)}</span>}</div><form className="inline-form" onSubmit={e=>void loadCustomer(e)}><input aria-label="Customer UUID" required value={lookup} onChange={e=>setLookup(e.target.value)} placeholder="Customer UUID"/><button className="secondary-btn" disabled={busy}>Load customer</button></form></section>
    {error && <div className="notice error" role="alert">{error}</div>}{notice && <div className="notice success" role="status">{notice}</div>}
    {customer && <div className="ws-columns"><section className="panel"><div className="panel-heading"><span className="panel-icon">＋</span><div><h3>New service request</h3><p>Linked to the selected customer</p></div></div>
      <form className="form-stack" onSubmit={e=>void create(e)}><label>Service address<select required value={addressId} onChange={e=>setAddressId(e.target.value)}><option value="">Select address</option>{records.addresses.map(a=><option key={a.addressId} value={a.addressId}>{a.addressLabel || a.addressLine1} · {a.city}</option>)}</select></label><label>Equipment<select value={assetId} onChange={e=>setAssetId(e.target.value)}><option value="">No equipment</option>{records.assets.map(a=><option key={a.assetId} value={a.assetId}>{a.category} · {a.manufacturer || 'Unknown'} {a.modelNumber || ''}</option>)}</select></label><label>Issue summary<input required maxLength={200} value={summary} onChange={e=>setSummary(e.target.value)} placeholder="Refrigerator is not cooling"/></label><label>Description<textarea required rows={4} value={description} onChange={e=>setDescription(e.target.value)} placeholder="Describe the problem"/></label><label>Priority<select value={priority} onChange={e=>setPriority(e.target.value)}>{['LOW','NORMAL','HIGH','URGENT'].map(p=><option key={p}>{p}</option>)}</select></label><button className="primary-btn" disabled={busy || records.loading || records.addresses.length===0}>Create service request →</button>{records.addresses.length===0 && <p className="ws-help">This customer needs a service address before a request can be created.</p>}</form>
    </section><section className="panel"><div className="ws-heading"><div><h3>Customer request history</h3><p>Real records from the paginated API</p></div><button className="secondary-btn ws-refresh" disabled={records.loading} onClick={()=>void records.reload(records.requestPage)}>Refresh</button></div>
      {records.error && <p className="ws-error">{records.error}</p>}
      {current && <div className="ws-created"><strong>Latest created request</strong><code>{current.requestId}</code><span>{current.requestStatus}</span></div>}
      {records.requests.length ? records.requests.map(r=><div className="ws-list-row" key={r.requestId}><div><strong>{r.issueSummary}</strong><small>{r.priority} · {new Date(r.createdAt).toLocaleString('en-PH')}</small><code>{r.requestId}</code></div><span className="ws-status">{r.requestStatus}</span></div>) : empty('No requests yet')}
      {records.totalPages>1 && <div className="ws-pages"><button disabled={records.loading || records.requestPage===0} onClick={()=>void records.reload(records.requestPage-1)}>Previous</button><span>Page {records.requestPage+1} of {records.totalPages}</span><button disabled={records.loading || records.requestPage+1>=records.totalPages} onClick={()=>void records.reload(records.requestPage+1)}>Next</button></div>}
    </section></div>}
  </div>;
}
