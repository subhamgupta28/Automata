# Guest Mode Implementation - Testing Guide

## Quick Test Summary (5-10 minutes)

### Prerequisites
- Backend running on http://localhost:8080
- Frontend running on http://localhost:5173
- MongoDB running and connected

---

## Test 1: Backend Guest User Creation ✓

**Objective:** Verify guest user is created automatically on startup

**Steps:**
1. Start the backend application
2. Check logs for:
   ```
   [INFO] Creating guest user...
   // or
   [INFO] Guest user already exists
   ```
3. Connect to MongoDB:
   ```bash
   mongosh  # or mongo
   use automata_db  # or your database name
   db.users.find({ email: "guest@automata.local" })
   ```
4. Should see guest user document

**Expected Result:** ✅ Guest user exists in database with:
- email: guest@automata.local
- firstName: Guest
- lastName: User
- role: GUEST
- password: bcrypt encoded

---

## Test 2: Guest Login API ✓

**Objective:** Verify guest can authenticate via API

**Command:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@automata.local",
    "password": "guest"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "firstName": "Guest",
  "lastName": "User",
  "email": "guest@automata.local",
  "userId": "guest",
  "role": "GUEST",
  "message": "User logged in successfully"
}
```

**Success Indicators:**
- ✅ Status 200 OK
- ✅ `role` field is "GUEST"
- ✅ accessToken is not empty
- ✅ User details are correct

**Troubleshooting:**
- If 401: Check guest user exists in database
- If role is null: Check AuthenticationService was updated
- If 500: Check logs for database errors

---

## Test 3: Guest Read Access (GET) ✓

**Objective:** Verify guest can read data

**Get Token:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@automata.local",
    "password": "guest"
  }' | jq -r '.accessToken')

echo $TOKEN
```

**Test GET Request:**
```bash
curl -X GET http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Expected Response:**
- ✅ Status 200 OK
- ✅ Returns list of devices (or empty list)
- ✅ No 403 Forbidden error

**Success Indicators:**
- GET request succeeds
- Guest can read data
- Data is returned correctly

---

## Test 4: Guest Write Block (POST) ✓

**Objective:** Verify guest cannot create data

**Test POST Request:**
```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Device",
    "type": "light"
  }'
```

**Expected Response:**
- ✅ Status 403 Forbidden
- ✅ Error message: "Guests cannot perform write operations"

```json
{
  "error": "Guests cannot perform write operations"
}
```

**Success Indicators:**
- POST returns 403 (not 200)
- Error message is clear
- Guest cannot create anything

---

## Test 5: Guest Other Write Operations ✓

**Objective:** Verify all non-GET methods are blocked

**Test PUT:**
```bash
curl -X PUT http://localhost:8080/api/v1/devices/123 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Updated"}'
```

**Expected:** ✅ 403 Forbidden

**Test DELETE:**
```bash
curl -X DELETE http://localhost:8080/api/v1/devices/123 \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** ✅ 403 Forbidden

**Test PATCH:**
```bash
curl -X PATCH http://localhost:8080/api/v1/devices/123 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "off"}'
```

**Expected:** ✅ 403 Forbidden

**Success Indicators:**
- All write methods blocked
- Consistent 403 responses
- No data was modified

---

## Test 6: Regular User Can Still Write ✓

**Objective:** Verify regular users are not affected

**Login as Regular User:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your.email@example.com",
    "password": "your_password"
  }'
```

**Test POST with Regular User Token:**
```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $REGULAR_USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Device",
    "type": "light"
  }'
```

**Expected:**
- ✅ Status 200/201 Success (not 403)
- ✅ Device is created
- ✅ Regular users unaffected

---

## Test 7: Frontend Guest Login ✓

**Objective:** Verify frontend guest button works

**Steps:**
1. Open http://localhost:5173 in browser
2. Should see Sign In page
3. Look for "Continue as Guest" button
4. Click the button
5. Should redirect to home page
6. Check user profile:
   - Avatar should be orange
   - Should show "Guest" or "Guest User"
   - Should have "Read-Only" badge

**Expected Elements:**
- ✅ Guest button on sign-in
- ✅ Successful login
- ✅ Redirected to home
- ✅ Guest UI indicators visible
- ✅ Orange avatar with "G"

---

## Test 8: Frontend Route Protection ✓

**Objective:** Verify guest sees limited menu

**Steps:**
1. Still logged in as guest
2. Check sidebar menu
3. Should ONLY see:
   - Home
   - Automations
   - Analytics
4. Should NOT see:
   - Devices
   - Configure
   - Settings
   - etc.

**Expected Menu:**
```
✅ Home
✅ Automations
✅ Analytics
❌ Devices (hidden)
❌ Configure (hidden)
❌ Other routes (hidden)
```

---

## Test 9: Frontend Restricted Route Access ✓

**Objective:** Verify guest cannot access restricted routes

**Steps:**
1. While logged in as guest
2. Try to access: http://localhost:5173/devices
3. Should redirect to: http://localhost:5173/
4. Or show "Access Denied" message

**Expected Behavior:**
- ✅ Cannot access /devices
- ✅ Cannot access /configure
- ✅ Redirected or blocked
- ✅ Stays on allowed routes

---

## Test 10: Frontend Guest Features ✓

**Objective:** Verify guest mode UI features

**Steps:**
1. Logged in as guest
2. Try to create something (click Create button)
3. Should be disabled or blocked

**Expected:**
- ✅ Create buttons are disabled
- ✅ Edit buttons are disabled
- ✅ Delete buttons are disabled
- ✅ Write-only features hidden
- ✅ Read-only features enabled

---

## Test 11: Logout ✓

**Objective:** Verify guest can logout

**Steps:**
1. Click profile menu (top right)
2. Click "Logout" or "Sign Out"
3. Should redirect to sign-in
4. Guest data cleared from localStorage
5. Can login again

**Expected:**
- ✅ Logs out successfully
- ✅ Returns to sign-in
- ✅ Cannot access protected routes

---

## Test 12: Admin/User Features Still Work ✓

**Objective:** Verify admin users are unaffected

**Steps:**
1. Login as admin user (not guest)
2. Check menu shows all options
3. Try to create device
4. Should work normally

**Expected:**
- ✅ Full menu visible
- ✅ Create operations work
- ✅ Edit operations work
- ✅ No 403 errors for writes
- ✅ Admin features unchanged

---

## Quick Checklist

### Backend Tests
- [ ] Application starts without errors
- [ ] Guest user created in MongoDB
- [ ] Guest login returns success with role: GUEST
- [ ] Guest GET requests return 200
- [ ] Guest POST requests return 403
- [ ] Guest PUT requests return 403
- [ ] Guest DELETE requests return 403
- [ ] Regular users can still POST/PUT/DELETE

### Frontend Tests
- [ ] Guest login button visible on sign-in
- [ ] Guest login succeeds
- [ ] Redirected to home page
- [ ] Orange avatar displayed
- [ ] "Read-Only" badge shown
- [ ] Menu shows only 3 items
- [ ] Cannot access restricted routes
- [ ] Create buttons disabled/hidden
- [ ] Logout works correctly

### Integration Tests
- [ ] Full guest login flow works
- [ ] Guest can view home/automations/analytics
- [ ] Guest cannot create/edit/delete
- [ ] Error messages display correctly
- [ ] Regular users unaffected
- [ ] Admin features work normally

---

## If Tests Fail

### Guest Login Returns 401
```
Problem: Guest credentials not working
Solution: 
1. Check MongoDB: db.users.find({email:"guest@automata.local"})
2. Verify password is "guest"
3. Check PasswordEncoder is working
4. Restart backend
```

### Role is Null in Response
```
Problem: Role not included in auth response
Solution:
1. Check AuthenticationService.authenticate() includes .role(user.getRole())
2. Check AuthenticationResponse has role field
3. Recompile backend: ./mvnw clean compile
```

### Guest Can POST/PUT/DELETE
```
Problem: Filter not blocking guests
Solution:
1. Verify GuestAccessFilter.java exists
2. Check it's registered as @Component
3. Verify it checks role == GUEST
4. Check HTTP method is not GET
5. Restart backend
```

### Frontend Guest Login Fails
```
Problem: Frontend cannot reach backend
Solution:
1. Check backend is running on port 8080
2. Check CORS configuration
3. Check network tab in DevTools
4. Check console errors
5. Verify API endpoint in apis.jsx
```

### Guest Menu Shows Full Options
```
Problem: Frontend not limiting menu for guests
Solution:
1. Check SideDrawer.jsx has guestItems
2. Check AuthContext isGuest is working
3. Check user.role is "GUEST" (case-sensitive)
4. Verify PrivateRoute includes path prop
5. Clear localStorage and re-login
```

---

## Performance Check

**Monitor during tests:**
- Login time: Should be < 1 second
- GET requests: Should be < 500ms
- Blocked requests: Should be < 100ms (returns 403 quickly)
- No memory leaks
- Database queries are minimal

---

## Security Verification

During testing, verify:
- ✅ Guest cannot elevate privileges
- ✅ Guest token can't be modified
- ✅ Backend enforces restrictions (not just frontend)
- ✅ Error messages don't leak sensitive info
- ✅ Audit logs record guest actions
- ✅ No SQL injection or XSS vectors

---

## Test Completion

Once all tests pass:

1. Document any issues found
2. Fix critical issues (403 blocking, login, routes)
3. Note performance metrics
4. Verify with team
5. Schedule deployment

**Estimated Total Test Time: 15-20 minutes**

---

## Additional Notes

### Test Data
Use this curl script to test multiple operations:

```bash
#!/bin/bash

# Get guest token
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@automata.local",
    "password": "guest"
  }')

TOKEN=$(echo $RESPONSE | jq -r '.accessToken')
echo "Guest Token: $TOKEN"
echo ""

# Test GET
echo "Testing GET (should succeed):"
curl -X GET http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
echo ""

# Test POST
echo "Testing POST (should fail with 403):"
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}'
echo ""
```

### Debugging
Enable debug logging:

```properties
# application.properties
logging.level.dev.automata.automata.security=DEBUG
logging.level.org.springframework.security=DEBUG
```

This will show:
- Authentication details
- Filter execution
- Authorization decisions

---

**Next Steps After Testing:**
1. Fix any issues
2. Document findings
3. Get approval
4. Deploy to production
5. Monitor logs and metrics

Good luck! 🚀
