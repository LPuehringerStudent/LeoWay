import { Routes } from '@angular/router';
import { HomePage } from './home/home-page';
import { PlaceholderPage } from './pages/placeholder-page';

export const routes: Routes = [
  { path: '', component: HomePage, pathMatch: 'full', title: 'LeoWay' },
  { path: 'route', component: PlaceholderPage, title: 'Route and alerts' },
  { path: 'privacy', component: PlaceholderPage, title: 'Privacy' },
  // Admin only once accounts exist (#20).
  { path: 'admin/feeds', component: PlaceholderPage, title: 'Feed status' },
  { path: 'connections', component: PlaceholderPage, title: 'Other connections' },
  { path: 'disruptions', component: PlaceholderPage, title: 'Report disruption' },
  { path: 'lateness', component: PlaceholderPage, title: 'My lateness' },
  { path: '**', redirectTo: '' },
];
