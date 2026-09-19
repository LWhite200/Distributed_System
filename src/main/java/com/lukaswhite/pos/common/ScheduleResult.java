package com.lukaswhite.pos.common;

import java.io.Serializable;

/**
 * What a ServerNode sends back after a "SCHEDULE" request: either a
 * success message describing when/where the appointment landed, or a
 * failure message explaining why (e.g. unknown service name).
 *
 * Kept deliberately simple - one boolean and one human-readable String -
 * because the GUI just needs to show the message to the user, not parse
 * it apart. The server does all the formatting work up front.
 */
public class ScheduleResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;

    public ScheduleResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
