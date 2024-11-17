import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// const BROWSER_URL = window.location.href;
// const apiUrl = mode === 'development'
//     ? 'http://localhost:8080' // Local API server for development
//     : 'api'; // Production API server

// export default defineConfig({
//     plugins: [react()],
//     proxy: {
//         '/api': {
//             target: 'http://localhost:8080',
//             changeOrigin: true,
//             secure: false,
//             rewrite: (path) => path.replace(/^\/api/, '')
//         }
//     },
//     define: {
//         __API_URL__: JSON.stringify('http://localhost:8080'),
//         global: 'window',
//     },
//     build: {
//         outDir: '../src/main/resources/static', // Change this to your desired folder
//     },
// })

export default defineConfig(({ command, mode, isSsrBuild, isPreview }) => {

    return {
        plugins: [react()],
        define: {
            __API_MODE__: JSON.stringify(command), // Define the API URL for use in the app
            global: 'window', // Ensure global is set to window for browser environment
        },
        build: {
            outDir: '../src/main/resources/static', // Change this to your desired folder
        },
    };
})