import React, {createContext, useContext, useEffect, useState} from "react";
import {getDevices} from "./apis.jsx";
import {useAuth} from "../components/auth/AuthContext.jsx";
import isEmpty from "../utils/Helper.jsx";

const AppCacheContext = createContext(null);

export const AppCacheProvider = ({children}) => {
    const [devices, setDevices] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const {user, loading: authLoading} = useAuth();

    useEffect(() => {
        let mounted = true;

        const fetchDevices = async () => {
            try {
                const res = await getDevices();
                if (mounted) {
                    setDevices(res);
                }
            } catch (e) {
                if (mounted) {
                    console.error("Error fetching devices:", e);
                    setError("Something went wrong");
                }
            } finally {
                if (mounted) {
                    setLoading(false);
                }
            }
        };

        // Only fetch devices if auth is ready and user is authenticated
        if (!authLoading && !isEmpty(user)) {
            fetchDevices();
        } else if (!authLoading && isEmpty(user)) {
            // No user logged in, don't fetch devices
            setDevices(null);
            setLoading(false);
        }

        return () => {
            mounted = false;
        };
    }, [authLoading, user]); // 👈 depends on auth state

    return (
        <AppCacheContext.Provider value={{devices, loading, error}}>
            {children}
        </AppCacheContext.Provider>
    );
};

export const useCachedDevices = () => {
    const context = useContext(AppCacheContext);

    if (!context) {
        throw new Error("useCachedDevices must be used inside AppCacheProvider");
    }

    return context;
};
