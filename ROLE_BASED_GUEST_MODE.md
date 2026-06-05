# Role-Based Guest Mode Implementation

## Overview

Guest Mode now uses a **role-based authentication system** where a dedicated guest user account exists on the backend. When users click "Continue as Guest", they authenticate as the guest user account, and the backend controls all permissions based on the user's role.

## Architecture

### Flow
```
User clicks "Continue as Guest"
    ↓
Frontend calls: guestLoginReq()
    ↓
API calls: POST /auth/authenticate with guest credentials
    ↓
Backend validates guest user and returns user object with role: 'guest'
    ↓
Frontend stores user in localStorage and state
    ↓
useIsGuest() checks user.role === 'guest'
    ↓
PrivateRoute enforces guest-only routes
    ↓
Backend API calls enforce permissions per role
```

## Backend Setup Required

You must create a guest user account with the following credentials:

```
Email: guest@automata.local
Password: guest
Role: guest
```

The guest role should have restrictions:
- ✅ GET requests allowed (read operations)
- ❌ POST/PUT/DELETE blocked (write operations)
- ✅ Access to: automations, analytics, dashboard
- ❌ Access denied to: device management, configuration, admin functions

## Frontend Implementation

### Core Changes

#### 1. AuthContext (apis.jsx)
Added `guestLoginReq()` function:
```javascript
export const guestLoginReq = async () => {
    const response = await api.post("auth/authenticate", {
        email: 'guest@automata.local',
        password: 'guest'
    }, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
}
```

#### 2. AuthContext (AuthContext.jsx)
- `loginAsGuest()` now calls the API
- `isGuest` checks `user.role === 'guest'`
- Async authentication with error handling

#### 3. SignIn Page (SignIn.jsx)
- "Continue as Guest" button calls async `loginAsGuest()`
- Error state for failed guest logins
- Shows error message if guest login unavailable

## How It Works

### Guest User Object Structure

When a guest logs in successfully, the user object will look like:
```javascript
{
  id: "guest-user-id",
  email: "guest@automata.local",
  firstName: "Guest",
  lastName: "User",
  role: "guest",
  // ... other fields from backend
}
```

### Detecting Guest Mode

In any component:
```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function MyComponent() {
  const isGuest = useIsGuest();
  
  // isGuest is true if user.role === 'guest'
  return (
    <button disabled={isGuest}>Edit</button>
  );
}
```

### Backend Permission Enforcement

The backend must check the user's role on every API call:

**Example (Node.js/Express):**
```javascript
function checkGuestRestrictions(req, res, next) {
  if (req.user.role === 'guest' && req.method !== 'GET') {
    return res.status(403).json({ 
      error: 'Guests cannot perform write operations' 
    });
  }
  next();
}

// Apply to protected routes
app.post('/api/devices', checkGuestRestrictions, createDevice);
app.put('/api/automation/:id', checkGuestRestrictions, updateAutomation);
```

**Example (Java/Spring):**
```java
@PreAuthorize("hasRole('ADMIN') or (hasRole('GUEST') and #request.method == 'GET')")
@PostMapping("/api/devices")
public ResponseEntity<?> createDevice(@RequestBody Device device) {
    // Implementation
}
```

## Advantages of Role-Based System

✅ **Security** - Backend controls all permissions, client-side is just UI
✅ **API Integration** - Guest user authenticates normally through API
✅ **Scalable** - Easy to add more roles and permissions
✅ **Consistent** - Same authentication flow for all users
✅ **Auditable** - Guest actions are logged with actual user account
✅ **Flexible** - Can change guest permissions without client update

## Testing Guest Mode

### 1. Verify Guest User Exists
- Check backend database for guest user with role 'guest'
- Verify email: `guest@automata.local`
- Verify password: `guest` (or as configured)

### 2. Test Guest Login
1. Navigate to sign-in page
2. Click "Continue as Guest (Read-Only)"
3. Observe:
   - User logged in with role 'guest'
   - Only 3 routes accessible
   - Orange avatar with "G"
   - "Read-Only" badge in menu

### 3. Test Route Protection
1. Try accessing `/devices` → Redirects to `/`
2. Try accessing `/configure` → Redirects to `/`
3. Verify only home, automations, analytics work

### 4. Test API Protection
1. In browser console, try: `fetch('/api/devices', {method: 'POST'})`
2. Should receive 403 Forbidden from backend
3. Verify error message displayed to user

### 5. Test Error Handling
1. Temporarily change guest credentials in apis.jsx
2. Try guest login → Should show error message
3. Verify user can try other login options

## What Changes on Backend

### User Model
Add a `role` field (if not already present):
```java
@Entity
public class User {
    @Id
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    
    @Enumerated(EnumType.STRING)
    private Role role;  // NEW FIELD - values: ADMIN, USER, GUEST
    
    // ... getters/setters
}

public enum Role {
    ADMIN, USER, GUEST
}
```

### Guest User Creation
Create guest user during system initialization or migration:
```java
User guestUser = new User();
guestUser.setId("guest");
guestUser.setEmail("guest@automata.local");
guestUser.setFirstName("Guest");
guestUser.setLastName("User");
guestUser.setPassword(passwordEncoder.encode("guest"));
guestUser.setRole(Role.GUEST);
userRepository.save(guestUser);
```

### Authorization Middleware/Annotation
Apply to endpoints that modify data:
```java
@PreAuthorize("!hasRole('GUEST')")
@PostMapping("/api/devices")
public ResponseEntity<?> createDevice(@RequestBody Device device) {
    // Only non-guests can create devices
}
```

Or check explicitly:
```java
@PostMapping("/api/automation/{id}")
public ResponseEntity<?> updateAutomation(
    @PathVariable String id,
    @RequestBody Automation automation,
    @AuthenticationPrincipal User user) {
    
    if (user.getRole() == Role.GUEST) {
        throw new AccessDeniedException("Guests cannot modify automations");
    }
    
    // Update logic
}
```

## Important Security Notes

⚠️ **CRITICAL: Backend Enforcement Required**
- Never rely on client-side checks alone
- Always verify user role on every API request
- Deny write operations for guest users
- Return proper HTTP status codes (403 Forbidden)
- Log all guest access attempts

⚠️ **Password Security**
- Guest password should be simple and non-sensitive
- Consider using environment variables for credentials
- In production, consider JWT or API keys instead of password

⚠️ **Guest Account Restrictions**
- Consider limiting guest user rate limits
- Monitor guest account activity
- Disable guest login if abuse detected
- Never grant guest user database write permissions

## Migration Path (From Old System)

If you had the old client-side guest mode:

1. ✅ Create guest user account on backend
2. ✅ Add role-based authorization
3. ✅ Update frontend with new guestLoginReq()
4. ✅ Deploy backend changes
5. ✅ Test guest login thoroughly
6. ✅ Remove old client-side isGuest logic (already done)

## Files Modified

### Frontend
- `services/apis.jsx` - Added guestLoginReq()
- `components/auth/AuthContext.jsx` - Updated to use API
- `components/auth/SignIn.jsx` - Added error handling

### Backend (Required)
- User model - Add role field
- User repository - Add guest user
- API endpoints - Add authorization checks
- Authentication service - Ensure role included in response

## Troubleshooting

**Guest login fails with "Invalid credentials"**
- Verify guest user exists in backend database
- Check email: `guest@automata.local`
- Check password matches configuration
- Verify guest role is set correctly

**Guest can access restricted routes**
- Check PrivateRoute.jsx - guest routes are ['/', '/actions', '/analytics']
- Verify backend user.role includes 'guest' in response
- Clear browser cache and localStorage
- Check browser console for errors

**Guest can modify data**
- Backend is not enforcing guest restrictions
- Check authorization middleware is applied
- Verify role check is working
- Review API logs for guest requests

**"Read-Only" badge not showing**
- Check user.role value in console
- Verify isGuest computed property returns true
- Check OptionsMenu.jsx styling
- Ensure Avatar component has guest styling

## Examples

See `COMPONENT_IMPLEMENTATION_EXAMPLES.md` for code examples using `useIsGuest()` hook.
