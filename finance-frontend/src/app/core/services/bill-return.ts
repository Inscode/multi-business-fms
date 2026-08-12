import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
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

@Injectable({ providedIn: 'root' })
export class BillReturnService {
  private apiUrl = `${environment.apiUrl}/bill-returns`;

  constructor(private http: HttpClient) {}

  create(billId: number, req: CreateBillReturnRequest): Observable<BillReturnResponse> {
    return this.http.post<BillReturnResponse>(`${this.apiUrl}/bills/${billId}`, req);
  }

  getAll(status?: string): Observable<BillReturnResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
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