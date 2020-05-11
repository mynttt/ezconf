package de.mynttt.ezconf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class Stack<T> implements Iterable<T> {
    private final ArrayList<T> backing = new ArrayList<>();
    private int idx = -1;

    public T pop() {
        if(idx < 0)
            throw new NoSuchElementException("stack is empty");
        return backing.remove(idx--);
    }

    public T peek() {
        if(idx < 0)
            throw new NoSuchElementException("stack is empty");
        return backing.get(idx);
    }

    public boolean isEmpty() {
        return idx < 0;
    }

    public void push(T element) {
        backing.add(element);
        ++idx;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Iterator<T> it = backing.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public T next() {
                return it.next();
            }
        };
    }

    public int size() {
        return idx + 1;
    }

}
