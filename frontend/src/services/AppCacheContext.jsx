import React, {createContext, useContext, useEffect, useState} from "react";
import {getDevices} from "./apis.jsx";

const AppCacheContext = createContext(null);

export const AppCacheProvider = ({children}) => {
    const [devices, setDevices] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

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
                    setError("Something went wrong");
                }
            } finally {
                if (mounted) {
                    setLoading(false);
                }
            }
        };

        fetchDevices();

        return () => {
            mounted = false;
        };
    }, []); // 👈 runs once

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
