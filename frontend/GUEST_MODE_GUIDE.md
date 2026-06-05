# Guest Mode Implementation Guide

## Overview

Guest Mode is a read-only access feature that allows specific users to view and explore the application without being able to make changes. Guest users have access to:

- **Home (/)** - View dashboard
- **Automations (/actions)** - View automation flows
- **Analytics (/analytics)** - View analytics and reports

## Guest User Access

### How Guests Login
1. Users click "Continue as Guest (Read-Only)" on the sign-in page
2. A temporary guest session is created with `isGuest: true` flag
3. Guests are redirected to the home page
4. A "Read-Only" badge appears in the user menu

### Features Available to Guests
- ✅ View all enabled routes
- ✅ Explore automation flows
- ✅ View analytics and reports
- ✅ Interact with read-only visualizations

### Features NOT Available to Guests
- ❌ Create/Edit/Delete devices
- ❌ Create/Edit/Delete automations
- ❌ Change device configurations
- ❌ Access restricted routes (Virtual Device, Dashboard Config, etc.)

## Making Components Read-Only

To make your components respect guest mode, use the `useIsGuest()` hook.

### Basic Implementation

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function MyComponent() {
  const isGuest = useIsGuest();

  return (
    <div>
      <input 
        disabled={isGuest}
        onChange={(e) => setValue(e.target.value)}
      />
      {!isGuest && (
        <button onClick={handleSave}>Save</button>
      )}
    </div>
  );
}
```

### Disabling Buttons in Guest Mode

```jsx
import { Button } from '@mui/material';
import { useIsGuest } from '../hooks/useIsGuest';

function ActionButtons() {
  const isGuest = useIsGuest();

  return (
    <>
      <Button 
        disabled={isGuest}
        onClick={handleCreate}
      >
        Create
      </Button>
      <Button 
        disabled={isGuest}
        onClick={handleEdit}
      >
        Edit
      </Button>
      <Button 
        disabled={isGuest}
        onClick={handleDelete}
        color="error"
      >
        Delete
      </Button>
    </>
  );
}
```

### Conditional Rendering

```jsx
function AdvancedForm() {
  const isGuest = useIsGuest();

  if (isGuest) {
    return <ReadOnlyView data={data} />;
  }

  return (
    <EditableForm 
      onSubmit={handleSubmit}
      onChange={handleChange}
    />
  );
}
```

### Showing Notifications to Guests

```jsx
import { useIsGuest } from '../hooks/useIsGuest';
import { useSnackbar } from 'notistack';

function RestrictedAction() {
  const isGuest = useIsGuest();
  const { enqueueSnackbar } = useSnackbar();

  const handleDelete = () => {
    if (isGuest) {
      enqueueSnackbar('This action is not available in guest mode', { 
        variant: 'info' 
      });
      return;
    }
    // Perform delete action
  };

  return (
    <button onClick={handleDelete}>
      Delete Item
    </button>
  );
}
```

## API Integration

### Protecting API Calls

```jsx
import { useIsGuest } from '../hooks/useIsGuest';
import { useSnackbar } from 'notistack';

function DataForm() {
  const isGuest = useIsGuest();
  const { enqueueSnackbar } = useSnackbar();

  const handleSubmit = async (data) => {
    if (isGuest) {
      enqueueSnackbar('Sign in to make changes', { variant: 'warning' });
      return;
    }

    try {
      await api.post('/data', data);
      enqueueSnackbar('Successfully saved', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar('Error saving data', { variant: 'error' });
    }
  };

  return (
    // Form content
  );
}
```

## Routing Protection

Routes are automatically protected by the `PrivateRoute` component. Guest users can only access:
- `/` (Home)
- `/actions` (Automations)
- `/analytics` (Analytics)

Attempting to access other routes will redirect guests back to home (`/`).

To restrict a route from guest access, verify the route is NOT in the `GUEST_ALLOWED_ROUTES` array in `PrivateRoute.jsx`.

## Storage

Guest mode data is stored in `localStorage` with the `user` key:

```javascript
{
  id: 'guest',
  email: 'guest@automata.local',
  name: 'Guest User',
  isGuest: true
}
```

When a guest logs out, this data is cleared from localStorage.

## Authentication Context

The `AuthContext` provides the `isGuest` flag:

```jsx
const { user, login, logout, loading, loginAsGuest, isGuest } = useAuth();
```

- `isGuest` - Boolean indicating if current user is a guest
- `loginAsGuest()` - Function to enter guest mode
- Other methods remain the same

## Security Considerations

1. **Guest mode is client-side read-only** - Always validate user permissions on the backend
2. **Guest users still have valid auth tokens** - Backend should verify permission levels
3. **Don't rely solely on client-side checks** - Always enforce restrictions server-side
4. **Session management** - Guest sessions are not persisted across browser restarts if storage is cleared

## Testing

To test guest mode:

1. Navigate to the sign-in page
2. Click "Continue as Guest (Read-Only)"
3. Verify only home, automations, and analytics are available
4. Verify edit/delete buttons are disabled
5. Try accessing restricted routes manually via URL (should redirect)

## Common Patterns

### Disable Form Inputs
```jsx
<TextField
  disabled={isGuest}
  value={formData.name}
  onChange={(e) => setFormData({...formData, name: e.target.value})}
/>
```

### Hide Edit Controls
```jsx
{!isGuest && (
  <Box sx={{ display: 'flex', gap: 2 }}>
    <EditButton />
    <DeleteButton />
  </Box>
)}
```

### Show Info Message
```jsx
{isGuest && (
  <Alert severity="info">
    You are viewing in read-only mode. Sign in to make changes.
  </Alert>
)}
```

## Troubleshooting

**Guest user losing access to allowed routes:**
- Check browser console for errors
- Verify `GUEST_ALLOWED_ROUTES` in `PrivateRoute.jsx` includes the route
- Clear localStorage and try again

**isGuest hook returning false:**
- Ensure you imported from the correct path: `../hooks/useIsGuest`
- Check that user is logged in with `isGuest: true` flag
- Verify `useAuth()` is available in component context

**Guest buttons still showing as enabled:**
- Make sure to use the `useIsGuest()` hook
- Check if condition is correctly applied to disabled prop
- Clear browser cache and reload
