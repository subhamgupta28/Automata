// HealthCheckService.js
import { useEffect } from 'react';
import axios from 'axios';

const HealthCheck = () => {
    // useEffect(() => {
    //     const checkHealth = async () => {
    //         try {
    //             const response = await axios.get('http://raspberry.local:8010/api/v1/main/healthCheck');
    //             console.log('[HealthCheck] Success:', response.data);
    //         } catch (err) {
    //             console.error('[HealthCheck] Failed:', err.message);
    //         }
    //     };
    //
    //     checkHealth(); // initial

        // const interval = setInterval(checkHealth, 30_000); // every 30 sec

        // return () => clearInterval(interval); // cleanup
    // }, []);

    return null; // no UI
};

export default HealthCheck;
