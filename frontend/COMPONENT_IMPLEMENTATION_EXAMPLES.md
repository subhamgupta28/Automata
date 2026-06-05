# Guest Mode Component Implementation Examples

This document provides specific code examples for making components read-only in guest mode.

## Import Pattern

For any component, add this import:
```jsx
import { useIsGuest } from '../hooks/useIsGuest';
```

Then at the top of your component:
```jsx
const isGuest = useIsGuest();
```

## Common Patterns

### Pattern 1: Disable Input Fields

```jsx
<TextField
  label="Device Name"
  value={name}
  onChange={(e) => setName(e.target.value)}
  disabled={isGuest}
  fullWidth
/>
```

### Pattern 2: Conditional Button Rendering

```jsx
{!isGuest && (
  <>
    <button onClick={handleCreate}>Create</button>
    <button onClick={handleDelete}>Delete</button>
  </>
)}
```

### Pattern 3: Disabled Buttons with Tooltip

```jsx
import { Tooltip } from '@mui/material';

<Tooltip title={isGuest ? "Not available in guest mode" : ""}>
  <span>
    <button 
      disabled={isGuest}
      onClick={handleEdit}
    >
      Edit
    </button>
  </span>
</Tooltip>
```

### Pattern 4: Show Alert Message

```jsx
import { Alert } from '@mui/material';

{isGuest && (
  <Alert severity="info" sx={{ mb: 2 }}>
    You are viewing in read-only mode. Sign in to make changes.
  </Alert>
)}
```

## Component-Specific Examples

### ActionBoard.jsx (Automations)

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

export default function ActionBoard() {
  const isGuest = useIsGuest();
  const [automations, setAutomations] = useState([]);

  // ... existing code ...

  return (
    <Box>
      {isGuest && (
        <Alert severity="info" sx={{ mb: 2 }}>
          You are viewing automations in read-only mode.
        </Alert>
      )}
      
      <Box sx={{ mb: 2 }}>
        {!isGuest && (
          <Button 
            variant="contained" 
            onClick={handleCreateNew}
          >
            Create Automation
          </Button>
        )}
      </Box>

      <Grid container spacing={2}>
        {automations.map(automation => (
          <Grid item xs={12} sm={6} key={automation.id}>
            <Card>
              <CardContent>
                <Typography>{automation.name}</Typography>
              </CardContent>
              <CardActions>
                <Button 
                  disabled={isGuest}
                  onClick={() => handleEdit(automation.id)}
                >
                  Edit
                </Button>
                <Button 
                  disabled={isGuest}
                  color="error"
                  onClick={() => handleDelete(automation.id)}
                >
                  Delete
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
```

### Devices.jsx (Device Management)

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

export default function Devices() {
  const isGuest = useIsGuest();

  return (
    <Box>
      {isGuest && (
        <Alert severity="info">
          You are viewing devices in read-only mode.
        </Alert>
      )}

      <Box sx={{ mb: 2 }}>
        {!isGuest && (
          <>
            <Button onClick={handleAddDevice}>
              Add Device
            </Button>
            <Button onClick={handleImport}>
              Import Devices
            </Button>
          </>
        )}
      </Box>

      <DeviceList 
        devices={devices}
        onEdit={isGuest ? null : handleEdit}
        onDelete={isGuest ? null : handleDelete}
        disabled={isGuest}
      />
    </Box>
  );
}
```

### DashboardV2.jsx (Home/Dashboard)

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

export default function DashboardV2() {
  const isGuest = useIsGuest();

  return (
    <Box>
      {isGuest && (
        <Alert severity="warning">
          Guest mode - read-only view
        </Alert>
      )}

      <Grid container spacing={2}>
        {/* Dashboard cards - mostly read-only anyway */}
        <Grid item xs={12} sm={6} md={3}>
          <StatCard title="Devices" value={deviceCount} />
        </Grid>
        
        {/* Only show edit/config if not guest */}
        {!isGuest && (
          <Grid item xs={12} sm={6} md={3}>
            <Button onClick={handleConfigureDashboard}>
              Configure Dashboard
            </Button>
          </Grid>
        )}
      </Grid>
    </Box>
  );
}
```

### AnalyticsView.jsx (Analytics - Minimal Changes)

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

export default function AnalyticsView() {
  const isGuest = useIsGuest();

  return (
    <Box>
      {isGuest && (
        <Alert severity="info">
          Viewing analytics in read-only mode
        </Alert>
      )}

      {/* Analytics charts - already read-only */}
      <AnalyticsCharts data={data} />

      {/* Only show export if not guest */}
      {!isGuest && (
        <Box sx={{ mt: 2 }}>
          <Button onClick={handleExport}>
            Export Data
          </Button>
        </Box>
      )}
    </Box>
  );
}
```

## Form Component Example

```jsx
import { useIsGuest } from '../hooks/useIsGuest';

function EditAutomationForm({ automation, onSave, onCancel }) {
  const isGuest = useIsGuest();
  const [formData, setFormData] = useState(automation);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (isGuest) return; // Guard clause
    onSave(formData);
  };

  if (isGuest) {
    return (
      <Box>
        <Alert severity="info">
          You cannot edit automations in guest mode.
        </Alert>
        <Box sx={{ mt: 2 }}>
          <Button onClick={onCancel}>Close</Button>
        </Box>
      </Box>
    );
  }

  return (
    <form onSubmit={handleSubmit}>
      <TextField
        label="Name"
        value={formData.name}
        onChange={(e) => setFormData({...formData, name: e.target.value})}
        fullWidth
      />
      <TextField
        label="Description"
        value={formData.description}
        onChange={(e) => setFormData({...formData, description: e.target.value})}
        fullWidth
        multiline
        rows={4}
      />
      <Box sx={{ mt: 2 }}>
        <Button type="submit" variant="contained">
          Save
        </Button>
        <Button onClick={onCancel}>Cancel</Button>
      </Box>
    </form>
  );
}
```

## Dialog/Modal Example

```jsx
import { useIsGuest } from '../hooks/useIsGuest';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button } from '@mui/material';

function CreateDeviceDialog({ open, onClose }) {
  const isGuest = useIsGuest();

  if (isGuest) {
    return (
      <Dialog open={open} onClose={onClose}>
        <DialogTitle>Create Device</DialogTitle>
        <DialogContent>
          <Alert severity="info">
            You cannot create devices in guest mode. Sign in to continue.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Create Device</DialogTitle>
      <DialogContent>
        {/* Device creation form */}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={handleCreate} variant="contained">
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
```

## Data Grid with Read-Only Actions

```jsx
import { DataGrid } from '@mui/x-data-grid';
import { useIsGuest } from '../hooks/useIsGuest';

function DeviceDataGrid({ devices }) {
  const isGuest = useIsGuest();

  const columns = [
    { field: 'id', headerName: 'ID', width: 90 },
    { field: 'name', headerName: 'Name', width: 150 },
    { field: 'status', headerName: 'Status', width: 130 },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 150,
      sortable: false,
      renderCell: (params) => (
        <>
          <button 
            disabled={isGuest}
            onClick={() => handleEdit(params.row.id)}
          >
            Edit
          </button>
          <button 
            disabled={isGuest}
            onClick={() => handleDelete(params.row.id)}
          >
            Delete
          </button>
        </>
      ),
    },
  ];

  return <DataGrid rows={devices} columns={columns} />;
}
```

## API Call Protection

```jsx
import { useIsGuest } from '../hooks/useIsGuest';
import { useSnackbar } from 'notistack';

function DataForm() {
  const isGuest = useIsGuest();
  const { enqueueSnackbar } = useSnackbar();

  const handleSave = async (data) => {
    if (isGuest) {
      enqueueSnackbar(
        'Sign in to save changes',
        { variant: 'warning' }
      );
      return;
    }

    try {
      await api.post('/data', data);
      enqueueSnackbar('Saved successfully', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar('Error saving data', { variant: 'error' });
    }
  };

  return (
    <form onSubmit={(e) => {
      e.preventDefault();
      handleSave(formData);
    }}>
      {/* form fields */}
    </form>
  );
}
```

## Summary

**Common patterns:**
1. Import `useIsGuest` hook
2. Get `const isGuest = useIsGuest()`
3. Use `disabled={isGuest}` for buttons/inputs
4. Use `{!isGuest && <Component />}` for conditional rendering
5. Add alert messages explaining read-only mode

**For each component:**
- Disable all create/edit/delete buttons
- Show info message to guests
- Keep read operations working
- Prevent form submission for guests
