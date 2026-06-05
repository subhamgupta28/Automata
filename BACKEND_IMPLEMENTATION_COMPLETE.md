# Backend Role-Based Guest Mode Implementation

## Overview

The backend has been updated to support role-based guest mode authentication. A dedicated guest user account is created on application startup with read-only access.

## Changes Made

### 1. Model Changes

#### Role.java (NEW FILE)
- Created as a separate public enum
- Added `GUEST` role alongside existing `USER` and `ADMIN`

**Location:** `src/main/java/dev/automata/automata/model/Role.java`

```java
public enum Role {
    USER,
    ADMIN,
    GUEST
}
```

#### Users.java (UPDATED)
- Role field already existed
- Removed inner enum definition
- Now uses the new Role.java file

### 2. Security Response Updates

#### AuthenticationResponse.java (UPDATED)
- Added `role` field of type `Role`
- Role is now included in all authentication responses
- Frontend can check `response.role` to determine user type

```java
private Role role;  // NEW FIELD
```

### 3. Authentication Service Updates

#### AuthenticationService.java (UPDATED)
- Imported Role enum
- Updated `register()` method to set default role as `Role.USER`
- Updated `authenticate()` method to include role in response

```java
// In register()
.role(Role.USER)

// In authenticate()
.role(user.getRole())
```

### 4. Guest User Initialization

#### GuestUserDataLoader.java (NEW FILE)
- Implements CommandLineRunner
- Runs on application startup
- Creates guest user if it doesn't exist
- Guest credentials: email: `guest@automata.local`, password: `guest`

**Features:**
- Idempotent (safe to run multiple times)
- Sets timezone and timestamp
- Uses password encoder for security

### 5. Guest Access Control

#### GuestAccessFilter.java (NEW FILE)
- Servlet Filter that enforces guest restrictions
- Blocks all non-GET requests for GUEST role users
- Returns 403 Forbidden with error message
- Allows GET requests for read-only access

**Security Logic:**
```
if (user.role == GUEST) {
    if (request.method != GET) {
        return 403 Forbidden
    }
}
```

#### DenyGuest.java (NEW FILE)
- Custom annotation for method-level protection
- Optional - provides custom error messages
- Can be applied to specific methods if needed

```java
@PostMapping("/devices")
@DenyGuest(message = "Guests cannot create devices")
public ResponseEntity<?> createDevice() { }
```

#### GuestAccessAspect.java (NEW FILE)
- AOP aspect to handle @DenyGuest annotation
- Throws AccessDeniedException for guests
- Optional - if you use @DenyGuest annotation

### 6. Documentation

#### GuestProtectionExample.java (NEW FILE)
- Shows how to use the guest protection features
- Example controller with GET/POST/PUT/DELETE endpoints
- Comments explaining each scenario

## Architecture

### How Guest Access Works

```
1. Frontend calls: POST /api/v1/auth/authenticate
   Credentials: email: guest@automata.local, password: guest

2. Backend validates credentials via AuthenticationService

3. JWT token generated with user data including role: 'GUEST'

4. Frontend stores user with role: 'GUEST'

5. Frontend makes API request with Authorization header

6. JwtAuthenticationFilter validates JWT and sets Authentication

7. GuestAccessFilter checks:
   - If user role == GUEST
   - If request method != GET
   - If both true: Return 403 Forbidden

8. Otherwise, request proceeds normally
```

### What's Protected

**Blocked for GUEST (Returns 403 Forbidden):**
- POST requests (Create operations)
- PUT requests (Update operations)
- DELETE requests (Delete operations)
- PATCH requests (Modify operations)

**Allowed for GUEST (Read-Only):**
- GET requests (List/retrieve operations)
- HEAD requests
- OPTIONS requests

## Guest User Details

**Email:** `guest@automata.local`
**Password:** `guest`
**Role:** `GUEST`
**Timezone:** `Asia/Kolkata` (default)

The guest user is created automatically when the application starts.

## Files Modified/Created

### Created (NEW)
1. ✅ `Role.java` - Public Role enum
2. ✅ `GuestUserDataLoader.java` - Guest user initialization
3. ✅ `GuestAccessFilter.java` - Request filtering for guests
4. ✅ `DenyGuest.java` - Custom annotation
5. ✅ `GuestAccessAspect.java` - AOP aspect
6. ✅ `GuestProtectionExample.java` - Example controller

### Modified (UPDATED)
1. ✅ `Users.java` - Removed inner enum, uses Role.java
2. ✅ `AuthenticationResponse.java` - Added role field
3. ✅ `AuthenticationService.java` - Added role to responses

## How to Use

### Automatic Protection (Recommended)

The `GuestAccessFilter` automatically blocks all non-GET requests for guest users. No additional code needed in your controllers!

**Example:**
```java
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    @GetMapping
    public ResponseEntity<?> getDevices() {
        // Allowed for guests - returns 200
    }

    @PostMapping
    public ResponseEntity<?> createDevice(@RequestBody Device device) {
        // Blocked for guests - returns 403
    }
}
```

### Optional: Custom Messages with @DenyGuest

For more specific error messages, use the `@DenyGuest` annotation:

```java
@PostMapping
@DenyGuest(message = "Guests cannot create devices")
public ResponseEntity<?> createDevice(@RequestBody Device device) {
    // Blocked for guests with custom message
}
```

## Testing Guest Access

### Test 1: Guest Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@automata.local",
    "password": "guest"
  }'
```

Expected Response:
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "...",
  "firstName": "Guest",
  "lastName": "User",
  "email": "guest@automata.local",
  "userId": "guest",
  "role": "GUEST"
}
```

### Test 2: Guest Read Request (Allowed)
```bash
curl -X GET http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
```

Expected: 200 OK with device list

### Test 3: Guest Write Request (Blocked)
```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Device"}'
```

Expected: 403 Forbidden
```json
{
  "error": "Guests cannot perform write operations"
}
```

## Configuration & Customization

### Changing Guest Credentials

To change guest password, update `GuestUserDataLoader.java`:

```java
.password(passwordEncoder.encode("YOUR_NEW_PASSWORD"))
```

And update frontend `guestLoginReq()` in `apis.jsx`:

```javascript
export const guestLoginReq = async () => {
    const response = await api.post("auth/authenticate", {
        email: 'guest@automata.local',
        password: 'YOUR_NEW_PASSWORD'  // Match backend
    }, ...);
    return response.data;
}
```

### Allowing Additional Guest Routes

If you want certain endpoints to be accessible to guests (besides default GET), you can:

1. Add custom logic in GuestAccessFilter
2. Create exemptions for specific paths
3. Use method-level annotations

### Disabling Guest Mode

To disable guest mode entirely:

1. Comment out `GuestUserDataLoader.java` or don't load it
2. Remove `GuestAccessFilter.java` registration
3. Remove guest login button from frontend
4. Update the guest login check on PrivateRoute.jsx

## Security Considerations

✅ **Backend Enforcement:** Guest restrictions are enforced at the filter level, not just client-side

✅ **Token Security:** Guest tokens have the same JWT validation as regular users

✅ **Audit Trail:** Guest actions are logged with the guest user ID

✅ **No Privilege Escalation:** Guests cannot modify their own role

⚠️ **Client-Side Check:** Frontend also checks role, but server is the source of truth

## Troubleshooting

### Guest login returns 401 Unauthorized
- Check that GuestUserDataLoader ran on startup (check logs)
- Verify guest user exists in MongoDB
- Check email and password are correct

### Guest can perform write operations
- Verify GuestAccessFilter is registered as @Component
- Check filter is not being bypassed
- Review logs for filter execution

### Role not showing in response
- Verify AuthenticationResponse includes role field
- Check AuthenticationService includes role in builder
- Check JWT payload includes role

### Guest user not created on startup
- Check application logs for DataLoader errors
- Verify PasswordEncoder bean is available
- Check MongoDB connection is working

## Logs to Watch For

On application startup, you should see:
```
[INFO] Guest user already exists
// or
[INFO] Guest user created successfully with email: guest@automata.local
```

On guest login attempt:
```
[DEBUG] Attempting authentication with email: guest@automata.local
[DEBUG] User authenticated successfully
[DEBUG] JWT token generated for guest user
```

On guest write attempt:
```
[DEBUG] GuestAccessFilter blocking GUEST user from method: POST
[DEBUG] Returning 403 Forbidden for guest user
```

## Next Steps

1. ✅ Backend implementation complete
2. ✅ Guest user created on startup
3. ✅ Authorization enforced via filter
4. ✅ Frontend already updated
5. Deploy and test end-to-end

---

**Backend Status: ✅ Complete and Ready to Deploy**

**Testing: See GUEST_MODE_QUICK_START.md for testing procedures**
