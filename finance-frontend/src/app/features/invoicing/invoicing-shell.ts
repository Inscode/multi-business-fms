import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Hosts the ported wholesale invoicing pages inside the FMS layout.
 * A slim tab bar replaces the wholesale app's sidebar — everything below
 * it is the wholesale UI unchanged.
 */
@Component({
  selector: 'app-invoicing-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  template: `
    <nav class="inv-nav">
      <a routerLink="/invoicing/invoices" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
        <mat-icon>receipt_long</mat-icon> Invoices
      </a>
      <a routerLink="/invoicing/items" routerLinkActive="active">
        <mat-icon>inventory_2</mat-icon> Items
      </a>
      <a routerLink="/invoicing/brands" routerLinkActive="active">
        <mat-icon>sell</mat-icon> Brands
      </a>
      <a routerLink="/invoicing/import" routerLinkActive="active">
        <mat-icon>upload_file</mat-icon> Import
      </a>
    </nav>
    <router-outlet></router-outlet>
  `,
  styles: [`
    .inv-nav {
      display: flex;
      gap: 4px;
      margin-bottom: 12px;
      border-bottom: 1px solid #e0e6ed;
      flex-wrap: wrap;

      a {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 10px 16px;
        font-size: 13.5px;
        font-weight: 500;
        color: #607d8b;
        text-decoration: none;
        border-bottom: 2px solid transparent;
        margin-bottom: -1px;

        mat-icon { font-size: 18px; width: 18px; height: 18px; }

        &:hover { color: #1565c0; }
        &.active {
          color: #1565c0;
          border-bottom-color: #1565c0;
        }
      }
    }
  `],
})
export class InvoicingShell {}
