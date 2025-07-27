// Generate account ID using crypto.randomUUID() or fallback
function generateAccountId() {
  let id;
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    id = crypto.randomUUID().replace(/-/g, '').substring(0, 16).toUpperCase();
  } else {
    // Fallback for older browsers
    id = Math.random().toString(36).substring(2, 18).toUpperCase();
  }
  
  // Add dashes every 4 characters
  return id.replace(/(.{4})/g, '$1-').slice(0, -1);
}
