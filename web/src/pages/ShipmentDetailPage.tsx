import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { ApiError, apiGet, apiPost } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { asString } from '../types';

interface ActionAck {
  commandId?: string;
  topic?: string;
}

export function ShipmentDetailPage() {
  const { shipmentId = '' } = useParams();
  const detail = useAsync(() => apiGet<Record<string, unknown>>(`/ops/shipments/${encodeURIComponent(shipmentId)}`), [shipmentId]);
  const [ack, setAck] = useState<ActionAck | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function notifyCustomer() {
    setSubmitting(true);
    setActionError(null);
    setAck(null);
    try {
      const response = await apiPost<ActionAck>('/ops/actions/notify-customer', { shipmentId });
      setAck(response);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 403) {
        setActionError('Your role cannot perform this action.');
      } else {
        setActionError(cause instanceof Error ? cause.message : 'action failed');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (detail.loading) {
    return <p className="muted">Loading shipment…</p>;
  }
  if (detail.error || !detail.data) {
    return <p className="error">Failed to load shipment: {detail.error}</p>;
  }

  const data = detail.data;
  const pickup = (data.pickup as Record<string, unknown> | undefined)?.name;
  const delivery = (data.delivery as Record<string, unknown> | undefined)?.name;

  return (
    <section className="detail">
      <h2>{asString(data.shipmentId, shipmentId)}</h2>
      <dl>
        <dt>Status</dt>
        <dd>{asString(data.status, '-')}</dd>
        <dt>Tenant</dt>
        <dd>{asString(data.tenantId, '-')}</dd>
        <dt>Origin</dt>
        <dd>{asString(pickup, '-')}</dd>
        <dt>Destination</dt>
        <dd>{asString(delivery, '-')}</dd>
      </dl>
      <button type="button" disabled={submitting} onClick={() => void notifyCustomer()}>
        {submitting ? 'Sending…' : 'Notify customer'}
      </button>
      {ack ? <p className="ok">Command queued: {asString(ack.commandId, 'unknown')}</p> : null}
      {actionError ? <p className="error">{actionError}</p> : null}
    </section>
  );
}
