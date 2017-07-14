package misc;

import ml.Prediction;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A list implementing the prune_n function as described in the thesis. It is guaranteed that the entries of the list are sorted by score
 * in descending order. Note that this implementation is rather inefficient so reimplementing it might speed up the generation process.
 */
public class PrunedList extends ArrayList<Prediction> {

    private int maxEntries;

    /**
     * Creates a new pruned list.
     * @param maxEntries the maximum number of entries to store within the list
     */
    public PrunedList(int maxEntries) {
        super(maxEntries+1);
        this.maxEntries = maxEntries;
    }

    /**
     * Inserts a new element into the list. If the lists size is above {@link PrunedList#maxEntries} afterwards, the element with the lowest score is removed.
     * @param p the prediction to insert
     */
    @Override
    public boolean add(Prediction p) {

        boolean duplicate = false;
        boolean ret = false;

        for(Prediction pred: this) {
            if(pred.getValue().equals(p.getValue())) {
                pred.setScoreAndLmFreeScore(Math.max(pred.getScore(), p.getScore()), Math.max(pred.getLmFreeScore(), p.getLmFreeScore()));
                duplicate = true;
                break;
            }
        }

        if(!duplicate) {
            ret = super.add(p);
        }

        sortAndRestrictSize();

        return ret;
    }

    /**
     * Inserts all elements contained within a collection of predictions into the list. For further details, see {@link PrunedList#add}
     * @param p the collection of predictions to insert
     */
    @Override
    public boolean addAll(Collection<? extends Prediction> p) {
        for(Prediction pred: p) {
            add(pred);
        }
        return false;
    }

    /**
     * sorts the pruned list and removes the element with the least score, if necessary.
     */
    private void sortAndRestrictSize() {
        sort((o1, o2) -> {
            if(o1.getScore() == o2.getScore()) return 0;
            if(o1.getScore() > o2.getScore()) return -1;
            return 1;
        });

        while(size() > maxEntries) {
            remove(size()-1);
        }
    }
}
