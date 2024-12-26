import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

export const darkTheme = createTheme({
    colorSchemes: { light: true, dark: true },
    palette: {
        mode: 'dark',
        primary: {
            main: '#e8eaf1',
        },
        secondary: {
            main: '#9ccc65',
        },
    },
});

export const lightTheme = createTheme({
    palette: {
        mode: 'light',
        primary: {
            main: '#47abf5',
        },
        secondary: {
            main: '#9ccc65',
        },
    },
});

