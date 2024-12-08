// src/components/PrivateRoute.js
import React from 'react';
import { Route, NavLink } from 'react-router-dom';
import {useAuth} from "./AuthContext.jsx";


// Protected route component
const PrivateRoute = ({ component: Component, ...rest }) => {
    const { isAuthenticated } = useAuth();

    return (
        <Route
            {...rest}
            render={props =>
                isAuthenticated ? (
                    <Component {...props} />
                ) : (
                    <a href="/login" />
                )
            }
        />
    );
};

export default PrivateRoute;
