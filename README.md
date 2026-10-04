# SuSagi

## Real-Time Scam & Investor Fraud Resilience for Bharat

**Track A — Digital Fraud & Scam Resilience | SANGYAN, IIT (BHU)**

SuSagi is a real-time scam intervention system built to protect retail and first-time investors from social-engineering fraud **before money, credentials, or trust are lost**.

A fake investment opportunity rarely begins with malware. It may begin with a WhatsApp message, a Telegram group, a convincing phone call, a fake IPO allotment, a phishing link, or someone pretending to be a broker, bank, depository, regulator, or trusted person.

SuSagi looks beyond isolated keywords. It analyses the **context and progression of an interaction** — who the person claims to be, what they want the user to do, whether they are creating urgency or pressure, and whether the requested action involves credentials, money, remote access, or suspicious links.

The current prototype combines:

**Agora RTC + BHASHINI + Contextual Risk Intelligence + Semantic AI Evidence + Deterministic Risk Assessment + Guardian Verification**

> **The word “OTP” is not the risk. The intent around it is.**

---

# Why We Built SuSagi

India is seeing rapid growth in first-time and retail investors, including users from Tier-2 and Tier-3 cities.

At the same time, fraud has become increasingly conversational.

A scammer may say:

> “Your IPO allotment has been confirmed.”

> “You need to pay ₹5,000 immediately to release the shares.”

> “Use this link — the offer expires in 10 minutes.”

Or:

> “I am calling from your broker. Your account needs urgent verification.”

> “Tell me the OTP you just received.”

The dangerous part is not one sentence.

It is the **sequence**:

```text
Trust
  ↓
Impersonation
  ↓
Urgency / Fear
  ↓
Financial or Credential Request
  ↓
User Acts
  ↓
Loss
```

Many existing tools see individual pieces of this journey.

SuSagi is designed to understand the interaction as a whole and intervene while the user's decision is still reversible.

---

# What SuSagi Does

During a suspicious interaction, SuSagi looks for evidence such as:

### Identity

- Broker / financial institution claims
- Bank / company claims
- Authority or regulator impersonation
- Family / trusted-person impersonation
- Identity mismatch
- Sudden contact changes

### Manipulation

- Urgency
- Fear
- Pressure
- Artificial deadlines
- High-pressure instructions
- Emotional manipulation

### Sensitive Requests

- OTP
- CVV
- Passwords
- Aadhaar / PAN
- Account credentials
- Personal financial information

### Financial Actions

- Money-transfer requests
- "Processing fee" demands
- Fake investment deposits
- Payment requests
- Suspicious QR codes
- Phishing links
- Remote-access requests

These signals are combined to determine whether the interaction should remain low risk or escalate.

---

# Risk Model

SuSagi exposes four user-facing risk states:

```text
LOW → CAUTION → HIGH → CRITICAL
```

The user does not receive only a score.

SuSagi also explains:

### What did the system notice?

### Why did the risk increase?

### What should the user do next?

Example:

```text
CRITICAL RISK

Detected:

✓ Financial authority claim
✓ Artificial urgency
✓ Credential request
✓ Financial context
✓ Suspicious action requested

Recommended action:

• Do not share the OTP.
• Do not make the requested payment.
• End the interaction.
• Verify using an official channel.
• Contact a trusted Guardian if identity is uncertain.
```

The objective is not simply to identify suspicious content.

The objective is to **prevent the next unsafe action**.

---

# Context Matters

Consider:

```text
“Never share your OTP with anyone.”
```

and:

```text
“Tell me your OTP immediately or your account will be blocked.”
```

Both contain the word `OTP`.

Their meaning is completely different.

SuSagi's contextual layer distinguishes between:

```text
REQUEST
COMMAND
MENTION
WARNING
QUOTE
NEGATION
UNKNOWN
```

For example:

```text
“Banks will never ask for your OTP.”

→ WARNING
→ No active credential request
```

```text
“Give me your OTP now.”

→ REQUEST
→ Active credential risk
```

```text
“I refused to share my OTP.”

→ NEGATION
→ Protective / benign context
```

This helps reduce false alarms caused by simple keyword matching.

---

# Hybrid Intelligence Architecture

SuSagi deliberately does not make an unrestricted AI model the sole authority for the final safety decision.

The architecture separates **understanding** from **risk policy**.

```text
Interaction
     │
     ▼
Contextual Signal Extraction
     │
     ├───────────────┐
     │               │
     ▼               ▼
Local Signals   Semantic AI Evidence
     │               │
     └───────┬───────┘
             ▼
    Deterministic Risk Engine
             │
             ▼
LOW / CAUTION / HIGH / CRITICAL
             │
             ▼
 Explanation + Protective Action
```

Semantic AI helps interpret indirect or unfamiliar language.

The structured risk engine keeps escalation more predictable, explainable, and testable.

---

# Real-Time Voice Architecture

For VoIP interactions, SuSagi uses **Agora RTC** as the real-time communication layer.

```text
                         ┌───────────────────────────┐
                         │      Agora RTC Engine     │
                         │     PCM Audio Streaming   │
                         └─────────────┬─────────────┘
                                       │
                              AgoraFrameBridge
                         ┌─────────────┴─────────────┐
                         ▼                           ▼
                 User / Local Audio          Remote Caller Audio
                         │                           │
                         ▼                           ▼
                    BHASHINI STT                BHASHINI STT
                    Streaming Client            Streaming Client
                         │                           │
                         └─────────────┬─────────────┘
                                       ▼
                              DualSttController
                                       │
                                       ▼
                               LiveRiskAnalyzer
                                       │
                                       ▼
                         Context + Semantic Evidence
                                       │
                                       ▼
                          Deterministic Risk Engine
                                       │
                     ┌─────────────────┴─────────────────┐
                     ▼                                   ▼
              Live Defense UI                     Protective Action
          • Risk state                         • Voice warning
          • Dual transcript                    • Guardian alert
          • Threat explanation                 • Verify independently
          • Incident context                   • Exit / stop advice
```

Both sides of the conversation can contribute to the risk assessment.

That matters because fraud often becomes visible only when multiple statements are considered together.

---

# Agora RTC

Agora powers the prototype's real-time VoIP communication layer.

`AgoraFrameBridge` handles audio-frame observation for local and remote streams.

The pipeline supports:

- Local microphone audio
- Remote caller audio
- PCM audio processing
- Frame-rate normalisation
- Parallel speech processing
- Real-time transcript updates
- Continuous risk analysis

Rather than analysing a conversation after the call is over, the objective is to identify risk **while the user still has time to act differently**.

---

# BHASHINI — Bharat-First Language Layer

Financial scams in India are not English-only.

A conversation may sound like:

> “Sir, your demat account verification pending hai.”

> “Abhi OTP bata dijiye.”

> “Nahi toh account temporarily block ho jayega.”

The language changes.

The manipulation pattern does not.

SuSagi integrates **BHASHINI speech services** into the live voice pipeline for multilingual speech processing.

Current language handling includes:

- Hindi
- Tamil
- Telugu
- Bengali
- Marathi
- Kannada
- Malayalam
- Gujarati
- Punjabi
- English

The pipeline is:

```text
Indian-Language Speech
        ↓
BHASHINI STT
        ↓
SuSagi Risk Intelligence
        ↓
Risk Explanation
        ↓
BHASHINI TTS Warning
```

The long-term goal is simple:

> A user's ability to receive scam protection should not depend on their ability to understand an English cybersecurity warning.

---

# Live Defense

During an interaction, the Live Defense experience can display:

- Separate caller and user transcript bubbles
- Current risk level
- Detected threat signals
- Plain-language explanation
- Recommended next action
- Incident context

The interface is designed around two immediate questions:

## Why is this risky?

and

## What should I do now?

---

# Investor-Focused Scam Scenarios

## Scenario 1 — Fake IPO Allotment

```text
“Congratulations, your IPO allotment is confirmed.”

“To release the shares you need to pay a ₹5,000 processing fee.”

“Pay immediately using this link.”
```

Potential SuSagi signals:

```text
✓ Investment-related claim
✓ Payment request
✓ Artificial urgency
✓ Suspicious link
```

Possible result:

```text
HIGH RISK

Do not make the payment.
Verify the allotment through an official channel.
Do not use the link provided in the message.
```

---

## Scenario 2 — Fake Broker / Account Verification

```text
“I am calling from your broker.”

“Your account is about to be suspended.”

“Tell me the OTP to complete verification.”
```

Potential signals:

```text
✓ Financial identity claim
✓ Fear / urgency
✓ Credential request
✓ Account context
```

Possible result:

```text
CRITICAL RISK

Do not share the OTP.
End the interaction.
Verify through the official broker application or support channel.
```

---

## Scenario 3 — Telegram / WhatsApp Investment Scam

```text
“Guaranteed multibagger stock.”

“Only 20 slots left.”

“Transfer ₹25,000 today and we will manage the trade for you.”
```

Potential signals:

```text
✓ Unrealistic financial claim
✓ Urgency
✓ Payment request
✓ High-pressure investment solicitation
```

SuSagi does not decide whether the investment itself will rise or fall.

Its purpose is to identify the **manipulative and fraudulent behaviour around the transaction**.

---

## Scenario 4 — Family Impersonation

```text
“Papa, mera phone change ho gaya hai.”

“Mujhe urgently ₹30,000 chahiye.”

“Is number par transfer kar do.”
```

Potential signals:

```text
✓ Personal identity claim
✓ Sudden contact change
✓ Urgency
✓ Financial request
```

This is where the Guardian Circle becomes particularly useful.

---

# Guardian Circle

Not every identity problem can be solved reliably by AI.

Sometimes the safest action is to verify with another human.

SuSagi's **Guardian Circle** provides a trusted-contact layer.

```text
Suspicious Identity Claim
          ↓
Verification Request
          ↓
Trusted Contact
          ↓
VERIFIED / CONFLICT
          ↓
Risk Assessment Updated
```

The backend supports:

```text
POST /family/register
POST /alerts/trusted-contact
```

High-risk interactions can trigger Firebase Cloud Messaging alerts to trusted contacts.

A conflicting verification result can itself become additional risk evidence.

---

# Cellular Call Speakerphone Assist

Android does not give normal third-party applications unrestricted access to cellular call audio.

SuSagi handles this platform limitation explicitly.

`CellularSpeakerphoneAssist` guides the user to use speakerphone so that microphone audio can be used for transcription and scam-risk analysis.

```text
Cellular Call
      ↓
Speakerphone Assist
      ↓
Microphone Capture
      ↓
BHASHINI STT
      ↓
Risk Analysis
```

We prefer being technically honest about this limitation rather than claiming access Android does not provide.

---

# SMS & Notification Protection

SuSagi also includes a multi-channel protection layer for suspicious messages and notifications.

The system can:

- inspect suspicious incoming content,
- identify scam-related signals,
- surface the warning experience,
- preserve incident context,
- and combine textual evidence with the wider protection workflow.

Any production use of SMS, notification, or sensitive content would require appropriate user permission, consent, and data-minimisation controls.

---

# Suspicious Link & QR Protection

The Android application also includes link and QR inspection capabilities using:

- CameraX
- ML Kit Barcode Scanning
- URL extraction
- Domain / URL structure analysis
- Risk handoff into the SuSagi protection flow

This extends protection beyond voice fraud into phishing-style financial attacks.

---

# Contact Consistency & Synthetic Voice Signals

The protection suite also explores advanced supporting evidence including:

- unexpected phone-number changes,
- known-contact inconsistencies,
- voice-characteristic deviations,
- synthetic / cloned-voice indicators,
- abnormal acoustic characteristics.

These signals are treated as **supporting evidence** rather than absolute proof of fraud.

This distinction matters because a safety system should communicate uncertainty rather than pretend that every AI inference is certain.

---

# Product Experience

The current SuSagi product experience includes:

- **Home**
- **Protect**
- **Live Defense**
- **Activity**
- **Incident Detail**
- **Guardian Circle**
- **Identity Verification**
- **Settings**

The project contains both:

### Android application

and

### Interactive web companion

---

# Technology Stack

## Android

- Kotlin
- Jetpack Compose
- Room
- CameraX
- ML Kit
- Android Call Screening APIs
- AndroidX encrypted storage

## Real-Time Communication

- Agora RTC

## Language

- BHASHINI Speech-to-Text
- BHASHINI Text-to-Speech
- Multilingual speech processing

## Risk Intelligence

- Contextual signal extraction
- Live risk analysis
- Semantic AI evidence
- Deterministic risk assessment
- Identity contradiction handling
- False-positive suppression

## AI

- Gemini semantic analysis
- Structured semantic evidence
- Local / deterministic safety logic around final risk assessment

## Backend

- Node.js
- Express
- Firebase Cloud Messaging
- Agora token generation
- Trusted-contact APIs

## Web

- Next.js
- React
- TypeScript

---

# Backend Services

The backend includes services such as:

```text
/api/agora/token
/family/register
/alerts/trusted-contact
```

These support:

- Agora RTC dynamic token generation
- Trusted-contact registration
- High-priority fraud alerts

---

# Privacy, Trust & Guardrails

SuSagi is an **investor-protection system**, not an investment recommendation system.

It does not aim to provide:

- stock tips,
- buy / sell / hold recommendations,
- stock-price predictions,
- return predictions,
- trading algorithms,
- speculative investment nudges,
- promotion of a specific broker,
- promotion of a specific financial product.

SuSagi focuses on detecting the **fraud, manipulation and coercion surrounding a financial decision**.

For example, it can warn:

```text
“This person is creating urgency and asking for payment through an
unverified channel.”
```

It does **not** say:

```text
“This stock is a good or bad investment.”
```

---

## Sensitive Data

SuSagi does not need an OTP value to make a financial transaction or authenticate into a user's financial account.

OTP, password, or credential-related language is treated as a **risk signal**.

Any deployment involving speech, SMS, notifications, or personal information must follow:

- explicit permission,
- informed consent,
- data minimisation,
- secure transport,
- appropriate retention controls,
- and user-controlled privacy settings.

The prototype already includes encrypted local session handling, while further privacy hardening would be required before production deployment.

---

# Accuracy & Validation

We intentionally do **not** claim an unsupported “99% accuracy”.

For a safety product, accuracy cannot responsibly be reduced to one number.

The evaluation should include:

- Precision
- Recall
- F1 Score
- False Positive Rate
- Context classification accuracy
- Risk calibration
- Intervention latency
- Explanation quality

False positives are especially important.

For example:

```text
“Never share your OTP.”
```

should not produce the same result as:

```text
“Send me your OTP immediately.”
```

Similarly:

```text
“He asked me for ₹25,000 yesterday.”
```

should not automatically be treated like:

```text
“Transfer ₹25,000 immediately.”
```

SuSagi therefore evaluates both **dangerous behaviour and benign context**.

---

# Verification Matrix

| ID | Test Area | Scope | Status |
|---|---|---|---|
| T1–T5 | VoIP Audio & STT | Agora bridge, dual speech streams, resampling | VERIFIED |
| T6–T8 | Live Call UI | Risk state, transcript, TTS and alert flow | VERIFIED |
| T9–T10 | Cellular Assist | Speakerphone guidance and microphone fallback | VERIFIED |
| T11–T13 | SMS / Notification Protection | Detection, interception and warning flow | VERIFIED |
| T14–T16 | Advanced Protection | Contact consistency, voice signals and reasoning | VERIFIED |
| T17–T20 | Screening & History | Call screening, Room persistence and incident records | VERIFIED |
| T21–T24 | Security & Languages | Encrypted storage and multilingual handling | VERIFIED |
| T25–T28 | Backend Integration | Agora token generation and FCM alert dispatch | VERIFIED |
| T29–T30 | End-to-End Builds | Debug and release APK generation | VERIFIED |

---

# APK Artifacts

## Debug APK

```text
artifacts/app-debug.apk
```

Size:

```text
209,959,297 bytes (~200.2 MB)
```

SHA-256:

```text
149F53ED18121FC1DD06E9225579747DDF1D066CC7744084122CDE3AD7DED490
```

---

## Release APK

```text
artifacts/app-release.apk
```

Size:

```text
202,879,076 bytes (~193.5 MB)
```

SHA-256:

```text
076ABEA4E1790B54CAD0985AEAA45102A05B9718C0A60C0AC4CB7317B332C52C
```

---

## Why is the APK large?

The current hackathon build packages native libraries for several Android architectures along with:

- Agora RTC
- CameraX
- ML Kit
- speech and language-related components

The priority of the prototype is demonstration reliability and cross-architecture compatibility.

Production distribution could reduce download size significantly using:

- Android App Bundles
- ABI splits
- dependency optimisation
- asset optimisation

---

# Building & Running

## Requirements

- JDK 17
- Android SDK 35
- Minimum Android SDK 26
- Node.js 18+

---

## Android Debug Build

```bash
./gradlew assembleDebug
```

## Android Release Build

```bash
./gradlew assembleRelease
```

---

## Backend

```bash
cd server
npm install
node server.js
```

Default port:

```text
3001
```

or the value supplied using the server environment configuration.

---

# Who We Are Building For

SuSagi is especially relevant to:

### First-Time Investors

Users who are new to financial markets and may not recognise impersonation or fraudulent investment claims.

### Tier-2 and Tier-3 India

Users for whom English-heavy cybersecurity tools may create an additional barrier.

### Regional-Language Users

Users who naturally communicate in Hindi or other Indian languages — including mixed Hindi-English conversations.

### Elderly Investors

Users who may be more vulnerable to authority impersonation, urgency, account-verification fraud, and remote-access scams.

### Young Investors

Users exposed to investment claims, Telegram groups, WhatsApp forwards, influencer content, phishing links, and high-pressure financial promotions.

### Families

Households where a trusted family member can provide a second line of verification through Guardian Circle.

---

# Bharat-First Design

SANGYAN asks teams to think beyond metro, English-first users.

SuSagi's Bharat-first approach currently focuses on:

### Multilingual Interaction

BHASHINI-backed speech processing for Indian languages.

### Voice-First Protection

Users do not need to understand cybersecurity terminology or inspect raw technical data.

### Plain-Language Explanations

Instead of:

```text
Anomalous transaction vector detected.
```

SuSagi aims to say:

```text
The caller is creating urgency and asking for sensitive information.

Do not share the OTP.
```

### Low Cognitive Load

The main user decision is kept simple:

```text
What happened?

Why is it risky?

What should I do next?
```

Low-bandwidth optimisation and broader offline capability remain areas for future production development rather than claims of the current prototype.

---

# Scalability

The architecture separates:

```text
Communication
      ↓
Language
      ↓
Risk Intelligence
      ↓
Protective Action
      ↓
Product Interface
```

This makes the risk intelligence reusable across multiple channels.

Potential future deployment surfaces include:

- Voice calls
- SMS
- Messaging applications
- Notifications
- Links
- QR codes
- FinTech safety workflows
- Investor-protection applications
- Telecom systems
- Enterprise fraud protection
- Family safety products

The long-term opportunity is not to build a separate scam detector for every platform.

It is to create a reusable **fraud-intervention intelligence layer**.

---

# Current Project Status

SuSagi is beyond the idea stage.

The repository contains working foundations for:

- Agora-based real-time VoIP
- Dual-speaker audio processing
- BHASHINI speech processing
- Multilingual interaction handling
- Contextual scam-signal extraction
- Semantic AI evidence
- Structured risk assessment
- Live Defense UI
- Trusted-contact alerts
- Guardian Circle
- Identity-verification flows
- Cellular speakerphone assistance
- SMS / notification protection
- Suspicious-link inspection
- QR scanning
- Incident history
- Android product experience
- Interactive web companion
- Backend communication services
- Debug and release APK generation

Current priorities include:

- stronger end-to-end integration,
- larger labelled benchmarks,
- measured Precision / Recall / F1,
- false-positive reduction,
- demo reliability,
- privacy hardening,
- and production optimisation.

---

# SANGYAN Alignment

SuSagi is being submitted under:

## Track A — Digital Fraud & Scam Resilience

The challenge asks teams to help users detect, warn against, and intercept deceptive financial vectors **before money changes hands**.

That is the point at which SuSagi operates.

---

## 1. Investor Resilience & Safety

SuSagi is designed to intervene before the user:

- transfers money,
- shares credentials,
- opens a phishing link,
- grants remote access,
- or trusts an impersonator.

The primary success metric is not simply:

```text
“How many scams did we classify?”
```

It is:

```text
“How many unsafe actions could we prevent?”
```

---

## 2. Bharat-First Usability

The prototype includes:

- BHASHINI-based multilingual speech processing
- Voice-first interaction
- Regional-language handling
- Plain-language risk explanations
- Minimal-action protective guidance

The intention is to make protection useful to investors beyond English-first, metro-centric interfaces.

---

## 3. Trust & Guardrails

SuSagi:

- does not provide stock recommendations,
- does not predict investment returns,
- does not provide buy / sell / hold signals,
- does not promote brokers or financial products,
- distinguishes AI evidence from deterministic safety logic,
- communicates reasons behind warnings,
- and treats privacy as a product requirement.

---

## 4. Technical Execution

The prototype combines:

```text
Agora RTC
+
BHASHINI
+
Dual-Speaker Speech Processing
+
Contextual Signal Extraction
+
Semantic AI Evidence
+
Structured Risk Engine
+
Firebase / Trusted Contact Alerts
+
Android + Web Product Interfaces
```

The technologies are used to solve specific parts of the fraud-intervention problem rather than being included only for novelty.

---

## 5. Feasibility & Scalability

The architecture is modular and can evolve beyond a standalone application.

The same risk-intelligence layer can potentially support different languages, communication channels, and investor-protection interfaces without rebuilding the entire system for each use case.

---

# What SuSagi Is Not

SuSagi is not:

- a trading application,
- an investment advisor,
- a stock predictor,
- a portfolio recommendation engine,
- a broker promotion platform,
- a generic chatbot,
- or simply a spam-number database.

It is a **fraud and manipulation intervention layer**.

---

# Design Principle

Most security systems try to protect the device.

SuSagi is designed around protecting the **decision**.

A scammer may only need a few seconds of misplaced trust.

Our goal is to use those seconds to help the user:

```text
PAUSE
  ↓
UNDERSTAND
  ↓
VERIFY
  ↓
ACT SAFELY
```

---

# SuSagi

## Real-Time Scam & Investor Fraud Resilience for Bharat

**Understand the interaction.  
Detect the manipulation.  
Explain the risk.  
Verify when necessary.  
Intervene before the loss.**
