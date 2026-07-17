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
            chunkSizeWarningLimit: 900,
            outDir: '../src/main/resources/static',
            rolldownOptions: {
                output: {
                    codeSplitting: {
                        groups: [
                            {name: 'router', test: /node_modules\/react-router-dom/},
                            {name: 'ws', test: /node_modules\/(@stomp|sockjs-client)/},
                            {name: 'charts', test: /node_modules\/(@mui\/x-charts|recharts)/},
                            {name: 'mui', test: /node_modules\/@mui/},
                            {name: 'flow', test: /node_modules\/@xyflow/},
                            {name: 'vendor', test: /node_modules\/(react-dom|react)\//},
                        ]
                    }
                }
            }
        }
    };
})