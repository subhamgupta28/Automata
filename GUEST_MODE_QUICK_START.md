# Guest Mode - Quick Start Guide (Role-Based)

## ✅ What's Been Done (Frontend)

The guest mode feature has been refactored to use **role-based authentication**:

### Core Features
- ✅ Guest login via dedicated guest user account
- ✅ API-based authentication with `guestLoginReq()`
- ✅ Role-based access control (role: 'guest')
- ✅ Route protection - guests only see Home, Automations, and Analytics
- ✅ Navigation menu filtered by user role
- ✅ Guest user badge with "Read-Only" indicator
- ✅ Custom `useIsGuest()` hook checking `user.role === 'guest'`
- ✅ Error handling for failed guest logins

### Routes
**Guest can access:**
- `/` - Home (Dashboard)
- `/actions` - Automations
- `/analytics` - Analytics

**Guest cannot access:**
- `/virtual` - Virtual Device
- `/dashboard` - Dashboard Config
- `/devices` - Device Management
- `/configure` - System Configuration
- All other protected routes

## ⚠️ Backend Setup Required

You MUST create a guest user account with these credentials:

```
Email: guest@automata.local
Password: guest
Role: guest
```

**Example SQL:**
```sql
INSERT INTO users (id, email, password, first_name, last_name, role) 
VALUES ('guest', 'guest@automata.local', '$2a$10$...', 'Guest', 'User', 'GUEST');
```

**Example Java/Spring:**
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

## 🚀 How to Test Guest Mode

1. **Ensure guest user exists on backend** with role 'guest'
2. **Start the application**
3. **Navigate to Sign In page** (`/signin`)
4. **Click "Continue as Guest (Read-Only)"** button
5. **Observe:**
   - User authenticates with guest credentials
   - Only 3 menu items appear: Home, Automations, Analytics
   - User avatar shows "G" with orange color
   - "Read-Only" badge appears next to username
   - User object has `role: 'guest'`
6. **Try accessing restricted URL** (e.g., `/devices`) → redirects to home
7. **Click Logout** in user menu to exit guest mode

## 🔐 Backend Security (Critical!)

All API endpoints must enforce guest restrictions:

**Pattern 1: Annotation-based (Spring)**
```java
@PreAuthorize("!hasRole('GUEST')")
@PostMapping("/api/devices")
public ResponseEntity<?> createDevice(@RequestBody Device device) {
    // Implementation
}
```

**Pattern 2: Middleware-based**
```javascript
function checkGuestRestrictions(req, res, next) {
  if (req.user.role === 'guest' && req.method !== 'GET') {
    return res.status(403).json({ error: 'Guests cannot perform write operations' });
  }
  next();
}
```

**Pattern 3: Explicit check**
```java
if (user.getRole() == Role.GUEST && !request.getMethod().equals("GET")) {
    throw new AccessDeniedException("Guests cannot modify data");
}
```

### API Endpoints Requiring Protection

- ❌ POST `/api/devices` - Create device (block guests)
- ❌ PUT `/api/devices/*` - Edit device (block guests)
- ❌ DELETE `/api/devices/*` - Delete device (block guests)
- ❌ POST `/api/automation/*` - Create automation (block guests)
- ❌ PUT `/api/automation/*` - Edit automation (block guests)
- ❌ DELETE `/api/automation/*` - Delete automation (block guests)
- ❌ POST `/api/configure/*` - Any configuration (block guests)
- ✅ GET `/api/**` - All read operations (allow guests)

## 📝 To Make Components Read-Only (Next Step)

Components should disable write operations. The backend will also enforce this, but client-side UX is important too.

### Example: Disable Edit Button
```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function DeviceCard({ device }) {
  const isGuest = useIsGuest();

  return (
    <div>
      <h3>{device.name}</h3>
      <button disabled={isGuest}>
        Edit
      </button>
    </div>
  );
}
```

### Example: Hide Delete Button
```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function ActionItem({ action }) {
  const isGuest = useIsGuest();

  return (
    <div>
      {!isGuest && (
        <button onClick={() => deleteAction(action.id)}>
          Delete
        </button>
      )}
    </div>
  );
}
```

## 🔧 Components That Should Be Updated

To complete the guest mode integration, update these components:

1. **ActionBoard.jsx** - Disable create/edit/delete automation buttons
2. **Devices.jsx** - Disable device add/edit/delete
3. **DashboardV2.jsx** - Disable dashboard configuration
4. **AnalyticsView.jsx** - Already mostly read-only

**Pattern:** Add `const isGuest = useIsGuest()` and wrap buttons with `disabled={isGuest}`.

## 📚 Documentation Files

- **ROLE_BASED_GUEST_MODE.md** - Complete architecture and backend setup guide
- **GUEST_MODE_GUIDE.md** - Implementation patterns
- **COMPONENT_IMPLEMENTATION_EXAMPLES.md** - Code examples

## 🎯 Current Status

| Feature | Frontend | Backend | Notes |
|---------|----------|---------|-------|
| Guest Login API | ✅ Done | ⚠️ TODO | Need guest user account + auth endpoint |
| Route Protection | ✅ Done | N/A | Client-side only |
| Menu Filtering | ✅ Done | N/A | Client-side only |
| User Badge | ✅ Done | N/A | Client-side only |
| useIsGuest Hook | ✅ Done | N/A | Checks role from user object |
| API Permission Check | N/A | ⚠️ TODO | CRITICAL - Block guest write ops |
| Component Updates | ⚠️ TODO | N/A | Disable UI elements for guests |

## Key Changes from Old System

**Old System:**
- Client-side only flag `isGuest: true`
- No API integration
- Fake guest user object

**New System:**
- Real guest user account on backend
- API-based authentication
- Role-based permissions
- Backend enforces all restrictions
- Scalable and secure

## 💡 Tips for Backend Implementation

1. **Create Guest User First** - Do this before testing
2. **Add Role Field** - If not already in User model
3. **Apply Authorization** - Check role on every write operation
4. **Return User Object** - Include role in auth response
5. **Log Access** - Track guest user activity
6. **Test Thoroughly** - Ensure guest can't bypass restrictions

## ❓ FAQ

**Q: What's the guest password?**
A: `guest` (simple because it's for public access)

**Q: Where is the guest login API?**
A: `guestLoginReq()` in `frontend/src/services/apis.jsx` - calls `POST /auth/authenticate` with guest credentials

**Q: How does the backend know it's a guest?**
A: The guest user account has `role: 'guest'` which is returned in the auth response

**Q: Can I change the guest password?**
A: Yes, update both backend and the guestLoginReq() function in apis.jsx

**Q: What if backend doesn't have roles yet?**
A: Add a role field to your User model and update the guest user creation

**Q: Is guest mode secure?**
A: Frontend restrictions are just UX. Backend MUST enforce permissions via role checks.

---

**IMPORTANT: See ROLE_BASED_GUEST_MODE.md for complete backend setup instructions!**
