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
  const [errors, setErrors] = useState<Record<string, string>>({});
  const reload = useCallback(async (page = 0) => {
    if (!customerId) {
      setAddresses([]); setAssets([]); setRequests([]); setRequestPage(0); setTotalPages(0); setErrors({});
      return;
    }
    setLoading(true); setErrors({});
    const id = encodeURIComponent(customerId);
    const [addressesResult, assetsResult, requestsResult] = await Promise.allSettled([
      api<Page<Address>>(`/customers/${id}/addresses?page=0&size=100`),
      api<Page<Asset>>(`/customers/${id}/assets?page=0&size=100`),
      api<Page<RequestRecord>>(`/service-requests/customer/${id}?page=${page}&size=20`),
    ]);
    const nextErrors: Record<string, string> = {};
    if (addressesResult.status === 'fulfilled') setAddresses(addressesResult.value.items ?? []);
    else { setAddresses([]); nextErrors.addresses = String(addressesResult.reason); }
    if (assetsResult.status === 'fulfilled') setAssets(assetsResult.value.items ?? []);
    else { setAssets([]); nextErrors.assets = String(assetsResult.reason); }
    if (requestsResult.status === 'fulfilled') {
      setRequests(requestsResult.value.items ?? []);
      setRequestPage(requestsResult.value.page); setTotalPages(requestsResult.value.totalPages);
    } else {
      setRequests([]); setRequestPage(0); setTotalPages(0);
      nextErrors.requests = String(requestsResult.reason);
    }
    setErrors(nextErrors); setLoading(false);
  }, [customerId]);
  useEffect(() => { void reload(0); }, [reload]);
  return { addresses, assets, requests, requestPage, totalPages, loading, errors, reload };
}

const short = (id: string) => id.slice(0, 8);
const empty = (s: string) => <div className="ws-empty">{s}</div>;

type RegistrationProps = {
  customerId: string;
  onSaved: () => Promise<void>;
};

function CustomerRecordForms({ customerId, onSaved }: RegistrationProps) {
  const [address, setAddress] = useState({
    addressLabel: 'Home', addressLine1: '', addressLine2: '',
    city: '', region: '', postalCode: '', countryCode: 'PH',
  });
  const [asset, setAsset] = useState({
    category: 'REFRIGERATOR', manufacturer: '', modelNumber: '',
    serialNumber: '', notes: '',
  });
  const [saving, setSaving] = useState<'address' | 'asset' | null>(null);
  const [feedback, setFeedback] = useState<Record<string, string>>({});
  const [failures, setFailures] = useState<Record<string, string>>({});

  async function saveAddress(event: FormEvent) {
    event.preventDefault(); setSaving('address');
    setFeedback(old => ({ ...old, address: '' }));
    setFailures(old => ({ ...old, address: '' }));
    try {
      await api(`/customers/${encodeURIComponent(customerId)}/addresses`, 'POST', {
        addressLabel: address.addressLabel.trim() || null,
        addressLine1: address.addressLine1.trim(),
        addressLine2: address.addressLine2.trim() || null,
        city: address.city.trim(), region: address.region.trim() || null,
        postalCode: address.postalCode.trim() || null,
        countryCode: address.countryCode.trim().toUpperCase(),
      });
      setAddress(old => ({ ...old, addressLine1: '', addressLine2: '', postalCode: '' }));
      setFeedback(old => ({ ...old, address: 'Address saved successfully.' }));
      await onSaved();
    } catch (err) {
      setFailures(old => ({ ...old, address: err instanceof Error ? err.message : 'Could not save address' }));
    } finally { setSaving(null); }
  }

  async function saveAsset(event: FormEvent) {
    event.preventDefault(); setSaving('asset');
    setFeedback(old => ({ ...old, asset: '' }));
    setFailures(old => ({ ...old, asset: '' }));
    try {
      await api(`/customers/${encodeURIComponent(customerId)}/assets`, 'POST', {
        category: asset.category.trim().toUpperCase(),
        manufacturer: asset.manufacturer.trim() || null,
        modelNumber: asset.modelNumber.trim() || null,
        serialNumber: asset.serialNumber.trim() || null,
        notes: asset.notes.trim() || null,
      });
      setAsset(old => ({ ...old, manufacturer: '', modelNumber: '', serialNumber: '', notes: '' }));
      setFeedback(old => ({ ...old, asset: 'Equipment registered successfully.' }));
      await onSaved();
    } catch (err) {
      setFailures(old => ({ ...old, asset: err instanceof Error ? err.message : 'Could not register equipment' }));
    } finally { setSaving(null); }
  }

  return <div className="ws-columns ws-record-forms">
    <section className="panel"><div className="panel-heading"><span className="panel-icon">⌖</span><div><h3>Add service address</h3><p>Save an address for this customer</p></div></div>
      <form className="form-stack" onSubmit={e => void saveAddress(e)}>
        <label>Label<input maxLength={80} value={address.addressLabel} onChange={e=>setAddress({...address,addressLabel:e.target.value})} placeholder="Home / Office" /></label>
        <label>Address line 1<input required maxLength={255} value={address.addressLine1} onChange={e=>setAddress({...address,addressLine1:e.target.value})} placeholder="Street and building number" /></label>
        <label>Address line 2 (optional)<input maxLength={255} value={address.addressLine2} onChange={e=>setAddress({...address,addressLine2:e.target.value})} /></label>
        <div className="form-row"><label>City<input required maxLength={120} value={address.city} onChange={e=>setAddress({...address,city:e.target.value})} /></label><label>Region<input maxLength={120} value={address.region} onChange={e=>setAddress({...address,region:e.target.value})} /></label></div>
        <div className="form-row"><label>Postal code<input maxLength={30} value={address.postalCode} onChange={e=>setAddress({...address,postalCode:e.target.value})} /></label><label>Country code<input required minLength={2} maxLength={2} pattern="[A-Za-z]{2}" title="Two-letter country code" value={address.countryCode} onChange={e=>setAddress({...address,countryCode:e.target.value.toUpperCase()})} /></label></div>
        <button type="submit" className="primary-btn" disabled={saving!==null}>{saving==='address'?'Saving...':'Save address →'}</button>
      </form>
      {feedback.address && <p className="ws-feedback-success" role="status">{feedback.address}</p>}
      {failures.address && <p className="ws-error" role="alert">{failures.address}</p>}
    </section>
    <section className="panel"><div className="panel-heading"><span className="panel-icon">⚙</span><div><h3>Register equipment</h3><p>Link an asset to this customer</p></div></div>
      <form className="form-stack" onSubmit={e => void saveAsset(e)}>
        <label>Equipment category<input required maxLength={60} value={asset.category} onChange={e=>setAsset({...asset,category:e.target.value.toUpperCase()})} placeholder="REFRIGERATOR" /></label>
        <div className="form-row"><label>Manufacturer<input maxLength={120} value={asset.manufacturer} onChange={e=>setAsset({...asset,manufacturer:e.target.value})} placeholder="Samsung" /></label><label>Model number<input maxLength={120} value={asset.modelNumber} onChange={e=>setAsset({...asset,modelNumber:e.target.value})} /></label></div>
        <label>Serial number<input maxLength={150} value={asset.serialNumber} onChange={e=>setAsset({...asset,serialNumber:e.target.value})} /></label>
        <label>Notes<textarea rows={3} value={asset.notes} onChange={e=>setAsset({...asset,notes:e.target.value})} placeholder="Location, condition, or additional information" /></label>
        <button type="submit" className="primary-btn" disabled={saving!==null}>{saving==='asset'?'Saving...':'Register equipment →'}</button>
      </form>
      {feedback.asset && <p className="ws-feedback-success" role="status">{feedback.asset}</p>}
      {failures.asset && <p className="ws-error" role="alert">{failures.asset}</p>}
    </section>
  </div>;
}

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
    {customer && <CustomerRecordForms key={String(customer.customerId)} customerId={String(customer.customerId)} onSaved={() => records.reload(records.requestPage)} />}
    {customer && <section className="panel"><div className="ws-heading"><div><h3>Customer records</h3><p>Addresses, registered assets and service history</p></div><button className="secondary-btn ws-refresh" disabled={records.loading} onClick={()=>void records.reload(records.requestPage)}>Refresh</button></div>
      {Object.entries(records.errors).map(([section, reason]) => <p className="ws-error" role="alert" key={section}>{section}: {reason}</p>)}
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
  useEffect(()=>{setAddressId(previous => records.addresses.some(a=>a.addressId===previous) ? previous : (records.addresses[0]?.addressId ?? '')); setAssetId(previous => records.assets.some(a=>a.assetId===previous) ? previous : '');},[records.addresses, records.assets]);
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
    {customer && <details className="ws-setup-details"><summary>Need to add an address or equipment? Register it here</summary><CustomerRecordForms key={String(customer.customerId)} customerId={String(customer.customerId)} onSaved={() => records.reload(records.requestPage)} /></details>}
    {customer && <div className="ws-columns"><section className="panel"><div className="panel-heading"><span className="panel-icon">＋</span><div><h3>New service request</h3><p>Linked to the selected customer</p></div></div>
      <form className="form-stack" onSubmit={e=>void create(e)}><label>Service address<select required value={addressId} onChange={e=>setAddressId(e.target.value)}><option value="">Select address</option>{records.addresses.map(a=><option key={a.addressId} value={a.addressId}>{a.addressLabel || a.addressLine1} · {a.city}</option>)}</select></label><label>Equipment<select value={assetId} onChange={e=>setAssetId(e.target.value)}><option value="">No equipment</option>{records.assets.map(a=><option key={a.assetId} value={a.assetId}>{a.category} · {a.manufacturer || 'Unknown'} {a.modelNumber || ''}</option>)}</select></label><label>Issue summary<input required maxLength={200} value={summary} onChange={e=>setSummary(e.target.value)} placeholder="Refrigerator is not cooling"/></label><label>Description<textarea required rows={4} value={description} onChange={e=>setDescription(e.target.value)} placeholder="Describe the problem"/></label><label>Priority<select value={priority} onChange={e=>setPriority(e.target.value)}>{['LOW','NORMAL','HIGH','URGENT'].map(p=><option key={p}>{p}</option>)}</select></label><button className="primary-btn" disabled={busy || records.loading || records.addresses.length===0}>Create service request →</button>{records.addresses.length===0 && <p className="ws-help">This customer needs a service address before a request can be created.</p>}</form>
    </section><section className="panel"><div className="ws-heading"><div><h3>Customer request history</h3><p>Real records from the paginated API</p></div><button className="secondary-btn ws-refresh" disabled={records.loading} onClick={()=>void records.reload(records.requestPage)}>Refresh</button></div>
      {Object.entries(records.errors).map(([section, reason]) => <p className="ws-error" role="alert" key={section}>{section}: {reason}</p>)}
      {current && <div className="ws-created"><strong>Latest created request</strong><code>{current.requestId}</code><span>{current.requestStatus}</span></div>}
      {records.requests.length ? records.requests.map(r=><div className="ws-list-row" key={r.requestId}><div><strong>{r.issueSummary}</strong><small>{r.priority} · {new Date(r.createdAt).toLocaleString('en-PH')}</small><code>{r.requestId}</code></div><span className="ws-status">{r.requestStatus}</span></div>) : empty('No requests yet')}
      {records.totalPages>1 && <div className="ws-pages"><button disabled={records.loading || records.requestPage===0} onClick={()=>void records.reload(records.requestPage-1)}>Previous</button><span>Page {records.requestPage+1} of {records.totalPages}</span><button disabled={records.loading || records.requestPage+1>=records.totalPages} onClick={()=>void records.reload(records.requestPage+1)}>Next</button></div>}
    </section></div>}
  </div>;
}
