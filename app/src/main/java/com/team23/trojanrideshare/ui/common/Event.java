package com.team23.trojanrideshare.ui.common;

/** A LiveData payload that should be handled once (a toast, a navigation). */
public class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Returns the content the first time, then null. */
    public T take() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
