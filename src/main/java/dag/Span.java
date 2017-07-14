package dag;

import misc.StaticHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * This class represents a span as described in the thesis, which is basically a set of elements.
 * For convenience, the minimum and maximum element of the span are stored explicitly.
 */
public class Span {

    public int min, max;
    public boolean hasElements = false;

    private Set<Integer> elements;

    /**
     * Creates a new span that does not contain any elements.
     */
    public Span() {min=Integer.MAX_VALUE; max=Integer.MIN_VALUE; elements = new HashSet<>();}

    /**
     * Adds a new element to the span.
     * @param i the element to be added
     */
    public void add(int i) {
        if(i < min) {
            min = i;
        }
        if(i > max) {
            max = i;
        }
        hasElements = true;
        elements.add(i);
    }

    /**
     * Adds a set of elements to the span.
     * @param integers the elements to be added
     */
    public void addAll(Set<Integer> integers) {
        for(int i: integers) {
            add(i);
        }
    }

    /**
     * @return the average of all elements in the span or 0 if the span is empty
     */
    public double avg() {
        if(elements.isEmpty()) return 0;
        return elements.stream().mapToInt(i -> i).average().getAsDouble();
    }

    /**
     * @return the median of all elements in the span or 0 if the span is empty
     */
    public double median() {
        if(elements.isEmpty()) return 0;
        return StaticHelper.median(elements);
    }

    public String toString() {
        return "(" + min + "," + max + ")";
    }

    public Set<Integer> getElements() {
        return elements;
    }
}
