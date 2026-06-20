import React, {createContext, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import {getHomes} from '../../services/apis.jsx';
import {useAuth} from '../auth/AuthContext.jsx';
import {LinearProgress} from "@mui/material"; // Assuming AuthContext is in this path

const HomeContext = createContext();

export const HomeProvider = ({children}) => {
    const {user, loading: authLoading} = useAuth();
    const [homes, setHomes] = useState([]);
    const [selectedHomeId, setSelectedHomeId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchHomes = useCallback(async () => {
        if (authLoading) {
            // console.log("HomeContext: Auth is loading, skipping fetchHomes.");
            return;
        }

        const userId = user?.id || user?.userId;

        if (!user || !userId) {
            // console.log("HomeContext: No user or user ID found, skipping fetchHomes.", {user});
            setLoading(false);
            return;
        }

        // console.log("HomeContext: User found, proceeding to fetch homes for user:", userId);
        setLoading(true);
        setError(null);
        try {
            const userHomes = await getHomes();
            // console.log("HomeContext: Fetched homes successfully.", userHomes);
            setHomes(userHomes);

            const storedHomeId = localStorage.getItem('selectedHomeId');
            if (storedHomeId && userHomes.some(home => home.id === storedHomeId)) {
                setSelectedHomeId(storedHomeId);
            } else if (userHomes.length > 0) {
                setSelectedHomeId(userHomes[0].id);
                localStorage.setItem('selectedHomeId', userHomes[0].id);
            } else {
                setSelectedHomeId(null);
                localStorage.removeItem('selectedHomeId');
            }
        } catch (err) {
            console.error("HomeContext: Failed to fetch homes:", err);
            setError("Failed to load homes.");
            setHomes([]);
            setSelectedHomeId(null);
            localStorage.removeItem('selectedHomeId');
        } finally {
            setLoading(false);
        }
    }, [user, authLoading]);

    useEffect(() => {
        fetchHomes();
    }, [fetchHomes]);

    const selectHome = useCallback((homeId) => {
        if (homes.some(home => home.id === homeId)) {
            setSelectedHomeId(homeId);
            localStorage.setItem('selectedHomeId', homeId);
            window.location.reload();
        } else {
            console.warn(`Attempted to select invalid homeId: ${homeId}`);
        }
    }, [homes]);

    const selectedHome = useMemo(() => {
        return homes.find(home => home.id === selectedHomeId);
    }, [homes, selectedHomeId]);

    return (
        <HomeContext.Provider value={{
            homes,
            selectedHomeId,
            selectedHome,
            selectHome,
            loading,
            error,
            fetchHomes,
        }}>
            {loading ? (
                <div style={{
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    height: '100vh',
                    width: '100vw',
                    background: 'transparent'
                }}>
                    <div style={{
                        color: '#ffd821', width: '100%',
                        height: '100dvh',
                        background: 'transparent',
                        backdropFilter: 'blur(4px)',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center'
                    }}>
                        <LinearProgress aria-label="Loading…" style={{width: '80%'}}/>
                    </div>
                </div>
            ) : children}
        </HomeContext.Provider>
    );
};

export const useHome = () => useContext(HomeContext);