import { createTheme } from '@mui/material/styles';

export const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        primary: {
            main: 'rgb(244,255,87)', // Primary yellow accent
        },
        secondary: {
            main: 'rgb(120,126,45)', // Secondary blue for highlights
        },
        background: {
            default: '#000000', // Dark background for the main canvas
            // paper: '#1c1c1c', // Slightly lighter background for cards and paper
        },
        text: {
            primary: '#ffffff', // White text for easy readability
            secondary: '#b0b0b0', // Light gray text for secondary text
        },
        divider: '#444444', // Dividers to separate sections with a soft contrast
    },
    typography: {
        fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif', // Clean, modern font family
        h1: {
            fontSize: '2.5rem',
            fontWeight: 500,
            color: 'rgb(244,255,87)',
        },
        h2: {
            fontSize: '2rem',
            fontWeight: 500,
            color: '#ffffff',
        },
        body1: {
            fontSize: '1rem',
            color: '#e0e0e0', // Slightly lighter body text
        },
    },
    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    // backgroundColor: '#fce02b', // Primary color for buttons
                    // color: '#121212', // Text color on buttons
                    '&:hover': {
                        borderColor: '#e5c21b', // Darker yellow on hover
                    },
                },
            },
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

