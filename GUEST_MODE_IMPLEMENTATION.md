# Guest Mode Implementation - Summary

## What Has Been Added

### 1. Core Files Modified

#### AuthContext.jsx
- Added `loginAsGuest()` function to create guest user session
- Added `isGuest` computed property to check if user is in guest mode
- Guest user has: `{ id: 'guest', email: 'guest@automata.local', name: 'Guest User', isGuest: true }`

#### PrivateRoute.jsx
- Updated to support guest users on allowed routes
- Added `GUEST_ALLOWED_ROUTES` array with: `['/', '/actions', '/analytics']`
- Guest users are redirected to home if trying to access restricted routes
- Takes `path` prop to validate against allowed routes

#### SignIn.jsx
- Added "Continue as Guest (Read-Only)" button
- Added `handleGuestLogin()` function
- Imported `loginAsGuest` from AuthContext

#### SideDrawer.jsx
- Created `guestItems` array with limited menu items
- Updated menu to show different items based on `isGuest` flag
- All routes now pass `path` prop to PrivateRoute for validation
- Guest users only see: Home, Automations, Analytics

#### OptionsMenu.jsx
- Guest users show "Guest User" name and orange avatar
- Added "Read-Only" chip badge for guest users
- Avatar color changes to orange (#ff9800) for guests

### 2. New Files Created

#### useIsGuest.js (hooks/)
- Custom hook for checking guest mode in any component
- Usage: `const isGuest = useIsGuest();`
- Simplifies read-only enforcement across components

#### GuestRoute.jsx (components/auth/)
- Alternative route component (currently not used but available)
- Can be used if you need different behavior from PrivateRoute

#### GUEST_MODE_GUIDE.md (frontend/)
- Comprehensive documentation for implementing guest mode
- Examples of how to disable buttons, inputs, and hide controls
- Testing and troubleshooting guide

## Routes Accessible to Guests

✅ **Allowed Routes:**
- `/` (Home/Dashboard)
- `/actions` (Automations)
- `/analytics` (Analytics)

❌ **Blocked Routes:**
- `/automation-analytics` - Automation Analytics
- `/virtual` - Virtual Device
- `/dashboard` - Dashboard Configuration
- `/devices` - Device Management
- `/configure` - System Configuration
- `/presentation` - Presentation Mode
- And any other protected routes

## How Guests Login

1. User clicks "Continue as Guest (Read-Only)" on the sign-in page
2. `loginAsGuest()` is called
3. Guest user object is created and stored in localStorage
4. User is redirected to home page
5. Guest avatar with "Read-Only" badge shows in the drawer

## Implementation in Components

To make any component respect guest mode:

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function MyComponent() {
  const isGuest = useIsGuest();

  return (
    <>
      <button disabled={isGuest}>Edit</button>
      {!isGuest && <button>Delete</button>}
    </>
  );
}
```

## Next Steps (Recommended)

1. **Update Components** to use `useIsGuest()` hook:
   - `ActionBoard.jsx` - Disable add/edit/delete automation buttons
   - `Devices.jsx` - Disable device edit/delete
   - `AnalyticsView.jsx` - Already mostly read-only, but verify
   - `DashboardV2.jsx` - Disable any write operations

2. **Backend Validation** (IMPORTANT):
   - Always verify guest status on backend API calls
   - Deny any write operations for guest users
   - Return 403 Forbidden for unauthorized actions

3. **Testing**:
   - Test each page in guest mode
   - Verify buttons are disabled
   - Try accessing restricted URLs
   - Test logout functionality

4. **UI Enhancements** (Optional):
   - Add info banner in edit forms for guest users
   - Add tooltip on disabled buttons explaining guest limitation
   - Create a welcome dialog explaining guest mode features

## File Locations Reference

- **Auth Logic**: `frontend/src/components/auth/`
  - `AuthContext.jsx` - Authentication state
  - `PrivateRoute.jsx` - Route protection
  - `SignIn.jsx` - Login page

- **Navigation**: `frontend/src/components/custom_drawer/`
  - `SideDrawer.jsx` - Main navigation with routes
  - `OptionsMenu.jsx` - User menu

- **Hooks**: `frontend/src/hooks/`
  - `useIsGuest.js` - Check guest mode

- **Documentation**: `frontend/`
  - `GUEST_MODE_GUIDE.md` - Implementation guide

## Key Features

✅ **Guest Separation** - Clear distinction between guest and authenticated users
✅ **Route Protection** - Guests blocked from sensitive routes
✅ **UI Feedback** - "Read-Only" badge shows guest status
✅ **Easy Component Integration** - Simple `useIsGuest()` hook
✅ **Session Persistence** - Guest status saved in localStorage
✅ **Clean Logout** - Guest data cleared on logout
✅ **Extensible** - Easy to add more restrictions

## Security Notes

⚠️ **Important**: This implementation provides client-side read-only protection. Always implement server-side authorization checks for:
- API endpoints that modify data
- Sensitive operations
- Resource access

Guest users should not be able to:
- Modify any data via API calls
- Access protected endpoints
- Escalate privileges

Implement proper JWT/session validation on your backend.
