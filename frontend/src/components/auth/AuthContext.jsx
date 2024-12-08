// src/context/AuthContext.js
import React, { createContext, useState, useContext } from 'react';

// Create a context for authentication
const AuthContext = createContext();

// Create a provider for authentication context
export const AuthProvider = ({ children }) => {
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    const login = () => setIsAuthenticated(true);
    const logout = () => setIsAuthenticated(false);

    return (
        <AuthContext.Provider value={{ isAuthenticated, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

// Custom hook to access auth context
export const useAuth = () => {
    return useContext(AuthContext);
};
