import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

export const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        primary: {
            main: '#d5d5d5',
        },
        secondary:{
            main: '#4085ee',

        }
    },
});


