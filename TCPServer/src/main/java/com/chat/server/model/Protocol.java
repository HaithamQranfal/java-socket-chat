package com.chat.server.model;

/** Line-based text protocol shared (by convention) with the client. */
public final class Protocol {
    private Protocol() {}

    // client -> server
    public static final String HELLO = "HELLO";       // "HELLO <username>" or "HELLO" (read-only)
    public static final String MSG = "MSG";           // "MSG <text>"
    public static final String ALLUSERS = "ALLUSERS";
    public static final String BYE = "BYE";

    // server -> client
    public static final String INFO = "INFO";         // "INFO <text>"
    public static final String USERS = "USERS";       // "USERS a,b,c"
    public static final String ERROR = "ERROR";       // "ERROR <text>"
    // "MSG [HH:mm] user: text" is also used server -> client
}
