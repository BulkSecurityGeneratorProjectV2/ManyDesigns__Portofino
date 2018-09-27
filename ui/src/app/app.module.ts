import {Component, ModuleWithProviders, NgModule} from '@angular/core';
import {PortofinoModule, Page, PageComponent, NAVIGATION_COMPONENT, NavigationComponent, DefaultNavigationComponent} from "portofino";

@Component({
  selector: 'portofino-hello',
  template: `<p>Welcome to Portofino 5!</p>`
})
export class HelloPortofino {}

@Component({
  selector: 'custom-navigation',
  template: `<h3>Custom navigation</h3><p><a routerLink="/start">Start here</a> </p>`
})
export class CustomNavigation implements NavigationComponent {
  page: Page;
}

@Component({
  selector: 'app-root',
  template: `<portofino-app appTitle="Demo-TT" apiRoot="http://localhost:8080/demo-tt/"></portofino-app>`
})
export class AppComponent {}

@NgModule({
  declarations: [AppComponent, HelloPortofino, CustomNavigation],
  providers: [
    { provide: NAVIGATION_COMPONENT, useFactory: AppModule.navigation },
  ],
  imports: [ PortofinoModule.withRoutes([{ path: "start", component: HelloPortofino }]) ],
  entryComponents: [ CustomNavigation ],
  bootstrap: [AppComponent]
})
export class AppModule {
  static navigation() {
    return DefaultNavigationComponent
    //return CustomNavigation
  }
}
