import { useCallback } from 'react';
import { Handle, Position } from '@xyflow/react';

const handleStyle = { top: 10 };

function Node({ data, isConnectable }) {
    const onChange = useCallback((evt) => {
        console.log(evt.target.value);
    }, []);

    return (
        <div className="text-updater-node">
            <div className={'card'} style={{ padding: '12px' }}>
                <label htmlFor="text">Node 1</label>

            </div>
            <Handle
                type="source"
                position={Position.Right}
                id="a"
                style={handleStyle}
                isConnectable={isConnectable}
            />
            <Handle
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
        </div>
    );
}

export default Node;
