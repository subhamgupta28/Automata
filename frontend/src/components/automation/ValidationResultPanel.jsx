import React, {useMemo, useState} from "react";
import {
    Alert,
    AlertTitle,
    Box,
    Chip,
    Collapse,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    List,
    ListItem,
    ListItemIcon,
    ListItemText,
    Stack,
    Tab,
    Tabs,
    Typography,
} from "@mui/material";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";

const SEVERITY_META = {
    ERROR: {color: "error", icon: <ErrorOutlineIcon color="error"/>},
    WARN: {color: "warning", icon: <WarningAmberIcon color="warning"/>},
    INFO: {color: "info", icon: <InfoOutlinedIcon color="info"/>},
};

function IssueList({issues}) {
    if (!issues.length) {
        return (
            <Box sx={{p: 2}}>
                <Typography variant="body2" color="text.secondary">
                    No issues in this category.
                </Typography>
            </Box>
        );
    }

    return (
        <List dense disablePadding>
            {issues.map((issue, idx) => {
                const meta = SEVERITY_META[issue.severity] ?? SEVERITY_META.INFO;
                return (
                    <React.Fragment key={idx}>
                        <ListItem alignItems="flex-start">
                            <ListItemIcon sx={{minWidth: 36, mt: 0.5}}>
                                {meta.icon}
                            </ListItemIcon>
                            <ListItemText
                                primary={
                                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                        {issue.nodeId && (
                                            <Chip
                                                label={issue.nodeId}
                                                size="small"
                                                variant="outlined"
                                            />
                                        )}
                                        <Typography variant="body2">{issue.message}</Typography>
                                    </Stack>
                                }
                            />
                        </ListItem>
                        {idx < issues.length - 1 && <Divider component="li"/>}
                    </React.Fragment>
                );
            })}
        </List>
    );
}

const DIALOG_PAPER_SX = {
    backgroundColor: 'rgba(255, 255, 255, 0.0)',
    backdropFilter: 'blur(8px)',
};
/**
 * Renders a validate() ValidationResult:
 *   { issues: [{ severity: 'ERROR' | 'WARN' | 'INFO', nodeId, message }] }
 *
 * Props:
 *  - result: the ValidationResult object (or null/undefined before first run)
 *  - loading: boolean, shows a spinner while POST /validate is in flight
 *  - onValidate: callback fired when the "Validate" button is clicked
 *  - onIssueClick: optional (issue) => void, e.g. to focus a node in the editor
 */
export default function ValidationResultPanel({
                                                  open,
                                                  result,
                                                  loading = false,
                                                  onValidate,
                                                  onIssueClick,
                                                  onClose
                                              }) {
    const [tab, setTab] = useState("ERROR");

    const grouped = useMemo(() => {
        const issues = result?.issues ?? [];
        return {
            ERROR: issues.filter((i) => i.severity === "ERROR"),
            WARN: issues.filter((i) => i.severity === "WARN"),
            INFO: issues.filter((i) => i.severity === "INFO"),
        };
    }, [result]);

    const totalIssues = (result?.issues ?? []).length;
    const hasErrors = grouped.ERROR.length > 0;

    return (
        <Dialog open={open} onClose={onClose}
                PaperProps={{sx: {...DIALOG_PAPER_SX, padding: '12px', borderRadius: '12px'}}}>
            <DialogTitle sx={{padding: '10px'}}/>
            <Typography sx={{fontWeight: 700, fontSize: '14px'}}>
                Automation Validator
            </Typography>
            <DialogContent>
                {!loading && result && (
                    <Box sx={{mt: 2}}>
                        {totalIssues === 0 ? (
                            <Alert severity="success" icon={<CheckCircleOutlineIcon/>}>
                                Automation graph is valid.
                            </Alert>
                        ) : (
                            <Alert severity={hasErrors ? "error" : "warning"}>
                                <AlertTitle>
                                    {hasErrors ? "Cannot save — errors found" : "Saved with warnings"}
                                </AlertTitle>
                                {result.summary
                                    ? result.summary
                                    : `${grouped.ERROR.length} error(s), ${grouped.WARN.length} warning(s), ${grouped.INFO.length} info`}
                            </Alert>
                        )}

                        {totalIssues > 0 && (
                            <Box sx={{mt: 2}}>
                                <Tabs
                                    value={tab}
                                    onChange={(_, v) => setTab(v)}
                                    variant="fullWidth"
                                >
                                    <Tab
                                        value="ERROR"
                                        label={`Errors (${grouped.ERROR.length})`}
                                        disabled={grouped.ERROR.length === 0}
                                    />
                                    <Tab
                                        value="WARN"
                                        label={`Warnings (${grouped.WARN.length})`}
                                        disabled={grouped.WARN.length === 0}
                                    />
                                    <Tab
                                        value="INFO"
                                        label={`Info (${grouped.INFO.length})`}
                                        disabled={grouped.INFO.length === 0}
                                    />
                                </Tabs>

                                <Box
                                    sx={{
                                        mt: 1,
                                        maxHeight: 320,
                                        overflowY: "auto",
                                        cursor: onIssueClick ? "pointer" : "default",
                                    }}
                                    onClick={(e) => {
                                        if (!onIssueClick) return;
                                        // simple delegation: find nearest issue index via data attr if needed
                                    }}
                                >
                                    <Collapse in={tab === "ERROR"} unmountOnExit>
                                        <IssueList issues={grouped.ERROR}/>
                                    </Collapse>
                                    <Collapse in={tab === "WARN"} unmountOnExit>
                                        <IssueList issues={grouped.WARN}/>
                                    </Collapse>
                                    <Collapse in={tab === "INFO"} unmountOnExit>
                                        <IssueList issues={grouped.INFO}/>
                                    </Collapse>
                                </Box>
                            </Box>
                        )}
                    </Box>
                )}
            </DialogContent>
        </Dialog>
    );
}

/**
 * Example usage against the given endpoint:
 *
 * const [result, setResult] = useState(null);
 * const [loading, setLoading] = useState(false);
 *
 * const handleValidate = async () => {
 *   setLoading(true);
 *   try {
 *     const res = await fetch("/api/automations/validate", {
 *       method: "POST",
 *       headers: {
 *         "Content-Type": "application/json",
 *         "X-Home-Id": homeId,
 *       },
 *       body: JSON.stringify(automationDetail),
 *     });
 *     const data = await res.json(); // ValidationResult, even on 422
 *     setResult(data);
 *   } finally {
 *     setLoading(false);
 *   }
 * };
 *
 * <ValidationResultPanel result={result} loading={loading} onValidate={handleValidate} />
 */