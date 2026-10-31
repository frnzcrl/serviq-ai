import { useState, type FormEvent } from 'react';
import './ServiceAdvisorWorkspace.css';

type Suggestion = {
  category: string; priority: string; issueSummary: string; issueDescription: string;
  followUpQuestions: string[]; signals: string[]; needsReview: boolean; engine: string;
};

export function ServiceAdvisorWorkspace() {
  const [description, setDescription] = useState('');
  const [draft, setDraft] = useState<Suggestion | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function analyze(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(''); setDraft(null);
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
          onChange={e => setDraft({ ...draft, category: e.target.value })} />
        <label htmlFor="advisor-priority">Priority</label>
        <select id="advisor-priority" value={draft.priority}
          onChange={e => setDraft({ ...draft, priority: e.target.value })}>
          {['NORMAL', 'HIGH', 'URGENT'].map(v => <option key={v} value={v}>{v}</option>)}
        </select>
        <h3>Follow-up questions</h3><ul>{draft.followUpQuestions.map(q => <li key={q}>{q}</li>)}</ul>
        {draft.signals.length > 0 && <><h3>Why these suggestions?</h3><ul>{draft.signals.map(s => <li key={s}>{s}</li>)}</ul></>}
        {draft.priority === 'URGENT' && <p className="advisor-warning">Potential safety concern: advise the customer to avoid unsafe equipment and contact appropriate emergency or qualified services when necessary.</p>}
        <p className="advisor-disclaimer">{draft.engine === 'OLLAMA_LOCAL' ? 'Generated with local Ollama. Review all model suggestions before creating a request. No records were changed.' : 'Rules-based fallback (Ollama is off, unavailable, or returned invalid output). Review before creating a request. No records were changed.'}</p>
        <button type="button" className="advisor-copy" onClick={() => void navigator.clipboard.writeText(
          `Summary: ${draft.issueSummary}\nDescription: ${draft.issueDescription}\nCategory: ${draft.category}\nPriority: ${draft.priority}`
        )}>Copy reviewed draft</button>
      </>}
    </section>
  </div>;
}
