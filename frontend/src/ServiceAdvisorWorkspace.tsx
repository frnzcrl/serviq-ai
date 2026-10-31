import { useState, type FormEvent } from 'react';
import './ServiceAdvisorWorkspace.css';
import './AdvisorAvailability.css';

type AvailabilitySlot = { technicianId: string; technicianName?: string; startsAt: string; endsAt: string };
type Props = { onUseAvailability: (selection: { category: string; date: string; slots: AvailabilitySlot[] }) => void };

function localTomorrow(): string {
  const now = new Date();
  now.setDate(now.getDate() + 1);
  return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function displayTime(value: string): string {
  const time = new Date(value);
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('en-PH', { timeZone: 'Asia/Manila', dateStyle: 'medium', timeStyle: 'short' });
}

type Suggestion = {
  category: string; priority: string; issueSummary: string; issueDescription: string;
  followUpQuestions: string[]; signals: string[]; needsReview: boolean; engine: string;
};

export function ServiceAdvisorWorkspace({ onUseAvailability }: Props) {
  const [description, setDescription] = useState('');
  const [draft, setDraft] = useState<Suggestion | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [date, setDate] = useState(localTomorrow);
  const [duration, setDuration] = useState(60);
  const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
  const [availabilityState, setAvailabilityState] = useState<'idle' | 'loading' | 'ready'>('idle');
  const [availabilityError, setAvailabilityError] = useState('');
  async function analyze(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(''); setDraft(null); setSlots([]); setAvailabilityState('idle'); setAvailabilityError('');
    try {
      const response = await fetch('/api/v1/advisor/analyze', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description: description.trim() }),
      });
      if (!response.ok) {
        if (response.status === 401) throw new Error('Your session has expired. Please sign in again.');
        if (response.status === 403) throw new Error('Your role is not authorized to analyze service requests.');
        throw new Error(`Analysis failed (${response.status}).`);
      }
      setDraft(await response.json() as Suggestion);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Request failed'); }
    finally { setBusy(false); }
  }
  async function findAvailability() {
    if (!draft) return;
    const reviewedCategory = draft.category.trim().toUpperCase();
    if (!['REFRIGERATOR', 'AIR_CONDITIONER', 'WASHING_MACHINE', 'DISHWASHER', 'OVEN', 'ELECTRICAL'].includes(reviewedCategory)) {
      setAvailabilityError('Choose a supported equipment category before searching.'); return;
    }
    if (!date) { setAvailabilityError('Choose a service date.'); return; }
    setAvailabilityState('loading'); setAvailabilityError(''); setSlots([]);
    try {
      const params = new URLSearchParams({ category: reviewedCategory, date, durationMinutes: String(duration), stepMinutes: '30' });
      const response = await fetch(`/api/v1/availability?${params.toString()}`);
      if (!response.ok) throw new Error(`Availability search failed (HTTP ${response.status}).`);
      const data: unknown = await response.json();
      if (!Array.isArray(data)) throw new Error('Unexpected availability response.');
      const valid = data.filter((item): item is AvailabilitySlot => item !== null && typeof item === 'object' && typeof item.technicianId === 'string' && typeof item.startsAt === 'string' && typeof item.endsAt === 'string');
      setSlots(valid); setAvailabilityState('ready');
    } catch (cause) { setAvailabilityState('idle'); setAvailabilityError(cause instanceof Error ? cause.message : 'Could not retrieve availability'); }
  }
  return <div className="advisor-layout">
    <section className="advisor-card">
      <div className="advisor-kicker">INTAKE ASSISTANT · LOCAL MODEL OR RULES FALLBACK</div>
      <h2>Turn a description into a reviewable request</h2>
      <p>Paste the customer’s description. Nothing is saved or booked automatically.</p>
      <form onSubmit={analyze}>
        <label htmlFor="advisor-desc">Customer description</label>
        <textarea id="advisor-desc" minLength={12} maxLength={4000} required rows={7}
          placeholder="Example: Our refrigerator stopped cooling yesterday. There is water underneath it."
          value={description} onChange={e => setDescription(e.target.value)} />
        <div className="advisor-actions"><span>{description.length}/4000 characters</span>
          <button type="submit" disabled={busy || description.trim().length < 12}>{busy ? 'Analyzing…' : 'Suggest intake draft ↗'}</button>
        </div>
      </form>
      {error && <p className="advisor-error" role="alert">{error}</p>}
    </section>
    <section className="advisor-card" aria-live="polite">
      <div className="advisor-kicker">STAFF REVIEW REQUIRED</div>
      {!draft ? <div className="advisor-empty">Suggested category, priority and follow-up questions will appear here.</div> : <>
        <h2>Suggested intake</h2>
        <div className="advisor-badges"><span>{draft.category}</span><span>{draft.priority} priority</span><span>{draft.engine}</span></div>
        <label htmlFor="advisor-summary">Suggested summary</label>
        <textarea id="advisor-summary" rows={3} maxLength={200} value={draft.issueSummary}
          onChange={e => setDraft({ ...draft, issueSummary: e.target.value })} />
        <label htmlFor="advisor-category">Equipment category</label>
        <input id="advisor-category" value={draft.category}
          onChange={e => { setDraft({ ...draft, category: e.target.value }); setSlots([]); setAvailabilityState('idle'); }} />
        <label htmlFor="advisor-priority">Priority</label>
        <select id="advisor-priority" value={draft.priority}
          onChange={e => setDraft({ ...draft, priority: e.target.value })}>
          {['NORMAL', 'HIGH', 'URGENT'].map(v => <option key={v} value={v}>{v}</option>)}
        </select>
        <h3>Follow-up questions</h3><ul>{draft.followUpQuestions.map(q => <li key={q}>{q}</li>)}</ul>
        {draft.signals.length > 0 && <><h3>Why these suggestions?</h3><ul>{draft.signals.map(s => <li key={s}>{s}</li>)}</ul></>}
        {draft.priority === 'URGENT' && <p className="advisor-warning">Potential safety concern: advise the customer to avoid unsafe equipment and contact appropriate emergency or qualified services when necessary.</p>}
        <p className="advisor-disclaimer">{draft.engine === 'OLLAMA_LOCAL' ? 'Generated with local Ollama. Review all model suggestions before creating a request. No records were changed.' : 'Rules-based fallback (Ollama is off, unavailable, or returned invalid output). Review before creating a request. No records were changed.'}</p>
        <div className="advisor-availability">
          <h3>Find qualified technician availability</h3>
          <p>Uses the reviewed category and live scheduling rules. AI does not assign a technician or reserve a slot.</p>
          <div className="advisor-availability-fields">
            <label>Service date<input type="date" min={localTomorrow()} value={date} onChange={e => { setDate(e.target.value); setSlots([]); setAvailabilityState('idle'); }} /></label>
            <label>Duration<select value={duration} onChange={e => { setDuration(Number(e.target.value)); setSlots([]); setAvailabilityState('idle'); }}>
              {[30, 60, 90, 120].map(minutes => <option key={minutes} value={minutes}>{minutes} minutes</option>)}
            </select></label>
          </div>
          <button className="advisor-copy" type="button" disabled={availabilityState === 'loading'} onClick={() => void findAvailability()}>
            {availabilityState === 'loading' ? 'Checking schedules…' : 'Find technician slots ↗'}
          </button>
          {availabilityError && <p className="advisor-error" role="alert">{availabilityError}</p>}
          {availabilityState === 'ready' && <>
            <p role="status">{slots.length ? `${slots.length} available windows found. Showing the first 8.` : 'No available technician windows for this date and category.'}</p>
            {slots.slice(0, 8).map(slot => <div className="advisor-slot" key={`${slot.technicianId}-${slot.startsAt}`}>
              <strong>{slot.technicianName || 'Technician'}</strong><span>{displayTime(slot.startsAt)} – {displayTime(slot.endsAt)}</span>
            </div>)}
            {slots.length > 0 && <button className="advisor-copy" type="button" onClick={() => onUseAvailability({ category: draft.category.trim().toUpperCase(), date, slots })}>Continue to Scheduling ↗</button>}
          </>}
          <p className="advisor-disclaimer">Booking requires a real service request, staff confirmation, and backend revalidation. Slots may become unavailable.</p>
        </div>
        <button type="button" className="advisor-copy" onClick={() => void navigator.clipboard.writeText(
          `Summary: ${draft.issueSummary}\nDescription: ${draft.issueDescription}\nCategory: ${draft.category}\nPriority: ${draft.priority}`
        )}>Copy reviewed draft</button>
      </>}
    </section>
  </div>;
}
