import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MediaUploadResponse {
  id: string;
  url: string;
  originalFileName: string;
  contentType: string;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  private base = `${environment.apiBaseUrl}/media/images`;

  constructor(private http: HttpClient) {}

  upload(file: File): Observable<MediaUploadResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<MediaUploadResponse>(this.base, form);
  }

  getImageUrl(id: string): string {
    return `${this.base}/${id}`;
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
