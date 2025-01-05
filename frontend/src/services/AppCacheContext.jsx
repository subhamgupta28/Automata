import React, { createContext, useState, useContext, useEffect } from 'react';
import {getDevices} from "./apis.jsx";


export const useCachedDevices = () => {
    const { cache, cacheData } = useAppCache();
    const [devices, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Check if data is already cached
        if (cache["devices"]) {
            setData(cache["devices"]);
            console.log("cached")
        } else {
            const fetch = async () => {
                console.log("not cached")
                // If not, fetch data from the API
                const res = await getDevices();
                if (res){
                    setData(res);
                    cacheData("devices", res);  // Cache the fetched data

                }else{
                    setError("Something went wrong");
                }
            }
            fetch();
        }
        setLoading(false);
    }, [cache, cacheData]);

    return { devices, loading, error };
};

// Create the context for API cache
const AppCacheContext = createContext([]);

export const useAppCache = () => {
    return useContext(AppCacheContext);
};

export const AppCacheProvider = ({ children }) => {
    const [cache, setCache] = useState({});

    const cacheData = (key, data) => {
        setCache((prevCache) => ({ ...prevCache, [key]: data }));
    };

    return (
        <AppCacheContext.Provider value={{ cache, cacheData }}>
            {children}
        </AppCacheContext.Provider>
    );
};
