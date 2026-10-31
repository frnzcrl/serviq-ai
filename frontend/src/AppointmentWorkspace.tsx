import { useCallback, useEffect, useState, type FormEvent } from 'react';

const API = '/api/v1';

type Slot = { technicianId: string; technicianName?: string; startsAt: string; endsAt: string };
type ServiceRequest = { requestId: string; issueSummary: string; requestStatus: string; assetId?: string | null };
type Page<T> = { items: T[]; totalPages: number };
type Appointment = {
  appointmentId: string; requestId: string; technicianId: string;
  startsAt: string; endsAt: string; appointmentStatus: string;
};

type Props = {
  customerId: string;
  date: string;
  category: string;
  slots: Slot[];
  onAvailabilityRefresh: () => Promise<void>;
};

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...(options?.body ? { 'Content-Type': 'application/json' } : {}), ...options?.headers },
  });
  const text = await response.text();
  let result: unknown;
  try { result = text ? JSON.parse(text) : null; } catch { result = text; }
  if (!response.ok) {
    const info = result && typeof result === 'object' ? result as Record<string, unknown> : {};
    throw new Error(`HTTP ${response.status}: ${String(info.detail ?? info.message ?? info.error ?? text ?? 'Request failed')}`);
  }
  return result as T;
}

function readable(value: string): string {
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('en-PH', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Manila' });
}

const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

export function AppointmentWorkspace({ customerId, date, category, slots, onAvailabilityRefresh }: Props) {
  const [lookupId, setLookupId] = useState(customerId);
  const [requestId, setRequestId] = useState('');
  const [requests, setRequests] = useState<ServiceRequest[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loadingRequests, setLoadingRequests] = useState(false);
  const [loadingAppointments, setLoadingAppointments] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [requestNotice, setRequestNotice] = useState('');

  useEffect(() => { setLookupId(customerId); setRequests([]); setRequestId(''); }, [customerId]);
  useEffect(() => { setSelectedSlot(null); }, [date, category, slots]);

  const refreshSchedule = useCallback(async () => {
    if (!date) return;
    setLoadingAppointments(true);
    try {
      // Backend date parameter is UTC-calendar based; it is not a Philippine-local calendar query.
      const records = await api<Appointment[]>(`/appointments?date=${encodeURIComponent(date)}`);
      setAppointments(records);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to load appointments');
    } finally { setLoadingAppointments(false); }
  }, [date]);
  useEffect(() => { void refreshSchedule(); }, [refreshSchedule]);

  async function loadRequests(event?: FormEvent) {
    event?.preventDefault(); setError(''); setRequestNotice(''); setRequests([]); setRequestId('');
    const id = lookupId.trim();
    if (!isUuid(id)) { setError('Enter a valid customer UUID. You can select a customer in the Customers workspace first.'); return; }
    setLoadingRequests(true);
    try {
      // Fetch bounded pages, never assume all service requests fit in the first page.
      const all: ServiceRequest[] = [];
      for (let page = 0; page < 10; page++) {
        const data = await api<Page<ServiceRequest>>(`/service-requests/customer/${encodeURIComponent(id)}?page=${page}&size=50`);
        all.push(...(data.items ?? []));
        if (page + 1 >= (data.totalPages ?? 1)) break;
      }
      const eligible = all.filter(item => ['NEW', 'TRIAGED', 'SCHEDULED'].includes(item.requestStatus));
      setRequests(eligible);
      setRequestNotice(eligible.length ? `${eligible.length} eligible requests loaded.` : 'No eligible requests for this customer. Create a service request first.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not load service requests'); }
    finally { setLoadingRequests(false); }
  }

  async function book() {
    if (!selectedSlot || !requestId) return;
    if (!window.confirm(`Book this technician for ${readable(selectedSlot.startsAt)}?`)) return;
    setError(''); setSuccess(''); setBusy(true);
    try {
      const booking = await api<Appointment>('/appointments', {
        method: 'POST',
        body: JSON.stringify({ requestId, technicianId: selectedSlot.technicianId, startsAt: selectedSlot.startsAt, endsAt: selectedSlot.endsAt }),
      });
      setSuccess(`Appointment booked: ${booking.appointmentId}`);
      setSelectedSlot(null);
      await Promise.allSettled([refreshSchedule(), onAvailabilityRefresh()]);
    } catch (e) { setError(e instanceof Error ? e.message : 'Booking failed'); }
    finally { setBusy(false); }
  }

  async function cancel(appointment: Appointment) {
    if (!window.confirm(`Cancel appointment ${appointment.appointmentId}? This frees the technician assignment.`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await api<Appointment>(`/appointments/${encodeURIComponent(appointment.appointmentId)}/cancel`, { method: 'PATCH' });
      setSuccess(`Appointment ${appointment.appointmentId} cancelled.`);
      await Promise.allSettled([refreshSchedule(), onAvailabilityRefresh()]);
    } catch (e) { setError(e instanceof Error ? e.message : 'Cancellation failed'); }
    finally { setBusy(false); }
  }

  return <section className="panel booking-workspace">
    <div className="panel-heading"><span className="panel-icon">◷</span><div><h3>Book a service appointment</h3><p>Select a request, choose a real available slot, and confirm with the booking API.</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {success && <div className="notice success" role="status">{success}</div>}
    <div className="booking-grid">
      <div className="booking-step">
        <div className="booking-step-title"><span>01</span><strong>Select a service request</strong></div>
        <form className="form-stack" onSubmit={e => void loadRequests(e)}>
          <label>Customer UUID<input required value={lookupId} onChange={e => setLookupId(e.target.value)} placeholder="Select a customer in the Customers workspace" /></label>
          <button className="secondary-btn booking-action" disabled={loadingRequests || busy}> {loadingRequests ? 'Loading…' : 'Load requests'} </button>
        </form>
        {requestNotice && <p className="booking-hint">{requestNotice}</p>}
        {requests.length > 0 && <label className="booking-select-label">Service request
          <select value={requestId} onChange={e => { setRequestId(e.target.value); setSelectedSlot(null); }}>
            <option value="">Choose a request</option>
            {requests.map(r => <option key={r.requestId} value={r.requestId}>{r.issueSummary} · {r.requestStatus} · {r.requestId.slice(0, 8)}</option>)}
          </select>
        </label>}
      </div>
      <div className="booking-step">
        <div className="booking-step-title"><span>02</span><strong>Choose a technician slot</strong></div>
        <p className="booking-hint">Availability above is filtered by <strong>{category}</strong> on <strong>{date}</strong>. Match this category to the equipment in the selected request; the backend rechecks the skill and schedule.</p>
        {slots.length === 0 ? <div className="ws-empty">Search availability above to display bookable slots.</div> :
          <div className="booking-slot-list" role="group" aria-label="Available technician time slots">{slots.map((slot, i) => {
            const active = selectedSlot?.technicianId === slot.technicianId && selectedSlot?.startsAt === slot.startsAt;
            return <button type="button" disabled={!requestId || busy} aria-pressed={active} className={`booking-slot ${active ? 'active' : ''}`} key={`${slot.technicianId}-${slot.startsAt}-${i}`} onClick={() => setSelectedSlot(slot)}>
              <strong>{slot.technicianName || 'Technician'}</strong><span>{readable(slot.startsAt)} – {new Date(slot.endsAt).toLocaleTimeString('en-PH', { timeStyle: 'short', timeZone: 'Asia/Manila' })}</span>
            </button>;
          })}</div>}
      </div>
    </div>
    <div className="booking-confirm"><span>{selectedSlot ? `Selected: ${selectedSlot.technicianName || selectedSlot.technicianId} · ${readable(selectedSlot.startsAt)}` : 'Choose a service request and one available slot to continue.'}</span><button className="primary-btn" disabled={!selectedSlot || !requestId || busy} onClick={() => void book()}>{busy ? 'Processing…' : 'Confirm booking →'}</button></div>
    <div className="booking-schedule-head"><div><h3>Appointments for {date}</h3><p>Uses the backend’s UTC date filter; times below are displayed in Manila time.</p></div><button className="secondary-btn booking-action" disabled={loadingAppointments || busy} onClick={() => void refreshSchedule()}>{loadingAppointments ? 'Refreshing…' : 'Refresh schedule'}</button></div>
    {appointments.length === 0 ? <div className="ws-empty">No appointments returned for this UTC date.</div> : <div className="booking-appointments">{appointments.map(a => <div className="booking-appointment" key={a.appointmentId}>
      <div><strong>{readable(a.startsAt)}</strong><span>Request {a.requestId.slice(0, 8)} · Technician {a.technicianId.slice(0, 8)}</span><code>{a.appointmentId}</code></div>
      <div className="booking-appointment-actions"><span className="ws-status">{a.appointmentStatus}</span>{!['CANCELLED', 'COMPLETED'].includes(a.appointmentStatus) && <button className="secondary-btn" disabled={busy} onClick={() => void cancel(a)}>Cancel</button>}</div>
    </div>)}</div>}
  </section>;
}
