import { TestBed } from '@angular/core/testing';
import { DatabaseService } from './database.service';

describe('DatabaseService', () => {
  let service: DatabaseService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DatabaseService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should save and retrieve local orders', async () => {
    const mockOrder = {
      id: 99,
      code: 'WO-2026-0000099',
      title: 'Mantenimiento Preventivo',
      status: 'ASSIGNED' as const,
      priority: 'HIGH' as const,
      clientId: 1,
      clientName: 'Cliente Alpha',
      createdAt: '2026-03-01T10:00:00Z',
      version: 1,
    };

    await service.saveLocalOrders([mockOrder]);
    const orders = await service.getLocalOrders();
    expect(orders.length).toBeGreaterThan(0);
    const found = orders.find((o) => o.id === 99);
    expect(found).toBeDefined();
    expect(found?.code).toBe('WO-2026-0000099');
  });

  it('should queue pending operations and update status', async () => {
    const op = await service.addPendingOperation('STATUS_CHANGE', 99, { newStatus: 'IN_PROGRESS' });
    expect(op.id).toBeDefined();
    expect(op.status).toBe('PENDING');

    const pending = await service.getPendingOperations();
    expect(pending.some((p) => p.id === op.id)).toBe(true);

    await service.updatePendingOperationStatus(op.id, 'COMPLETED');
    const remaining = await service.getPendingOperations();
    expect(remaining.some((p) => p.id === op.id)).toBe(false);
  });
});
