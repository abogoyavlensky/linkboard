# Linkboard - Project Summary

## Overview
Linkboard is a self-hosted personal bookmark manager built with Clojure, SQLite, HTMX, AlpineJS, and TailwindCSS. It provides a clean web interface for organizing bookmarks into boards with automatic metadata fetching for links.

## Key Features
- Personal bookmark management with board organization
- Automatic link metadata extraction (title, icons)
- PWA-ready with modern web app icons
- Account-based authentication with auto-generated account numbers
- Client-side account number generation using crypto.randomUUID()
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
  - `GET /` - Home page with board list
  - `POST /create-account` - Account creation endpoint (rate limited: 5/min per IP)
  - `POST /boards` - Create new board
  - `GET /boards/:id` - Board details with links
  - `POST /links` - Create link (can be associated with board or standalone)
  - `PUT/DELETE` operations for boards and links
  - `POST /login` - User login endpoint (rate limited: 20/min per IP)
- `wrap-auth` middleware for automatic session-id generation and persistence
- **Rate limiting middleware** (`src/linkboard/limits.clj`): Global protection (200 requests/min per IP) with endpoint-specific limits
- Malli schema validation for request parameters

#### Handlers
- **Home handlers** (`src/linkboard/home/handlers.clj`): Board listing, creation, account management, and login/logout
- **Board handlers** (`src/linkboard/board/handlers.clj`): Link management within boards with comprehensive security validation
- **Session-based user management**: All handlers validate session and auto-create users as needed  
- **Account creation workflow**: Secure registration with bcrypt+sha512 password hashing
- **Security patterns**: All board/link operations validate user ownership using `user-owns-board?` function
- **Error handling**: Form validation with error display and user-friendly 403 responses for unauthorized access
- **Conditional logic**: Handlers use `cond` for clean multi-branch decision making (validation, authorization, success)

#### Link Metadata Fetching (`src/linkboard/board/fetch.clj`)
- Automatic title and icon extraction from URLs
- HTTP client with Hickory for HTML parsing
- Favicon detection and processing

#### User Management System (`src/linkboard/queries.clj` + handlers)
- **Automatic user creation**: Users created on first interaction with empty account_number
- **Session-based identification**: All operations tied to session_id from `wrap-auth` middleware
- **Account registration**: Users can later register with account numbers for persistence
- **Secure password storage**: Account numbers hashed with bcrypt+sha512 algorithm
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

#### Account Number UX (`src/linkboard/ui/components.clj`)
- **Enhanced copy functionality** prevents checkmark icon from being copied to clipboard
- External circled checkmark indicator positioned right of account number container
- Reserved space layout prevents text container from resizing when checkmark appears
- Satisfying scale animation (`scale-0` to `scale-100`) for visual feedback
- Improved warning message: "Save this account number now! It's shown only once and cannot be recovered if lost."

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
│   └── fetch.clj        # Link metadata fetching
├── ui/                 # UI components
│   ├── components.clj   # Base layout, modals, login forms, toast notifications, error handling, fixed footer
│   └── icons.clj
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
   - id, title, user_id, created_at
3. **link**: Individual bookmarks
   - id, url, title, icon, board_id, created_at

### Migrations
- `0001.up.sql`: Initial schema
- `0002.up.sql`: User table creation with indexes
- `0003.up.sql`: Sample data insertion
- `0004.up.sql`: Performance indexes for session_id and account_number

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
(user-owns-board? db {:board-id board-id :session-id session-id}) ; Check board ownership
(user-owns-link? db {:link-id link-id :session-id session-id}) ; Check link ownership via JOIN
(delete-link! db {:link-id link-id :user-id user-id}) ; Delete link with user validation
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

### URL Validation (`src/linkboard/spec.clj`)
```clojure
; Link schema with lambdaisland/uri validation
(def Link [:and [:string {:min 1}]
           [:fn {:error/message "must be a valid URL"}
            #(try
               (let [parsed (uri/uri %)]
                 (boolean (:host parsed)))
               (catch Exception _ false))]])
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
- **Empty State Removal**: `{:hx-swap-oob "delete:#empty-boards"}` removes "No boards yet" message
- **Form Clearing**: Target form fields with `innerHTML` swap to reset forms after submission
- **Modal Integration**: Use `HX-Trigger-After-Swap` with custom events to close modals
- **Dynamic Content Updates**: Use unique element IDs (`#link-{id}`) for targeted DOM updates

### Error Handling
- Schema validation with Malli
- Default error pages for 404/405/406
- Integrant component lifecycle management
- **HTMX Response Targets**: Use `hx-target-error` for form validation errors with proper HTTP status codes
- **Status Code Routing**: 200 responses target main elements, 400/4xx responses target form fields
- **Form Validation Flow**: Invalid submissions (400) update form fields, valid ones (200) update content

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
- Pagination for large link collections
- Search functionality
- Link categorization/tagging
- Import/export capabilities
- Link sharing and collaboration

### Technical Enhancements
- WebSocket support for real-time updates
- Full-text search with SQLite FTS
- Link preview generation
- Bulk operations
- **API rate limiting** (✅ implemented with configurable per-endpoint limits)
- **Fixed footer with Add Link button** (✅ implemented with transparent blur background)
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
- **Icon Design**: Bookmark icons for link-related empty states, folder icons for board-related states
- **Navigation UX**: Enhanced back buttons with icon + text, proper padding for touch targets

### Testing Strategy
- Unit tests with eftest
- Browser automation with etaoin
- Test coverage with cloverage
- Containerized testing environment

### Performance Considerations
- SQLite WAL mode for concurrent access
- HikariCP connection pooling
- Static asset caching in production
- Minified CSS/JS builds