import React from "react";
import { Handle } from "@xyflow/react";
import {Button} from "@mui/material";


export const BaseHandle = React.forwardRef(({ className, ...props }, ref) => (
    <div>
        <Handle ref={ref} className={className} {...props} />
        <Button>he</Button>
    </div>
));
BaseHandle.displayName = "BaseHandle";