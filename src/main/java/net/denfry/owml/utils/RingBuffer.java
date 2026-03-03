package net.denfry.owml.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A synchronized ring buffer for storing a fixed number of recent elements.
 * 
 * @param <T> The type of elements stored in the buffer.
 */
public class RingBuffer<T> {
    private final Object[] buffer;
    private int head = 0;
    private int tail = 0;
    private int size = 0;
    private final int capacity;

    public RingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    /**
     * Adds an element to the buffer.
     * 
     * @param element The element to add.
     */
    public synchronized void add(T element) {
        buffer[tail] = element;
        tail = (tail + 1) % capacity;
        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) % capacity;
        }
    }

    /**
     * Returns a list of all elements in the buffer in the order they were added.
     * 
     * @return List of elements.
     */
    @SuppressWarnings("unchecked")
    public synchronized List<T> getAll() {
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add((T) buffer[(head + i) % capacity]);
        }
        return list;
    }

    /**
     * Clears the buffer.
     */
    public synchronized void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }

    /**
     * Returns the current number of elements in the buffer.
     * 
     * @return Current size.
     */
    public synchronized int size() {
        return size;
    }
}
