package gen;

import dag.Amr;
import dag.Edge;
import dag.Vertex;
import misc.PosHelper;
import misc.WordLists;
import misc.WordNetHelper;
import ml.Prediction;
import net.sf.extjwnl.data.POS;
import simplenlg.features.Form;
import simplenlg.features.NumberAgreement;
import simplenlg.features.Person;
import simplenlg.features.Tense;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.phrasespec.VPPhraseSpec;
import simplenlg.realiser.english.Realiser;

import java.util.*;

/**
 * This helper class provides, for each vertex, the default realizations described in Section 5 (Implementation) of the thesis.
 */
public class DefaultRealizer {

    // a set of words for which the default realization is the empty word
    private static final Set DEFAULT_EMPTY_REALIZATION = new HashSet<>(Arrays.asList("person", "thing", "amr-unknown", "have-org-role", "contrast", "distance-quantity"));

    // a map containing some handwritten default realizations
    private static final Map<String,List<String>> DEFAULT_REALIZATIONS = new HashMap<>();

    static {
        DefaultRealizer.DEFAULT_REALIZATIONS.put("possible", Collections.singletonList("can"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("obligate-01", Collections.singletonList("must"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("+", Collections.singletonList("please"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("-", Arrays.asList("no", "not"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("cause-01", Arrays.asList("due to", "because", "for"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("contrast-01", Collections.singletonList("but"));
        DefaultRealizer.DEFAULT_REALIZATIONS.put("resemble-01", Collections.singletonList("like"));
    }

    private final WordNetHelper wordNetHelper;
    private final Lexicon lexicon;
    private final Realiser realizer;
    private final NLGFactory phraseFactory;

    public DefaultRealizer(WordNetHelper wordNetHelper) {
        this.wordNetHelper = wordNetHelper;
        this.lexicon = Lexicon.getDefaultLexicon();
        this.realizer = new Realiser(lexicon);
        this.phraseFactory = new NLGFactory(lexicon);
    }

    /**
     * Returns all default realizations for a vertex.
     * @param v the vertex
     * @param syntacticAnnotation the syntactic annotation assigned to this vertex
     * @return the default realizations of {@code v}
     */
    public List<String> getDefaultRealizations(Vertex v, Map<String, Prediction> syntacticAnnotation) {

        String inst = v.getInstance();

        if(inst == null) return Collections.singletonList("");

        if(inst.equals("do")) {
            return Arrays.asList("do", "did");
        }
        else if(inst.equals("be")) {
            if(v.mode.equals("imperative")) return Collections.singletonList("be");
            return Arrays.asList("is", "are", "was", "were", "be");
        }
        else if(inst.equals("have")) {
            return Arrays.asList("have", "has", "had");
        }


        inst = inst.replaceFirst("-[0-9]+","");

        if(DEFAULT_REALIZATIONS.keySet().contains(inst)) {
            return new ArrayList<>(DEFAULT_REALIZATIONS.get(inst));
        }

        if(DEFAULT_EMPTY_REALIZATION.contains(inst)) {
            return Collections.singletonList("");
        }

        if(Arrays.asList("he", "she", "i", "you", "they", "we", "it").contains(inst)) {
            if (!v.getIncomingEdges().isEmpty()) {

                if((inst.equals("you") || inst.equals("we")) && v.getIncomingEdges().get(0).getFrom().mode.equals("imperative")) {
                    return Collections.singletonList("");
                }
                if(v.getIncomingEdges().get(0).getLabel().equals(":poss")) {
                    return Collections.singletonList(WordLists.possessivePronouns.get(inst));
                }

            }
            List<String> ret = new ArrayList<>();
            ret.add(WordLists.possessivePronouns.get(inst));
            if(WordLists.personalPronouns.containsKey(inst)) ret.add(WordLists.personalPronouns.get(inst));
            ret.add(inst);
            return ret;
        }

        if(v.isLink()) {

            if(!v.getIncomingEdges().isEmpty()) {
                if(v.getIncomingEdges().get(0).getLabel().equals(":poss")) {
                    if(v.annotation.original.predictions.containsKey("number")) {
                        if(v.annotation.original.predictions.get("number").get(0).getValue().equals(GoldSyntacticAnnotations.SINGULAR)) {
                            return Arrays.asList(WordLists.possessivePronouns.get("he"), WordLists.possessivePronouns.get("it"), WordLists.possessivePronouns.get("she"));
                        }
                        else {
                            return Arrays.asList(WordLists.possessivePronouns.get("they"));
                        }
                    }
                }
            }

            return Collections.emptyList();
        }

        if(WordLists.NO_ALIGNMENT_CONCEPTS.contains(inst)) {
            return Collections.singletonList("");
        }

        String mappedPos = PosHelper.mapPos(v.getPos(), true);

        inst = inst.replaceAll("\"","");
        inst = inst.replace("-"," ");

        List<POS> instPosTags;

            instPosTags = new ArrayList<>();

            Map<POS,Integer> instPosCounts = wordNetHelper.getAllPOSTagsWithCount(inst, true);
            int maxCount = 0;
            for(POS p: instPosCounts.keySet()) {
                maxCount = Math.max(maxCount, instPosCounts.get(p));
            }

            for(POS p: instPosCounts.keySet()) {
                if(instPosCounts.get(p) >= maxCount) {
                    instPosTags.add(p);
                }
            }


        if (mappedPos.equals("VBG") && v.isPropbankEntry()) {
            if(!instPosTags.contains(POS.VERB)) return Collections.emptyList();
            VPPhraseSpec instSpec = phraseFactory.createVerbPhrase(inst);
            SPhraseSpec clause = phraseFactory.createClause();
            clause.setVerbPhrase(instSpec);
            clause.setFeature(simplenlg.features.Feature.FORM, Form.GERUND);
            inst = realizer.realise(clause).getRealisation();
            return Collections.emptyList();
        }
        else if (mappedPos.equals("VBN") && v.isPropbankEntry()) {
            if(!instPosTags.contains(POS.VERB)) return Collections.emptyList();
            VPPhraseSpec instSpec = phraseFactory.createVerbPhrase(inst);
            SPhraseSpec clause = phraseFactory.createClause();
            clause.setVerbPhrase(instSpec);
            clause.setFeature(simplenlg.features.Feature.FORM, Form.PAST_PARTICIPLE);
            inst = realizer.realise(clause).getRealisation();
            return Collections.emptyList();
        }
        else if (mappedPos.equals("NN") && v.isPropbankEntry()) {

            NumberAgreement numberAgreement = NumberAgreement.SINGULAR;

            if(syntacticAnnotation.containsKey("number")) {
                if(syntacticAnnotation.get("number").getValue().equals(GoldSyntacticAnnotations.PLURAL)) {
                    numberAgreement = NumberAgreement.PLURAL;
                }
            }

            List<String> candidates = new ArrayList<>();
            List<String> ret = new ArrayList<>();

            if(instPosTags.contains(POS.NOUN)) candidates.add(inst);

            if(WordLists.morphVerbalization.containsKey(inst)) {
                candidates.addAll(WordLists.morphVerbalization.get(inst));
            }
            for(String candidate: candidates) {
                NPPhraseSpec instSpec = phraseFactory.createNounPhrase(candidate);
                SPhraseSpec clause = phraseFactory.createClause();
                clause.setSubject(instSpec);
                instSpec.setFeature(simplenlg.features.Feature.NUMBER, numberAgreement);
                String realization = realizer.realise(clause).getRealisation();

                    if(wordNetHelper.getAllPOSTagsWithCount(realization, true).getOrDefault(POS.NOUN, 0) > 1) {
                        ret.add(realization);
                    }

            }
            return ret;
        }

        else if(mappedPos.equals("VB") && v.isPropbankEntry()) {

            if(!instPosTags.contains(POS.VERB)) return Collections.emptyList();

            VPPhraseSpec instSpec = phraseFactory.createVerbPhrase(inst);
            SPhraseSpec clause = phraseFactory.createClause();
            clause.setVerbPhrase(instSpec);

            if(!v.mode.isEmpty()) {
                clause.setFeature(simplenlg.features.Feature.FORM, Form.IMPERATIVE);
                return Collections.singletonList(inst);
            }

            else {

                Tense tense = Tense.PAST;

                if(syntacticAnnotation.containsKey("tense")) {
                    switch(syntacticAnnotation.get("tense").getValue()) {
                        case GoldSyntacticAnnotations.PAST: tense = Tense.PAST; break;
                        case GoldSyntacticAnnotations.PRESENT: tense = Tense.PRESENT; break;
                        case GoldSyntacticAnnotations.NONE: return Collections.singletonList(inst);
                        case GoldSyntacticAnnotations.FUTURE: return Collections.singletonList(inst);
                    }
                }

                clause.setFeature(simplenlg.features.Feature.FORM, Form.NORMAL);
                clause.setFeature(simplenlg.features.Feature.TENSE, tense);
                clause.setFeature(simplenlg.features.Feature.NUMBER, NumberAgreement.SINGULAR);
                clause.setFeature(simplenlg.features.Feature.PERSON, Person.THIRD);

                String realization1 = realizer.realise(clause).getRealisation();

                clause.setFeature(simplenlg.features.Feature.NUMBER, NumberAgreement.PLURAL);
                //clause.setFeature(simplenlg.features.Feature.TENSE, Tense.PRESENT);

                String realization2 = realizer.realise(clause).getRealisation();

                if(realization1.equals(realization2)) return Collections.singletonList(realization1);

                boolean thirdPerson = guessThirdPerson(null, v, true);
                if(thirdPerson) {
                    return Arrays.asList(realization1, realization2);
                }

                return Collections.singletonList(realization2);

            }
        }
        else if(mappedPos.equals("JJ") && v.isPropbankEntry()) {
            if(!instPosTags.contains(POS.ADJECTIVE) && !instPosTags.contains(POS.ADVERB)) return Collections.emptyList();

            if(instPosTags.contains(POS.ADJECTIVE) && ! instPosTags.contains(POS.ADVERB)) {

                // get the correspoding adverb
                String adverb;

                if(Arrays.asList("fast","hard","very","quite").contains(inst)) {
                    adverb = inst;
                }
                else if(inst.equals("good")) {
                    adverb = "well";
                }
                else if(inst.endsWith("-ic")) {
                    adverb = inst + "ally";
                }
                else if(inst.endsWith("-le")) {
                    adverb = inst.substring(0, inst.length()-1) + "y";
                }
                else if(inst.endsWith("y")) {
                    adverb = inst.substring(0, inst.length()-1) + "ily";
                }
                else {
                    adverb = inst + "ly";
                }


                if(wordNetHelper.getAllPOSTags(adverb).contains(POS.ADVERB)) {
                    return Arrays.asList(inst, adverb);
                }
            }
        }
        else if(v.isPropbankEntry()) {
            return Collections.emptyList();
        }

        // check dates
        if(!v.getIncomingEdges().isEmpty()) {

            Edge inEdge = v.getIncomingEdges().get(0);
            String lab = inEdge.getLabel();
            if(lab.equals(":month")) {
                int month = Integer.valueOf(inst);
                inst = WordLists.months.get(month);
            }
            else if(lab.equals(":day")) {
            }
            else {
                boolean isNumeric = true;
                long val = 0;
                try {
                    val = Long.valueOf(inst);
                }
                catch(NumberFormatException e) {
                    isNumeric = false;
                }
                if(isNumeric) {
                    if (val >= 1000 && val <= 999999) {
                    } else if (val >= 1000000000) {
                        double decVal = val / 1000000000d;
                        String decValString = (decVal+"").replace(".0","");
                        inst = decValString + " billion";
                    } else if (val >= 1000000) {
                        double decVal = val / 1000000d;
                        String decValString = (decVal+"").replace(".0","");
                        inst = decValString + " million";
                    }
                }
            }
            if(lab.equals(":value")) {
                if(inEdge.getFrom().getInstance().equals("ordinal-entity")) {
                    int val = Integer.valueOf(inst);
                    if(val == 1) inst = "first";
                    if(val == 2) inst = "second";
                    if(val == 3) inst = "third";
                    if(val == 4) inst = "fourth";
                    if(val == 5) inst = "fifth";
                    if(val == 6) inst = "sixth";
                }
            }
        }

        if(inst.equals("1")) {return Arrays.asList("one", "1");}
        if (inst.equals("2")) {return Arrays.asList("two", "2");}

        return Collections.singletonList(inst);
    }

    /**
     * Makes an educated guess about whether a vertex represents a verb that is in third person.
     * @param amr the AMR graph to which the vertex belongs
     * @param vertex the vertex
     * @param forTesting whether this guess is made during test time (i.e. whether no gold annotations are available)
     * @return true iff it is likely that the verb is in third person
     */
    public static boolean guessThirdPerson(Amr amr, Vertex vertex, boolean forTesting) {
        boolean thirdPerson = false;

        for(int i=0; i <= 1; i++) {
            for (Edge e : vertex.getOutgoingEdges()) {
                if (e.getLabel().equals(":ARG"+i)) {
                    if (WordLists.possessivePronouns.keySet().contains(e.getTo().getInstance())) {
                        if (Arrays.asList("he", "she", "it").contains(e.getTo().getInstance())) thirdPerson = true;
                    } else if (!forTesting) {
                        if (GoldSyntacticAnnotations.getGoldNumber(amr, e.getTo()).equals(GoldSyntacticAnnotations.SINGULAR)) thirdPerson = true;
                    } else {
                        if (e.getTo().predictions.containsKey("number")) {
                            if (e.getTo().predictions.get("number").get(0).getValue().equals(GoldSyntacticAnnotations.SINGULAR))
                                thirdPerson = true;
                        }
                    }
                    break;
                }
            }
        }

        return thirdPerson;
    }
}
