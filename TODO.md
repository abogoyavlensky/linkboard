# Full-Text Search Implementation Plan

## 1. Database Migration (0005.up.sql)
- Create **contentless** FTS5 virtual table `link_search` with `content=""` option
- Index `title` and `url` columns with explicit `content_rowid` mapping to `link.id`
- Add triggers to maintain FTS5 index on INSERT/UPDATE/DELETE operations
- Populate initial FTS5 data from existing links

## 2. Backend Implementation

### Enhanced Query Functions (`src/linkboard/queries.clj`)
- Modify existing `all-links-handler` and `board-handler` queries to support FTS5 search
- Add conditional FTS5 JOIN when `q` parameter is present
- Use FTS5 `MATCH` operator with `rank` ordering for relevance
- Maintain user isolation and existing pagination patterns

### Enhanced Handlers (`src/linkboard/board/handlers.clj`)
- Modify `all-links-handler` to check for `q` parameter and switch to FTS5 query
- Modify `board-handler` to support search within specific board links
- Preserve existing 3-response pattern (full page, HTMX page, pagination fragment)
- Include search term in pagination URLs and result metadata

### Route Parameter Updates (`src/linkboard/routes.clj`)
- Add optional `q` parameter to existing `/links` and `/boards/:id` routes
- Use Malli schema validation for search query parameter
- Maintain existing pagination support with search context

## 3. Frontend Enhancement

### Enhanced Search Bar (`src/linkboard/ui/components.clj`)
- Add HTMX attributes to existing search-bar component:
  - Dynamic `hx-get` targeting current page route
  - `hx-trigger="input changed delay:300ms, search"`
  - `hx-include="[name='q']"` for search input
  - `hx-push-url="true"` for URL state management

### Search Integration (`src/linkboard/board/views.clj`)
- Enhance existing views to display search context when `q` parameter exists
- Add search result count and "clear search" functionality
- Modify existing pagination components to preserve search terms
- Reuse all existing link display patterns and infinite scroll

## 4. Key Benefits
- **No new routes** - extends existing `/links` and `/boards/:id` endpoints
- **Contentless FTS5** - efficient storage without data duplication
- **Seamless integration** - preserves all existing UI patterns and pagination
- **URL state** - search terms appear in browser URL for bookmarking/sharing