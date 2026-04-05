import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({command, mode, isSsrBuild, isPreview}) => {
    return {
        plugins: [react()],
        define: {
            __API_MODE__: JSON.stringify(command),
            global: 'window',
        },
        build: {
            outDir: '../src/main/resources/static',
            rolldownOptions: {
                output: {
                    manualChunks: (id) => {
                        if (id.includes('node_modules')) {
                            if (id.includes('react-router-dom')) return 'router';
                            if (id.includes('@stomp') || id.includes('sockjs-client')) return 'ws';
                            if (id.includes('@mui/x-charts') || id.includes('recharts')) return 'charts';
                            if (id.includes('@mui')) return 'mui';
                            if (id.includes('@xyflow')) return 'flow';
                            if (id.includes('react-dom') || id.includes('react/')) return 'vendor';
                        }
                    }
                }
            }
        }
    };
})