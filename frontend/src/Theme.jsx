import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

export const darkTheme = createTheme({
    colorSchemes: { light: true, dark: true },
    palette: {
        mode: 'dark',
        primary: {
            main: '#fce02b',
            // light: will be calculated from palette.primary.main,
            // dark: will be calculated from palette.primary.main,
            // contrastText: will be calculated to contrast with palette.primary.main
        },
        // secondary: {
        //     main: '#E0C2FF',
        //     light: '#F5EBFF',
        //     // dark: will be calculated from palette.secondary.main,
        //     contrastText: '#47008F',
        // },
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

