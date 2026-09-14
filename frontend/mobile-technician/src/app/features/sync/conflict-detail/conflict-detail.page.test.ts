import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConflictDetailPage } from './conflict-detail.page';
import { DatabaseService } from '../../../core/services/database.service';
import { WorkOrderService } from '../../../core/services/work-order.service';
import { SyncService } from '../../../core/services/sync.service';

describe('ConflictDetailPage', () => {
  let component: ConflictDetailPage;
  let fixture: ComponentFixture<ConflictDetailPage>;
  let db: DatabaseService;
  let workOrderService: WorkOrderService;
  let syncService: SyncService;
  let testOpId: number;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConflictDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'sync/conflicts', component: ConflictDetailPage }]),
        DatabaseService,
        WorkOrderService,
        SyncService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? String(testOpId || 1) : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    db = TestBed.inject(DatabaseService);
    workOrderService = TestBed.inject(WorkOrderService);
    syncService = TestBed.inject(SyncService);
    await db.initialize();

    const op = await db.addPendingOperation('STATUS_CHANGE', 10, {
      newStatus: 'IN_PROGRESS',
      notes: 'Starting job',
      expectedVersion: 1,
    });
    testOpId = op.id;
    await db.updatePendingOperationStatus(
      testOpId,
      'CONFLICT_MANUAL_REVIEW',
      'Version 409 conflict'
    );

    const mockServerOrder: any = {
      id: 10,
      code: 'WO-2026-0000010',
      title: 'Reparación de ascensor',
      status: 'ASSIGNED',
      priority: 'HIGH',
      version: 5,
      client: { id: 1, businessName: 'Test Corp' },
      createdAt: '2026-01-01T00:00:00Z',
    };
    vi.spyOn(workOrderService, 'getWorkOrderById').mockReturnValue(of(mockServerOrder));

    fixture = TestBed.createComponent(ConflictDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load conflict details', async () => {
    await component.loadDetails(testOpId);
    expect(component).toBeTruthy();
    expect(component.operation()).toBeTruthy();
    expect(component.operation()?.id).toBe(testOpId);
    expect(component.serverOrder()?.version).toBe(5);
  });

  it('should allow retry with fresh server version and transition to PENDING', async () => {
    await component.loadDetails(testOpId);
    const validation = component.canRetryWithServer();
    expect(validation.allowed).toBe(true);

    await component.retryWithCurrentVersion();

    const updatedOp = await db.getPendingOperationById(testOpId);
    expect(updatedOp?.status).toBe('PENDING');
    const payload = JSON.parse(updatedOp!.payloadJson);
    expect(payload.expectedVersion).toBe(5);
  });

  it('should forbid retry if server order was already cancelled', async () => {
    vi.spyOn(workOrderService, 'getWorkOrderById').mockReturnValue(
      of({
        id: 10,
        code: 'WO-2026-0000010',
        title: 'Cancelada',
        status: 'CANCELLED',
        priority: 'LOW',
        version: 6,
        client: { id: 1, businessName: 'Test Corp' },
        createdAt: '2026-01-01T00:00:00Z',
      } as any)
    );

    await component.loadDetails(testOpId);
    const validation = component.canRetryWithServer();
    expect(validation.allowed).toBe(false);
    expect(validation.reason).toContain('cancelada');
  });
});
