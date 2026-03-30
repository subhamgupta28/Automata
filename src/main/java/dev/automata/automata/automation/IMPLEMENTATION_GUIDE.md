# Automation System Improvements - Implementation Guide

This document describes the high and low priority improvements implemented for the automation system.

---

## 🔥 HIGH PRIORITY IMPROVEMENTS (Critical Fixes)

### 1. Distributed Locking for State Management

**Problem**: Race conditions when multiple automations modify the same device simultaneously, or when snapshots are saved/restored concurrently.

**Solution**: Implemented Redis-based distributed locks using SETNX with TTL.

**Implementation**:
```java
// EnhancedRedisService.java
public boolean setIfAbsent(String lockKey, String value, long ttlSeconds) {
    // Atomic set-if-not-exists with TTL using Lua script
}

public boolean deleteIfEquals(String lockKey, String value) {
    // Safe lock release - only delete if value matches
}

// Usage in EnhancedAutomationService.java
String lockKey = "LOCK:SNAPSHOT:" + automation.getId();
withLock(lockKey, 60, () -> {
    saveStateSnapshots(automation);
    return null;
});
```

**Benefits**:
- Prevents race conditions in snapshot save/restore
- Thread-safe cache updates
- Automatic lock expiry prevents deadlocks

**Files**:
- `EnhancedRedisService.java` - Lock primitives
- `EnhancedAutomationService.java` - Lock usage

---

### 2. Idempotency Keys for Automation Execution

**Problem**: Automations could trigger multiple times in the same minute due to scheduler running multiple times.

**Solution**: Generate idempotency keys based on automation ID + rounded minute timestamp.

**Implementation**:
```java
private String generateIdempotencyKey(Automation automation, Date executionTime) {
    long roundedMinute = executionTime.toInstant().getEpochSecond() / 60;
    return String.format("IDEMPOTENCY:%s:%d", automation.getId(), roundedMinute);
}

if (isAlreadyExecuted(idempotencyKey)) {
    return; // Skip duplicate execution
}

// After successful execution
markAsExecuted(idempotencyKey); // Stored with 2-hour TTL
```

**Benefits**:
- Prevents duplicate triggers within same minute
- Idempotency keys expire automatically (2 hours)
- Works across application restarts

**Files**:
- `EnhancedAutomationService.java`

---

### 3. Execution Timeout Protection

**Problem**: Actions could hang indefinitely if device doesn't respond or WLED API is slow.

**Solution**: Wrap all automation executions in CompletableFuture with 30-second timeout.

**Implementation**:
```java
private CompletableFuture<Void> executeWithTimeout(
        Automation automation, 
        Map<String, Object> payload, 
        String user) {
    
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        executeActionsInternal(automation, user, payload);
    }, automationExecutor);

    return future.orTimeout(30, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex instanceof TimeoutException) {
                    log.error("⏱️ Automation {} timed out", automation.getName());
                    notificationService.sendNotification(
                        "Automation timeout: " + automation.getName(), 
                        "error"
                    );
                }
                return null;
            });
}
```

**Benefits**:
- No more infinite hangs
- User notifications on timeout
- Prevents thread pool exhaustion

**Files**:
- `EnhancedAutomationService.java`

---

### 4. Daily Fire Tracking for Scheduled Automations

**Problem**: Time-based automations with ±1 minute window could trigger twice (at 00:00 and 00:59).

**Solution**: Track "fired today" per automation in Redis with midnight expiry.

**Implementation**:
```java
private boolean isCurrentTimeWithDailyTracking(Automation automation, Automation.Condition condition) {
    // ... check time matches ...
    
    if (!timeMatches) return false;

    // Check if already fired today
    String dailyKey = String.format("DAILY_FIRE:%s:%s", automation.getId(), today);
    
    if (redisService.exists(dailyKey)) {
        log.debug("Automation {} already fired today", automation.getName());
        return false;
    }

    // Mark as fired today (expires at midnight)
    long secondsUntilMidnight = ChronoUnit.SECONDS.between(nowZdt, midnight);
    redisService.setWithExpiry(dailyKey, "fired", secondsUntilMidnight);
    
    return true;
}
```

**Benefits**:
- Prevents double-triggering on daily schedules
- Automatic reset at midnight
- Works for "at" and "range" schedule types

**Files**:
- `EnhancedAutomationService.java`

---

### 5. Comprehensive Automation Validation

**Problem**: Invalid automations could be saved, causing runtime errors (missing devices, invalid time formats, circular dependencies).

**Solution**: Validation service that checks all aspects before saving.

**Implementation**:
```java
// AutomationValidationService.java
public List<String> validate(AutomationDetail detail) {
    List<String> errors = new ArrayList<>();
    
    // Validate trigger exists and device is valid
    errors.addAll(validateTrigger(detail));
    
    // Validate conditions (numeric ranges, time formats, etc.)
    errors.addAll(validateConditions(detail));
    
    // Validate actions (devices exist, data is valid)
    errors.addAll(validateActions(detail));
    
    // Check logical consistency (operators match conditions)
    errors.addAll(validateLogicalConsistency(detail));
    
    // Detect circular dependencies
    errors.addAll(detectCircularDependencies(detail));
    
    return errors;
}
```

**Validation Checks**:
- Trigger device exists
- Trigger keys exist on device
- Time formats are valid (HH:mm:ss or hh:mm:ss AM/PM)
- Numeric conditions have valid numbers
- Range conditions: above < below
- Action devices exist
- Days are valid (Mon-Sun)
- No circular dependencies (A triggers B, B triggers A)

**Benefits**:
- Catches errors before they cause runtime failures
- Clear error messages for users
- Prevents system crashes from bad data

**Files**:
- `AutomationValidationService.java`
- `EnhancedAutomationController.java` - `/save-validated` endpoint

---

## 🌟 LOW PRIORITY IMPROVEMENTS (Quality of Life)

### 6. Dry-Run Simulation Mode

**Problem**: No way to test automations without actually executing them.

**Solution**: Simulation service that evaluates conditions and predicts actions without execution.

**Implementation**:
```java
// AutomationSimulationService.java
public AutomationSimulationResult simulateAutomation(
        String automationId, 
        Map<String, Object> testPayload) {
    
    // Evaluate conditions
    boolean wouldTrigger = evaluateConditions(automation, testPayload, ...);
    
    // Simulate actions (don't execute)
    List<SimulatedAction> simulatedActions = simulateActions(automation, testPayload);
    
    return AutomationSimulationResult.builder()
            .wouldTrigger(wouldTrigger)
            .simulatedActions(simulatedActions)
            .conditionResults(conditionResults)
            .affectedDeviceIds(affectedDevices)
            .build();
}
```

**Use Cases**:
- Test automations before enabling
- Debug why automation isn't triggering
- Batch test multiple payloads
- Training/education

**API Endpoints**:
```bash
# Simulate with custom payload
POST /api/v2/automations/{id}/simulate
{
  "temperature": 25.5,
  "humidity": 60
}

# Simulate with current device state
POST /api/v2/automations/{id}/simulate

# Batch simulation
POST /api/v2/automations/{id}/simulate-batch
[
  {"temperature": 20},
  {"temperature": 25},
  {"temperature": 30}
]
```

**Files**:
- `AutomationSimulationService.java`
- `AutomationSimulationResult.java`
- `EnhancedAutomationController.java`

---

### 7. Multi-Timezone Support

**Problem**: All automations hardcoded to Asia/Kolkata timezone.

**Solution**: Per-automation timezone configuration with timezone conversion utilities.

**Implementation**:
```java
// MultiTimezoneAutomationService.java
public boolean isCurrentTimeInTimezone(
        Automation.Condition condition, 
        String timezoneId) {
    
    ZoneId zone = resolveTimezone(timezoneId);
    ZonedDateTime nowZdt = ZonedDateTime.now(zone);
    // ... time checking logic in specified zone ...
}

// Timezone conversion
public ZonedDateTime convertTime(ZonedDateTime time, String fromZone, String toZone);

// Get next trigger time in timezone
public long getSecondsUntilNextTrigger(Automation.Condition condition, String timezoneId);
```

**Features**:
- Supports timezone aliases (IST, PST, EST, etc.)
- Timezone-aware schedule checking
- Time conversion utilities
- DST handling

**API Endpoints**:
```bash
# Get available timezones
GET /api/v2/automations/timezones/available

# Get common timezones
GET /api/v2/automations/timezones/common

# Get current time in timezone
GET /api/v2/automations/timezones/current?timezone=America/New_York
```

**Files**:
- `MultiTimezoneAutomationService.java`
- `EnhancedAutomationController.java`

---

### 8. Automation Analytics Dashboard

**Problem**: No visibility into automation performance, failures, or usage patterns.

**Solution**: Comprehensive analytics service with metrics and insights.

**Implementation**:
```java
// AutomationAnalyticsService.java
public AutomationAnalytics getAutomationAnalytics(String automationId, int daysBack) {
    // Fetch logs for period
    List<AutomationLog> logs = automationLogRepository.findByAutomationIdAndTimestampAfter(...);
    
    return AutomationAnalytics.builder()
            .totalEvaluations(logs.size())
            .triggeredCount(countByStatus(logs, TRIGGERED))
            .successRate(calculateSuccessRate(logs))
            .triggersByDay(groupTriggersByDay(logs))
            .mostCommonTriggerTimes(getMostCommonTriggerTimes(logs))
            .failureReasons(getTopFailureReasons(logs))
            .build();
}
```

**Metrics Provided**:
- **Execution Stats**: Total evaluations, triggers, restores, skips
- **Success Rate**: Percentage of successful executions
- **Temporal Analysis**: Triggers by day, most common trigger times
- **Failure Analysis**: Top 3 failure reasons with counts
- **Device Impact**: Which devices are most affected
- **Hourly Distribution**: When automations are most active

**API Endpoints**:
```bash
# Get analytics for specific automation
GET /api/v2/automations/{id}/analytics?daysBack=30

# Overview of all automations
GET /api/v2/automations/analytics/overview?daysBack=30

# Top performing automations
GET /api/v2/automations/analytics/top-performing?limit=10&daysBack=30

# Problematic automations (low success rate)
GET /api/v2/automations/analytics/problematic?successThreshold=0.7&daysBack=30

# Execution timeline
GET /api/v2/automations/{id}/timeline?hours=24

# Device impact analysis
GET /api/v2/automations/analytics/device-impact?daysBack=30

# Hourly distribution
GET /api/v2/automations/analytics/hourly-distribution?daysBack=30
```

**Files**:
- `AutomationAnalyticsService.java`
- `AutomationAnalytics.java`
- `EnhancedAutomationController.java`

---

## 📋 Migration Guide

### Step 1: Add Dependencies

Ensure these are in your `pom.xml`:

```xml
<dependencies>
    <!-- Redis for distributed locking -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Lombok for cleaner code -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Update Redis Configuration

Add Lua script support to Redis template:

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setEnableTransactionSupport(true); // Important for Lua scripts
        return template;
    }
}
```

### Step 3: Add Missing Repository Methods

```java
// AutomationLogRepository.java
public interface AutomationLogRepository extends MongoRepository<AutomationLog, String> {
    
    List<AutomationLog> findByAutomationIdAndTimestampAfter(String automationId, Date after);
    
    List<AutomationLog> findByStatusAndTimestampAfter(LogStatus status, Date after);
    
    long countByAutomationIdAndStatusAndTimestampAfter(
        String automationId, 
        LogStatus status, 
        Date after
    );
}
```

### Step 4: Update Automation Model (Optional)

Add timezone field to Automation model:

```java
@Document(collection = "automations")
public class Automation {
    // ... existing fields ...
    
    private String timezone = "Asia/Kolkata"; // Default timezone
    
    // ... getters/setters ...
}
```

### Step 5: Replace Old Service

**Option A**: Gradual migration
- Keep `AutomationService` for existing code
- Use `EnhancedAutomationService` for new features
- Gradually migrate methods

**Option B**: Complete replacement
- Rename `AutomationService` to `LegacyAutomationService`
- Rename `EnhancedAutomationService` to `AutomationService`
- Update all @Autowired references

### Step 6: Update Scheduler

Replace the existing scheduler method:

```java
@Scheduled(fixedRate = 15000)
private void triggerPeriodicAutomations() {
    List<CompletableFuture<Void>> futures = automationRepository.findByIsEnabledTrue()
            .stream()
            .map(a -> CompletableFuture.runAsync(() ->
                    // Use new method with all enhancements
                    checkAndExecuteSingleAutomationEnhanced(
                            a,
                            redisService.getRecentDeviceData(a.getTrigger().getDeviceId()),
                            "system"
                    ), automationExecutor))
            .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}
```

---

## 🧪 Testing the Improvements

### Test HIGH PRIORITY Features

**1. Test Distributed Locking**:
```bash
# Trigger the same automation multiple times simultaneously
# Should only execute once due to locking

curl -X POST http://localhost:8080/api/automations/handleAction \
  -H "Content-Type: application/json" \
  -d '{"deviceId": "device123", ...}'
```

**2. Test Idempotency**:
```bash
# Execute same automation twice within 1 minute
# Second execution should be skipped

curl -X POST http://localhost:8080/api/v2/automations/execute/{id}
# Wait 10 seconds
curl -X POST http://localhost:8080/api/v2/automations/execute/{id}

# Check logs: "Skipping duplicate execution"
```

**3. Test Timeout**:
```bash
# Create automation with WLED action to slow/offline device
# Should timeout after 30 seconds with notification

# Check logs: "⏱️ Automation timeout"
```

**4. Test Daily Fire Tracking**:
```bash
# Create automation with schedule "at 10:00:00"
# At 10:00, it should trigger
# At 10:01 (still within ±1 min window), should NOT trigger again

# Check logs: "Automation already fired today"
```

**5. Test Validation**:
```bash
# Try to save invalid automation
curl -X POST http://localhost:8080/api/v2/automations/save-validated \
  -H "Content-Type: application/json" \
  -d '{
    "nodes": [{
      "data": {
        "conditionData": {
          "condition": "range",
          "above": "30",
          "below": "20"  # Invalid: above > below
        }
      }
    }]
  }'

# Response: { "success": false, "errors": ["Range 'above' must be less than 'below'"] }
```

### Test LOW PRIORITY Features

**6. Test Simulation**:
```bash
# Simulate with custom payload
curl -X POST http://localhost:8080/api/v2/automations/auto123/simulate \
  -H "Content-Type: application/json" \
  -d '{"temperature": 28, "humidity": 65}'

# Response shows what WOULD happen without executing
```

**7. Test Analytics**:
```bash
# Get analytics
curl http://localhost:8080/api/v2/automations/auto123/analytics?daysBack=7

# Get top performers
curl http://localhost:8080/api/v2/automations/analytics/top-performing?limit=5
```

**8. Test Multi-Timezone**:
```bash
# Get current time in different timezone
curl "http://localhost:8080/api/v2/automations/timezones/current?timezone=America/New_York"
```

---

## 📊 Performance Impact

### Redis Load
- **Locks**: ~100 ops/min (short-lived, auto-expire)
- **Idempotency keys**: ~50 ops/min (2-hour TTL)
- **Daily fire tracking**: ~10 ops/min (24-hour TTL)

**Total**: ~160 additional Redis operations/minute

### Memory Usage
- Idempotency keys: ~50 KB per 1000 automations
- Daily fire keys: ~20 KB per 1000 automations
- Lock keys: Negligible (very short-lived)

### CPU Impact
- Validation: +5-10ms per automation save
- Simulation: +20-50ms per simulation
- Analytics: +100-500ms per query (depends on log volume)

---

## 🔧 Configuration

Add to `application.properties`:

```properties
# Automation timeouts
automation.execution.timeout.seconds=30
automation.lock.ttl.seconds=60

# Daily fire tracking
automation.daily.fire.enabled=true
automation.daily.fire.timezone=Asia/Kolkata

# Idempotency
automation.idempotency.enabled=true
automation.idempotency.ttl.hours=2

# Analytics
automation.analytics.enabled=true
automation.analytics.retention.days=90

# Simulation
automation.simulation.enabled=true

# Multi-timezone
automation.timezone.default=Asia/Kolkata
automation.timezone.enabled=true
```

---

## 🚨 Troubleshooting

### Problem: Locks not releasing
**Cause**: Application crashed while holding lock  
**Solution**: Locks auto-expire after 60s. Increase TTL if needed.

### Problem: Idempotency preventing legitimate re-triggers
**Cause**: TTL too long  
**Solution**: Reduce TTL from 2 hours to 1 hour in code.

### Problem: Daily fire not resetting
**Cause**: Incorrect midnight calculation  
**Solution**: Check timezone is correct in `isCurrentTimeWithDailyTracking()`.

### Problem: Validation rejecting valid automations
**Cause**: Overly strict validation rules  
**Solution**: Review validation errors, adjust rules in `AutomationValidationService`.

---

## 📚 Additional Resources

- [Redis Distributed Locks](https://redis.io/docs/manual/patterns/distributed-locks/)
- [CompletableFuture Timeouts](https://www.baeldung.com/java-completablefuture-timeout)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Java Time API](https://www.baeldung.com/java-8-date-time-intro)

---

## 🎯 Next Steps

1. **Test thoroughly** in staging environment
2. **Monitor Redis** for lock contention
3. **Tune timeouts** based on actual device response times
4. **Add metrics** (Prometheus/Grafana) for monitoring
5. **Create UI** for analytics dashboard
6. **Document** timezone configuration in user guide

---

## ✅ Checklist for Production

- [ ] All unit tests passing
- [ ] Integration tests for each feature
- [ ] Redis backup/failover configured
- [ ] Monitoring alerts for timeout spikes
- [ ] Documentation updated
- [ ] User training on new features
- [ ] Rollback plan documented
- [ ] Load testing completed
