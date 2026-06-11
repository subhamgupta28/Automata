# Admin Login Analytics Dashboard - Implementation Guide

## Overview

A comprehensive admin-only dashboard has been created to track and visualize user login activities across the Automata system. This feature logs all login attempts (successful and failed) with detailed information including IP address, browser, operating system, and timestamp.

## Features

### 1. **Login Tracking**
- Automatic logging of successful logins
- Failed login attempt recording with failure reasons
- Session duration tracking
- IP address extraction (handles X-Forwarded-For headers for proxies)
- Browser and OS detection from User-Agent header

### 2. **Admin Dashboard Analytics**
The dashboard displays:

#### Summary Cards
- Total registered users
- Total login attempts
- Successful logins
- Failed login attempts

#### Visualizations
- **Login Trends Chart**: Line chart showing login frequency over time
- **Browser Distribution**: Pie chart showing browser usage
- **Operating System Distribution**: Bar chart showing OS usage

#### User Statistics Table
Shows per-user metrics:
- Total login count
- Successful vs failed logins
- Last login timestamp
- Number of unique IP addresses used
- Devices/browsers used

#### Recent Logins Table
- Latest login attempts with pagination
- Email and name
- Exact timestamp
- IP address
- Browser and OS
- Success/failure status

#### User History Dialog
Click "View History" on any user to see:
- All their login attempts (paginated)
- Device information used
- Login timestamps and locations

### 3. **Filters**
- Time range selector (1 hour, 6 hours, 24 hours, 7 days, 30 days)
- Real-time data refresh

## Technical Architecture

### Backend Components

#### 1. **LoginHistory Model** (`src/main/java/dev/automata/automata/model/LoginHistory.java`)
MongoDB document storing login records with fields:
- User information (id, email, name)
- Access information (IP, browser, OS)
- Timestamp and session duration
- Success/failure status and reason

#### 2. **LoginHistoryRepository** (`src/main/java/dev/automata/automata/repository/LoginHistoryRepository.java`)
Spring Data MongoDB repository providing:
- Find logins by user email
- Find logins in time range
- Aggregate login statistics

#### 3. **AuditService** (`src/main/java/dev/automata/automata/service/AuditService.java`)
Service handling login logging logic:
- `logSuccessfulLogin()`: Called after successful authentication
- `logFailedLogin()`: Called for failed login attempts
- `logLogout()`: Tracks session end time
- `getUserLogins()`: Retrieves user login history
- `getRecentLogins()`: Gets recent logins across all users
- `getLoginStatsByUser()`: Aggregates user statistics

Helper methods:
- `extractIpAddress()`: Parses X-Forwarded-For headers
- `extractBrowser()`: Detects browser from User-Agent
- `extractOS()`: Detects operating system from User-Agent

#### 4. **LoginAnalyticsController** (`src/main/java/dev/automata/automata/controller/LoginAnalyticsController.java`)
REST endpoints (all require ADMIN role):
- `GET /api/admin/login-analytics/stats` - User-wise login statistics
- `GET /api/admin/login-analytics/summary` - Overall summary statistics
- `GET /api/admin/login-analytics/recent?hours=24` - Recent logins (default 24 hours)
- `GET /api/admin/login-analytics/user/{email}` - Specific user's login history

#### 5. **AuthenticationService Update**
Modified to automatically call `AuditService.logSuccessfulLogin()` after successful authentication.

### Frontend Components

#### 1. **AdminLoginDashboard** (`frontend/src/components/admin/AdminLoginDashboard.jsx`)
Main dashboard component featuring:
- Material-UI cards for summary statistics
- Recharts for data visualization
- Multiple data tables with pagination
- User history modal dialog
- Time range filters
- Error handling and loading states

#### 2. **API Integration** (`frontend/src/services/apis.jsx`)
New function:
```javascript
export const getLoginAnalytics = async (endpoint) {
    const response = await api.get(`admin/login-analytics/${endpoint}`);
    return response;
}
```

#### 3. **PrivateRoute Enhancement** (`frontend/src/components/auth/PrivateRoute.jsx`)
Added `requiredRole` parameter to enforce role-based access:
```jsx
<PrivateRoute 
    path="/admin/login-analytics" 
    element={<AdminLoginDashboard/>} 
    requiredRole="ADMIN"
/>
```

#### 4. **SideDrawer Navigation Update** (`frontend/src/components/custom_drawer/SideDrawer.jsx`)
- Added "Admin Panel" menu item (only visible to ADMIN role users)
- Conditionally added route for admin dashboard
- Uses AdminPanelSettingsIcon for visual identification

## Usage

### For Administrators

1. **Access the Dashboard**
   - Log in as an ADMIN user
   - Click "Admin Panel" in the left sidebar
   - Or navigate directly to `/admin/login-analytics`

2. **Viewing Dashboard**
   - Summary cards show key metrics at the top
   - Charts visualize login trends and device distribution
   - User statistics table shows per-user metrics
   - Recent logins table shows latest activity

3. **Filtering Data**
   - Use the time range selector to view logins from different periods
   - Dashboard updates automatically when filter changes

4. **Viewing User History**
   - Click "View History" button on any user in the statistics table
   - Modal dialog shows all logins for that user
   - Includes device information and timestamps

### For Security Monitoring

The dashboard helps with:
- **Suspicious Activity Detection**: Identify unusual IP addresses or devices
- **Session Management**: Track active sessions and logout times
- **Compliance**: Maintain audit trail of user access
- **Device Tracking**: See which devices users are using
- **Geographic Analysis**: IP addresses can be mapped to locations
- **Failure Analysis**: Review failed login attempts

## Security Considerations

### Authorization
- Admin dashboard only accessible to users with `ADMIN` role
- Backend endpoints enforce `@PreAuthorize("hasRole('ADMIN')")`
- Frontend also validates role before rendering component

### Data Privacy
- Only admin users can view login history
- IP addresses are stored (can be pseudonymized if needed)
- Session data is encrypted in transit

### Audit Trail
- All successful and failed logins are logged
- Timestamps are in UTC (Instant)
- Complete user agent information is captured

## Database Schema

MongoDB Collection: `login_history`

```javascript
{
  "_id": ObjectId,
  "userId": String,
  "email": String,
  "firstName": String,
  "lastName": String,
  "ipAddress": String,
  "userAgent": String,
  "deviceInfo": String,
  "browser": String,
  "operatingSystem": String,
  "country": String,
  "city": String,
  "success": Boolean,
  "failureReason": String,
  "loginTime": ISODate,
  "logoutTime": ISODate,
  "sessionDurationSeconds": Long
}
```

## Integration Points

### Login Flow
1. User submits credentials to `/api/auth/authenticate`
2. AuthenticationService authenticates user
3. On success, `AuditService.logSuccessfulLogin()` is called
4. HttpServletRequest is passed to extract client information
5. LoginHistory document is created and saved to MongoDB

### How It Works

```
User Login
    ↓
AuthenticationService.authenticate()
    ↓
authenticationManager.authenticate()
    ↓
User credentials verified
    ↓
auditService.logSuccessfulLogin(user, httpRequest)
    ↓
Extract: IP, Browser, OS from request
    ↓
Create LoginHistory document
    ↓
Save to MongoDB
    ↓
Return JWT token + user details
```

## Customization

### Extending Data Collection
To capture additional information, modify `LoginHistory.java` and `AuditService.java`:
```java
// Add new fields to LoginHistory
private String location;      // Geolocation
private String deviceType;    // Mobile/Desktop/Tablet
private String userDeviceId;  // Device fingerprint
```

### Changing Time Ranges
Modify `AdminLoginDashboard.jsx` MenuItem values:
```jsx
<MenuItem value={1}>Last 1 hour</MenuItem>
<MenuItem value={6}>Last 6 hours</MenuItem>
// Add more options as needed
```

### Geolocation Integration
To add IP-to-location mapping:
```java
// In AuditService.logSuccessfulLogin()
String location = geoIpService.getLocation(ipAddress);
history.setCity(location.getCity());
history.setCountry(location.getCountry());
```

## Troubleshooting

### Dashboard Not Accessible
- Verify user has ADMIN role
- Check browser console for errors
- Verify backend API endpoints are responding

### No Login Data Displayed
- Ensure logins have been performed after deployment
- Check MongoDB `login_history` collection has documents
- Verify AuthenticationService is injecting AuditService

### IP Address Shows as localhost
- If running locally, all IPs will be 127.0.0.1
- In production, verify X-Forwarded-For headers are configured
- Check nginx/reverse proxy is forwarding client IP

## Performance Considerations

- LoginHistory collection should have indexes on `email`, `loginTime`, `userId`
- Dashboard queries aggregate data; for large datasets (>1M records), consider:
  - Adding date range filters
  - Creating MongoDB aggregation pipeline
  - Implementing caching in AuditService

## Future Enhancements

1. **Geolocation Mapping**: Add MaxMind GeoIP2 integration
2. **IP Reputation**: Check IPs against threat databases
3. **Device Fingerprinting**: Track unique devices using WebGL/Canvas
4. **Machine Learning**: Detect anomalous login patterns
5. **Export/Reports**: Generate CSV/PDF login reports
6. **Real-time Alerts**: Notify admins of suspicious activity
7. **Session Management**: Force logout malicious sessions
8. **Two-Factor Auth Logs**: Track 2FA attempts and failures

## Files Modified/Created

### Created
- ✅ `src/main/java/dev/automata/automata/model/LoginHistory.java`
- ✅ `src/main/java/dev/automata/automata/repository/LoginHistoryRepository.java`
- ✅ `src/main/java/dev/automata/automata/service/AuditService.java`
- ✅ `src/main/java/dev/automata/automata/controller/LoginAnalyticsController.java`
- ✅ `frontend/src/components/admin/AdminLoginDashboard.jsx`

### Modified
- ✅ `src/main/java/dev/automata/automata/security/AuthenticationService.java` - Added AuditService integration
- ✅ `frontend/src/services/apis.jsx` - Added getLoginAnalytics() function
- ✅ `frontend/src/components/auth/PrivateRoute.jsx` - Added requiredRole parameter
- ✅ `frontend/src/components/custom_drawer/SideDrawer.jsx` - Added admin menu item and route

## Testing

### Backend Testing
```bash
# Create a test admin user
db.users.insertOne({
  email: "admin@example.com",
  password: "hashedPassword",
  role: "ADMIN"
})

# Log in and check login_history collection
db.login_history.find({email: "admin@example.com"}).pretty()
```

### Frontend Testing
1. Log in as ADMIN user
2. Navigate to Admin Panel
3. Verify dashboard loads
4. Verify data is displayed
5. Test filters and pagination

## Dependencies

### Backend
- Spring Boot 3.5.0+
- Spring Data MongoDB
- Spring Security

### Frontend
- React 18+
- Material-UI 5+
- Recharts (charts)
- date-fns (date formatting)

## License

This feature follows the same license as the Automata project.
