// src/context/AuthContext.js
import React, {createContext, useState, useContext, useEffect} from 'react';
import isEmpty from "../../utils/Helper.jsx";

// Create a context for authentication
const AuthContext = createContext();

// Create a provider for authentication context
export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState({});

    // Check for stored user on mount
    useEffect(() => {
        const storedUser = JSON.parse(localStorage.getItem('user'));
        console.log("user",storedUser)
        if (!isEmpty(storedUser)) {
            setUser(storedUser);
        }
    }, []);

    const login = (userData) => {
        setUser(userData);
        localStorage.setItem('user', JSON.stringify(userData));
    };

    const logout = () => {
        setUser({});
        localStorage.removeItem('user');
    };

    return (
        <AuthContext.Provider value={{ user, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

// Custom hook to access auth context
export const useAuth = () => {
    return useContext(AuthContext);
};
