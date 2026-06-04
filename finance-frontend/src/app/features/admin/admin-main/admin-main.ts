import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { OwnerCollect } from '../../dashboard/owner/owner-collect/owner-collect';
import { EditRequestsPage } from '../edit-requests/edit-requests';
import { UsersPage } from '../users-page/users-page';
import { ReturnProductsPage } from '../return-products/return-products';
import { AdminCollections } from '../admin-collections/admin-collections';
import { AdminReminders } from '../admin-reminders/admin-reminders';

@Component({
  selector: 'app-admin-main',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, MatIconModule,
    OwnerCollect, EditRequestsPage, UsersPage,
    ReturnProductsPage, AdminCollections, AdminReminders,
  ],
  templateUrl: './admin-main.html',
  styles: [`
    .main-page { max-width: 1200px; }
    .page-title { font-size: 20px; font-weight: 600; margin: 0 0 16px; color: #1a1a2e; }
    .tab-body { padding: 16px 0; }
  `],
})
export class AdminMain {}
