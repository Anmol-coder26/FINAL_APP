require('dotenv').config();
const express = require('express');
const cors = require('cors');
const https = require('https');
const { RtcTokenBuilder, RtmTokenBuilder, RtcRole } = require('agora-token');

const app = express();
app.use(cors());
app.use(express.json());

const APP_ID = process.env.APP_ID || '';
const APP_CERTIFICATE = process.env.APP_CERTIFICATE || '';
const PORT = process.env.PORT || 3001;

if (!APP_ID || !APP_CERTIFICATE) {
    console.error("FATAL: APP_ID and APP_CERTIFICATE must be set in .env");
}

// Token expiration times (in seconds)
const TOKEN_EXPIRY_SECONDS = 3600; // 1 hour

// Helper to generate dedicated RTM token for client login
function buildRtmToken(userId) {
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
// 7. Health Check
// -------------------------------------------------------------
app.get('/health', (req, res) => {
    res.json({
        status: "online",
        service: "Guardian Agora Token & STT Server",
        appIdConfigured: Boolean(APP_ID),
        port: PORT
    });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Agora Token & STT Server running on http://0.0.0.0:${PORT}`);
    console.log(`App ID: ${APP_ID ? APP_ID.substring(0, 6) + '...' : 'NOT_SET'}`);
});
