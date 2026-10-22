package com.chat.client.model;

import java.util.List;

/**
 * Implemented by the view. The model only ever talks to the UI through this
 * interface, so it has no dependency on JavaFX. Callbacks arrive on a
 * background thread; the implementer is responsible for UI-thread hand-off.
 */
public interface ChatListener {
    void onMessage(String formattedMessage);
    void onInfo(String text);
    void onUserList(List<String> users);
    void onError(String text);
    void onDisconnected();
}
