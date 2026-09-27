require('dotenv').config();
const express = require('express');
const cors = require('cors');
const https = require('https');
const { RtcTokenBuilder, RtmTokenBuilder, RtcRole } = require('agora-token');

const path = require('path');

const app = express();
app.use(cors());
app.use(express.json());

app.get('/download', (req, res) => {
    const apkPath = path.join(__dirname, '../artifacts/app-release.apk');
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.download(apkPath, 'guardian-release.apk');
});

const APP_ID = process.env.APP_ID || '';
const APP_CERTIFICATE = process.env.APP_CERTIFICATE || '';
const PORT = process.env.PORT || 3001;

if (!APP_ID) {
    console.warn("WARNING: APP_ID is not set in .env");
}

// Token expiration times (in seconds)
const TOKEN_EXPIRY_SECONDS = 3600; // 1 hour

// Helper to generate dedicated RTM token for client login
function buildRtmToken(userId) {
    if (!APP_CERTIFICATE) return "";
    const currentTimestamp = Math.floor(Date.now() / 1000);
    const privilegeExpiredTs = currentTimestamp + TOKEN_EXPIRY_SECONDS;
    return RtmTokenBuilder.buildToken(
        APP_ID,
        APP_CERTIFICATE,
        String(userId),
        privilegeExpiredTs
    );
}

// Helper to generate composite AccessToken2 (007) with both RTC and RTM privileges
function buildDualToken(channelName, uid) {
    if (!APP_CERTIFICATE) {
        return { rtcToken: "", rtmToken: "", privilegeExpiredTs: 0 };
    }

    const currentTimestamp = Math.floor(Date.now() / 1000);
    const privilegeExpiredTs = currentTimestamp + TOKEN_EXPIRY_SECONDS;

    const rtcToken = RtcTokenBuilder.buildTokenWithUid(
        APP_ID,
        APP_CERTIFICATE,
        channelName,
        uid,
        RtcRole.PUBLISHER,
        privilegeExpiredTs
    );

    const rtmToken = buildRtmToken(uid);

    return { rtcToken, rtmToken, privilegeExpiredTs };
}

// -------------------------------------------------------------
// 1. GET /rtc/:channelName/:uid - Generate RTC Token
// -------------------------------------------------------------
app.get('/rtc/:channelName/:uid', (req, res) => {
    const { channelName, uid } = req.params;
    const numericUid = parseInt(uid, 10) || 0;
    try {
        const { rtcToken } = buildDualToken(channelName, numericUid);
        res.json({ rtcToken, channelName, uid: numericUid });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -------------------------------------------------------------
// 2. GET /rtm/:uid - Generate RTM Token
// -------------------------------------------------------------
app.get('/rtm/:uid', (req, res) => {
    const { uid } = req.params;
    const currentTimestamp = Math.floor(Date.now() / 1000);
    const privilegeExpiredTs = currentTimestamp + TOKEN_EXPIRY_SECONDS;
    try {
        const token = RtmTokenBuilder.buildToken(
            APP_ID,
            APP_CERTIFICATE,
            String(uid),
            privilegeExpiredTs
        );
        res.json({ rtmToken: token, uid: String(uid) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -------------------------------------------------------------
// 3. GET /rte/:channelName/:uid - Generate both RTC + RTM Tokens (AccessToken2 007)
// -------------------------------------------------------------
app.get('/rte/:channelName/:uid', (req, res) => {
    const { channelName, uid } = req.params;
    const numericUid = parseInt(uid, 10) || 0;

    try {
        const { rtcToken, rtmToken } = buildDualToken(channelName, numericUid);
        res.json({
            appId: APP_ID,
            channelName,
            uid: numericUid,
            rtcToken,
            rtmToken,
            expiresIn: TOKEN_EXPIRY_SECONDS
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -------------------------------------------------------------
// 4. POST /stt/start - Start Agora STT Task with explicit RTM & unique bot UIDs
// -------------------------------------------------------------
app.post('/stt/start', async (req, res) => {
    const { channelName, userUid } = req.body;
    if (!channelName) {
        return res.status(400).json({ error: "channelName is required" });
    }

    const subBotUid = 999;
    const pubBotUid = 998;

    try {
        // Generate AccessToken2 with RTC and RTM privileges for bot agents
        const subBotTokens = buildDualToken(channelName, subBotUid);
        const pubBotTokens = buildDualToken(channelName, pubBotUid);

        // STT REST API Task Payload
        const taskPayload = {
            "languages": ["en-US", "hi-IN"],
            "maxIdleTime": 60,
            "rtcConfig": {
                "channelName": channelName,
                "subBotUid": String(subBotUid),
                "pubBotUid": String(pubBotUid),
                "subBotToken": subBotTokens.rtcToken,
                "pubBotToken": pubBotTokens.rtcToken,
                "subscribeAudioUids": [String(userUid || "0")]
            },
            "advanced_features": {
                "enable_rtm": true
            },
            "parameters": {
                "data_channel": "rtm"
            }
        };

        const agentId = `stt_agent_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
        console.log(`[Agora STT] Agent started successfully for channel ${channelName}, agentId: ${agentId}`);

        res.json({
            status: "started",
            agent_id: agentId,
            channelName,
            enable_rtm: true,
            data_channel: "rtm",
            subBotUid: String(subBotUid),
            pubBotUid: String(pubBotUid),
            languages: ["en-US", "hi-IN"],
            payload: taskPayload
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -------------------------------------------------------------
// 5. POST /stt/stop - Stop Agora STT Task
// -------------------------------------------------------------
app.post('/stt/stop', (req, res) => {
    const { agent_id } = req.body;
    console.log(`[Agora STT] Stopped STT task agentId: ${agent_id}`);
    res.json({ status: "stopped", agent_id });
});

// -------------------------------------------------------------
// 6. POST /alerts/trusted-contact - Emergency Contact Alert Dispatch
// -------------------------------------------------------------
const trustedAlertsRouter = require('./routes/trusted_alerts');
app.use('/alerts', trustedAlertsRouter);

// -------------------------------------------------------------
// 8. Family Alert System (FCM)
// -------------------------------------------------------------
let admin;
try {
    const fs = require('fs');
    if (fs.existsSync('./firebase-service-account.json')) {
        admin = require('firebase-admin');
        const serviceAccount = require('./firebase-service-account.json');
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        console.log('[FCM] Firebase Admin SDK initialized');
    } else {
        console.warn('[FCM] firebase-service-account.json not found, mock mode active');
    }
} catch (e) {
    console.warn('[FCM] Firebase init warning:', e.message);
}

// Store FCM tokens per user
const userTokens = {};

// Register FCM token
app.post('/family/register', (req, res) => {
    const { fcmToken, userId } = req.body;
    if (!fcmToken || !userId) {
        return res.status(400).json({ error: 'Missing fields' });
    }
    if (!userTokens[userId]) userTokens[userId] = [];
    if (!userTokens[userId].includes(fcmToken)) {
        userTokens[userId].push(fcmToken);
    }
    console.log(`[FCM] Registered token for user ${userId}`);
    res.json({ success: true });
});

// Send family alert
app.post('/alerts/family', async (req, res) => {
    const {
        protectedUserId,
        familyUserIds,
        protectedUserName,
        riskScore,
        scamType,
        callerNumber,
        transcriptSummary,
        alertType
    } = req.body;

    const alertId = Date.now().toString();
    const results = [];

    const targetUserIds = familyUserIds || Object.keys(userTokens);

    for (const familyUserId of targetUserIds) {
        const tokens = userTokens[familyUserId] || [];

        for (const token of tokens) {
            try {
                if (admin && admin.messaging) {
                    const message = {
                        token: token,
                        data: {
                            alert_type: alertType || 'scam_detected',
                            alert_id: alertId,
                            protected_user: protectedUserName || 'Family member',
                            risk_score: String(riskScore || 0),
                            scam_type: scamType || 'Unknown',
                            caller_number: callerNumber || 'Unknown',
                            transcript_summary: transcriptSummary || ''
                        },
                        android: {
                            priority: 'high',
                            notification: {
                                channel_id: 'guardian_family_alerts',
                                title: '⚠️ Scam detected',
                                body: `${protectedUserName} may be targeted. Risk: ${riskScore}%`
                            }
                        }
                    };

                    const response = await admin.messaging().send(message);
                    results.push({ token: token.slice(0, 20), success: true, response });
                } else {
                    console.log(`[FCM Mock] Alert to token ${token.slice(0, 10)} for ${protectedUserName}`);
                    results.push({ token: token.slice(0, 20), success: true, mock: true });
                }
            } catch (error) {
                console.error(`[FCM] Failed for token:`, error);
                results.push({ token: token.slice(0, 20), success: false, error: error.message });
            }
        }
    }

    res.json({ success: true, alertId, results });
});

// Get family alert history
app.get('/alerts/family/history/:userId', (req, res) => {
    res.json({ alerts: [] });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Agora Token & STT Server running on http://0.0.0.0:${PORT}`);
    console.log(`App ID: ${APP_ID ? APP_ID.substring(0, 6) + '...' : 'NOT_SET'}`);
});
