import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { WorkOrderDetailComponent } from './work-order-detail.component';
import { AuthService } from '../../../core/services';
import { environment } from '../../../../environments/environment';
import { WorkOrder } from '../../../core/models';

describe('WorkOrderDetailComponent', () => {
  let component: WorkOrderDetailComponent;
  let fixture: ComponentFixture<WorkOrderDetailComponent>;
  let httpMock: HttpTestingController;

  const mockOrder: WorkOrder = {
    id: 10,
    code: 'OT-2026-00010',
    title: 'Mantenimiento preventivo',
    description: 'Revisión periódica de equipos',
    status: 'ASSIGNED',
    priority: 'HIGH',
    client: {
      id: 1,
      businessName: 'Acme Corp',
      taxId: 'B12345678',
      active: true,
    },
    assignedTechnicianId: 2,
    createdBy: 1,
    createdAt: '2026-09-13T10:00:00',
    scheduledAt: '2026-09-14T14:00:00',
    startedAt: null,
    completedAt: null,
    version: 1,
    evidences: [],
    statusHistory: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkOrderDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? '10' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    // Authenticate as supervisor so action buttons are enabled
    authService.setSession({
      accessToken: 'fake-token',
      refreshToken: 'fake-refresh',
      expiresIn: 900,
      user: { id: 1, username: 'supervisor', fullName: 'Supervisor', roles: ['ROLE_SUPERVISOR'] },
    });

    fixture = TestBed.createComponent(WorkOrderDetailComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load and display work order details on init', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10`);
    expect(req.request.method).toBe('GET');
    req.flush(mockOrder);

    expect(component.order()?.code).toBe('OT-2026-00010');
    expect(component.loading()).toBe(false);
  });

  it('should display conflict error message and reload button on 409 conflict during status change', () => {
    fixture.detectChanges();

    // Initial load
    const reqInitial = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10`);
    reqInitial.flush(mockOrder);

    // Open status form
    component.toggleStatusForm();
    fixture.detectChanges();

    component.statusForm.setValue({
      newStatus: 'IN_PROGRESS',
      notes: 'Iniciando labores',
    });

    // Submit status change
    component.submitStatusChange();
    fixture.detectChanges();

    const reqPatch = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10/status`);
    expect(reqPatch.request.method).toBe('PATCH');
    expect(reqPatch.request.headers.get('If-Match')).toBe('"1"');

    // Simulate 409 Conflict
    reqPatch.flush(
      { detail: 'Version conflict: optimistic locking failure' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    // Assert that conflict message is set
    expect(component.isConflictError()).toBe(true);
    expect(component.actionError()).toBe(
      'Otro usuario modificó esta orden. Recarga para ver los cambios actuales.'
    );

    // Verify template contains the conflict banner and reload button
    const bannerEl: HTMLElement | null = fixture.nativeElement.querySelector('.conflict-banner');
    expect(bannerEl).toBeTruthy();
    expect(bannerEl?.textContent).toContain(
      'Otro usuario modificó esta orden. Recarga para ver los cambios actuales.'
    );
    expect(bannerEl?.textContent).toContain('Recargar');

    // Clicking reload button should invoke loadOrder
    const reloadBtn: HTMLButtonElement | null = bannerEl?.querySelector('button') ?? null;
    expect(reloadBtn).toBeTruthy();

    reloadBtn?.click();
    fixture.detectChanges();

    const reqReload = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10`);
    expect(reqReload.request.method).toBe('GET');
    reqReload.flush({ ...mockOrder, version: 2, status: 'IN_PROGRESS' });

    expect(component.order()?.version).toBe(2);
  });

  it('should display conflict error message and reload button on 409 conflict during technician assign', () => {
    fixture.detectChanges();

    // Initial load
    const reqInitial = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10`);
    reqInitial.flush(mockOrder);

    // Open assign form
    component.toggleAssignForm();
    fixture.detectChanges();

    component.assignForm.setValue({
      technicianId: 3,
      scheduledAt: '2026-09-15T09:00',
    });

    // Submit assign
    component.submitAssign();
    fixture.detectChanges();

    const reqPatch = httpMock.expectOne(`${environment.apiBaseUrl}/work-orders/10/assign`);
    expect(reqPatch.request.method).toBe('PATCH');
    expect(reqPatch.request.headers.get('If-Match')).toBe('"1"');

    // Simulate 409 Conflict
    reqPatch.flush(
      { detail: 'Version conflict: entity modified concurrently' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    // Assert that conflict message is set
    expect(component.isConflictError()).toBe(true);
    expect(component.actionError()).toBe(
      'Otro usuario modificó esta orden. Recarga para ver los cambios actuales.'
    );

    const bannerEl: HTMLElement | null = fixture.nativeElement.querySelector('.conflict-banner');
    expect(bannerEl).toBeTruthy();
  });
});
