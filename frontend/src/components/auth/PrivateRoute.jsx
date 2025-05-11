// src/components/PrivateRoute.js
import React from 'react';
import {Route, NavLink, Navigate} from 'react-router-dom';
import {useAuth} from "./AuthContext.jsx";


// Protected route component
const PrivateRoute = ({ element }) => {
    const { user } = useAuth();

    if (!user) {
        return <Navigate to="/signin" />;
    }

    return element;
};

export default PrivateRoute;
