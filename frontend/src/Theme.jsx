import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

export const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        primary: {
            main: '#e3f2fd',
        },
        secondary: {
            main: '#9ccc65',
        },
    },
});


