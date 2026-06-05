# Backend Guest Mode Setup Guide

## Overview

This guide provides step-by-step instructions for setting up the role-based guest mode on your backend. The frontend is ready; you just need to:

1. Add a `role` field to the User model (if not present)
2. Create a guest user account
3. Add authorization checks to protect write operations

## Step 1: Update User Model

### Java/Spring Boot

#### Add Role Enum
```java
package dev.automata.security;

public enum Role {
    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER"),
    GUEST("ROLE_GUEST");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }
}
```

#### Update User Entity
```java
package dev.automata.entity;

import dev.automata.security.Role;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    // ADD THIS FIELD
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    // ... existing getters/setters

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
```

#### Update Database Migration
```sql
-- Add role column if not exists
ALTER TABLE users ADD COLUMN role VARCHAR(50) DEFAULT 'USER' NOT NULL;

-- Create enum constraint
ALTER TABLE users ADD CONSTRAINT check_role 
CHECK (role IN ('ADMIN', 'USER', 'GUEST'));
```

### Node.js/Express (MongoDB)

```javascript
const UserSchema = new Schema({
    id: String,
    email: { type: String, unique: true, required: true },
    password: { type: String, required: true },
    firstName: String,
    lastName: String,
    
    // ADD THIS FIELD
    role: {
        type: String,
        enum: ['ADMIN', 'USER', 'GUEST'],
        default: 'USER'
    },
    
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('User', UserSchema);
```

### Python/Flask (SQLAlchemy)

```python
from enum import Enum
from sqlalchemy import Column, String, Enum as SQLEnum

class RoleEnum(Enum):
    ADMIN = "ADMIN"
    USER = "USER"
    GUEST = "GUEST"

class User(db.Model):
    __tablename__ = 'users'
    
    id = Column(String, primary_key=True)
    email = Column(String, unique=True, nullable=False)
    password = Column(String, nullable=False)
    first_name = Column(String)
    last_name = Column(String)
    
    # ADD THIS FIELD
    role = Column(SQLEnum(RoleEnum), nullable=False, default=RoleEnum.USER)
    
    created_at = Column(DateTime, default=datetime.utcnow)
```

## Step 2: Create Guest User

### Java/Spring Boot

#### Option 1: SQL Script
```sql
-- Insert guest user
INSERT INTO users (id, email, password, first_name, last_name, role) 
VALUES (
    'guest',
    'guest@automata.local',
    '$2a$10$slYQmyNdGzin7olVi9hFOOYvxkMLLAVR4KLaSZSTQFQT1s2seFjPi', -- bcrypt of 'guest'
    'Guest',
    'User',
    'GUEST'
);
```

#### Option 2: Java Code (DataLoader or Initialization)
```java
package dev.automata.config;

import dev.automata.entity.User;
import dev.automata.repository.UserRepository;
import dev.automata.security.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Create guest user if it doesn't exist
        if (userRepository.findByEmail("guest@automata.local").isEmpty()) {
            User guestUser = new User();
            guestUser.setId("guest");
            guestUser.setEmail("guest@automata.local");
            guestUser.setPassword(passwordEncoder.encode("guest"));
            guestUser.setFirstName("Guest");
            guestUser.setLastName("User");
            guestUser.setRole(Role.GUEST);
            
            userRepository.save(guestUser);
            System.out.println("Guest user created successfully");
        }
    }
}
```

### Node.js/Express

```javascript
const User = require('./models/User');
const bcrypt = require('bcryptjs');

async function createGuestUser() {
    try {
        const existingGuest = await User.findOne({ email: 'guest@automata.local' });
        
        if (!existingGuest) {
            const hashedPassword = await bcrypt.hash('guest', 10);
            
            const guestUser = new User({
                id: 'guest',
                email: 'guest@automata.local',
                password: hashedPassword,
                firstName: 'Guest',
                lastName: 'User',
                role: 'GUEST'
            });
            
            await guestUser.save();
            console.log('Guest user created successfully');
        }
    } catch (error) {
        console.error('Error creating guest user:', error);
    }
}

// Call on application startup
module.exports = { createGuestUser };
```

### Python/Flask

```python
from werkzeug.security import generate_password_hash
from app.models import User, db
from app.models import RoleEnum

def create_guest_user():
    try:
        existing_guest = User.query.filter_by(email='guest@automata.local').first()
        
        if not existing_guest:
            guest_user = User(
                id='guest',
                email='guest@automata.local',
                password=generate_password_hash('guest'),
                first_name='Guest',
                last_name='User',
                role=RoleEnum.GUEST
            )
            
            db.session.add(guest_user)
            db.session.commit()
            print('Guest user created successfully')
    except Exception as e:
        print(f'Error creating guest user: {e}')

# Call on application startup in __init__.py or main.py
```

## Step 3: Ensure Role is Included in Auth Response

### Java/Spring Boot

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody AuthRequest request) {
        // ... authentication logic ...
        
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Create JWT with role
        String token = jwtProvider.generateToken(user);
        
        return ResponseEntity.ok(new AuthResponse(
            token,
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole().toString()  // INCLUDE ROLE
        ));
    }
}
```

### Node.js/Express

```javascript
router.post('/authenticate', async (req, res) => {
    try {
        const { email, password } = req.body;
        
        const user = await User.findOne({ email });
        if (!user) {
            return res.status(401).json({ error: 'User not found' });
        }
        
        const validPassword = await bcrypt.compare(password, user.password);
        if (!validPassword) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }
        
        const token = jwt.sign(
            { 
                id: user.id, 
                email: user.email, 
                role: user.role  // INCLUDE ROLE
            },
            process.env.JWT_SECRET,
            { expiresIn: '24h' }
        );
        
        return res.json({
            token,
            user: {
                id: user.id,
                email: user.email,
                firstName: user.firstName,
                lastName: user.lastName,
                role: user.role  // INCLUDE ROLE
            }
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});
```

## Step 4: Add Authorization Checks

### Method 1: Annotation-Based (Java/Spring)

```java
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping
    public ResponseEntity<?> createDevice(@RequestBody DeviceRequest request) {
        // Only ADMIN and USER can create
        // GUEST will get 403 Forbidden
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDevice(
        @PathVariable String id,
        @RequestBody DeviceRequest request) {
        // Only ADMIN and USER can update
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDevice(@PathVariable String id) {
        // Only ADMIN and USER can delete
    }

    @GetMapping
    public ResponseEntity<?> getDevices() {
        // All roles can read (no annotation)
    }
}
```

### Method 2: Middleware-Based (Node.js/Express)

```javascript
const checkGuestRestriction = (req, res, next) => {
    if (req.user && req.user.role === 'GUEST' && req.method !== 'GET') {
        return res.status(403).json({ 
            error: 'Guests cannot perform write operations' 
        });
    }
    next();
};

// Apply to POST, PUT, DELETE routes
router.post('/api/devices', 
    authenticate, 
    checkGuestRestriction, 
    createDevice
);

router.put('/api/devices/:id', 
    authenticate, 
    checkGuestRestriction, 
    updateDevice
);

router.delete('/api/devices/:id', 
    authenticate, 
    checkGuestRestriction, 
    deleteDevice
);

// GET routes don't need restriction
router.get('/api/devices', authenticate, getDevices);
```

### Method 3: Explicit Check (Python/Flask)

```python
from functools import wraps
from flask import jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required

def deny_guest(f):
    @wraps(f)
    @jwt_required()
    def decorated_function(*args, **kwargs):
        claims = get_jwt()
        if claims.get('role') == 'GUEST':
            return jsonify({'error': 'Guests cannot perform this action'}), 403
        return f(*args, **kwargs)
    return decorated_function

@app.route('/api/devices', methods=['POST'])
@deny_guest
def create_device():
    # Only ADMIN and USER
    pass

@app.route('/api/devices', methods=['GET'])
@jwt_required()
def get_devices():
    # All authenticated users including GUEST
    pass
```

## Step 5: Protect Key Endpoints

Apply guest restrictions to these endpoints:

### Device Management
```
❌ POST   /api/devices
❌ PUT    /api/devices/:id
❌ DELETE /api/devices/:id
✅ GET    /api/devices
✅ GET    /api/devices/:id
```

### Automation Management
```
❌ POST   /api/automations
❌ POST   /api/action/automation
❌ PUT    /api/automations/:id
❌ DELETE /api/automations/:id
✅ GET    /api/automations
✅ GET    /api/automations/:id
```

### System Configuration
```
❌ POST   /api/configure
❌ PUT    /api/configure
❌ DELETE /api/configure
✅ GET    /api/configure
```

### Virtual Devices
```
❌ POST   /api/virtual
❌ PUT    /api/virtual/:id
❌ DELETE /api/virtual/:id
✅ GET    /api/virtual
```

## Step 6: Testing

### Test 1: Guest User Login
```bash
curl -X POST http://localhost:8080/api/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@automata.local",
    "password": "guest"
  }'
```

Expected Response:
```json
{
  "token": "eyJhbGc...",
  "user": {
    "id": "guest",
    "email": "guest@automata.local",
    "firstName": "Guest",
    "lastName": "User",
    "role": "GUEST"
  }
}
```

### Test 2: Guest Cannot Create Device
```bash
curl -X POST http://localhost:8080/api/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Device"}'
```

Expected Response:
```
403 Forbidden
{
  "error": "Guests cannot perform write operations"
}
```

### Test 3: Guest Can Read Devices
```bash
curl -X GET http://localhost:8080/api/devices \
  -H "Authorization: Bearer $TOKEN"
```

Expected Response:
```
200 OK
[
  {
    "id": "device-1",
    "name": "Device 1"
  }
]
```

## Troubleshooting

### Guest login returns "User not found"
- Verify guest user exists in database with email `guest@automata.local`
- Check user record: `SELECT * FROM users WHERE email = 'guest@automata.local';`
- Create user if missing (see Step 2)

### Role is null in response
- Ensure User model has role field
- Verify guest user has role set to 'GUEST'
- Check that role is included in auth response (see Step 3)

### Guest can still create resources
- Verify authorization checks are applied to POST/PUT/DELETE endpoints
- Check that guest user has role 'GUEST' (case-sensitive)
- Verify middleware/annotations are working
- Check application logs for auth errors

### 403 Forbidden for all guest requests
- Guest role check might be too strict
- Verify only write operations (POST/PUT/DELETE) are blocked
- GET requests should work
- Check if token is being passed correctly

### Role field not updating
- Database migration might not have run
- Try explicit ALTER TABLE statement
- Restart application after database changes
- Verify migration file was applied

## Security Checklist

- [ ] Guest user account created with role 'GUEST'
- [ ] Role field added to User model
- [ ] Role included in authentication response
- [ ] POST/PUT/DELETE endpoints deny GUEST role
- [ ] GET endpoints allow GUEST role
- [ ] Authorization tested with actual guest account
- [ ] Error messages don't leak sensitive information
- [ ] Guest account logged for audit trail
- [ ] API rate limiting applied to guest user
- [ ] HTTPS enabled for all authentication

## Configuration Examples

### Java/Spring Boot (application.properties)
```properties
# Ensure JWT includes role
jwt.secret=your-secret-key
jwt.expiration=86400000

# Enable method security
spring.security.enable-method-security=true
```

### Node.js/.env
```
JWT_SECRET=your-secret-key
JWT_EXPIRATION=24h
NODE_ENV=production
```

### Python/.env
```
JWT_SECRET_KEY=your-secret-key
JWT_EXPIRATION_HOURS=24
FLASK_ENV=production
```

---

**Once these steps are complete, test the frontend guest login - it should work seamlessly!**
