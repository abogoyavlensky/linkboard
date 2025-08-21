# Account Page Implementation Plan

## 1. Account Page Infrastructure
**Files to create:**
- `src/linkboard/account/handlers.clj` - Account page handlers
- `src/linkboard/account/views.clj` - Account page UI
- Add account routes to `src/linkboard/routes.clj`

## 2. Account Page (`GET /account`)
**Handler**: Display account page for registered users
**View**: Simple page with:
- Account info section (registration date, session info - no account number displayed)
- Export data to CSV button
- Logout button (moved from header)
- Delete account button (with confirmation modal)

## 3. Export Data to CSV (`GET /account/export`)
**Handler**: 
- Query all user's boards and links
- Format as CSV: `board_title,link_title,url,created_at`
- Return as downloadable file with proper headers
- Security: Validate user is registered and owns the data

## 4. Delete Account (`DELETE /account`)
**Handler**:
- CASCADE delete user (automatically deletes all boards and links)
- Clear session
- Redirect to home page with toast
**UI**: Confirmation modal with strong warnings about permanent data loss

## 5. Header Updates
- **Remove logout button** from header (move to account page)
- **Account button action**: Navigate to `/account` using `hx-get`

This leverages existing auth patterns, moves logout to account page, and provides clean account management for registered users.