import { Link } from 'react-router-dom';
import { apiGet } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { asString, type Overview } from '../types';

export function ShipmentsPage() {
  const overview = useAsync(() => apiGet<Overview>('/ops/overview'), []);

  if (overview.loading) {
    return <p className="muted">Loading shipments…</p>;
  }
  if (overview.error || !overview.data) {
    return <p className="error">Failed to load shipments: {overview.error}</p>;
  }

  const shipments = overview.data.shipments ?? [];
  if (shipments.length === 0) {
    return <p className="muted">No shipments.</p>;
  }

  return (
    <table className="grid">
      <thead>
        <tr>
          <th>Shipment</th>
          <th>Status</th>
          <th>Tenant</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {shipments.map((shipment, index) => {
          const id = asString(shipment.shipmentId ?? shipment.id, `row-${index}`);
          return (
            <tr key={id}>
              <td>{id}</td>
              <td>{asString(shipment.status, '-')}</td>
              <td>{asString(shipment.tenantId, '-')}</td>
              <td>
                <Link to={`/shipments/${encodeURIComponent(id)}`}>Open</Link>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
