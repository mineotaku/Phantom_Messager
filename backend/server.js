const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

app.use(cors());
app.use(express.json());

// IN-MEMORY DATABASE (For MVP/Testing)
// In production, this should be Redis or MongoDB
const profiles = {}; // userId -> { username, displayName, avatarStyle, bio, identityPublicKeyHex }
const prekeyBundles = {}; // userId -> { signedPrekeyPublicHex, signedPrekeySignatureHex, oneTimePrekeysHex: [] }
const offlineQueues = {}; // userId -> [ encryptedPackets ]

// Active WebSocket connections mapping
const activeConnections = {}; // userId -> socket.id

// REST API for Key Management
app.post('/api/register', (req, res) => {
    const { profile, signedPrekeyPublicHex, signedPrekeySignatureHex, oneTimePrekeysHex } = req.body;
    
    if (!profile || !profile.userId) {
        return res.status(400).json({ error: "Invalid profile" });
    }

    profiles[profile.userId] = profile;
    prekeyBundles[profile.userId] = {
        signedPrekeyPublicHex,
        signedPrekeySignatureHex,
        oneTimePrekeysHex: oneTimePrekeysHex || []
    };

    console.log(`Registered user: ${profile.userId}`);
    res.json({ success: true });
});

app.get('/api/profile/:userId', (req, res) => {
    const userId = req.params.userId;
    const profile = profiles[userId];
    if (profile) {
        res.json(profile);
    } else {
        res.status(404).json({ error: "Profile not found" });
    }
});

app.get('/api/bundle/:userId', (req, res) => {
    const userId = req.params.userId;
    const bundle = prekeyBundles[userId];
    if (bundle) {
        // Pop an OPK if available
        let opk = null;
        if (bundle.oneTimePrekeysHex && bundle.oneTimePrekeysHex.length > 0) {
            opk = bundle.oneTimePrekeysHex.shift();
        }
        
        res.json({
            identityPublicKeyHex: profiles[userId].identityPublicKeyHex,
            signedPrekeyPublicHex: bundle.signedPrekeyPublicHex,
            signedPrekeySignatureHex: bundle.signedPrekeySignatureHex,
            oneTimePrekeyHex: opk
        });
    } else {
        res.status(404).json({ error: "Bundle not found" });
    }
});

app.get('/api/search', (req, res) => {
    const query = (req.query.q || "").toLowerCase();
    const results = Object.values(profiles).filter(p => 
        p.username.toLowerCase().includes(query) || 
        p.displayName.toLowerCase().includes(query)
    );
    res.json(results);
});

// WebSocket for Real-time Messaging
io.on('connection', (socket) => {
    console.log(`Client connected: ${socket.id}`);

    // User authenticates their socket connection
    socket.on('authenticate', (userId) => {
        activeConnections[userId] = socket.id;
        console.log(`User ${userId} authenticated on socket ${socket.id}`);

        // Flush offline queue if any
        if (offlineQueues[userId] && offlineQueues[userId].length > 0) {
            console.log(`Flushing ${offlineQueues[userId].length} offline messages to ${userId}`);
            offlineQueues[userId].forEach(packet => {
                socket.emit('receive_message', packet);
            });
            offlineQueues[userId] = [];
        }
    });

    // Handle incoming E2EE packets from Alice to Bob
    socket.on('send_message', (packet) => {
        const targetUserId = packet.receiverId;
        const targetSocketId = activeConnections[targetUserId];

        console.log(`Routing packet from ${packet.senderId} to ${packet.receiverId}`);

        if (targetSocketId && io.sockets.sockets.get(targetSocketId)) {
            // Target is online, push immediately
            io.to(targetSocketId).emit('receive_message', packet);
        } else {
            // Target is offline, store in queue
            if (!offlineQueues[targetUserId]) {
                offlineQueues[targetUserId] = [];
            }
            offlineQueues[targetUserId].push(packet);
            console.log(`Stored packet offline for ${targetUserId}`);
        }
    });

    socket.on('disconnect', () => {
        console.log(`Client disconnected: ${socket.id}`);
        // Remove from active connections
        for (const [userId, sid] of Object.entries(activeConnections)) {
            if (sid === socket.id) {
                delete activeConnections[userId];
                break;
            }
        }
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Phantom Relay Server running on port ${PORT}`);
});
