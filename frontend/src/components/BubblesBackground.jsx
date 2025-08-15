import React, { useEffect } from "react";
import "./BubblesBackground.css";
import DeviceNodes from "./home/DeviceNodes.jsx"; // We'll put the CSS in a separate file

export default function BubblesBackground({props}) {


    return (
        <div className="gradient-bg">
            <div className="gradients-container">
                <div className="g1"></div>
                <div className="g2"></div>
                <div className="g3"></div>
                <div className="g4"></div>
                <div className="g5"></div>
            </div>
            <div className="text-container" >
                {props}
            </div>
        </div>
    );
}
