import React from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {useAuth} from './AuthContext.jsx';
import isEmpty from '../../utils/Helper.jsx';

// Routes allowed for guest users
const GUEST_ALLOWED_ROUTES = ['/', '/actions', '/analytics', '/dashboard', '/automation-analytics'];

export default function PrivateRoute({element, path, requiredRole}) {
    const {user, loading, isGuest} = useAuth();
    const location = useLocation();

    if (loading) {
        // You can replace this with a spinner, skeleton, or splash screen
        return <div style={{color: '#fff', textAlign: 'center'}}>Loading...</div>;
    }

    if (isEmpty(user)) {
        return <Navigate to="/signin" replace state={{from: location}}/>;
    }

    // Check if user has required role
    if (requiredRole && user?.role?.toUpperCase() !== requiredRole.toUpperCase()) {
        return <Navigate to="/" replace/>;
    }

    // Allow guests on specific routes only
    if (isGuest && path && !GUEST_ALLOWED_ROUTES.includes(path)) {
        return <Navigate to="/" replace/>;
    }

    return React.cloneElement(element, {key: location.pathname});
}
