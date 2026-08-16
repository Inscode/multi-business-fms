import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface BillReturnItemRequest {
  productId?: number;
  itemName?: string;
  unitPrice?: number;
  quantityRequested: number;
  quantityReturned?: number;
  /** Set when returning against this bill's own invoice line. */
  invoiceLineId?: number;
  itemId?: number;
  /** Typed only for a different-bill return; read off the line otherwise. */
  slabDiscountPct?: number;
  /** A credit typed over the computed one — flagged to the admin. */
  creditAmountOverride?: number;
}

/**
 * A line of the invoice behind the bill, offered for return. The prices are the ones
 * actually charged, so a credit reverses the sale rather than today's price list.
 */
export interface ReturnableLine {
  invoiceLineId: number;
  itemId: number;
  itemCode: string;
  description: string;
  brandName?: string;
  qtySold: number;
  qtyAlreadyReturned: number;
  qtyAvailable: number;
  wsp: number;
  appliedDiscountPct?: number;
  cashDiscountPct?: number;
}

export interface CreateBillReturnRequest {
  returnType: string;
  /** Items picked off this bill's own invoice lines. */
  fromSameBill?: boolean;
  /** Only read for a different-bill return; taken from the bill otherwise. */
  cashSale?: boolean;
  items: BillReturnItemRequest[];
  discountPercentage?: number;
  discountFixed?: number;
  predictedValue?: number;
  responsibleWorkerId?: number;
  notes?: string;
}

export interface BillReturnItemResponse {
  id: number;
  productId?: number;
  itemName: string;
  unitPrice: number;
  quantityRequested: number;
  quantityReturned?: number;
  lineTotal: number;
  invoiceLineId?: number;
  itemId?: number;
  itemCode?: string;
  grossValue?: number;
  slabDiscountPct?: number;
  cashDiscountPct?: number;
  creditAmount?: number;
  amountEdited?: boolean;
  computedCreditAmount?: number;
}

export interface BillReturnResponse {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  business: string;
  returnType: string;
  status: string;
  items: BillReturnItemResponse[];
  itemsTotal: number;
  discountPercentage?: number;
  discountFixed?: number;
  calculatedReturnAmount: number;
  predictedValue?: number;
  approvedWith?: string;
  approvedAmount?: number;
  rejectionReason?: string;
  notes?: string;
  responsibleWorkerName?: string;
  submittedByName: string;
  submittedAt: string;
  reviewedByName?: string;
  reviewedAt?: string;
  shortfallAmount?: number;

  fromSameBill?: boolean;
  cashSale?: boolean;
  cashDiscountPct?: number;

  /** ALL | PARTIAL | NONE — what the accountant found in the box. */
  goodsReceipt?: string;
  goodsConfirmedByName?: string;
  goodsConfirmedAt?: string;
  goodsConfirmedNote?: string;

  /** A line credit was typed over the calculation. */
  amountEdited?: boolean;
  amountEditedBy?: string;

  stockApplied?: boolean;
  cancelledByName?: string;
  cancelledAt?: string;
  cancelReason?: string;

  /** Still awaiting a confirmation or review — blocks payment on the bill. */
  open?: boolean;
}

/** What a bill is worth once its returns come off. */
export interface BillReturnSummary {
  billTotal: number;
  salableTotal: number;
  damageTotal: number;
  returnsTotal: number;
  payable: number;
  amountPaid: number;
  balanceRemaining: number;
  openCount: number;
  openAmount: number;
  returns: BillReturnResponse[];
}

export interface ConfirmGoodsRequest {
  receipt: 'ALL' | 'PARTIAL' | 'NONE';
  note?: string;
  items?: ReceivedItemDto[];
}

export interface ReceivedItemDto {
  id: number;
  quantityReturned: number;
}

export interface ApproveReturnRequest {
  approveWith: string;
  items: ReceivedItemDto[];
}

/** A photograph behind a return — its own, or a page of its round's book. */
export interface ReturnImage {
  id: number;
  imageUrl: string;
  pageNo?: number;
  returnType: 'DAMAGE' | 'SALABLE';
  uploadedBy?: string;
  uploadedAt?: string;
  /** True when it is a round's book page, covering every shop on that round. */
  fromRun: boolean;
  runLabel?: string;
}

@Injectable({ providedIn: 'root' })
export class BillReturnService {
  private apiUrl = `${environment.apiUrl}/bill-returns`;

  constructor(private http: HttpClient) {}

  create(billId: number, req: CreateBillReturnRequest): Observable<BillReturnResponse> {
    return this.http.post<BillReturnResponse>(`${this.apiUrl}/bills/${billId}`, req);
  }

  /**
   * @param month any date inside the month wanted — a round counts against its own
   *              month, which is not always the month its bills were dated in
   * @param runId one lorry round
   * @param mode  IMMEDIATE or STORE_PICKUP, which have no round to belong to
   */
  getAll(status?: string, month?: string, runId?: number, mode?: string):
      Observable<BillReturnResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (month)  params = params.set('month', month);
    if (runId)  params = params.set('runId', runId);
    if (mode)   params = params.set('mode', mode);
    return this.http.get<BillReturnResponse[]>(this.apiUrl, { params });
  }

  getForBill(billId: number): Observable<BillReturnResponse[]> {
    return this.http.get<BillReturnResponse[]>(`${this.apiUrl}/bills/${billId}`);
  }

  /** Damage and salable apart, plus what is still open on the bill. */
  getSummary(billId: number): Observable<BillReturnSummary> {
    return this.http.get<BillReturnSummary>(`${this.apiUrl}/bills/${billId}/summary`);
  }

  /** Empty for bills entered before invoicing — those return from the catalogue. */
  getReturnableLines(billId: number): Observable<ReturnableLine[]> {
    return this.http.get<ReturnableLine[]>(`${this.apiUrl}/bills/${billId}/returnable-lines`);
  }

  confirmGoods(id: number, req: ConfirmGoodsRequest): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/confirm-goods`, req);
  }

  markNotReceived(id: number, reason: string): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/not-received`, { reason });
  }

  cancel(id: number, reason: string): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/cancel`, { reason });
  }

  // ── Photographs ─────────────────────────────────────────────────
  // Damage and salable upload to separate folders: one supports a claim against the
  // agent, the other a credit to the customer, and they are kept for different reasons.

  uploadImage(file: File, returnType: 'DAMAGE' | 'SALABLE'): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ url: string }>(
      `${environment.apiUrl}/returns/upload-image?returnType=${returnType}`, form
    ).pipe(map(r => r.url));
  }

  /** Everything behind one return: its own photo, or its round's pages. */
  images(returnId: number): Observable<ReturnImage[]> {
    return this.http.get<ReturnImage[]>(`${this.apiUrl}/${returnId}/images`);
  }

  addImage(returnId: number, imageUrl: string, pageNo?: number): Observable<ReturnImage> {
    return this.http.post<ReturnImage>(`${this.apiUrl}/${returnId}/images`, { imageUrl, pageNo });
  }

  /** The book pages for a round. Several is normal, and more can be added later. */
  runImages(runId: number, returnType?: 'DAMAGE' | 'SALABLE'): Observable<ReturnImage[]> {
    const q = returnType ? `?returnType=${returnType}` : '';
    return this.http.get<ReturnImage[]>(`${this.apiUrl}/runs/${runId}/images${q}`);
  }

  addRunImage(runId: number, returnType: 'DAMAGE' | 'SALABLE',
              imageUrl: string, pageNo?: number): Observable<ReturnImage> {
    return this.http.post<ReturnImage>(`${this.apiUrl}/runs/${runId}/images`,
      { returnType, imageUrl, pageNo });
  }

  deleteImage(imageId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/images/${imageId}`);
  }

  getPendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/pending-count`);
  }

  approve(id: number, req: ApproveReturnRequest): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/approve`, req);
  }

  reject(id: number, reason: string): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/reject`, { reason });
  }

  fixHistoricalBillAmounts(): Observable<{ fixed: number }> {
    return this.http.post<{ fixed: number }>(`${this.apiUrl}/fix-bill-amounts`, {});
  }
}