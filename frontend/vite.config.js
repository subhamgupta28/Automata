import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
// import visualizer from "rollup-plugin-visualizer";

export default defineConfig(({command, mode, isSsrBuild, isPreview}) => {

    return {
        plugins: [
            react(),
            // visualizer({open: true, filename: 'bundle-stats.html'})
        ],
        define: {
            __API_MODE__: JSON.stringify(command), // Define the API URL for use in the app
            global: 'window', // Ensure global is set to window for browser environment
        },
        build: {
            outDir: '../src/main/resources/static',
            rollupOptions: {
                output: {
                    manualChunks: {
                        vendor: ['react', 'react-dom'],
                        router: ['react-router-dom'],
                        ws: ['@stomp/stompjs', 'sockjs-client'],
                        mui: ['@mui/material', '@mui/icons-material'],
                        charts: ['@mui/x-charts', 'recharts'],
                        flow: ['@xyflow/react'],
                    }
                }
            }
        }
    };
})