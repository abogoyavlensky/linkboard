# Add Favorite Boards Feature

## Overview
Add ability to mark boards as favorites with star icons that turn yellow when clicked, and sort favorites first on the main page.

## Implementation Steps

### 1. Database Changes
- **Create Migration (`0006.up.sql`)**: Add `favorite` BOOLEAN column to `board` table with DEFAULT FALSE
- **Update existing boards**: Set all existing boards to `favorite = FALSE`

### 2. Backend Changes

#### Database Queries (`src/linkboard/queries.clj`)
- Add `toggle-board-favorite!` function to toggle board favorite status
- Update board ordering in `home-handler` to sort by favorite status first, then created_at

#### Routes (`src/linkboard/routes.clj`) 
- Add new PATCH route: `/boards/:id/favorite` for toggling favorite status

#### Handler (`src/linkboard/home/handlers.clj`)
- Add `toggle-board-favorite-handler` function with user ownership validation
- Update board query in `home-handler` to use: `:order-by [[:b.favorite :desc] [:b.created-at :desc]]`

### 3. Frontend Changes

#### Icons (`src/linkboard/ui/icons.clj`)
- Add `star` icon (unfavorited - outlined)
- Add `star-solid` icon (favorited - filled yellow)

#### Views (`src/linkboard/home/views.clj`) 
- Update `list-item` component to include star icon before folder icon
- Add click handler for star with HTMX request to toggle favorite
- Use Alpine.js to provide immediate visual feedback (star color change)
- Implement conditional rendering: `star-solid` (yellow) if favorited, `star` (gray) if not

### 4. HTMX Integration
- Star click triggers PATCH request to `/boards/:id/favorite`
- Response includes updated board data for OOB swap
- Use `hx-swap-oob` to update the specific board item without full page reload
- Add toast notification for favorite toggle success

## Key Implementation Details
- **Security**: All board favorite operations validate user ownership via `user-owns-board?`
- **UX**: Immediate visual feedback with Alpine.js before server response
- **Performance**: Minimal database impact with simple BOOLEAN column and efficient ordering
- **Consistency**: Follows existing patterns for HTMX interactions and toast notifications