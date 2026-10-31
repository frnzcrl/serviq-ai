import { useCallback, useEffect, useState, type FormEvent } from 'react';
import './WorkOrderWorkspace.css';

const API = '/api/v1';
const uuidPattern = /^[\da-f]{8}-[\da-f]{4}-[1-8][\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i;

type WorkOrder = {
  workOrderId: string; workOrderNumber: number; requestId: string; status: string;
  diagnosis: string | null; resolutionNotes: string | null;
  openedAt: string; startedAt: string | null; completedAt: string | null; updatedAt: string;
};
type EventRow = { status_history_id: string; previous_status: string | null; new_status: string; change_reason: string | null; changed_by: string; changed_at: string };
type NoteRow = { note_id: string; note_text: string; created_by: string; created_at: string };
type LineRow = { line_id: string; line_type: string; description: string; quantity: number; unit_price: number; currency_code: string; line_total: number; line_notes: string | null; created_at: string };
type Feedback = { kind: 'success' | 'error'; message: string } | null;

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, { ...options, headers: { ...(options?.body ? { 'Content-Type': 'application/json' } : {}), ...options?.headers } });
  const raw = await response.text();
  let data: unknown;
  try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
  if (!response.ok) {
    const details = data && typeof data === 'object' ? data as Record<string, unknown> : {};
    throw new Error(`HTTP ${response.status}: ${String(details.detail ?? details.message ?? details.error ?? raw ?? 'Request failed')}`);
  }
  return data as T;
}
const json = (value: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(value) });
const displayDate = (value: string | null) => value ? new Date(value).toLocaleString('en-PH', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Manila' }) : '—';
const transitions: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['ON_HOLD', 'COMPLETED', 'CANCELLED'],
  ON_HOLD: ['IN_PROGRESS', 'CANCELLED'],
  COMPLETED: [], CANCELLED: [],
};

export function WorkOrderWorkspace() {
  const [lookupId, setLookupId] = useState('');
  const [requestId, setRequestId] = useState('');
  const [orders, setOrders] = useState<WorkOrder[]>([]);
  const [order, setOrder] = useState<WorkOrder | null>(null);
  const [history, setHistory] = useState<EventRow[]>([]);
  const [notes, setNotes] = useState<NoteRow[]>([]);
  const [lines, setLines] = useState<LineRow[]>([]);
  const [diagnosis, setDiagnosis] = useState('');
  const [resolution, setResolution] = useState('');
  const [actor, setActor] = useState('Local Staff');
  const [nextStatus, setNextStatus] = useState('');
  const [reason, setReason] = useState('');
  const [noteText, setNoteText] = useState('');
  const [lineType, setLineType] = useState('LABOR');
  const [description, setDescription] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [unitPrice, setUnitPrice] = useState('0.00');
  const [currency, setCurrency] = useState('PHP');
  const [lineNotes, setLineNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [sectionError, setSectionError] = useState<string | null>(null);

  const sync = useCallback(async (id: string) => {
    const encoded = encodeURIComponent(id);
    // The primary work-order record is essential. Ancillary reads are independently fault-tolerant.
    const record = await api<WorkOrder>(`/work-orders/${encoded}`);
    setOrder(record);
    setLookupId(record.workOrderId);
    setDiagnosis(record.diagnosis ?? '');
    setResolution(record.resolutionNotes ?? '');
    const responses = await Promise.allSettled([
      api<EventRow[]>(`/work-orders/${encoded}/history`),
      api<NoteRow[]>(`/work-orders/${encoded}/notes`),
      api<LineRow[]>(`/work-orders/${encoded}/lines`),
    ]);
    setHistory(responses[0].status === 'fulfilled' ? responses[0].value : []);
    setNotes(responses[1].status === 'fulfilled' ? responses[1].value : []);
    setLines(responses[2].status === 'fulfilled' ? responses[2].value : []);
    setSectionError(responses.some(r => r.status === 'rejected') ? 'Some related records could not be loaded. Retry Refresh.' : null);
  }, []);

  useEffect(() => { setNextStatus(''); }, [order?.workOrderId, order?.status]);
  async function perform(fn: () => Promise<unknown>, success: string, reload = true) {
    setLoading(true); setFeedback(null);
    try {
      await fn();
      if (reload && order) await sync(order.workOrderId);
      setFeedback({ kind: 'success', message: success });
    } catch (error) { setFeedback({ kind: 'error', message: error instanceof Error ? error.message : 'Operation failed' }); }
    finally { setLoading(false); }
  }
  function lookup(e: FormEvent) {
    e.preventDefault();
    if (!uuidPattern.test(lookupId.trim())) { setFeedback({kind:'error',message:'Enter a valid work order UUID.'}); return; }
    setLoading(true); setFeedback(null);
    void sync(lookupId.trim()).catch(e => { setOrder(null); setFeedback({kind:'error',message:String(e)}); }).finally(() => setLoading(false));
  }
  function findByRequest(e: FormEvent) {
    e.preventDefault();
    if (!uuidPattern.test(requestId.trim())) { setFeedback({kind:'error',message:'Enter a valid service request UUID.'}); return; }
    void perform(async () => {
      const results = await api<WorkOrder[]>(`/work-orders?requestId=${encodeURIComponent(requestId.trim())}`);
      setOrders(results);
      if (results.length) await sync(results[0].workOrderId);
      else setOrder(null);
    }, 'Service request work orders loaded.', false);
  }
  function create() {
    if (!uuidPattern.test(requestId.trim())) { setFeedback({kind:'error',message:'Enter a valid service request UUID.'}); return; }
    void perform(async () => {
      const created = await api<WorkOrder>('/work-orders', json({requestId: requestId.trim()}));
      await sync(created.workOrderId);
      setOrders([created]);
    }, 'Work order created.', false);
  }
  const closed = !order || ['COMPLETED', 'CANCELLED'].includes(order.status);
  const nextOptions = order ? (transitions[order.status] ?? []) : [];
  return <div className="wo-workspace">
    <section className="panel wo-intro">
      <div className="panel-heading"><span className="panel-icon">▧</span><div><h3>Work order workspace</h3><p>Service details, technician notes, charges, and status history — live from PostgreSQL</p></div></div>
      <div className="wo-lookup-grid">
        <form className="form-stack" onSubmit={lookup}><label>Find a work order by UUID<input value={lookupId} onChange={e=>setLookupId(e.target.value)} placeholder="Work order UUID" required /></label><button type="submit" className="primary-btn" disabled={loading}>Open work order →</button></form>
        <div className="wo-divider" />
        <div><form className="form-stack" onSubmit={findByRequest}><label>Service request UUID<input value={requestId} onChange={e=>setRequestId(e.target.value)} placeholder="Service request UUID" required /></label><button type="submit" className="secondary-btn wo-action" disabled={loading}>Find work order</button></form><button type="button" className="wo-link-button" disabled={loading || !uuidPattern.test(requestId.trim())} onClick={create}>+ Open new work order for this request</button></div>
      </div>
      {orders.length > 0 && <div className="wo-matches">{orders.map(item=><button type="button" key={item.workOrderId} onClick={()=>void perform(()=>sync(item.workOrderId),'Work order loaded.',false)}>#{item.workOrderNumber} · {item.status}</button>)}</div>}
      {feedback && <div role="status" className={`wo-feedback ${feedback.kind}`}>{feedback.message}</div>}
      {sectionError && <div role="alert" className="wo-feedback error">{sectionError}</div>}
    </section>
    {order ? <>
      <section className="panel wo-summary"><div className="wo-summary-main"><div><span className="wo-kicker">SERVICE JOB #{order.workOrderNumber}</span><h2>Work Order <span className={`wo-status ${order.status.toLowerCase()}`}>{order.status.replaceAll('_',' ')}</span></h2><p>Request {order.requestId}</p></div><button className="secondary-btn wo-action" disabled={loading} onClick={()=>void perform(()=>sync(order.workOrderId),'Work order refreshed.',false)}>↻ Refresh</button></div><div className="wo-facts"><div><span>Opened</span><strong>{displayDate(order.openedAt)}</strong></div><div><span>Started</span><strong>{displayDate(order.startedAt)}</strong></div><div><span>Completed</span><strong>{displayDate(order.completedAt)}</strong></div><div><span>Updated</span><strong>{displayDate(order.updatedAt)}</strong></div></div></section>
      <div className="wo-columns">
        <section className="panel"><div className="panel-heading"><span className="panel-icon">✎</span><div><h3>Diagnosis & resolution</h3><p>Save findings against this work order</p></div></div><form className="form-stack" onSubmit={e=>{e.preventDefault(); void perform(()=>api(`/work-orders/${order.workOrderId}/details`,{method:'PATCH',body:JSON.stringify({diagnosis,resolutionNotes:resolution})}),'Service details saved.');}}><label>Diagnosis<textarea rows={4} value={diagnosis} onChange={e=>setDiagnosis(e.target.value)} /></label><label>Resolution notes<textarea rows={4} value={resolution} onChange={e=>setResolution(e.target.value)} /></label><button className="primary-btn" disabled={loading||closed}>Save details</button>{closed && <small className="wo-hint">Closed work orders are read-only for details and charges.</small>}</form></section>
        <section className="panel"><div className="panel-heading"><span className="panel-icon">↗</span><div><h3>Job status</h3><p>Only valid transitions are selectable</p></div></div><form className="form-stack" onSubmit={e=>{e.preventDefault();if(nextStatus==='COMPLETED'||nextStatus==='CANCELLED'){if(!window.confirm(`Change this work order to ${nextStatus}?`))return;}void perform(()=>api(`/work-orders/${order.workOrderId}/status`,{method:'PATCH',body:JSON.stringify({status:nextStatus,changedBy:actor,reason})}),`Work order moved to ${nextStatus}.`);setReason('');}}><label>Staff member (development actor)<input required value={actor} onChange={e=>setActor(e.target.value)}/></label><label>Change status<select required value={nextStatus} disabled={!nextOptions.length} onChange={e=>setNextStatus(e.target.value)}><option value="">Choose next status</option>{nextOptions.map(s=><option key={s} value={s}>{s.replaceAll('_',' ')}</option>)}</select></label><label>Reason<input value={reason} onChange={e=>setReason(e.target.value)} placeholder="Optional explanation" /></label><button className="primary-btn" disabled={loading||!nextStatus||!actor.trim()}>Update status</button>{!nextOptions.length && <small className="wo-hint">No further transitions are permitted.</small>}</form></section>
        <section className="panel"><div className="panel-heading"><span className="panel-icon">☷</span><div><h3>Technician notes</h3><p>{notes.length} saved notes</p></div></div><form className="form-stack" onSubmit={e=>{e.preventDefault();void perform(()=>api(`/work-orders/${order.workOrderId}/notes`,json({text:noteText,createdBy:actor})),'Note added.');setNoteText('');}}><label>New note<textarea required rows={3} value={noteText} onChange={e=>setNoteText(e.target.value)} placeholder="Inspection findings, updates, customer communication…" /></label><button className="primary-btn" disabled={loading||!noteText.trim()||!actor.trim()}>Add note</button></form><div className="wo-records">{notes.length?notes.map(n=><div className="wo-record" key={n.note_id}><p>{n.note_text}</p><small>{n.created_by} · {displayDate(n.created_at)}</small></div>):<p className="wo-muted">No notes recorded yet.</p>}</div></section>
        <section className="panel"><div className="panel-heading"><span className="panel-icon">₱</span><div><h3>Labor, parts & fees</h3><p>{lines.length} charge lines</p></div></div><form className="form-stack" onSubmit={e=>{e.preventDefault();void perform(()=>api(`/work-orders/${order.workOrderId}/lines`,json({type:lineType,description,quantity:Number(quantity),unitPrice:Number(unitPrice),currencyCode:currency,notes:lineNotes})),'Charge added.');setDescription('');setLineNotes('');}}><div className="form-row"><label>Type<select value={lineType} onChange={e=>setLineType(e.target.value)}><option value="LABOR">Labor</option><option value="PART">Part</option><option value="FEE">Fee</option></select></label><label>Currency<input value={currency} maxLength={3} onChange={e=>setCurrency(e.target.value.toUpperCase())} required /></label></div><label>Description<input value={description} onChange={e=>setDescription(e.target.value)} required /></label><div className="form-row"><label>Quantity<input type="number" min="0.001" step="0.001" value={quantity} onChange={e=>setQuantity(e.target.value)} required /></label><label>Unit price<input type="number" min="0" step="0.01" value={unitPrice} onChange={e=>setUnitPrice(e.target.value)} required /></label></div><label>Additional notes<input value={lineNotes} onChange={e=>setLineNotes(e.target.value)}/></label><button className="primary-btn" disabled={loading||closed||!description.trim()}>Add charge</button></form><div className="wo-records">{lines.length?lines.map(l=><div className="wo-record" key={l.line_id}><div className="wo-record-row"><strong>{l.line_type} · {l.description}</strong><strong>{l.currency_code} {Number(l.line_total).toFixed(2)}</strong></div><small>{l.quantity} × {Number(l.unit_price).toFixed(2)} · {displayDate(l.created_at)}</small></div>):<p className="wo-muted">No charges recorded.</p>}</div>{[...new Set(lines.map(l=>l.currency_code))].map(code=><div className="wo-total" key={code}>Total · {code}<strong>{lines.filter(l=>l.currency_code===code).reduce((sum,l)=>sum+Number(l.line_total),0).toFixed(2)}</strong></div>)}</section>
      </div>
      <section className="panel"><div className="panel-heading"><span className="panel-icon">◷</span><div><h3>Status history</h3><p>{history.length} events returned by the backend</p></div></div><div className="wo-timeline">{history.length?history.map(h=><div className="wo-event" key={h.status_history_id}><span className="wo-event-dot"/><div><strong>{h.previous_status ?? 'Created'} → {h.new_status}</strong><p>{h.change_reason ?? 'No reason provided'}</p><small>{h.changed_by} · {displayDate(h.changed_at)}</small></div></div>):<p className="wo-muted">No history returned. If you expected OPEN and IN_PROGRESS, check your backend and database history.</p>}</div></section>
    </>:<section className="panel wo-empty"><div>▧</div><h3>Open a work order</h3><p>Enter its UUID, or find one by service request ID. All changes are written through your existing Spring Boot APIs.</p></section>}
  </div>;
}
