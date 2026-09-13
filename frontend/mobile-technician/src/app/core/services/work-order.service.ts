import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpHeaders, HttpParams, HttpRequest } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChangeStatusRequest, Evidence, Page, WorkOrder, WorkOrderSummary } from '../models';
import { AuthService } from './auth.service';

export interface UploadProgressState {
  progress: number;
  response?: Evidence;
}

@Injectable({
  providedIn: 'root'
})
export class WorkOrderService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  getAssignedWorkOrders(page = 0, size = 20): Observable<Page<WorkOrderSummary>> {
    const user = this.authService.currentUser();
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', 'createdAt,desc');

    if (user?.id) {
      params = params.set('technicianId', user.id.toString());
    }

    return this.http.get<Page<WorkOrderSummary>>(`${environment.apiBaseUrl}/work-orders`, { params });
  }

  getWorkOrderById(id: number): Observable<WorkOrder> {
    return this.http.get<WorkOrder>(`${environment.apiBaseUrl}/work-orders/${id}`);
  }

  changeStatus(id: number, request: ChangeStatusRequest, version: number): Observable<WorkOrder> {
    const headers = new HttpHeaders().set('If-Match', `"${version}"`);
    return this.http.patch<WorkOrder>(
      `${environment.apiBaseUrl}/work-orders/${id}/status`,
      request,
      { headers }
    );
  }

  uploadEvidence(
    orderId: number,
    file: Blob,
    filename: string,
    metadata?: { latitude?: number; longitude?: number; capturedAt?: string }
  ): Observable<Evidence> {
    const formData = new FormData();
    formData.append('file', file, filename);
    if (metadata) {
      const metadataBlob = new Blob([JSON.stringify(metadata)], { type: 'application/json' });
      formData.append('metadata', metadataBlob);
    }

    return this.http.post<Evidence>(
      `${environment.apiBaseUrl}/work-orders/${orderId}/evidence`,
      formData
    );
  }

  uploadEvidenceWithProgress(
    orderId: number,
    file: Blob,
    filename: string,
    metadata?: { latitude?: number; longitude?: number; capturedAt?: string }
  ): Observable<UploadProgressState> {
    const formData = new FormData();
    formData.append('file', file, filename);
    if (metadata) {
      const metadataBlob = new Blob([JSON.stringify(metadata)], { type: 'application/json' });
      formData.append('metadata', metadataBlob);
    }

    const req = new HttpRequest('POST', `${environment.apiBaseUrl}/work-orders/${orderId}/evidence`, formData, {
      reportProgress: true
    });

    return this.http.request<Evidence>(req).pipe(
      map(event => {
        if (event.type === HttpEventType.UploadProgress) {
          const progress = event.total ? Math.round((100 * event.loaded) / event.total) : 0;
          return { progress };
        } else if (event.type === HttpEventType.Response) {
          return { progress: 100, response: event.body as Evidence };
        }
        return { progress: 0 };
      })
    );
  }
}
