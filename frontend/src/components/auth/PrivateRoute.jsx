// src/components/PrivateRoute.js
import React from 'react';
import {Navigate} from 'react-router-dom';

// Protected route component
const PrivateRoute = ({ element }) => {
    const storedUser = JSON.parse(localStorage.getItem('user'));
    if (!storedUser) {
        return <Navigate to="/signin" />;
    }

    return element;
};

export default PrivateRoute;
