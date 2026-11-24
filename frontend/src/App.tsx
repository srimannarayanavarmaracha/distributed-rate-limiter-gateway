import { useEffect, useState } from 'react';

type AlgorithmType = 'TOKEN_BUCKET' | 'SLIDING_WINDOW';

interface RateLimitRule {
  id: number;
  name: string;
  pathPattern: string;
  httpMethod: string;
  capacity: number;
  refillTokensPerSecond: number;
  windowSeconds: number;
  algorithm: AlgorithmType;
  enabled: boolean;
}

interface ProbeResult {
  index: number;
  status: number;
  limit: string | null;
  remaining: string | null;
  backend: string | null;
  algorithm: string | null;
  retryAfter: string | null;
  latencyMs: number;
}

const API_BASE = import.meta.env.VITE_API_BASE || '';

export default function App() {
  const [rules, setRules] = useState<RateLimitRule[]>([]);
  const [clientId, setClientId] = useState('client-alpha');
  const [burstSize, setBurstSize] = useState(8);
  const [results, setResults] = useState<ProbeResult[]>([]);
  const [summary, setSummary] = useState({ totalEvaluations: 0, allowed: 0, blocked: 0, redisEnabled: false });

  const [name, setName] = useState('custom-api-limit');
  const [capacity, setCapacity] = useState(5);
  const [refill, setRefill] = useState(1);
  const [algorithm, setAlgorithm] = useState<AlgorithmType>('TOKEN_BUCKET');

  async function refresh() {
    const [rulesRes, sumRes] = await Promise.all([
      fetch(`${API_BASE}/api/v1/rules`),
      fetch(`${API_BASE}/api/v1/metrics/summary`)
    ]);
    if (rulesRes.ok) setRules(await rulesRes.json());
    if (sumRes.ok) setSummary(await sumRes.json());
  }

  useEffect(() => {
    refresh().catch(() => undefined);
  }, []);

  async function fireBurst() {
    const out: ProbeResult[] = [];
    for (let i = 1; i <= burstSize; i++) {
      const start = performance.now();
      const res = await fetch(`${API_BASE}/api/v1/demo/resource`, {
        headers: { 'X-Client-Id': clientId }
      });
      const latencyMs = Math.round(performance.now() - start);
      out.push({
        index: i,
        status: res.status,
        limit: res.headers.get('X-RateLimit-Limit'),
        remaining: res.headers.get('X-RateLimit-Remaining'),
        backend: res.headers.get('X-RateLimit-Backend'),
        algorithm: res.headers.get('X-RateLimit-Algorithm'),
        retryAfter: res.headers.get('Retry-After'),
        latencyMs
      });
    }
    setResults(out);
    await refresh();
  }

  async function createRule(e: React.FormEvent) {
    e.preventDefault();
    const res = await fetch(`${API_BASE}/api/v1/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        pathPattern: '/api/v1/demo/**',
        httpMethod: 'GET',
        capacity,
        refillTokensPerSecond: refill,
        windowSeconds: 10,
        algorithm,
        enabled: true
      })
    });
    if (res.ok) {
      setName(`rule-${Date.now().toString().slice(-4)}`);
      await refresh();
    }
  }

  async function toggleRule(id: number) {
    await fetch(`${API_BASE}/api/v1/rules/${id}/toggle`, { method: 'PATCH' });
    await refresh();
  }

  return (
    <div className="container">
      <div className="header">
        <div>
          <h1 style={{ margin: 0 }}>Distributed Rate Limiter Gateway</h1>
          <p style={{ margin: '4px 0 0', color: '#475569' }}>
            Redis Lua Token Bucket &amp; Sliding Window with automatic in-memory failover
          </p>
        </div>
        <div style={{ display: 'flex', gap: 12, fontSize: 13 }}>
          <div className="card" style={{ padding: '10px 14px' }}>
            Evaluations: <strong>{summary.totalEvaluations}</strong>
          </div>
          <div className="card" style={{ padding: '10px 14px' }}>
            Allowed: <strong>{summary.allowed}</strong>
          </div>
          <div className="card" style={{ padding: '10px 14px' }}>
            Throttled (429): <strong>{summary.blocked}</strong>
          </div>
        </div>
      </div>

      <div className="grid">
        <div className="card">
          <h2 style={{ marginTop: 0 }}>Traffic Burst Simulator</h2>
          <label>Client Key (X-Client-Id)</label>
          <input className="input" value={clientId} onChange={(e) => setClientId(e.target.value)} />
          <label>Burst Requests</label>
          <input
            className="input"
            type="number"
            min={1}
            max={50}
            value={burstSize}
            onChange={(e) => setBurstSize(Number(e.target.value))}
          />
          <button className="btn" onClick={fireBurst}>Send Burst to /api/v1/demo/resource</button>

          {results.length > 0 && (
            <table style={{ marginTop: 16 }}>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Status</th>
                  <th>Remaining</th>
                  <th>Backend</th>
                  <th>Latency</th>
                </tr>
              </thead>
              <tbody>
                {results.map((r) => (
                  <tr key={r.index}>
                    <td>{r.index}</td>
                    <td>
                      <span className={r.status === 200 ? 'badge-ok' : 'badge-block'}>
                        {r.status}
                      </span>
                    </td>
                    <td>{r.remaining ?? '-'} / {r.limit ?? '-'}</td>
                    <td>{r.backend ?? '-'}</td>
                    <td>{r.latencyMs} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2 style={{ marginTop: 0 }}>Dynamic Rate Limit Rules</h2>
          <form onSubmit={createRule} style={{ marginBottom: 16 }}>
            <label>Rule Name</label>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
            <label>Algorithm</label>
            <select
              className="input"
              value={algorithm}
              onChange={(e) => setAlgorithm(e.target.value as AlgorithmType)}
            >
              <option value="TOKEN_BUCKET">TOKEN_BUCKET</option>
              <option value="SLIDING_WINDOW">SLIDING_WINDOW</option>
            </select>
            <div style={{ display: 'flex', gap: 10 }}>
              <div style={{ flex: 1 }}>
                <label>Capacity</label>
                <input
                  className="input"
                  type="number"
                  min={1}
                  value={capacity}
                  onChange={(e) => setCapacity(Number(e.target.value))}
                />
              </div>
              <div style={{ flex: 1 }}>
                <label>Refill / sec</label>
                <input
                  className="input"
                  type="number"
                  step="0.5"
                  min={0.5}
                  value={refill}
                  onChange={(e) => setRefill(Number(e.target.value))}
                />
              </div>
            </div>
            <button className="btn" type="submit">Add Rule</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>Rule</th>
                <th>Algorithm</th>
                <th>Cap</th>
                <th>State</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td>{rule.name}</td>
                  <td>{rule.algorithm}</td>
                  <td>{rule.capacity}</td>
                  <td>
                    <button className="btn btn-secondary" onClick={() => toggleRule(rule.id)}>
                      {rule.enabled ? 'Enabled' : 'Disabled'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
