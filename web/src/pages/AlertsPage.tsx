import { apiGet } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { asString, type Overview } from '../types';

export function AlertsPage() {
  const overview = useAsync(() => apiGet<Overview>('/ops/overview'), []);

  if (overview.loading) {
    return <p className="muted">Loading alerts…</p>;
  }
  if (overview.error || !overview.data) {
    return <p className="error">Failed to load alerts: {overview.error}</p>;
  }

  const alerts = overview.data.alerts ?? [];
  if (alerts.length === 0) {
    return <p className="muted">No open alerts.</p>;
  }

  return (
    <ul className="alerts">
      {alerts.map((alert, index) => (
        <li key={asString(alert.alertId ?? alert.id, `alert-${index}`)}>
          <strong>{asString(alert.severity, 'info')}</strong> · {asString(alert.message ?? alert.type, 'alert')} ·{' '}
          {asString(alert.shipmentId, '-')}
        </li>
      ))}
    </ul>
  );
}
