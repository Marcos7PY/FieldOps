import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Client, CreateClientRequest, Page } from '../models';

@Injectable({
  providedIn: 'root',
})
export class ClientsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/clients`;

  getClients(page = 0, size = 50): Observable<Page<Client>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<Page<Client>>(this.baseUrl, { params });
  }

  getClientById(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/${id}`);
  }

  createClient(payload: CreateClientRequest): Observable<Client> {
    return this.http.post<Client>(this.baseUrl, payload);
  }

  updateClient(id: number, payload: CreateClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.baseUrl}/${id}`, payload);
  }

  deactivateClient(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }
}
