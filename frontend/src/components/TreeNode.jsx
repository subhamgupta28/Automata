import {getDataByDeviceId} from "../services/apis.jsx";
import React, {useEffect, useState} from 'react';

const Tree = ({ node }) => {
    return (
        <div style={{ marginLeft: '20px' }}>
            <div className="card" style={{ margin: '8px', padding:'4px', width:'20%' }} >
                {
                    node.displayName || node.date
                }
            </div>
            {node.values && node.values.length > 0 && (
                <div>
                    {node.values.map((child, index) => (
                        <Tree key={index} node={child} />
                    ))}
                </div>
            )}
        </div>
    );
};

const TreeNode = () => {
    const [data, setData] = useState([]);

    useEffect(() => {
        getDataByDeviceId("66d36ec988b2900e1ee74c76").then((data) => {
            console.log(data);
            setData(data);
        })
    }, [])

    return (
        <div>
            <Tree node={data} />
        </div>
    );
};

export default TreeNode;
