// src/components/PrivateRoute.js
import React from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import { useAuth } from "./AuthContext.jsx";

export default function PrivateRoute({ element }) {
    const { user } = useAuth();
    const location = useLocation();

    if (!user) {
        return <Navigate to="/signin" replace />;
    }

    // Force re-mount of protected page when path changes
    return React.cloneElement(element, { key: location.pathname });
}

