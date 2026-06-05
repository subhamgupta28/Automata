# Guest Mode - Implementation Update Summary

## Change Overview

The guest mode implementation has been refactored from a **simple client-side flag** to a **proper role-based authentication system**.

### Why This Change?

**Old Approach (Client-Side Only):**
- ❌ No API integration
- ❌ All APIs still work for guests (no backend enforcement)
- ❌ Fake guest user object
- ❌ Security relies on client-side checks only

**New Approach (Role-Based):**
- ✅ Real guest user account on backend
- ✅ API-based authentication
- ✅ Backend enforces all permissions
- ✅ Scalable and maintainable
- ✅ Proper audit trail
- ✅ Can extend to other roles easily

## What Changed

### Frontend Changes

#### 1. AuthContext.jsx
**Before:**
```javascript
const loginAsGuest = () => {
    const guestUser = {
        id: 'guest',
        email: 'guest@automata.local',
        name: 'Guest User',
        isGuest: true
    };
    login(guestUser);
};

const isGuest = user?.isGuest === true;
```

**After:**
```javascript
const loginAsGuest = async () => {
    try {
        const guestUser = await guestLoginReq();
        login(guestUser);
        return guestUser;
    } catch (error) {
        console.error("Guest login failed:", error);
        throw error;
    }
};

const isGuest = user?.role?.toLowerCase() === 'guest';
```

**What Changed:**
- `loginAsGuest()` is now async and calls API
- Checks `user.role === 'guest'` instead of `isGuest` flag
- Proper error handling
- User object comes from backend

#### 2. apis.jsx
**Added:**
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

#### 3. SignIn.jsx
**Before:**
```javascript
const handleGuestLogin = () => {
    loginAsGuest();
    navigate("/");
};
```

**After:**
```javascript
const [guestError, setGuestError] = React.useState(false);
const [guestErrorMessage, setGuestErrorMessage] = React.useState('');

const handleGuestLogin = async () => {
    try {
        setGuestError(false);
        setGuestErrorMessage('');
        await loginAsGuest();
        navigate("/");
    } catch (error) {
        console.error('Guest login failed:', error);
        setGuestError(true);
        setGuestErrorMessage('Guest login unavailable. Please try again later or sign up.');
    }
};
```

**What Changed:**
- Now async
- Added error state
- Shows error message if guest login fails
- Can't just redirect if login fails

#### 4. PrivateRoute.jsx
**No changes needed** - Already supports guest role checks

#### 5. OptionsMenu.jsx
**No changes needed** - Already displays guest badge with role

### Frontend - No Changes Needed
Files that are already compatible:
- `useIsGuest.js` hook - Still works (checks role)
- `SideDrawer.jsx` - Still works (uses isGuest from auth)
- `GuestRoute.jsx` - Still available if needed
- Navigation menu filtering - Still works

### Backend Changes (You Need to Do These)

#### 1. User Model
Add `role` field:
```java
@Enumerated(EnumType.STRING)
private Role role = Role.USER;
```

#### 2. Create Guest User
Insert guest account:
```sql
INSERT INTO users (id, email, password, first_name, last_name, role) 
VALUES ('guest', 'guest@automata.local', '$2a$10$...', 'Guest', 'User', 'GUEST');
```

#### 3. Include Role in Auth Response
```json
{
  "token": "...",
  "user": {
    "id": "guest",
    "email": "guest@automata.local",
    "firstName": "Guest",
    "lastName": "User",
    "role": "GUEST"
  }
}
```

#### 4. Protect Endpoints
Block GUEST role from write operations:
```java
@PreAuthorize("!hasRole('GUEST')")
@PostMapping("/api/devices")
public ResponseEntity<?> createDevice() { }
```

## Migration Checklist

### Frontend
- [x] Update AuthContext to call API
- [x] Update SignIn with error handling
- [x] Update apis.jsx with guestLoginReq()
- [x] Update PrivateRoute (already works)
- [x] Update OptionsMenu (already works)
- [x] Update useIsGuest hook (already works)

### Backend (Required)
- [ ] Add role field to User model
- [ ] Create guest user account (email: guest@automata.local)
- [ ] Include role in auth response
- [ ] Add authorization checks to endpoints
- [ ] Test guest login
- [ ] Test guest restrictions

### Documentation
- [x] GUEST_MODE_QUICK_START.md - Updated with role-based info
- [x] ROLE_BASED_GUEST_MODE.md - New comprehensive guide
- [x] BACKEND_GUEST_MODE_SETUP.md - New backend setup guide
- [x] COMPONENT_IMPLEMENTATION_EXAMPLES.md - Already exists
- [x] GUEST_MODE_GUIDE.md - Still valid

## Key Differences

| Aspect | Old | New |
|--------|-----|-----|
| User Account | Fake client object | Real backend account |
| Authentication | No API call | API-based login |
| Role Detection | `isGuest: true` flag | `role: 'guest'` from backend |
| API Security | None (client-side) | Backend enforces via role checks |
| Error Handling | None | Error message on failed login |
| Permissions | Client-side only | Server-side enforced |
| Audit Trail | None | Guest actions logged |
| Scalability | Limited | Can add more roles easily |

## How It Works Now

```
1. User clicks "Continue as Guest"
   ↓
2. Frontend calls guestLoginReq()
   ↓
3. API: POST /auth/authenticate with guest credentials
   ↓
4. Backend validates guest user (email: guest@automata.local)
   ↓
5. Backend returns user object with role: 'guest'
   ↓
6. Frontend stores in localStorage and state
   ↓
7. useIsGuest() returns true (user.role === 'guest')
   ↓
8. PrivateRoute allows only home, automations, analytics
   ↓
9. API calls work through normal auth flow
   ↓
10. Backend checks role: if 'GUEST' and POST/PUT/DELETE → 403 Forbidden
    ↓
11. Frontend shows error message to user
```

## Important Notes

### For Frontend Developers
- Guest mode now actually works with APIs
- Still use `useIsGuest()` hook to check if guest
- Still disable UI elements with `disabled={isGuest}`
- Guest login may fail if backend not configured
- Show error messages if guest login fails

### For Backend Developers
- **CRITICAL:** Must implement role-based authorization
- Guest user account must exist with exact email: `guest@automata.local`
- Role field must be included in auth response
- All write operations (POST/PUT/DELETE) must deny GUEST role
- Must apply authorization consistently across all endpoints

### For DevOps/Infrastructure
- Guest password is simple ('guest') - this is acceptable for public read-only access
- Consider environment variables for sensitive credentials
- In production, monitor guest account activity
- May want rate limiting on guest endpoints
- Ensure HTTPS is enabled

## Breaking Changes

❌ **This is a breaking change for frontend:**
- Old hardcoded guest login won't work
- Must update to new async guestLoginReq()
- ✅ Already done in this implementation

✅ **Backward compatible:**
- Regular user login unchanged
- JWT tokens still work
- API endpoints unchanged (except authorization)

## What If Backend Isn't Ready?

If your backend doesn't have role-based access yet:

**Option 1: Temporary Workaround**
```javascript
export const guestLoginReq = async () => {
    // For testing only - use a real user account temporarily
    return signInReq({
        email: 'demo@example.com',
        password: 'demopass'
    });
};
```

**Option 2: Manual Role Addition**
```javascript
const guestLoginReq = async () => {
    const user = await signInReq({
        email: 'guest@automata.local',
        password: 'guest'
    });
    user.role = 'GUEST'; // Manually set role
    return user;
};
```

But **do NOT use in production** - implement proper backend setup.

## Testing After Migration

### Test Case 1: Guest Login Works
```
1. Go to /signin
2. Click "Continue as Guest"
3. Should redirect to /
4. User object has role: 'guest'
```

### Test Case 2: Guest Routes Protected
```
1. Logged in as guest
2. Navigate to /devices
3. Should redirect to /
4. Try accessing /devices via URL bar
5. Should redirect to /
```

### Test Case 3: Guest API Access
```
1. Open browser console
2. GET request to /api/devices → 200 OK
3. POST request to /api/devices → 403 Forbidden
```

### Test Case 4: Guest Login Fails
```
1. Temporarily break guestLoginReq() API call
2. Click "Continue as Guest"
3. Should show error message
4. User remains on /signin
```

## Documentation Files Reference

| File | Purpose |
|------|---------|
| GUEST_MODE_QUICK_START.md | Start here - overview and testing |
| ROLE_BASED_GUEST_MODE.md | Architecture and how it works |
| BACKEND_GUEST_MODE_SETUP.md | Step-by-step backend setup guide |
| GUEST_MODE_GUIDE.md | Component implementation patterns |
| COMPONENT_IMPLEMENTATION_EXAMPLES.md | Code examples for components |

## Questions?

**Frontend Question:** See GUEST_MODE_GUIDE.md or COMPONENT_IMPLEMENTATION_EXAMPLES.md

**Backend Question:** See BACKEND_GUEST_MODE_SETUP.md

**Architecture Question:** See ROLE_BASED_GUEST_MODE.md

**Quick Overview:** See GUEST_MODE_QUICK_START.md

---

**Status: Frontend ✅ Complete | Backend ⚠️ Required**
