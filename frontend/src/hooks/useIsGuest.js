import { useAuth } from '../components/auth/AuthContext.jsx';

/**
 * Custom hook to check if the user is in guest mode (read-only)
 * Returns true if user is a guest, false otherwise
 */
export const useIsGuest = () => {
    const { isGuest } = useAuth();
    return isGuest;
};
