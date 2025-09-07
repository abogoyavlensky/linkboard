# Linkboard - Project Summary

## Overview
Linkboard is a self-hosted personal bookmark manager built with Clojure, SQLite, HTMX, AlpineJS, and TailwindCSS. It provides a clean web interface for organizing bookmarks into boards with automatic metadata fetching for links.

## Key Features
- Personal bookmark management with board organization
- **Board and Link Favorites** with star icons (solid/outline) and priority sorting
- Automatic link metadata extraction (title, icons) with **optional user-provided titles**
- **Board selector in link edit forms** with dropdown showing all user boards ordered alphabetically by title
- **Board names on All Links page** with clickable navigation and bullet separator (•)
- **Link count badges** displayed in board and All Links page headers
- **Infinite scroll pagination** with HTMX-powered seamless loading (25 links per page, 10 per page for testing)
- **Full-text search** with SQLite FTS5 for fast link searching by title and URL
- **Hybrid search system** with FTS5 for terms ≥3 characters and LIKE for shorter terms
- **Clear search functionality** with X button and ESC keyboard shortcut
- **Static search bar** with HTMX targeting to prevent re-rendering during searches
- **Global keyboard shortcuts** with cross-platform compatibility (macOS/Windows/Linux):
  - Ctrl/Cmd + A: Add Link (global)
  - Ctrl/Cmd + B: Create Board (home page only)
  - Ctrl/Cmd + Shift + L: Navigate to All Links (global)
  - Ctrl/Cmd + K: Search (existing, in search contexts)
- **Smart modal management** prevents stacking by auto-closing existing modals when opening new ones via keyboard shortcuts
- **User limits with validation**: 50 boards and 5000 links per user with toast notifications and modal management
- PWA-ready with modern web app icons
- Account-based authentication with auto-generated account numbers
- Client-side account number generation using crypto.randomUUID()
- **Account management page** with export data to CSV, logout, and delete account functionality
- **Account number display** with password-style dots, show/hide toggle, and clipboard copy functionality
- Self-hosted deployment with Docker and Kamal

## Architecture

### Tech Stack
- **Backend**: Clojure with Ring/Jetty server
- **Database**: SQLite with WAL mode for better concurrency
- **Frontend**: Server-side rendered HTML with HTMX for interactivity
- **Styling**: TailwindCSS for responsive design
- **JavaScript**: AlpineJS for client-side behavior
- **Build**: Babashka (bb) for task automation

### Core Components

#### Configuration Management
- **Integrant**: Dependency injection and system lifecycle management
- **Profile-based configs**: dev/test/prod environments in `resources/config.edn`
- Environment variable support for production secrets

#### Database Layer (`src/linkboard/core/db.clj`)
- HikariCP connection pooling
- HoneySQL for query building
- Ragtime for database migrations
- SQLite with WAL mode enabled
- Unqualified kebab-case result mapping

#### Web Server (`src/linkboard/core/server.clj`)
- Jetty adapter with Ring
- Reitit for routing with validation
- Error handling with custom 404/405/406 pages
- Session management for sync codes

#### Routing (`src/linkboard/routes.clj`)
- RESTful API design:
  - `GET /?page=X` - Home page with board list and infinite scroll pagination
  - `GET /links?page=X&q=search` - All Links page with board names, link counts, infinite scroll pagination, and optional search
  - `POST /create-account` - Account creation endpoint (rate limited: 5/min per IP)
  - `POST /boards` - Create new board
  - `GET /boards/:id?page=X&q=search` - Board details with links, link counts, infinite scroll pagination, and optional search
  - `POST /links` - Create link with optional title (can be associated with board or standalone)
  - `PUT/DELETE` operations for boards and links (link updates now support board reassignment)
  - `PATCH /boards/:id/favorite` - Toggle board favorite status
  - `PATCH /links/:link-id/favorite` - Toggle link favorite status
  - `POST /login` - User login endpoint (rate limited: 20/min per IP)
  - `GET /account` - Account management page with user info and actions
  - `GET /account/export` - Export all user data as CSV download
  - `DELETE /account` - Delete account and all associated data
- `wrap-auth` middleware for automatic session-id generation and persistence
- **Rate limiting middleware** (`src/linkboard/limits.clj`): Global protection (200 requests/min per IP) with endpoint-specific limits
- **Pagination support**: Optional `page` query parameters with automatic HTMX infinite scroll
- **Search support**: Optional `q` query parameters for full-text search across link titles and URLs
- Malli schema validation for request parameters

#### Handlers
- **Home handlers** (`src/linkboard/home/handlers.clj`): Board listing, creation, account management, and login/logout
  - **Board list pagination**: Home handler supports infinite scroll for board lists with 3 response types
  - **Smart link creation flow**: Board-specific links use OOB updates, boardless links redirect to All Links page for immediate feedback
- **Board handlers** (`src/linkboard/board/handlers.clj`): Link management within boards with comprehensive security validation
  - **All Links handler**: Fetches all user links with board information using LEFT JOIN queries, supports full-text search
  - **Board handler**: Fetches board-specific links with efficient SQL COUNT queries for link counts, supports full-text search, includes 404 handling for non-existent boards
  - **Link count optimization**: Separate SQL COUNT queries instead of in-memory counting for performance, includes search result counting
  - **Infinite scroll pagination**: All handlers support 3 response types (full page, HTMX page, pagination fragment) with configurable page sizes
  - **Search integration**: Hybrid FTS5/LIKE search system with BM25 ranking, proper query preprocessing, and search term preservation in pagination
  - **Board deletion UX**: Shows custom deletion message with home page link instead of automatic redirect
  - **Link update enhancement**: Includes board title in response data for complete UI updates
- **Account handlers** (`src/linkboard/account/handlers.clj`): Account management functionality
  - **Account page handler**: Displays user account information with member since date
  - **Export data handler**: Generates CSV export of all user boards and links with proper HTTP headers
  - **Delete account handler**: Permanently removes user account and all associated data with CASCADE delete
- **Session-based user management**: All handlers validate session and auto-create users as needed  
- **Account creation workflow**: Account numbers generated client-side, stored in plain text in backend db, with rate limits to API
- **Security patterns**: All board/link operations validate user ownership using `user-owns-board?` function
- **Error handling**: Form validation with error display and user-friendly 403 responses for unauthorized access
- **Conditional logic**: Handlers use `cond` for clean multi-branch decision making (validation, authorization, success)
- **User limits enforcement**: Board creation limited to 50 per user, link creation limited to 5000 per user with validation, toast notifications, and proper error handling

#### Link Metadata Fetching (`src/linkboard/board/fetch.clj`)
- Automatic title and icon extraction from URLs
- HTTP client with Hickory for HTML parsing
- Favicon detection and processing

#### User Management System (`src/linkboard/queries.clj` + handlers)
- **Automatic user creation**: Users created on first interaction with empty account_number
- **Session-based identification**: All operations tied to session_id from `wrap-auth` middleware
- **Account registration**: Users can later register with account numbers for persistence
- **Secure password storage**: Account numbers stored in plain text
- **User isolation**: All boards/links scoped to individual users automatically
- **Authorization validation**: `user-owns-board?` function ensures users can only access their own boards
- **Comprehensive security**: All CRUD operations (add/update/delete) validate board ownership before execution
- **Anonymous user notifications**: UI displays "Using temporary session" for non-registered users

#### Account Management (`src/linkboard/ui/components.clj` + `resources/public/js/utils.js`)
- Client-side account number generation using `crypto.randomUUID()`
- 16-character alphanumeric IDs with dashes every 4 characters (format: XXXX-XXXX-XXXX-XXXX)
- Fallback to `Math.random()` for older browsers
- Alpine.js integration for modal state management
- One-time display warning for account security
- Automatic clipboard copy functionality with visual feedback

#### User Notifications (`src/linkboard/ui/components.clj` + `resources/public/js/utils.js`)
- **Toast notification system** with bottom-center placement and white design
- Success notifications with green borders and check mark icons
- Session-based toast messages: server can add messages to session that display on next page load
- Client-side toast triggering via `showToast(message, type)` JavaScript function
- Alpine.js state management with 4-second auto-dismiss
- Positioned above fixed footer (`bottom-20`) to prevent overlap
- Smooth slide-up animations with manual close option
- **Comprehensive notifications**: Creation, editing, and deletion actions all trigger appropriate toasts

#### Modal System (`src/linkboard/ui/components.clj`)
- **Simplified modal implementation** prevents background flickering during transitions
- Single container approach with `x-cloak` for flash-of-content prevention
- Semi-transparent backdrop with blur effect (`backdrop-blur-xs`)
- CSS rule `[x-cloak] { display: none !important; }` for proper Alpine.js integration
- Click-outside-to-close with `click.stop` on form content
- Escape key handling for accessibility
- **Modal closing via HTMX events**: Uses `HX-Trigger-After-Swap: modal-close` to close modals after successful form submissions
- **Alpine.js event handling**: Listens for `modal-close` window events to set `modalOpen = false`
- **Dynamic content processing**: Uses `x-init="htmx.process($el)"` and global `htmx:afterSwap` events to ensure HTMX attributes work in teleported modal content

#### Account Number UX (`src/linkboard/ui/components.clj` + `src/linkboard/account/views.clj`)
- **Enhanced copy functionality** with consistent copy/check icon transitions in both register modal and account page
- **Account number display** with password-style dots (••••••••) by default for security
- **Show/hide toggle** using eye/eye-slash icons with smooth Alpine.js state management
- **Copy functionality** with clipboard integration and green check-circle success feedback
- **Mobile-responsive design** with smaller text and wider modals to prevent account number wrapping
- **Consistent icon sizing** using same button dimensions to prevent content shifting during transitions
- External circled checkmark indicator positioned right of account number container
- Reserved space layout prevents text container from resizing when checkmark appears
- Satisfying scale animation (`scale-0` to `scale-100`) for visual feedback
- Improved warning message: "Please store account number safely - you cannot restore your account if it is lost."

#### Fixed Footer (`src/linkboard/ui/components.clj`)
- **Persistent Add Link button** positioned at bottom-right of screen
- Glass-morphism design with transparent background (`backdrop-blur-sm`) and semi-transparent border
- Width matches main content area (`max-w-4xl`) for consistent alignment
- Positioned above viewport bottom with proper z-index management
- Content area has bottom padding (`pb-20`) to prevent overlap
- Ready for integration with add link functionality via modal or navigation

## Dependencies

### Core Dependencies (deps.edn)
```clojure
org.clojure/clojure "1.12.1"           ; Core language
integrant/integrant "0.13.1"           ; System management
metosin/reitit-ring "0.9.1"           ; Routing
ring/ring-jetty-adapter "1.14.2"      ; Web server
org.xerial/sqlite-jdbc "3.50.3.0"     ; SQLite driver
com.github.seancorfield/next.jdbc "1.3.1048"  ; Database access
com.github.seancorfield/honeysql "2.7.1340"   ; SQL DSL
hikari-cp/hikari-cp "3.3.0"           ; Connection pooling
clj-http/clj-http "3.13.1"            ; HTTP client
org.clj-commons/hickory "0.7.7"       ; HTML parsing
lambdaisland/uri "1.19.155"           ; Modern URI parsing and validation
```

### Development Tools
```clojure
eftest/eftest "0.6.0"                 ; Test runner
etaoin/etaoin "1.1.43"                ; Browser automation
cloverage/cloverage "1.2.4"           ; Test coverage
io.github.borkdude/carve               ; Remove unused code
```

## Development Workflow

### Available Commands (bb.edn)
```bash
bb tasks                    # List all available tasks
bb deps                     # Install dependencies
bb lint                     # Code linting with clj-kondo
bb fmt                      # Code formatting with cljfmt
bb test                     # Run tests
bb check                    # Run all checks (fmt, lint, outdated, test)
bb carve                    # Remove unused code with aggressive cleanup
bb css-watch               # Watch CSS changes
bb css-build               # Build minified CSS
bb fetch-assets            # Download external JS assets
bb build                   # Build production uberjar
bb kamal setup             # Initial deployment
bb kamal deploy            # Regular deployment
```

### Project Structure
```
src/linkboard/
├── core.clj              # Main entry point
├── core/
│   ├── db.clj           # Database component
│   └── server.clj       # Web server component
├── handlers.clj         # Default error handlers
├── routes.clj           # Route definitions with rate limiting
├── limits.clj           # Rate limiting middleware for API protection
├── queries.clj          # Database queries (user management, boards, links)
├── spec.clj             # Malli schemas for validation (Link URL validation)
├── home/               # Home page functionality
│   ├── handlers.clj     # Board listing, account creation, login/logout
│   └── views.clj
├── board/              # Board management
│   ├── handlers.clj     # Link CRUD with security validation
│   ├── views.clj        # Board and link forms with error handling  
│   ├── pagination.clj   # Infinite scroll pagination utilities
│   └── fetch.clj        # Link metadata fetching
├── account/            # Account management
│   ├── handlers.clj     # Account page, export data, delete account
│   └── views.clj        # Account page UI with account number display
├── ui/                 # UI components
│   ├── components.clj   # Base layout, modals, login forms, toast notifications, error handling, fixed footer, infinite scroll
│   └── icons.clj        # UI icons including search, edit, delete, x-mark, eye, eye-slash, copy, check-circle
└── utils/

resources/
├── config.edn          # System configuration
├── migrations/         # Database migrations
└── public/            # Static assets
    ├── css/
    ├── js/
    │   └── utils.js     # Client-side utilities (account ID generation, toast notifications)
    └── images/

test/                  # Test files
```

## Database Schema

### Tables
1. **user**: User accounts and sessions
   - id, session_id (indexed), account_number (indexed), created_at
2. **board**: User's bookmark collections  
   - id, title, user_id, favorite (boolean), created_at
3. **link**: Individual bookmarks
   - id, url, title, icon, board_id, user_id, favorite (boolean), created_at

### Migrations
- `0001.up.sql`: Initial schema
- `0002.up.sql`: User table creation with indexes
- `0003.up.sql`: Sample data insertion (2 boards, 7 links)
- `0004.up.sql`: Pagination test data (30 boards, 150 links across 5 categories)
- `0005.up.sql`: FTS5 full-text search setup with contentless virtual table and triggers (fixed for proper DELETE/UPDATE operations)
- `0006.up.sql`: Add favorite boolean column to board table with DEFAULT FALSE
- `0007.up.sql`: Add favorite boolean column to link table with DEFAULT FALSE

## Available Functions and Queries

### User Management (`src/linkboard/queries.clj`)
```clojure
(get-user-by-session-id db session-id)           ; Retrieve user by session
(get-user-by-account-number db account-number)   ; Retrieve user by account number
(create-user-with-session! db session-id)        ; Create user with session only  
(create-user! db session-id hashed-account-number) ; Create user with account
(update-user-account-number! db user-id hash)    ; Add account to existing user
(ensure-user-exists! db session-id)              ; Get or create user helper
(get-board-by-id-and-user-id db board-id user-id) ; Get board if owned by user
(get-user-boards-minimal db user-id)             ; Get minimal board data (id, title) ordered by title
(user-owns-board? db {:board-id board-id :session-id session-id}) ; Check board ownership
(user-owns-link? db {:link-id link-id :session-id session-id}) ; Check link ownership via JOIN
(delete-link! db {:link-id link-id :user-id user-id}) ; Delete link with user validation
(toggle-board-favorite! db {:board-id board-id :user-id user-id}) ; Toggle board favorite status
(toggle-link-favorite! db {:link-id link-id :user-id user-id}) ; Toggle link favorite status
(get-user-board-count db user-id)                ; Get count of boards for user validation
(get-user-link-count db user-id)                 ; Get count of links for user validation
```

### Full-Text Search (`src/linkboard/queries.clj`)
```clojure
(preprocess-search-query raw-query)              ; Preprocess user input for FTS5 MATCH queries
(search-all-links-query user-id search-term raw-search-term)     ; Build hybrid query (FTS5 or LIKE) for all user links
(search-board-links-query user-id board-id search-term raw-search-term) ; Build hybrid query for board-specific links
(get-all-links-query user-id search-term)        ; Unified query function (search or regular)
(get-board-links-query user-id board-id search-term) ; Unified board query function (search or regular)
; Example: (get-all-links-query 1 "github") ; Returns FTS5 search results for "github"
; Example: (get-all-links-query 1 "go")     ; Returns LIKE search results for short term
; Example: (get-all-links-query 1 nil)     ; Returns all links without search
```

### Pagination Utilities (`src/linkboard/board/pagination.clj`)
```clojure
(add-pagination query page)                      ; Adds LIMIT/OFFSET to HoneySQL query (25 per page)
(has-more-pages? total-count page)               ; Determines if more pages exist
(get-page-param request)                         ; Extracts page param from request (defaults to 1)
(pagination-request? request)                    ; Detects HTMX pagination requests (page > 1)
; Example: (->> query (add-pagination 2) (db/exec! db)) ; Gets page 2 with 25 items
```

### Rate Limiting (`src/linkboard/limits.clj`)
```clojure
(wrap-rate-limit handler max-requests window-ms) ; Rate limiting middleware
(get-client-ip request)                          ; Extract client IP (proxy-aware)
(clean-expired-entries store window-ms)          ; Cleanup expired rate limit entries
; Usage: [wrap-rate-limit 10 60000] for 10 requests per minute
```

### Toast Notifications (`resources/public/js/utils.js`)
```javascript
showToast(message, type='success')               // Trigger toast notification
// Types: 'success', 'error', 'info', 'warning'
// Example: showToast('Board created successfully!')

closeModal()                                     // Close Alpine.js modals via custom event
// Dispatches 'modal-close' window event for Alpine.js listeners
```

### Infinite Scroll Components (`src/linkboard/ui/components.clj`)
```clojure
(infinite-scroll-trigger route next-page)        ; HTMX trigger with hx-trigger="revealed"
(paginated-links links has-more? route page fn)  ; Renders links + optional infinite scroll trigger
(search-bar {:search-term term :route route})    ; Live search bar with HTMX, Alpine.js, and clear functionality
; Example: (paginated-links links true "/boards/1?q=search" 2 link-list-item-fn)
; Example: (search-bar {:search-term "github" :route "/links"}) ; Shows X button to clear search
```

### URL Validation (`src/linkboard/spec.clj`)
```clojure
; Link schema with lambdaisland/uri validation - accepts URLs with or without schema
(def Link [:and [:string {:min 1}]
           [:fn {:error/message "must be a valid URL"}
            #(try
               (let [url (if (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" %) % (str "https://" %))
                     parsed (uri/uri url)]
                 (boolean (:host parsed)))
               (catch Exception _ false))]])
; Example: "example.com" -> becomes "https://example.com" and validates
; Example: "https://secure.site.com" -> validates as-is
```

### Account Creation Workflow
```clojure
; 1. User clicks Register button (generates client-side account number)
; 2. POST /create-account with account-number form field
; 3. Handler validates session, hashes account number, stores in database
; 4. Returns with identity data in session for future requests
```

## API Patterns

### Request/Response Flow
1. `wrap-auth` middleware ensures session-id exists and persists across requests
2. Reitit handles routing and parameter validation with Malli schemas
3. Handlers validate session and auto-create users via `ensure-user-exists!`
4. Database queries use HoneySQL DSL with proper user isolation
5. Views render Hiccup-style HTML with user-specific data
6. HTMX handles partial page updates and form submissions

### HTMX Out-of-Band (OOB) Patterns
- **Board Creation**: `{:hx-swap-oob "afterbegin:#board-list"}` adds new boards to list top
- **Link Creation**: `{:hx-swap-oob "afterbegin:#link-list"}` adds new links to All Links page
- **Boardless Link Redirect**: New boardless links redirect to All Links page for immediate feedback instead of OOB updates
- **Empty State Removal**: `{:hx-swap-oob "delete:#empty-boards"}` removes "No boards yet" message
- **Form Clearing**: Target form fields with `innerHTML` swap to reset forms after submission
- **Modal Integration**: Use `HX-Trigger-After-Swap` with custom events to close modals
- **Dynamic Content Updates**: Use unique element IDs (`#link-{id}`) for targeted DOM updates
- **Clickable Board Names**: Event handling with `event.preventDefault()` and `event.stopPropagation()` to prevent link conflicts
- **Infinite Scroll**: Use `hx-trigger="revealed"` and `hx-swap="outerHTML"` for seamless pagination loading

### HTMX Infinite Scroll Pattern
- **Trigger Element**: `hx-trigger="revealed"` detects when element enters viewport
- **Pagination Endpoint**: Same URL with `?page=X` parameter appends more content
- **Swap Strategy**: `hx-swap="outerHTML"` replaces trigger with new links + new trigger
- **Response Types**: Handlers detect pagination requests and return link fragments instead of full pages
- **No URL State**: Infinite scroll doesn't change browser URL or history

### Error Handling
- Schema validation with Malli
- Default error pages for 404/405/406 with home page navigation buttons
- Integrant component lifecycle management
- **HTMX Response Targets**: Use `hx-target-error` for form validation errors with proper HTTP status codes
- **Status Code Routing**: 200 responses target main elements, 400/4xx responses target form fields
- **Form Validation Flow**: Invalid submissions (400) update form fields, valid ones (200) update content
- **Board not found handling**: Returns proper 404 error page instead of crashes
- **Custom deletion messages**: Board deletions show informative content instead of immediate redirects

## Deployment

### Local Development
```bash
mise install              # Install system dependencies
bb css-watch             # Start CSS watching
bb clj-repl              # Start REPL with dev profile
```

### Production Deployment
- Kamal for container orchestration
- GitHub Actions for CI/CD
- Environment variables for secrets
- Docker containerization

## Extension Points

### Authentication
- **Fully implemented session-based authentication system**
- Automatic user creation on first interaction (empty account_number)
- Client-generated account numbers with secure bcrypt+sha512 hashing
- `wrap-auth` middleware for session-id generation and persistence
- Modal-based login/register UI with Alpine.js state management
- Account numbers formatted as XXXX-XXXX-XXXX-XXXX for usability
- Complete user isolation across all operations

### Features
- **Infinite scroll pagination** (✅ implemented with HTMX revealed triggers and 25-item pages)
- **Full-text search** (✅ implemented with SQLite FTS5, BM25 ranking, and live search with 300ms delay)
- **Account management** (✅ implemented with account page, data export, and account deletion)
- **Account number security** (✅ implemented with password-style display, show/hide toggle, and copy functionality)
- **Board and Link Favorites** (✅ implemented with star icons, priority sorting, and server-side HTMX toggle)
- **Optional Link Titles** (✅ implemented with smart conditional metadata fetching)
- **Board Management in Link Editing** (✅ implemented with dropdown selector, board reassignment, and ownership validation)
- **Global Keyboard Shortcuts** (✅ implemented with cross-platform support and smart modal management)
- **User limits** (✅ implemented with 50 board limit and 5000 link limit per user, including validation and error notifications)
- Link categorization/tagging
- Import/export capabilities (✅ CSV export implemented)
- Link sharing and collaboration

### Technical Enhancements
- WebSocket support for real-time updates
- Link preview generation
- Bulk operations
- **API rate limiting** (✅ implemented with configurable per-endpoint limits)
- **Fixed footer with Add Link button** (✅ implemented with transparent blur background)
- **Full-text search** (✅ implemented with SQLite FTS5, contentless virtual table, and BM25 ranking)
- Enhanced toast notifications for other user actions (create/update/delete)

## Development Guidelines

### Code Style
- Use single semicolon (;) for Clojure comments
- Run `bb lint` after changes (automatic formatting with pre-commit hooks)
- Run `bb test` for validation
- Follow existing kebab-case naming conventions
- Use `cond` for multi-branch conditional logic instead of nested `if` statements
- Implement comprehensive error handling with visual feedback in forms
- Validate user authorization before all board/link operations
- Use `lambdaisland/uri` for URL validation instead of deprecated `java.net.URL`
- **User limits pattern**: Use constants for limits (`DEFAULT-BOARD-LIMIT`, `DEFAULT-LINK-LIMIT`), validate early in handlers, return 200 status with error toast and modal close for consistent UX

### UI/UX Patterns
- **Modal Implementation**: Use single container with `x-cloak` and backdrop blur to prevent flickering
- **Toast Notifications**: Support both session-based (server-side) and client-side triggering
- **Copy Functionality**: Ensure clipboard operations only copy intended content, exclude UI indicators
- **Fixed Elements**: Position toast notifications and other overlays to avoid conflicts with fixed footer
- **Layout Stability**: Reserve space for dynamic elements to prevent layout shifts during animations
- **HTMX Modal Closing**: Use `HX-Trigger-After-Swap: modal-close` with Alpine.js `x-on:modal-close.window` listeners
- **Empty State Management**: Remove empty state elements using `hx-swap-oob="delete:#element-id"` when adding first items
- **Form State Management**: Clear forms using `innerHTML` swap after successful submissions
- **Dynamic Content Handling**: Use `htmx.onLoad()` for Alpine.js reinitialization on dynamically added content
- **HTMX Modal Processing**: Use `x-init="htmx.process($el)"` in teleported modals and global `hx-on:after-swap` events to ensure dynamic content HTMX attributes work properly
- **Icon Design**: Bookmark icons for link-related empty states, folder icons for board-related states
- **Navigation UX**: Enhanced back buttons with icon + text, proper padding for touch targets
- **Link Count Badges**: Rounded badges with gray background positioned on right side of headers
- **Board Name Integration**: Clickable board names on All Links page with bullet separator (•) and proper event handling
- **Extended Click Areas**: URL areas extend to edit buttons while maintaining visual clarity
- **Flexbox Alignment**: Use TailwindCSS flexbox utilities for proper left-alignment and responsive layouts
- **Search UX**: Live search with 300ms delay, keyboard shortcuts (/ or Ctrl+K), cursor positioning at end of pre-filled text
- **Clear Search UX**: X button appears when searching, ESC key clears search, smooth transition back to all results
- **URL State Management**: Search terms preserved in browser URL for bookmarking and sharing search results
- **Account Number Security**: Display as password dots by default, eye icons for show/hide toggle, consistent copy/check icon behavior
- **Mobile Account Number Layout**: Responsive text sizing, monospace fonts, whitespace-nowrap, wider modals to prevent wrapping on mobile screens
- **Favorite System**: Server-side star toggle with HTMX targeting specific icon containers, no Alpine.js state complexity
- **Star Icon UX**: Use `x-cloak` to prevent blinking, solid star (yellow) for favorites, outline star (gray) for non-favorites
- **Optional Form Fields**: Clear placeholder text indicating optional vs required fields, smart conditional processing
- **Priority Sorting**: Favorite items appear first in all listings (boards and links) with `:favorite :desc` sorting
- **Keyboard Shortcuts UX**: Use `event.code` instead of `event.key` for reliable cross-platform detection, especially with modifier keys
- **Modal Management**: Prevent stacking by dispatching `modal-close` events before opening new modals via keyboard shortcuts
- **Form Enhancement**: Board selectors with alphabetical ordering and "-------" option for standalone links
- **Error Page Navigation**: All error pages include "Go to Home Page" button for better user recovery
- **Board Deletion UX**: Custom deletion message replaces automatic redirects, provides clear next steps with home page navigation

### Testing Strategy
- **Unit tests with eftest**: Core business logic testing
- **Browser automation with etaoin**: E2E testing with Chrome/Firefox
- **Test coverage with cloverage**: Coverage analysis and reporting
- **Containerized testing environment**: Isolated test execution
- **Test organization**: 
  - `test/linkboard/home_test.clj`: Home page functionality, board creation, link creation
  - `test/linkboard/auth_test.clj`: Authentication flows (account creation, login)
  - `test/linkboard/test_utils.clj`: Test utilities and database helpers
- **Test fixtures**: Database truncation between tests, Chrome driver management
- **FTS5 Virtual Table Handling**: Tests skip FTS5 virtual table cleanup to prevent corruption
- **CI/Local Environment Differences**: Browser automation tests may have timing issues in CI due to Alpine.js loading, headless browser differences, and resource constraints
- **Test Maintenance**: Complex modal/JavaScript-dependent tests may be commented out temporarily to maintain CI stability while preserving test coverage for core functionality

### Performance Considerations
- SQLite WAL mode for concurrent access
- HikariCP connection pooling
- Static asset caching in production
- Minified CSS/JS builds
- **Efficient SQL COUNT queries**: Use database-level counting instead of in-memory collection counting for link counts
- **LEFT JOIN optimization**: Fetch board information with links in single queries for All Links page
- **Pagination Performance**: LIMIT/OFFSET queries with existing link-count calculations for smooth infinite scroll
- **FTS5 Performance**: Contentless virtual table prevents data duplication, BM25 ranking for relevance, automatic index maintenance via triggers with proper DELETE/UPDATE syntax
- **Hybrid Search Performance**: FTS5 for terms ≥3 characters (better for complex searches), LIKE for shorter terms (better for simple partial matches)
- **Query Preprocessing**: Smart FTS5 query preprocessing handles special characters, operators, and wildcards for robust search
- **Favorite Sorting Performance**: Efficient ORDER BY clauses with `:favorite :desc` prioritize starred items across all query types
- **Conditional Metadata Fetching**: Skip network requests when user provides custom titles, improving link creation performance