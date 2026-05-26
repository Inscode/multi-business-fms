import { CommonModule } from '@angular/common';
import { Component, OnInit, AfterViewInit, HostListener, ViewChild } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar';
import { Auth } from '../../core/services/auth';
import { EditRequestService } from '../../core/services/edit-request';

@Component({
  selector: 'app-main-layout',
  templateUrl: './main-layout.html',
  styleUrls: ['./main-layout.scss'],
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatIconModule,
    MatRippleModule,
    NavbarComponent,
  ]
})
export class MainLayoutComponent implements OnInit, AfterViewInit {
  @ViewChild('sidenav') sidenav!: MatSidenav;
  collapsed = false;
  isMobile  = false;
  pendingEditRequests = 0;

  constructor(private auth: Auth, private editRequestService: EditRequestService) {}

  ngOnInit(): void {
    this.checkScreen();
    if (this.showEditRequests) {
      this.loadPendingCount();
    }
  }

  ngAfterViewInit(): void {}

  @HostListener('window:resize')
  checkScreen(): void {
    this.isMobile = window.innerWidth < 768;
  }

  toggleCollapse(): void {
    if (this.isMobile) {
      this.sidenav.toggle();
    } else {
      this.collapsed = !this.collapsed;
    }
  }

  private loadPendingCount(): void {
    this.editRequestService.getPending().subscribe({
      next: (list) => this.pendingEditRequests = list.length,
      error: () => this.pendingEditRequests = 0,
    });
  }

  get currentRole(): string      { return this.auth.getRole() ?? ''; }
  get isShopAccountant(): boolean { return this.currentRole === 'SHOP_ACCOUNTANT'; }
  get showBills(): boolean        { return !this.isShopAccountant; }
  get showPayments(): boolean     { return !this.isShopAccountant; }
  get showStaff(): boolean        { return ['ADMIN', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showUsers(): boolean        { return this.currentRole === 'ADMIN'; }
  get showCollect(): boolean      { return this.currentRole === 'OWNER'; }
  get showEditRequests(): boolean { return this.currentRole === 'ADMIN'; }
}