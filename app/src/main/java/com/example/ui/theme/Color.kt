package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core Palette ──
val PhantomBackground = Color(0xFF0A0A0A)       // Near-black, warmer than pure #000
val PhantomSurface = Color(0xFF141414)           // Elevated surface
val PhantomSurfaceVariant = Color(0xFF1E1E1E)    // Cards, inputs
val PhantomSurfaceHover = Color(0xFF2A2A2A)      // Hover/pressed states
val PhantomOverlay = Color(0xFF1A1A1A)           // Modal overlays

// ── Brand ──
val PhantomPrimary = Color(0xFF6C8EEF)           // Muted periwinkle blue — premium, not generic
val PhantomPrimaryVariant = Color(0xFF5B6EBE)    // Darker variant
val PhantomOnPrimary = Color(0xFFFFFFFF)

// ── Accent ──
val PhantomSecondary = Color(0xFF4ECDC4)         // Teal mint — fresh, modern
val PhantomTertiary = Color(0xFFE8B45A)          // Warm gold — status, verification

// ── Text ──
val PhantomOnBackground = Color(0xFFF5F5F5)      // Primary text
val PhantomOnSurface = Color(0xFFEAEAEA)         // Surface text
val PhantomOnSurfaceVariant = Color(0xFF9E9E9E)  // Secondary text, captions
val PhantomTextMuted = Color(0xFF6B6B6B)         // Tertiary text, timestamps

// ── Borders & Dividers ──
val PhantomOutline = Color(0xFF2E2E2E)           // Subtle borders
val PhantomDivider = Color(0xFF1F1F1F)           // List dividers

// ── Functional ──
val PhantomError = Color(0xFFE85454)
val PhantomWarning = Color(0xFFE8B45A)
val PhantomSuccess = Color(0xFF4CAF7D)

// ── Chat Specific ──
val PhantomBubbleOutgoing = Color(0xFF6C8EEF)    // Matches primary
val PhantomBubbleIncoming = Color(0xFF1E1E1E)    // Matches surface variant
val PhantomOnBubbleOutgoing = Color(0xFFFFFFFF)
val PhantomOnBubbleIncoming = Color(0xFFEAEAEA)

// ── Status ──
val PhantomOnlineGreen = Color(0xFF4CAF7D)
val PhantomUnreadBadge = Color(0xFF6C8EEF)
val PhantomDelivered = Color(0xFF6C8EEF)
