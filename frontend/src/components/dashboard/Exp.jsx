import React, { useRef, useState, useEffect } from 'react';
import SpotifyPlayer from "../integrations/SpotifyPlayer.jsx";
import PersonTracker from "../charts/PersonTracker.jsx";


const Exp = () => {
    return (
        <div style={{background:"white"}}>
            <PersonTracker/>
        </div>
    );
};

export default Exp;
