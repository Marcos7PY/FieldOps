import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DailyMetricsResponse, RebuildProjectionResponse, TechnicianMetricsResponse } from '../models';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/analytics`;

  getDailyMetrics(from?: string, to?: string, technicianId?: number): Observable<DailyMetricsResponse> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    if (technicianId !== undefined && technicianId !== null) {
      params = params.set('technicianId', technicianId.toString());
    }

    return this.http.get<DailyMetricsResponse>(`${this.baseUrl}/metrics/daily`, { params });
  }

  getTechnicianMetrics(): Observable<TechnicianMetricsResponse> {
    return this.http.get<TechnicianMetricsResponse>(`${this.baseUrl}/metrics/technicians`);
  }

  rebuildProjection(): Observable<RebuildProjectionResponse> {
    return this.http.post<RebuildProjectionResponse>(`${this.baseUrl}/projections/rebuild`, {});
  }
}
