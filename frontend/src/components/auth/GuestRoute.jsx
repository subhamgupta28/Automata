import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';
import isEmpty from '../../utils/Helper.jsx';

// Routes allowed for guest users
const GUEST_ALLOWED_ROUTES = ['/', '/actions', '/analytics'];

export default function GuestRoute({ element, path }) {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return <div style={{ color: '#fff', textAlign: 'center' }}>Loading...</div>;
    }

    // Check if current path is allowed for guests
    const isGuestAllowedRoute = GUEST_ALLOWED_ROUTES.includes(path);
    const isGuest = user?.isGuest === true;
    const isLoggedIn = !isEmpty(user);

    if (!isLoggedIn && !isGuest) {
        return <Navigate to="/signin" replace state={{ from: location }} />;
    }

    // If guest tries to access non-guest routes, redirect to home
    if (isGuest && !isGuestAllowedRoute) {
        return <Navigate to="/" replace />;
    }

    return React.cloneElement(element, { key: location.pathname });
}
