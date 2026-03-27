import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'


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