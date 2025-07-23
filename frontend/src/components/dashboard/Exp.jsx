import React, { useRef, useState, useEffect } from 'react';
import SpotifyPlayer from "../integrations/SpotifyPlayer.jsx";
import PersonTracker from "../charts/PersonTracker.jsx";
import AudioReactiveWled from "../integrations/AudioReactiveWled.jsx";


const Exp = () => {
    return (
        <div style={{background:"white"}}>
            <AudioReactiveWled/>
        </div>
    );
};

export default Exp;
