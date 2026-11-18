import { Routes } from '@angular/router';
import { HomePage } from './home/home-page';
import { PlaceholderPage } from './pages/placeholder-page';
import { ConnectPage } from './connect/connect-page';
import { PrivacyPage } from './privacy/privacy-page';
import { RoutePage } from './route/route-page';
import { AdminFeedsPage } from './admin/feeds/admin-feeds-page';

export const routes: Routes = [
  { path: '', component: HomePage, pathMatch: 'full', title: 'LeoWay' },
  { path: 'route', component: RoutePage, title: 'Route and alerts' },
  { path: 'connect', component: ConnectPage, title: 'Connect WebUntis' },
  { path: 'privacy', component: PrivacyPage, title: 'Privacy' },
  { path: 'admin/feeds', component: AdminFeedsPage, title: 'Feed status · LeoWay' },
  { path: 'connections', component: PlaceholderPage, title: 'Other connections' },
  { path: 'disruptions', component: PlaceholderPage, title: 'Report disruption' },
  { path: 'lateness', component: PlaceholderPage, title: 'My lateness' },
  { path: '**', redirectTo: '' },
];
