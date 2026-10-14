import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Category } from './category';

// API-13. Goes through the dev-server proxy (/api -> localhost:8080) and the auth interceptor, like every other call.
@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Category[]> {
    return this.http
      .get<{ id: number; catName: string; active: boolean }[]>('/api/v1/categories')
      .pipe(map((list) => list.map((c) => ({ catId: c.id, catName: c.catName, active: c.active }))));
  }
}
