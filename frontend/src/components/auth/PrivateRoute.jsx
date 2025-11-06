import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';
import isEmpty from '../../utils/Helper.jsx';

export default function PrivateRoute({ element }) {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        // You can replace this with a spinner, skeleton, or splash screen
        return <div style={{ color: '#fff', textAlign: 'center' }}>Loading...</div>;
    }

    if (isEmpty(user)) {
        return <Navigate to="/signin" replace state={{ from: location }} />;
    }

    return React.cloneElement(element, { key: location.pathname });
}
