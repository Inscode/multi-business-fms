import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { catchError, of } from 'rxjs';
import { environment } from '../../../../../environments/environment';

interface ImportedLine {
  itemCode?: string;
  description?: string;
  qty?: number;
  unitPrice?: number;
  lineTotal?: number;
}

interface ParsedInvoice {
  invoiceNo?: string;
  customerName?: string;
  invoiceDate?: string;
  netTotal?: number;
  lines: ImportedLine[];
  /** Codes that could not be pinned to exactly one catalog item. */
  unmatchedCodes?: string[];
  /** True when this invoice will be refused — an unmatched item or unresolved customer. */
  blocked?: boolean;
  blockReason?: string;
}

interface PreviewResponse {
  invoiceCount: number;
  blockedCount: number;
  importableCount: number;
  warnings: string[];
  invoices: ParsedInvoice[];
}

interface ImportResponse {
  imported: number;
  blocked: number;
  warnings: string[];
  errors: string[];
}

type ImportCategory = 'RAINCO' | 'STATIONERY';

@Component({
  selector: 'app-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule,
            MatButtonModule, MatIconModule, MatProgressSpinnerModule,
            MatSelectModule, MatFormFieldModule, MatTooltipModule],
  templateUrl: './import.component.html',
  styleUrl: './import.component.scss'
})
export class ImportComponent {
  private http = inject(HttpClient);
  private cdr  = inject(ChangeDetectorRef);

  selectedCategory: ImportCategory | null = null;
  selectedFile: File | null = null;
  dragOver    = false;
  parsing     = false;
  importing   = false;
  parsed:     ParsedInvoice[] = [];
  parseWarnings: string[] = [];
  importResult: ImportResponse | null = null;
  error       = '';

  chooseCategory(category: ImportCategory) {
    this.selectedCategory = category;
    this.cdr.markForCheck();
  }

  changeCategory() {
    this.selectedCategory = null;
    this.reset();
  }

  onDragOver(e: DragEvent) { e.preventDefault(); this.dragOver = true; }
  onDragLeave()             { this.dragOver = false; }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.dragOver = false;
    const file = e.dataTransfer?.files?.[0];
    if (file) this.selectFile(file);
  }

  onFileChange(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.selectFile(file);
  }

  selectFile(file: File) {
    if (!file.name.match(/\.(xls|xlsx)$/i)) {
      this.error = 'Please select a valid Excel file (.xls or .xlsx)';
      this.cdr.markForCheck();
      return;
    }
    this.selectedFile = file;
    this.parsed       = [];
    this.parseWarnings = [];
    this.importResult = null;
    this.error        = '';
    this.cdr.markForCheck();
    this.parse();
  }

  parse() {
    if (!this.selectedFile || !this.selectedCategory) return;
    this.parsing = true;
    const fd = new FormData();
    fd.append('file', this.selectedFile);
    fd.append('category', this.selectedCategory);
    this.http.post<PreviewResponse>(`${environment.apiUrl}/invoicing/import/preview`, fd)
      .pipe(catchError(err => {
        this.error   = err?.error?.message ?? 'Failed to parse file';
        this.parsing = false;
        this.cdr.markForCheck();
        return of(null);
      }))
      .subscribe(res => {
        this.parsed        = res?.invoices ?? [];
        this.parseWarnings = res?.warnings ?? [];
        this.parsing = false;
        this.cdr.markForCheck();
      });
  }

  importAll() {
    if (!this.selectedFile || !this.selectedCategory) return;
    this.importing = true;
    const fd = new FormData();
    fd.append('file', this.selectedFile);
    fd.append('category', this.selectedCategory);
    this.http.post<ImportResponse>(`${environment.apiUrl}/invoicing/import/confirm`, fd)
      .pipe(catchError(err => {
        this.error     = err?.error?.message ?? 'Import failed';
        this.importing = false;
        this.cdr.markForCheck();
        return of(null);
      }))
      .subscribe(res => {
        this.importing    = false;
        this.importResult = res;
        this.cdr.markForCheck();
      });
  }

  reset() {
    this.selectedFile = null;
    this.parsed       = [];
    this.parseWarnings = [];
    this.importResult = null;
    this.error        = '';
    this.cdr.markForCheck();
  }

  /** Invoices that will actually import — blocked ones are refused by the server. */
  importable(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.blocked);
  }

  blockedInvoices(): ParsedInvoice[] {
    return this.parsed.filter(i => i.blocked);
  }

  isUnmatched(inv: ParsedInvoice, code?: string): boolean {
    return !!code && !!inv.unmatchedCodes?.includes(code);
  }

  totalLines() { return this.parsed.reduce((s, p) => s + (p.lines?.length ?? 0), 0); }
}
