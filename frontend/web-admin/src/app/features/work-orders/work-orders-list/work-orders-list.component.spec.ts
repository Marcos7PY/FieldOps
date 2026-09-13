import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { WorkOrdersListComponent } from './work-orders-list.component';
import { environment } from '../../../../environments/environment';
import { Page, WorkOrderSummary } from '../../../core/models';

describe('WorkOrdersListComponent', () => {
  let component: WorkOrdersListComponent;
  let fixture: ComponentFixture<WorkOrdersListComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  const mockOrder: WorkOrderSummary = {
    id: 10,
    code: 'OT-2026-00010',
    title: 'Mantenimiento preventivo',
    status: 'ASSIGNED',
    priority: 'HIGH',
    clientId: 1,
    clientName: 'Acme Corp',
    assignedTechnicianId: 2,
    createdAt: '2026-09-13T10:00:00',
    scheduledAt: '2026-09-14T14:00:00',
    version: 1
  };

  const mockPage: Page<WorkOrderSummary> = {
    content: [mockOrder],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    last: true
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkOrdersListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WorkOrdersListComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create and load initial orders', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(request => request.url === `${environment.apiBaseUrl}/work-orders`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(mockPage);

    expect(component.orders().length).toBe(1);
    expect(component.orders()[0].code).toBe('OT-2026-00010');
    expect(component.totalElements()).toBe(1);
    expect(component.loading()).toBe(false);
  });

  it('should debounce search input for 300 ms before sending request', () => {
    vi.useFakeTimers();
    try {
      fixture.detectChanges();
      const initialReq = httpMock.expectOne(request => request.url === `${environment.apiBaseUrl}/work-orders`);
      initialReq.flush(mockPage);

      component.searchControl.setValue('preventivo');
      vi.advanceTimersByTime(150);
      httpMock.expectNone(request => request.urlWithParams.includes('search=preventivo'));

      vi.advanceTimersByTime(160);
      const searchReq = httpMock.expectOne(request => request.urlWithParams.includes('search=preventivo'));
      expect(searchReq.request.method).toBe('GET');
      searchReq.flush(mockPage);
    } finally {
      vi.useRealTimers();
    }
  });

  it('should reload orders on status filter change', () => {
    fixture.detectChanges();
    const initialReq = httpMock.expectOne(request => request.url === `${environment.apiBaseUrl}/work-orders`);
    initialReq.flush(mockPage);

    component.statusControl.setValue('IN_PROGRESS');

    const statusReq = httpMock.expectOne(request => request.urlWithParams.includes('status=IN_PROGRESS'));
    expect(statusReq.request.method).toBe('GET');
    statusReq.flush(mockPage);

    expect(component.pageIndex()).toBe(0);
  });

  it('should handle pagination changes', () => {
    fixture.detectChanges();
    const initialReq = httpMock.expectOne(request => request.url === `${environment.apiBaseUrl}/work-orders`);
    initialReq.flush(mockPage);

    component.onPageChange({
      pageIndex: 2,
      pageSize: 20,
      length: 50
    });

    const pageReq = httpMock.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/work-orders` &&
      request.params.get('page') === '2' &&
      request.params.get('size') === '20'
    );
    expect(pageReq.request.method).toBe('GET');
    pageReq.flush(mockPage);

    expect(component.pageIndex()).toBe(2);
    expect(component.pageSize()).toBe(20);
  });

  it('should navigate to order detail on viewDetail', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    component.viewDetail(10);
    expect(navigateSpy).toHaveBeenCalledWith(['/work-orders', 10]);
  });
});
