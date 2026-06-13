import React, {createContext, useContext, useEffect, useState} from 'react';
import isEmpty from "../../utils/Helper.jsx";
import {guestLoginReq} from '../../services/apis.jsx';
import {registerLogoutCallback} from "../../services/CustomAxios.jsx"; // ← import registration fn

const AuthContext = createContext();

export const AuthProvider = ({children}) => {
    const [user, setUser] = useState({});
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // ─── FIX 2 (continued): register so the interceptor can clear React state ──
        // This runs once on mount, before any API call can possibly 401.
        registerLogoutCallback(() => {
            setUser({});
        });
    }, []);

    useEffect(() => {
        try {
            const storedUser = JSON.parse(localStorage.getItem('user'));
            if (storedUser && !isEmpty(storedUser)) {
                setUser(storedUser);
            }
        } catch (err) {
            console.error("Error parsing stored user:", err);
            localStorage.removeItem('user');
        } finally {
            setLoading(false);
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

    const loginAsGuest = async () => {
        try {
            const guestUser = await guestLoginReq();
            login(guestUser);
            return guestUser;
        } catch (error) {
            console.error("Guest login failed:", error);
            throw error;
        }
    };

    // Check if user has guest role
    const isGuest = user?.role?.toLowerCase() === 'guest';

    return (
        <AuthContext.Provider value={{user, login, logout, loading, loginAsGuest, isGuest}}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
