package com.seple.ThingsBoard_Bot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to store the conversational history (sliding window) for users.
 * <p>
 * Keeps a limited number of previous question/answer pairs in memory
 * to provide OpenAI with necessary context for follow-up questions.
 * </p>
 */
@Slf4j
@Service
public class ChatMemoryService {

    // Maps a userToken (or sessionId) to a deque of ChatMessages
    private final Map<String, ConcurrentLinkedDeque<ChatMessage>> chatHistory = new ConcurrentHashMap<>();
    
    // Maps a userToken to the list of device names they are currently discussing
    private final Map<String, List<String>> activeDevices = new ConcurrentHashMap<>();
    private final Map<String, String> activeBranch = new ConcurrentHashMap<>();
    private final Map<String, String> pendingTopic = new ConcurrentHashMap<>();

    // Last-touched timestamp per session, used to evict idle sessions and bound memory growth.
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();

    // Maximum number of messages to remember per user (2 Q&A pairs = 4 messages)
    private static final int MAX_HISTORY_MESSAGES = 4;

    // Idle sessions older than this are evicted by the scheduled sweep (30 minutes).
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    /**
     * Add a single message to a user's chronological history.
     */
    public void addMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        lastAccess.put(sessionId, System.currentTimeMillis());
        chatHistory.compute(sessionId, (key, deque) -> {
            if (deque == null) {
                deque = new ConcurrentLinkedDeque<>();
            }

            // Add to the end of the history
            deque.addLast(message);

            // Enforce sliding window token protection
            while (deque.size() > MAX_HISTORY_MESSAGES) {
                deque.removeFirst();
            }

            return deque;
        });
        
        log.debug("Added {} message to history for session '{}'. History size: {}", 
                message.getRole(), sessionId, chatHistory.get(sessionId).size());
    }

    /**
     * Adds both a user question and the AI's response to the history simultaneously.
     */
    public void recordInteraction(String sessionId, String userQuestion, String aiAnswer) {
        addMessage(sessionId, new ChatMessage("user", userQuestion));
        addMessage(sessionId, new ChatMessage("assistant", aiAnswer));
    }

    /**
     * Retrieve the recent conversation history for a user.
     * 
     * @return an ordered list of ChatMessage objects (oldest to newest)
     */
    public List<ChatMessage> getHistory(String sessionId) {
        if (sessionId == null || !chatHistory.containsKey(sessionId)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chatHistory.get(sessionId));
    }

    /**
     * Discards the oldest message to make room for tokens, if necessary.
     */
    public void removeOldestMessage(String sessionId) {
        if (sessionId != null && chatHistory.containsKey(sessionId)) {
            ConcurrentLinkedDeque<ChatMessage> deque = chatHistory.get(sessionId);
            if (!deque.isEmpty()) {
                ChatMessage removed = deque.removeFirst();
                log.debug("Dropping oldest history message ({}) to save tokens for {}", removed.getRole(), sessionId);
            }
        }
    }
    
    /**
     * Clear the history for a specific session.
     */
    public void clearHistory(String sessionId) {
        if (sessionId != null) {
            chatHistory.remove(sessionId);
            activeDevices.remove(sessionId);
            activeBranch.remove(sessionId);
            pendingTopic.remove(sessionId);
            lastAccess.remove(sessionId);
            log.debug("Cleared history and active devices for session '{}'", sessionId);
        }
    }

    /**
     * Evict idle sessions to bound memory growth in long-running JVMs.
     * Runs every 5 minutes; drops any session untouched for {@link #SESSION_TTL_MS}.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void evictIdleSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MS;
        int before = lastAccess.size();
        lastAccess.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                String sessionId = entry.getKey();
                chatHistory.remove(sessionId);
                activeDevices.remove(sessionId);
                activeBranch.remove(sessionId);
                pendingTopic.remove(sessionId);
                return true;
            }
            return false;
        });
        int evicted = before - lastAccess.size();
        if (evicted > 0) {
            log.info("[MEMORY] Evicted {} idle chat session(s); {} active.", evicted, lastAccess.size());
        }
    }

    /**
     * Set the currently active devices for a session.
     */
    public void setActiveDevices(String sessionId, List<String> deviceNames) {
        if (sessionId != null && deviceNames != null) {
            activeDevices.put(sessionId, new ArrayList<>(deviceNames));
            lastAccess.put(sessionId, System.currentTimeMillis());
            log.debug("Set active devices for session '{}' to {}", sessionId, deviceNames);
        }
    }

    /**
     * Get the currently active devices for a session.
     */
    public List<String> getActiveDevices(String sessionId) {
        if (sessionId == null || !activeDevices.containsKey(sessionId)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(activeDevices.get(sessionId));
    }

    public void setActiveBranch(String sessionId, String branchAlias) {
        if (sessionId != null) {
            if (branchAlias == null || branchAlias.isBlank()) {
                activeBranch.remove(sessionId);
                log.debug("Cleared active branch for session '{}'", sessionId);
            } else {
                activeBranch.put(sessionId, branchAlias);
                log.debug("Set active branch for session '{}' to {}", sessionId, branchAlias);
            }
        }
    }

    public String getActiveBranch(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return activeBranch.get(sessionId);
    }

    public void setPendingTopic(String sessionId, String topic) {
        if (sessionId != null) {
            if (topic == null) {
                pendingTopic.remove(sessionId);
            } else {
                pendingTopic.put(sessionId, topic);
            }
            log.debug("Set pending topic for session '{}' to {}", sessionId, topic);
        }
    }

    public String getPendingTopic(String sessionId) {
        return sessionId == null ? null : pendingTopic.get(sessionId);
    }
}
