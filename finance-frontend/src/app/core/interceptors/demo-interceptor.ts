import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import {
  DEMO_BILLS, DEMO_WORKERS, DEMO_PAYMENTS, DEMO_PENDING_PAYMENTS,
  DEMO_COLLECTION_NOTES, DEMO_WORKER_COLLECTIONS,
  DEMO_OWNER_DASHBOARD, DEMO_AGING_REPORT, DEMO_COLLECTION_HEALTH,
} from '../demo/demo-data';

function isDemoUser(): boolean {
  try {
    const token = localStorage.getItem('token');
    if (!token) return false;
    const payload = JSON.parse(atob(token.split('.')[1]));
    const u: string = payload?.sub ?? '';
    return u === 'demo_admin' || u === 'demo_acc' || u === 'demo_owner';
  } catch { return false; }
}

function ok(body: unknown) {
  return of(new HttpResponse({ status: 200, body }));
}

export const demoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isDemoUser()) return next(req);

  // Block all writes silently — banner already says read-only
  if (req.method !== 'GET') return ok(null);

  const url = req.url.split('?')[0];

  // Auth — must reach real backend for login
  if (url.includes('/api/auth/')) return next(req);

  // Bills
  if (url.match(/\/api\/bills$/) || url.match(/\/api\/bills\/search$/)) return ok(DEMO_BILLS);
  if (url.match(/\/api\/bills\/overdue-count$/)) return ok({ count: 3 });
  if (url.match(/\/api\/bills\/aging-report$/)) return ok(DEMO_AGING_REPORT);
  if (url.match(/\/api\/bills\/\d+$/)) {
    const id = Number(url.split('/').pop());
    return ok(DEMO_BILLS.find(b => b.id === id) ?? null);
  }
  if (url.includes('/api/bills')) return ok([]);

  // Workers
  if (url.match(/\/api\/workers$/)) return ok(DEMO_WORKERS);
  if (url.includes('/api/workers')) return ok(DEMO_WORKERS);

  // Payments
  if (url.match(/\/api\/payments\/pending$/)) return ok(DEMO_PENDING_PAYMENTS);
  if (url.match(/\/api\/payments\/today$/)) return ok([]);
  if (url.match(/\/api\/payments\/my-entered$/)) return ok(DEMO_PENDING_PAYMENTS);
  if (url.match(/\/api\/payments\/groups$/)) return ok([]);
  if (url.match(/\/api\/payments\/bill\/\d+$/)) {
    const billId = Number(url.split('/').slice(-1)[0]);
    return ok(DEMO_PAYMENTS.filter(p => p.billId === billId));
  }
  if (url.match(/\/api\/payments$/)) return ok(DEMO_PAYMENTS);
  if (url.includes('/api/payments')) return ok([]);

  // Collection notes
  if (url.match(/\/api\/collection-notes\/pending$/)) return ok(DEMO_COLLECTION_NOTES);
  if (url.includes('/api/collection-notes')) return ok([]);

  // Worker collections
  if (url.match(/\/api\/worker-collections\/pending$/)) return ok(DEMO_WORKER_COLLECTIONS);
  if (url.match(/\/api\/worker-collections\/bills$/)) return ok(DEMO_BILLS.slice(0, 5));
  if (url.match(/\/api\/worker-collections$/)) return ok(DEMO_WORKER_COLLECTIONS);
  if (url.includes('/api/worker-collections')) return ok([]);

  // Dashboard
  if (url.match(/\/api\/dashboard\/owner$/)) return ok(DEMO_OWNER_DASHBOARD);
  if (url.match(/\/api\/dashboard\/collection-health$/)) return ok(DEMO_COLLECTION_HEALTH);
  if (url.includes('/api/dashboard')) return ok({});

  // Reminders / expenses / tasks / returns / stock / time-log
  if (url.includes('/api/reminders') || url.includes('/reminders')) return ok([]);
  if (url.includes('/api/expenses')) return ok([]);
  if (url.includes('/api/tasks')) return ok([]);
  if (url.includes('/api/returns')) return ok([]);
  if (url.includes('/api/stock')) return ok([]);
  if (url.includes('/api/admin/time-log')) return ok([]);
  if (url.includes('/api/worker-portal')) return ok({ bills: [], stats: {} });

  // Unknown — return empty array so components don't crash
  return ok([]);
};
