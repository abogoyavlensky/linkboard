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
  - `POST /create-account` - Account creation endpoint
  - `POST /boards` - Create new board
  - `GET /boards/:id` - Board details with links
  - `POST /boards/:id/links` - Add link to board
  - `PUT/DELETE` operations for boards and links
- `wrap-auth` middleware for session-id management
- Malli schema validation for request parameters

#### Handlers
- **Home handlers** (`src/linkboard/home/handlers.clj`): Board listing and creation
- **Board handlers** (`src/linkboard/board/handlers.clj`): Link management within boards
- Hardcoded USER_ID=1 (authentication not implemented)

#### Link Metadata Fetching (`src/linkboard/board/fetch.clj`)
- Automatic title and icon extraction from URLs
- HTTP client with Hickory for HTML parsing
- Favicon detection and processing

#### Account Management (`src/linkboard/ui/components.clj` + `resources/public/js/utils.js`)
- Client-side account number generation using `crypto.randomUUID()`
- 16-character alphanumeric IDs with dashes every 4 characters (format: XXXX-XXXX-XXXX-XXXX)
- Fallback to `Math.random()` for older browsers
- Alpine.js integration for modal state management
- One-time display warning for account security
- Automatic clipboard copy functionality with visual feedback

## Dependencies

### Core Dependencies (deps.edn)
```clojure
org.clojure/clojure "1.12.1"           ; Core language
integrant/integrant "0.13.1"           ; System management
metosin/reitit-ring "0.9.1"           ; Routing
ring/ring-jetty-adapter "1.14.2"      ; Web server
org.xerial/sqlite-jdbc "3.50.3.0"     ; SQLite driver
com.github.seancorfield/next.jdbc "1.3.1048"  ; Database access
com.github.seancorfield/honeysql "2.7.1325"   ; SQL DSL
hikari-cp/hikari-cp "3.3.0"           ; Connection pooling
clj-http/clj-http "3.13.1"            ; HTTP client
org.clj-commons/hickory "0.7.7"       ; HTML parsing
```

### Development Tools
```clojure
eftest/eftest "0.6.0"                 ; Test runner
etaoin/etaoin "1.1.43"                ; Browser automation
cloverage/cloverage "1.2.4"           ; Test coverage
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
├── routes.clj           # Route definitions
├── queries.clj          # Database queries
├── home/               # Home page functionality
│   ├── handlers.clj
│   └── views.clj
├── board/              # Board management
│   ├── handlers.clj
│   ├── views.clj
│   └── fetch.clj       # Link metadata fetching
├── ui/                 # UI components
│   ├── components.clj
│   └── icons.clj
└── utils/

resources/
├── config.edn          # System configuration
├── migrations/         # Database migrations
└── public/            # Static assets
    ├── css/
    ├── js/
    │   └── utils.js     # Client-side utilities (account ID generation)
    └── images/

test/                  # Test files
```

## Database Schema

### Tables
1. **board**: User's bookmark collections
   - id, title, user_id, created_at
2. **link**: Individual bookmarks
   - id, url, title, icon, board_id, created_at

### Migrations
- `0001.up.sql`: Initial schema
- `0002.up.sql`: Additional constraints
- `0003.up.sql`: Schema updates

## API Patterns

### Request/Response Flow
1. Reitit handles routing and parameter validation
2. Handlers extract database and request context
3. Database queries use HoneySQL DSL
4. Views render Hiccup-style HTML
5. HTMX handles partial page updates

### Error Handling
- Schema validation with Malli
- Default error pages for 404/405/406
- Integrant component lifecycle management

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
- Account-based system with client-generated account numbers
- `wrap-auth` middleware for session-id management and persistence
- Session management infrastructure for cross-request persistence
- Modal-based login/register UI with Alpine.js state management
- Account numbers formatted as XXXX-XXXX-XXXX-XXXX for usability

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
- API rate limiting

## Development Guidelines

### Code Style
- Use single semicolon (;) for Clojure comments
- Run `bb lint` after changes
- Run `bb test` for validation
- Follow existing kebab-case naming conventions

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