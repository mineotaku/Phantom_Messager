const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

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
let profiles = {}; // userId -> { username, displayName, avatarStyle, bio, identityPublicKeyHex }
let prekeyBundles = {}; // userId -> { signedPrekeyPublicHex, signedPrekeySignatureHex, oneTimePrekeysHex: [] }
const offlineQueues = {}; // userId -> [ encryptedPackets ]
let friendRequests = {}; // recipientUserId -> [{ fromUserId, fromUsername, fromDisplayName, fromAvatarStyle, timestamp }]

const DATA_DIR = path.join(__dirname, 'data');
const DATA_FILE = path.join(DATA_DIR, 'phantom_data.json');

// File-based persistence
function loadData() {
    try {
        if (!fs.existsSync(DATA_DIR)) {
            fs.mkdirSync(DATA_DIR, { recursive: true });
        }
        if (fs.existsSync(DATA_FILE)) {
            const data = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
            if (data.profiles) profiles = data.profiles;
            if (data.prekeyBundles) prekeyBundles = data.prekeyBundles;
            if (data.friendRequests) friendRequests = data.friendRequests;
            console.log('Loaded data from disk');
        }
    } catch (e) {
        console.error('Failed to load data:', e);
    }
}

function saveData() {
    try {
        if (!fs.existsSync(DATA_DIR)) {
            fs.mkdirSync(DATA_DIR, { recursive: true });
        }
        const data = { profiles, prekeyBundles, friendRequests };
        fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
    } catch (e) {
        console.error('Failed to save data:', e);
    }
}

loadData();

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

    saveData();

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
            saveData();
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

// 1. Friend Request Storage & Endpoints
app.post('/api/friend-request', (req, res) => {
    const { fromUserId, toUserId } = req.body;
    const senderProfile = profiles[fromUserId];
    
    if (!senderProfile) return res.status(400).json({ error: "Sender not found" });
    if (!profiles[toUserId]) return res.status(400).json({ error: "Recipient not found" });

    if (!friendRequests[toUserId]) {
        friendRequests[toUserId] = [];
    }
    
    const requestData = {
        fromUserId,
        fromUsername: senderProfile.username,
        fromDisplayName: senderProfile.displayName,
        fromAvatarStyle: senderProfile.avatarStyle,
        timestamp: Date.now()
    };
    
    // Avoid duplicates
    if (!friendRequests[toUserId].find(r => r.fromUserId === fromUserId)) {
        friendRequests[toUserId].push(requestData);
        saveData();
    }
    
    const targetSocketId = activeConnections[toUserId];
    if (targetSocketId && io.sockets.sockets.get(targetSocketId)) {
        io.to(targetSocketId).emit('friend_request_received', requestData);
    }
    
    res.json({ success: true });
});

app.get('/api/friend-requests/:userId', (req, res) => {
    const userId = req.params.userId;
    res.json(friendRequests[userId] || []);
});

app.post('/api/friend-request/accept', (req, res) => {
    const { userId, friendUserId } = req.body;
    
    if (friendRequests[userId]) {
        friendRequests[userId] = friendRequests[userId].filter(r => r.fromUserId !== friendUserId);
        saveData();
    }
    
    const acceptorProfile = profiles[userId];
    if (acceptorProfile) {
        const friendSocketId = activeConnections[friendUserId];
        if (friendSocketId && io.sockets.sockets.get(friendSocketId)) {
            io.to(friendSocketId).emit('friend_request_accepted', {
                acceptedByUserId: userId,
                acceptedByUsername: acceptorProfile.username,
                acceptedByDisplayName: acceptorProfile.displayName,
                acceptedByAvatarStyle: acceptorProfile.avatarStyle
            });
        }
    }
    
    res.json({ success: true });
});

// 2. List All Users Endpoint
app.get('/api/users', (req, res) => {
    res.json(Object.values(profiles));
});

// 3. Ping Endpoint
app.get('/api/ping', (req, res) => {
    res.json({ status: 'alive', users: Object.keys(profiles).length });
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
        
        // 5. WebSocket Friend Request Events
        if (friendRequests[userId] && friendRequests[userId].length > 0) {
            friendRequests[userId].forEach(request => {
                socket.emit('friend_request_received', request);
            });
        }
    });

    // Handle incoming E2EE packets from Alice to Bob
    socket.on('send_message', (packet) => {
        const targetUserId = packet.recipientUserId;
        const targetSocketId = activeConnections[targetUserId];

        console.log(`Routing packet from ${packet.senderUserId} to ${packet.recipientUserId}`);

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
