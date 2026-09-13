import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AssignWorkOrderRequest,
  ChangeStatusRequest,
  CreateWorkOrderRequest,
  Evidence,
  OrderStatus,
  Page,
  WorkOrder,
  WorkOrderSummary
} from '../models';

export interface WorkOrderFilterParams {
  status?: OrderStatus | '';
  search?: string;
  clientId?: number;
  technicianId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface WorkOrderMetrics {
  totalOrders: number;
  draftOrders: number;
  assignedOrders: number;
  inProgressOrders: number;
  completedOrders: number;
  cancelledOrders: number;
}

@Injectable({
  providedIn: 'root'
})
export class WorkOrdersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/work-orders`;

  getWorkOrders(filters: WorkOrderFilterParams = {}): Observable<Page<WorkOrderSummary>> {
    let params = new HttpParams();

    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.search && filters.search.trim().length > 0) {
      params = params.set('search', filters.search.trim());
    }
    if (filters.clientId !== undefined && filters.clientId !== null) {
      params = params.set('clientId', filters.clientId.toString());
    }
    if (filters.technicianId !== undefined && filters.technicianId !== null) {
      params = params.set('technicianId', filters.technicianId.toString());
    }
    if (filters.page !== undefined && filters.page !== null) {
      params = params.set('page', filters.page.toString());
    }
    if (filters.size !== undefined && filters.size !== null) {
      params = params.set('size', filters.size.toString());
    }
    if (filters.sort) {
      params = params.set('sort', filters.sort);
    }

    return this.http.get<Page<WorkOrderSummary>>(this.baseUrl, { params });
  }

  getWorkOrder(id: number): Observable<WorkOrder> {
    return this.http.get<WorkOrder>(`${this.baseUrl}/${id}`);
  }

  createWorkOrder(payload: CreateWorkOrderRequest): Observable<WorkOrder> {
    return this.http.post<WorkOrder>(this.baseUrl, payload);
  }

  assignWorkOrder(id: number, payload: AssignWorkOrderRequest, version?: number): Observable<WorkOrder> {
    let headers = new HttpHeaders();
    if (version !== undefined) {
      headers = headers.set('If-Match', `"${version}"`);
    }
    return this.http.patch<WorkOrder>(`${this.baseUrl}/${id}/assign`, payload, { headers });
  }

  changeStatus(id: number, payload: ChangeStatusRequest, version?: number): Observable<WorkOrder> {
    let headers = new HttpHeaders();
    if (version !== undefined) {
      headers = headers.set('If-Match', `"${version}"`);
    }
    return this.http.patch<WorkOrder>(`${this.baseUrl}/${id}/status`, payload, { headers });
  }

  uploadEvidence(
    id: number,
    file: File,
    latitude?: number,
    longitude?: number,
    capturedAt?: string
  ): Observable<Evidence> {
    const formData = new FormData();
    formData.append('file', file);
    if (latitude !== undefined && latitude !== null) {
      formData.append('latitude', latitude.toString());
    }
    if (longitude !== undefined && longitude !== null) {
      formData.append('longitude', longitude.toString());
    }
    if (capturedAt) {
      formData.append('capturedAt', capturedAt);
    }

    return this.http.post<Evidence>(`${this.baseUrl}/${id}/evidences`, formData);
  }

  getMetrics(): Observable<WorkOrderMetrics> {
    return this.http.get<WorkOrderMetrics>(`${this.baseUrl}/metrics`);
  }
}
