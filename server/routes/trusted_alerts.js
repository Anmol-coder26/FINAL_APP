// In-memory family token registry
const familyTokens = new Set();

router.post('/family/register', (req, res) => {
    const { token, deviceName } = req.body;
    if (!token) {
        return res.status(400).json({ error: 'Token is required' });
    }
    familyTokens.add(token);
    console.log(`[FamilyAlert] Registered device "${deviceName || 'Anonymous'}" with token: ${token.substring(0, 10)}... (Total: ${familyTokens.size})`);
    res.json({ success: true, totalRegistered: familyTokens.size });
});

router.post('/trusted-contact', async (req, res) => {
    const { to, contactName, callerNumber, riskScore, signals } = req.body;

    if (!to || !callerNumber) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    const message = `⚠️ Guardian Alert\n\n${contactName || 'Family Member'}, you are listed as a trusted contact. A suspicious call was just detected on your family member's phone.\n\nCaller: ${callerNumber}\nRisk: ${riskScore}%\nSignals: ${(signals || []).join(', ')}\n\nPlease check on them immediately.`;

    try {
        console.log(`[TrustedAlert] Dispatching emergency alert to ${to}:\n${message}`);
        
        // Broadcast to registered family members if any
        if (familyTokens.size > 0) {
            console.log(`[FamilyAlert] Sending push notification to ${familyTokens.size} family devices.`);
        }

        res.json({
            success: true,
            provider: 'console-stub',
            recipient: to,
            familyAlertsDispatched: familyTokens.size
        });
    } catch (err) {
        console.error('[TrustedAlert] Failed:', err);
        res.status(500).json({ error: 'Send failed' });
    }
});

module.exports = router;

