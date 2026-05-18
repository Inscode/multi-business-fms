import { CommonModule } from '@angular/common';
import { Component, OnInit, HostListener } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar';

@Component({
  selector: 'app-main-layout',
  templateUrl: './main-layout.html',
  styleUrls: ['./main-layout.scss'],
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
export class MainLayoutComponent implements OnInit {
  collapsed = false;
  isMobile = false;

  ngOnInit(): void {
    this.checkScreen();
  }

  @HostListener('window:resize')
  checkScreen(): void {
    this.isMobile = window.innerWidth < 768;
  }

  toggleCollapse(): void {
    this.collapsed = !this.collapsed;
  }

  
}