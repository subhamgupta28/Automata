import React from 'react';
import './FloorLayout.css';

export default function FloorLayout() {
    return (
        <div className="floorplan">
            <div className="room living-room">Living Room</div>
            <div className="room kitchen">Kitchen</div>
            <div className="room mudroom">Mudroom</div>
            <div className="room bedroom">Bedroom</div>
            <div className="room bathroom">Bathroom</div>

            {/* Doors */}
            <div className="door door1"></div>
            <div className="door door2"></div>
            <div className="door door3"></div>
            <div className="door door4"></div>
        </div>
    );
};


