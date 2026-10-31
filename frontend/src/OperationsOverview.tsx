import { useCallback, useEffect, useState } from 'react';
import './OperationsOverview.css';

type Appointment = {
  appointmentId: string;
  requestId: string;
  startsAt: string;
  endsAt: string;
  status: string;
};

type Summary = {
  totalCustomers: number;
  openRequests: number;
  activeAppointments: number;
  activeTechnicians: number;
  inProgressWorkOrders: number;
  completedWorkOrders: number;
  upcomingAppointments: Appointment[];
  generatedAt: string;
};

const dateTime = (value: string) => new Date(value).toLocaleString('en-PH', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Manila',
});

export function OperationsOverview() {
  const [data, setData] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError('');
    try {
      const response = await fetch('/api/v1/operations/summary', { signal });
      if (!response.ok) throw new Error(`Analytics request failed (HTTP ${response.status})`);
      const result = await response.json() as Summary;
      if (!signal?.aborted) setData(result);
    } catch (err) {
      if (!signal?.aborted) setError(err instanceof Error ? err.message : 'Unable to load analytics');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  const cards = data ? [
    { label: 'Customers', value: data.totalCustomers, desc: 'Registered customer records' },
    { label: 'Open requests', value: data.openRequests, desc: 'Not resolved or cancelled' },
    { label: 'Active appointments', value: data.activeAppointments, desc: 'Not ended; active statuses' },
    { label: 'Active technicians', value: data.activeTechnicians, desc: 'Enabled technician profiles' },
    { label: 'Open work orders', value: data.inProgressWorkOrders, desc: 'OPEN / IN_PROGRESS / ON_HOLD' },
    { label: 'Completed work orders', value: data.completedWorkOrders, desc: 'Completed service jobs' },
  ] : [];

  return <section className="operations-summary" aria-label="Live operations analytics">
    <div className="operations-summary-heading">
      <div><h3>Live operations</h3><p>Actual counts from PostgreSQL — no demo figures</p></div>
      <button type="button" className="operations-refresh" disabled={loading} onClick={() => void refresh()}>
        {loading ? 'Loading…' : '↻ Refresh'}
      </button>
    </div>
    {error && <div className="operations-error" role="alert">{error}. Check that Spring Boot is running and the new endpoint is available.</div>}
    {loading && !data && <p className="operations-muted" role="status">Loading live metrics…</p>}
    {data && <>
      <div className="operations-kpis">{cards.map(card => <article key={card.label} className="operations-kpi">
        <span>{card.label}</span><strong>{card.value.toLocaleString()}</strong><small>{card.desc}</small>
      </article>)}</div>
      <div className="operations-upcoming">
        <div className="operations-summary-heading"><div><h3>Upcoming appointments</h3><p>Next six active visits, ordered by start time</p></div></div>
        {data.upcomingAppointments.length === 0 ? <p className="operations-muted">No upcoming appointments yet.</p> :
          <div className="operations-rows">{data.upcomingAppointments.map(item => <div className="operations-appointment" key={item.appointmentId}>
            <div><strong>{dateTime(item.startsAt)}</strong><small>Until {dateTime(item.endsAt)}</small></div>
            <div><span className="operations-status">{item.status.replaceAll('_', ' ')}</span><small>Request {item.requestId.slice(0, 8)}…</small></div>
          </div>)}</div>}
      </div>
      <p className="operations-updated">Last retrieved: {dateTime(data.generatedAt)} · Times shown in Asia/Manila</p>
    </>}
  </section>;
}
