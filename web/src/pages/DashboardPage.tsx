import { apiGet } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { asString, type Entitlement, type Overview } from '../types';

function flag(value: boolean | undefined): string {
  return value === false ? 'degraded' : 'ok';
}

export function DashboardPage() {
  const overview = useAsync(() => apiGet<Overview>('/ops/overview'), []);
  const entitlement = useAsync(() => apiGet<Entitlement>('/ops/entitlement'), []);

  if (overview.loading) {
    return <p className="muted">Loading overview…</p>;
  }
  if (overview.error || !overview.data) {
    return <p className="error">Failed to load overview: {overview.error}</p>;
  }

  const data = overview.data;
  const shipments = data.shipments?.length ?? 0;
  const alerts = data.alerts?.length ?? 0;
  const edition = entitlement.data;

  return (
    <section className="dashboard">
      <div className="cards">
        <article className="card">
          <span className="card-label">Active shipments</span>
          <span className="card-value">{shipments}</span>
        </article>
        <article className="card">
          <span className="card-label">Open alerts</span>
          <span className="card-value">{alerts}</span>
        </article>
        <article className="card">
          <span className="card-label">Upstream sources</span>
          <span className="card-value small">
            read-model: {flag(data.sourcesAvailable_readModel)} · tower: {flag(data.sourcesAvailable_tower)} · mdm:{' '}
            {flag(data.sourcesAvailable_mdm)}
          </span>
        </article>
        <article className="card">
          <span className="card-label">Edition</span>
          <span className="card-value small">
            {edition ? `${asString(edition.edition, 'unknown')} · ${asString(edition.mode, '')}` : 'unavailable'}
          </span>
        </article>
      </div>
      <p className="muted">
        Snapshot generated at{' '}
        {data.generatedAt ? new Date(data.generatedAt).toLocaleString() : 'unknown'}.
      </p>
    </section>
  );
}
