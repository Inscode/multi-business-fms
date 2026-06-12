import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  TaskTemplate, TaskInstance, Clarification,
  CreateTemplatePayload, TaskStatus,
} from '../models/task.models';

const BASE = `${environment.apiUrl}/tasks`;

// ── Response shapes from backend ─────────────────────────────────────────────

export interface TaskTemplateResponse {
  id: number;
  title: string;
  description: string;
  assignedRole: string;
  frequency: string;
  taskType: string;
  businessUnit: string | null;
  dueTime: string | null;
  urgencyLevel: string;
  active: boolean;
  createdAt: string;
}

export interface TaskInstanceResponse {
  id: number;
  template: TaskTemplateResponse;
  date: string;
  status: TaskStatus;
  completedAt: string | null;
  movedReason: string | null;
  movedToDate: string | null;
  attachmentUrls: string[];
}

export interface ClarificationResponse {
  id: number;
  instanceId: number;
  authorName: string;
  authorRole: string;
  message: string;
  type: 'QUESTION' | 'REPLY';
  imageUrls: string[];
  createdAt: string;
}

// ── Mappers: backend → frontend model ────────────────────────────────────────

function toTemplate(r: TaskTemplateResponse): TaskTemplate {
  return {
    id: r.id,
    title: r.title,
    description: r.description,
    referenceImageUrls: [],
    assignedRole: r.assignedRole as any,
    frequency: r.frequency as any,
    taskType: r.taskType as any,
    businessUnit: r.businessUnit ?? '',
    dueTime: r.dueTime ?? '',
    urgencyLevel: r.urgencyLevel as any,
    active: r.active,
  };
}

function toInstance(r: TaskInstanceResponse): TaskInstance {
  return {
    id: r.id,
    taskTemplateId: r.template.id,
    template: toTemplate(r.template),
    date: r.date,
    status: r.status,
    completedAt: r.completedAt ?? undefined,
    movedReason: r.movedReason ?? undefined,
    movedToDate: r.movedToDate ?? undefined,
    attachmentUrls: r.attachmentUrls,
  };
}

function toClarification(r: ClarificationResponse): Clarification {
  return {
    id: r.id,
    taskInstanceId: r.instanceId,
    authorName: r.authorName,
    authorRole: r.authorRole,
    message: r.message,
    imageUrls: r.imageUrls,
    type: r.type,
    createdAt: r.createdAt,
  };
}

// ── Service ───────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class TaskService {

  constructor(private http: HttpClient) {}

  // ── Instances ───────────────────────────────────────────────────────────────

  getInstancesForDate(date: string): Observable<TaskInstance[]> {
    return new Observable(obs => {
      this.http.get<TaskInstanceResponse[]>(`${BASE}/instances`, {
        params: new HttpParams().set('date', date),
      }).subscribe({
        next: list => { obs.next(list.map(toInstance)); obs.complete(); },
        error: e  => obs.error(e),
      });
    });
  }

  getAllInstances(): Observable<TaskInstance[]> {
    const d = new Date(); const today = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    return this.getInstancesForDate(today);
  }

  getHistory(from: string, to: string): Observable<TaskInstance[]> {
    return new Observable(obs => {
      this.http.get<TaskInstanceResponse[]>(`${BASE}/instances/history`, {
        params: new HttpParams().set('from', from).set('to', to),
      }).subscribe({
        next: list => { obs.next(list.map(toInstance)); obs.complete(); },
        error: e  => obs.error(e),
      });
    });
  }

  updateStatus(
    id: number,
    status: TaskStatus,
    movedReason?: string,
    movedToDate?: string,
  ): Observable<TaskInstance> {
    return new Observable(obs => {
      this.http.patch<TaskInstanceResponse>(`${BASE}/instances/${id}/status`, {
        status,
        movedReason: movedReason ?? null,
        movedToDate: movedToDate ?? null,
      }).subscribe({
        next: r  => { obs.next(toInstance(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  addAttachment(instanceId: number, imageUrl: string): Observable<TaskInstance> {
    return new Observable(obs => {
      this.http.post<TaskInstanceResponse>(
        `${BASE}/instances/${instanceId}/attachments`,
        { imageUrl },
      ).subscribe({
        next: r  => { obs.next(toInstance(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  // ── Clarifications ──────────────────────────────────────────────────────────

  getClarifications(_instanceId: number): Clarification[] {
    // Sync wrapper kept for component compatibility — loaded via loadClarifications()
    return [];
  }

  loadClarifications(instanceId: number): Observable<Clarification[]> {
    return new Observable(obs => {
      this.http.get<ClarificationResponse[]>(
        `${BASE}/instances/${instanceId}/clarifications`,
      ).subscribe({
        next: list => { obs.next(list.map(toClarification)); obs.complete(); },
        error: e   => obs.error(e),
      });
    });
  }

  addClarification(
    instanceId: number,
    message: string,
    type: 'QUESTION' | 'REPLY',
    _authorRole: string,
    imageUrls?: string[],
  ): Observable<Clarification> {
    return new Observable(obs => {
      this.http.post<ClarificationResponse>(
        `${BASE}/instances/${instanceId}/clarifications`,
        { message, type, imageUrls: imageUrls ?? [] },
      ).subscribe({
        next: r  => { obs.next(toClarification(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  // ── Templates ───────────────────────────────────────────────────────────────

  getTemplates(): Observable<TaskTemplate[]> {
    return new Observable(obs => {
      this.http.get<TaskTemplateResponse[]>(`${BASE}/templates`).subscribe({
        next: list => { obs.next(list.map(toTemplate)); obs.complete(); },
        error: e   => obs.error(e),
      });
    });
  }

  getInactiveOnDemandTemplates(): Observable<TaskTemplate[]> {
    return new Observable(obs => {
      this.http.get<TaskTemplateResponse[]>(`${BASE}/templates/on-demand/inactive`).subscribe({
        next: list => { obs.next(list.map(toTemplate)); obs.complete(); },
        error: e   => obs.error(e),
      });
    });
  }

  createTemplate(payload: CreateTemplatePayload): Observable<TaskTemplate> {
    return new Observable(obs => {
      this.http.post<TaskTemplateResponse>(`${BASE}/templates`, payload).subscribe({
        next: r  => { obs.next(toTemplate(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  updateTemplate(id: number, payload: Partial<CreateTemplatePayload>): Observable<TaskTemplate> {
    return new Observable(obs => {
      this.http.put<TaskTemplateResponse>(`${BASE}/templates/${id}`, payload).subscribe({
        next: r  => { obs.next(toTemplate(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  activateOnDemand(templateId: number): Observable<TaskTemplate> {
    return new Observable(obs => {
      this.http.patch<TaskTemplateResponse>(
        `${BASE}/templates/${templateId}/activate`, {},
      ).subscribe({
        next: r  => { obs.next(toTemplate(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  toggleActive(templateId: number): Observable<TaskTemplate> {
    return new Observable(obs => {
      this.http.patch<TaskTemplateResponse>(
        `${BASE}/templates/${templateId}/toggle-active`, {},
      ).subscribe({
        next: r  => { obs.next(toTemplate(r)); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }

  // ── Image upload ────────────────────────────────────────────────────────────

  uploadImage(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return new Observable(obs => {
      this.http.post<{ url: string }>(`${BASE}/upload-image`, form).subscribe({
        next: r  => { obs.next(r.url); obs.complete(); },
        error: e => obs.error(e),
      });
    });
  }
}
