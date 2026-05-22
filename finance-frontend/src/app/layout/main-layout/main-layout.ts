import { CommonModule } from '@angular/common';
import { Component, OnInit, AfterViewInit, HostListener, ViewChild } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar';
import { Auth } from '../../core/services/auth';

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

  constructor(private auth: Auth) {}

  ngOnInit(): void    { this.checkScreen(); }
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

  get currentRole(): string      { return this.auth.getRole() ?? ''; }
  get isShopAccountant(): boolean { return this.currentRole === 'SHOP_ACCOUNTANT'; }
  get showBills(): boolean        { return !this.isShopAccountant; }
  get showPayments(): boolean     { return !this.isShopAccountant; }
  get showStaff(): boolean        { return ['ADMIN', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showUsers(): boolean        { return this.currentRole === 'ADMIN'; }
  get showCollect(): boolean      { return this.currentRole === 'OWNER'; }
}