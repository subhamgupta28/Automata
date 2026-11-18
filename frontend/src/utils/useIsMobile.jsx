// utils/useIsMobile.jsx
import {useMediaQuery} from "@mui/material";
import {useTheme} from "@mui/material/styles";

/**
 * True when the viewport is narrower than the given MUI breakpoint
 * (default "md" = 900px, same threshold the rest of the app uses).
 * Reads the media query synchronously, so there is no desktop-layout flash
 * on the first render of a phone.
 */
export function useIsMobile(breakpoint = "md") {
    const theme = useTheme();
    return useMediaQuery(theme.breakpoints.down(breakpoint), {noSsr: true});
}
