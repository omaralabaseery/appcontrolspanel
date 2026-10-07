# Project structure

The server remains one Java Spring Boot application. HTML/CSS/JavaScript are served from `src/main/resources/static`; no frontend bundler or framework is required.

| Directory | Responsibility |
|---|---|
| `src/main/java/com/appcontrol/panel/` | Application entry point |
| `.../config/` | Firebase SDK setup, Spring Security and OpenAPI configuration |
| `.../controller/` | HTTP routes for mobile/admin operations and database initialization |
| `.../service/` | Application operations, Firestore access, initialization and version policy |
| `.../dto/` | Validated request/response records (`Models`) |
| `.../exception/` | Shared HTTP exception handling |
| `src/main/resources/database/schema.json` | Firestore collection/field manifest |
| `src/main/resources/static/index.html` | Application shell and asset entry points |
| `.../static/assets/css/vendor/bootstrap.css` | User-supplied Bootstrap foundation |
| `.../static/assets/css/base.css` | App tokens and foundational overrides |
| `.../static/assets/css/layout.css` | Sidebar, topbar and main shell |
| `.../static/assets/css/components.css` | Reusable controls, cards, tables and forms |
| `.../static/assets/css/pages.css` | Phone previews, dialogs and page-specific components |
| `.../static/assets/css/responsive.css` | Responsive and reduced-motion rules |
| `.../static/assets/js/app.js` | Routing, page composition and event binding |
| `.../static/assets/js/core/ui.js` | Escaping, icons, formatters, dialogs and toasts |
| `.../static/assets/js/components/activity-chart.js` | Demo-only activity chart |
| `.../static/assets/js/services/api.js` | Firebase sign-in, REST client and demo adapter |
| `.../static/assets/js/data/demo.js` | Explicit sample fixtures |
| `.../static/assets/js/config/firebase-config.js` | Public Firebase web configuration |
| `src/test/java/com/appcontrol/panel/service/` | Policy and database initializer tests |
| `src/test/java/com/appcontrol/panel/SecurityTest.java` | HTTP authorization/validation tests |
| `tests/` | Browser smoke and mocked database UI tests |
| `docs/` | Setup, API contract, data model and this guide |

## CSS customization

The uploaded `Pasted text(20261007-130511).txt` contains Bootstrap-style base CSS, grids, buttons, forms and utility classes. Its contents are preserved in `assets/css/vendor/bootstrap.css`, with only the unavailable source-map comment removed. Do not edit the vendor file for routine branding changes.

`index.html` loads vendor CSS first, then base, layout, components, pages and responsive overrides. The panel adopts its primary blue (`#007bff`), Bootstrap card/button classes, control borders and 4px card corners. Application-specific layouts and mobile behavior live in the separate override files.

Bootstrap's `.row` negative margins are reset for existing compact app rows. Native dialogs keep `dialog:not([open]) { display:none }` because the supplied reset sets `dialog` to `display:block`. The panel's JavaScript owns its dialogs/navigation; Bootstrap JavaScript/jQuery are not required.

The supplied file does not specify a new sidebar layout, graphics or dashboard HTML. The existing formal control-panel layout is retained and adapted to this CSS foundation.

## Configuration and credentials

Edit public browser project settings only in `assets/js/config/firebase-config.js`. Put private service-account credentials outside the repository and reference them through `GOOGLE_APPLICATION_CREDENTIALS`. Initialization instructions: [DATABASE_SETUP_AR.md](DATABASE_SETUP_AR.md).

Moving Java packages and browser assets does not change REST endpoint paths or Firestore collection names.
