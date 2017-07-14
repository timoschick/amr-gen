package misc;

import main.PathList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * A non-instantiable class that contains some WordLists useful for either classification or realization of nodes.
 */
public class WordLists {

    // non-instantiable class
    private WordLists() { }

    // a mapping from a countries and continents to the corresponding adjective (e.g. china -> chinese, england -> english, europe -> european, great britain -> british)
    public static final Map<String,String> countryforms = new HashMap<>();

    // a mapping from verbs to corresponding nouns (e.g. invest -> investor), extracted from http://amr.isi.edu/download/lists/morph-verbalization-v1.01.txt
    public static final Map<String,Set<String>> morphVerbalization = new HashMap<>();

    // a mapping from numbers to month names (e.g. 1 -> january, 10 -> october)
    public static Map<Integer, String> months = new HashMap<>();

    // the set of all english articles
    public static final Set<String> articles = new HashSet<>(Arrays.asList("a", "an", "the"));

    // a set containing all forms of the verb 'have'
    public static final List<String> haveForms = Arrays.asList("have", "has", "had", "having");

    // a set containing several AMR concepts that may be realized by forms of the verb 'have'
    public static final List<String> haveInstances = Arrays.asList("have-03", "have-06", "have-04", "obligate-01", "have-00");

    // a set containing all forms of the verb 'be'
    public static final List beForms = Arrays.asList("'m", "be", "is", "are", "am", "were", "been", "was", "being");

    // a set containing several question words
    public static final Set questionWords = new HashSet<>(Arrays.asList("how", "where", "why", "who", "which", "what", "when"));

    // a set containing all pronouns
    public static final Set<String> pronouns = new HashSet<>(Arrays.asList("i", "you", "he", "she", "it", "we", "they"));

    // a mapping from grammatical person to the corresponding possessive pronoun (e.g. i -> mine, he -> his)
    public static Map<String, String> possessivePronouns = new HashMap<>();

    // a mapping from grammatical person to the corresponding personal pronoun (e.g. i -> me, he -> him)
    public static Map<String, String> personalPronouns = new HashMap<>();

    // the set of words allowed for Insert-Between
    public static List<String> childInsertableWords = Arrays.asList("have", "has", "having", "had", "be", "am", "is", "are", "were", "was", "being", "been", "do", "did", "doing", "done", "will");

    // the set of words w allowed for Insert-Between-(w,r)
    public static final List<String> afterInsertableWords = Arrays.asList("'s");

    // the set of words w allowed for Insert-Between-(w,l), partially extracted from https://www.englishclub.com/grammar/prepositions-list.htm
    public static final List<String> beforeInsertableWords = Arrays.asList(
            "aboard","about","above","across","after","against","along","amid","among","anti","around","as","at",
            "before","behind","below","beneath","beside","besides","between","beyond","but","by",
            "concerning",
            "despite","down","during",
            "except","excepting","excluding",
            "following","for","from",
            "in","inside","into","like",
            "minus",
            "near",
            "of","off","on","onto","opposite","outside","over",
            "past","per","plus",
            "regarding","round",
            "save","since","than","through","to","toward","towards",
            "under","underneath","unlike","until","up","upon",
            "versus","via",
            "with","within","without",
            "that","when","who","how","which","what", "if" ,"due"
    );

    // the set of concept labels for which no alignment is allowed
    public static final Set<String> NO_ALIGNMENT_CONCEPTS = new HashSet<>(Arrays.asList("byline-91", "ordinal-entity", "temporal-quantity", "monetary-quantity", "mass-quantity", "multi-sentence", ":weekday", "date-entity"));

    // the set of edge labels for which no alignment is allowed
    public static final Set<String> NO_ALIGNMENT_EDGES = new HashSet<>(Arrays.asList(":op1",":op2",":op3",":op4",":op5",":op6",":op7",":op8",":op9",":op10",":snt1",":snt2",":snt3",":snt4",":snt5",":domain"));

    // the set of words for which no alignment is allowed
    public static final Set<String> NO_ALIGNMENT_WORDS = new HashSet<>(Arrays.asList("how", "where"));

    // a set of words for which the correct transition should always be DELETE
    public static final Set<String> ALWAYS_DELETE = Collections.singleton("multi-sentence");

    // a set of words for which the correct transition should never be DELETE
    public static final Set<String> NEVER_DELETE = new HashSet<>(Arrays.asList("-","+","amr-unknown"));

    // a mapping from words to AMR instances allowed for alignment (e.g. but -> {contrast-01})
    public static final Map<String,Set<String>> ALIGNMENT_MAP = new HashMap<>();

    // a mapping from insertable words to allowed edge labels (e.g. if -> {:concession})
    public static final Map<String,Set<String>> INSERTION_CONSTRAINTS = new HashMap<>();

    // fill the countryforms map
    static {
        countryforms.put("abkhazia","abkhazian"); countryforms.put("afghanistan","afghan"); countryforms.put("åland islands","åland island"); countryforms.put("albania","albanian"); countryforms.put("algeria","algerian"); countryforms.put("american samoa","american samoan"); countryforms.put("andorra","andorran"); countryforms.put("angola","angolan"); countryforms.put("anguilla","anguillan"); countryforms.put("antarctica","antarctic"); countryforms.put("argentina","argentine"); countryforms.put("armenia","armenian"); countryforms.put("aruba","aruban"); countryforms.put("australia","australian"); countryforms.put("austria","austrian"); countryforms.put("azerbaijan","azerbaijani"); countryforms.put("bahamas","bahamian"); countryforms.put("bahrain","bahraini"); countryforms.put("bangladesh","bangladeshi"); countryforms.put("barbados","barbadian"); countryforms.put("belarus","belarusian"); countryforms.put("belgium","belgian"); countryforms.put("belize","belizean"); countryforms.put("benin","beninese"); countryforms.put("bermuda","bermudian"); countryforms.put("bhutan","bhutanese"); countryforms.put("bolivia","bolivian"); countryforms.put("bonaire","bonaire"); countryforms.put("botswana","botswanan"); countryforms.put("bouvet island","bouvet island"); countryforms.put("brazil","brazilian"); countryforms.put("brunei","bruneian"); countryforms.put("bulgaria","bulgarian"); countryforms.put("burkina faso","burkinabé"); countryforms.put("burma","burmese"); countryforms.put("burundi","burundian"); countryforms.put("cabo verde","cabo verdean"); countryforms.put("cambodia","cambodian"); countryforms.put("cameroon","cameroonian"); countryforms.put("canada","canadian"); countryforms.put("cayman islands","caymanian"); countryforms.put("central african republic","central african"); countryforms.put("chad","chadian"); countryforms.put("chile","chilean"); countryforms.put("china","chinese"); countryforms.put("christmas island","christmas island"); countryforms.put("cocos (keeling) islands","cocos island"); countryforms.put("colombia","colombian"); countryforms.put("comoros","comorian"); countryforms.put("congo","congolese"); countryforms.put("côte d'ivoire","ivorian"); countryforms.put("croatia","croatian"); countryforms.put("cuba","cuban"); countryforms.put("curaçao","curaçaoan"); countryforms.put("cyprus","cypriot"); countryforms.put("czech republic","czech"); countryforms.put("denmark","danish"); countryforms.put("djibouti","djiboutian"); countryforms.put("dominica","dominican"); countryforms.put("dominican republic","dominican"); countryforms.put("east timor","timorese"); countryforms.put("ecuador","ecuadorian"); countryforms.put("egypt","egyptian"); countryforms.put("el salvador","salvadoran"); countryforms.put("england","english"); countryforms.put("eritrea","eritrean"); countryforms.put("estonia","estonian"); countryforms.put("ethiopia","ethiopian"); countryforms.put("european union","european"); countryforms.put("falkland islands","falkland island"); countryforms.put("faroe islands","faroese"); countryforms.put("fiji","fijian"); countryforms.put("finland","finnish"); countryforms.put("france","french"); countryforms.put("french guiana","french guianese"); countryforms.put("french polynesia","french polynesian"); countryforms.put("french southern territories","french southern territories"); countryforms.put("gabon","gabonese"); countryforms.put("gambia","gambian"); countryforms.put("georgia","georgian"); countryforms.put("germany","german"); countryforms.put("ghana","ghanaian"); countryforms.put("gibraltar","gibraltar"); countryforms.put("great britain","british"); countryforms.put("greece","greek"); countryforms.put("greenland","greenlandic"); countryforms.put("grenada","grenadian"); countryforms.put("guadeloupe","guadeloupe"); countryforms.put("guam","guamanian"); countryforms.put("guatemala","guatemalan"); countryforms.put("guinea","guinean"); countryforms.put("guinea-bissau","bissau-guinean"); countryforms.put("guyana","guyanese"); countryforms.put("haiti","haitian"); countryforms.put("hong kong","hong kongese"); countryforms.put("hungary","hungarian"); countryforms.put("iceland","icelandic"); countryforms.put("india","indian"); countryforms.put("indonesia","indonesian"); countryforms.put("iran","iranian"); countryforms.put("iraq","iraqi"); countryforms.put("ireland","irish"); countryforms.put("isle of man","manx"); countryforms.put("israel","israeli"); countryforms.put("italy","italian"); countryforms.put("ivory coast","ivorian"); countryforms.put("jamaica","jamaican"); countryforms.put("jan mayen","jan mayen"); countryforms.put("japan","japanese"); countryforms.put("jersey","channel island"); countryforms.put("jordan","jordanian"); countryforms.put("kazakhstan","kazakhstani"); countryforms.put("kenya","kenyan"); countryforms.put("kiribati","i-kiribati"); countryforms.put("north korea","north korean"); countryforms.put("south korea","south korean"); countryforms.put("kosovo","kosovar"); countryforms.put("kuwait","kuwaiti"); countryforms.put("kyrgyzstan","kyrgyzstani"); countryforms.put("laos","lao"); countryforms.put("latvia","latvian"); countryforms.put("lebanon","lebanese"); countryforms.put("lesotho","basotho"); countryforms.put("liberia","liberian"); countryforms.put("libya","libyan"); countryforms.put("liechtenstein","liechtenstein"); countryforms.put("lithuania","lithuanian"); countryforms.put("luxembourg","luxembourg"); countryforms.put("macau","macanese"); countryforms.put("macedonia, republic of","macedonian"); countryforms.put("madagascar","malagasy"); countryforms.put("malawi","malawian"); countryforms.put("malaysia","malaysian"); countryforms.put("maldives","maldivian"); countryforms.put("mali","malian"); countryforms.put("malta","maltese"); countryforms.put("marshall islands","marshallese"); countryforms.put("martinique","martiniquais"); countryforms.put("mauritania","mauritanian"); countryforms.put("mauritius","mauritian"); countryforms.put("mayotte","mahoran"); countryforms.put("mexico","mexican"); countryforms.put("micronesia, federated states of","micronesian"); countryforms.put("moldova","moldovan"); countryforms.put("monaco","monégasque"); countryforms.put("mongolia","mongolian"); countryforms.put("montenegro","montenegrin"); countryforms.put("montserrat","montserratian"); countryforms.put("morocco","moroccan"); countryforms.put("mozambique","mozambican"); countryforms.put("myanmar","burmese"); countryforms.put("namibia","namibian"); countryforms.put("nauru","nauruan"); countryforms.put("nepal","nepali"); countryforms.put("netherlands","dutch"); countryforms.put("new caledonia","new caledonian"); countryforms.put("new zealand","new zealand"); countryforms.put("nicaragua","nicaraguan"); countryforms.put("niger","nigerien"); countryforms.put("nigeria","nigerian"); countryforms.put("niue","niuean"); countryforms.put("norfolk island","norfolk island"); countryforms.put("northern ireland","northern irish"); countryforms.put("northern mariana islands","northern marianan"); countryforms.put("norway","norwegian"); countryforms.put("oman","omani"); countryforms.put("pakistan","pakistani"); countryforms.put("palau","palauan"); countryforms.put("palestine","palestinian"); countryforms.put("panama","panamanian"); countryforms.put("papua new guinea","papua new guinean"); countryforms.put("paraguay","paraguayan"); countryforms.put("peru","peruvian"); countryforms.put("philippines","filipino"); countryforms.put("pitcairn islands","pitcairn island"); countryforms.put("poland","polish"); countryforms.put("portugal","portuguese"); countryforms.put("puerto rico","puerto rican"); countryforms.put("qatar","qatari"); countryforms.put("réunion","réunionese"); countryforms.put("romania","romanian"); countryforms.put("russia","russian"); countryforms.put("rwanda","rwandan"); countryforms.put("saba","saba"); countryforms.put("saint barthélemy","barthélemois"); countryforms.put("saint helena, ascension and tristan da cunha","saint helenian"); countryforms.put("saint kitts and nevis","kittitian or nevisian"); countryforms.put("saint lucia","saint lucian"); countryforms.put("saint martin","saint-martinoise"); countryforms.put("saint pierre and miquelon","saint-pierrais or miquelonnais"); countryforms.put("saint vincent and the grenadines","saint vincentian"); countryforms.put("samoa","samoan"); countryforms.put("san marino","sammarinese"); countryforms.put("são tomé and príncipe","são toméan"); countryforms.put("saudi arabia","saudi"); countryforms.put("scotland","scots"); countryforms.put("senegal","senegalese"); countryforms.put("serbia","serbian"); countryforms.put("seychelles","seychellois"); countryforms.put("sierra leone","sierra leonean"); countryforms.put("singapore","singapore"); countryforms.put("sint eustatius","sint eustatius"); countryforms.put("sint maarten","sint maarten"); countryforms.put("slovakia","slovak"); countryforms.put("slovenia","slovenian"); countryforms.put("solomon islands","solomon island"); countryforms.put("somalia","somali"); countryforms.put("south africa","south african"); countryforms.put("south georgia and the south sandwich islands","south georgia or south sandwich islands"); countryforms.put("south ossetia (region of georgia)","south ossetian"); countryforms.put("south sudan","south sudanese"); countryforms.put("spain","spanish"); countryforms.put("sri lanka","sri lankan"); countryforms.put("sudan","sudanese"); countryforms.put("surinam","surinamese"); countryforms.put("svalbard","svalbard"); countryforms.put("swaziland","swazi"); countryforms.put("sweden","swedish"); countryforms.put("switzerland","swiss"); countryforms.put("syria","syrian"); countryforms.put("tajikistan","tajikistani"); countryforms.put("tanzania","tanzanian"); countryforms.put("thailand","thai"); countryforms.put("timor-leste","timorese"); countryforms.put("togo","togolese"); countryforms.put("tokelau","tokelauan"); countryforms.put("tonga","tongan"); countryforms.put("trinidad and tobago","trinidadian or tobagonian"); countryforms.put("tunisia","tunisian"); countryforms.put("turkey","turkish"); countryforms.put("turkmenistan","turkmen"); countryforms.put("turks and caicos islands","turks and caicos island"); countryforms.put("tuvalu","tuvaluan"); countryforms.put("uganda","ugandan"); countryforms.put("ukraine","ukrainian"); countryforms.put("united arab emirates","emirati"); countryforms.put("united kingdom","british"); countryforms.put("united states","american"); countryforms.put("uruguay","uruguayan"); countryforms.put("uzbekistan","uzbekistani"); countryforms.put("vanuatu","ni-vanuatu"); countryforms.put("vatican city state","vatican"); countryforms.put("venezuela","venezuelan"); countryforms.put("vietnam","vietnamese"); countryforms.put("virgin islands, british","british virgin island"); countryforms.put("virgin islands, united states","u.s. virgin island"); countryforms.put("wales","welsh"); countryforms.put("wallis and futuna","wallis and futuna"); countryforms.put("western sahara","sahrawi"); countryforms.put("yemen","yemeni"); countryforms.put("zambia","zambian"); countryforms.put("zimbabwe","zimbabwean"); countryforms.put("europe", "european"); countryforms.put("asia", "asian"); countryforms.put("america", "american"); countryforms.put("west", "western"); countryforms.put("taiwan", "taiwanese"); countryforms.put("britain","british"); countryforms.put("tajikistan","tajik"); countryforms.put("southeast asia","southeast asian"); countryforms.put("islam","islamic"); countryforms.put("kashmir","kashmiri"); countryforms.put("soviet union", "soviet"); countryforms.put("north africa", "north african"); countryforms.put("islamism", "islamist"); countryforms.put("himalayas", "himalayan"); countryforms.put("africa", "african"); countryforms.put("east asia", "east asian"); countryforms.put("south asia", "south asian"); countryforms.put("kurdistan", "kurdish");
    }

    // fill the alignment map and the insertionConstraints map
    static {
        ALIGNMENT_MAP.put("but", Collections.singleton("contrast-01"));
        ALIGNMENT_MAP.put("but", Collections.singleton("have-concession-91"));
        ALIGNMENT_MAP.put("like", Collections.singleton("resemble-01"));

        INSERTION_CONSTRAINTS.put("but", new HashSet<>(Arrays.asList(":concession", ":concession-of")));
        INSERTION_CONSTRAINTS.put("if", Collections.singleton(":condition"));
    }

    // fill the morphVerbalization map
    static {
        try {
            List<String> lines = Files.readAllLines(Paths.get(PathList.MORPH_VERBALIZATION_PATH));
            for(String line: lines) {
                String comps[] = line.split(" ");
                if(comps.length >= 2) {
                    Set<String> nouns = new HashSet<>();
                    for(int i=1; i < comps.length; i++) {
                        nouns.add(comps[i]);
                    }
                    morphVerbalization.put(comps[0], nouns);
                }
            }
        } catch (IOException e) {
            System.err.println("couldnt read morp verbalization file");
        }
    }

    // fill pronouns and months maps
    static {
        possessivePronouns.put("i", "my");
        possessivePronouns.put("you", "your");
        possessivePronouns.put("he", "his");
        possessivePronouns.put("she", "her");
        possessivePronouns.put("it", "its");
        possessivePronouns.put("we", "our");
        possessivePronouns.put("they", "their");

        personalPronouns.put("i", "me");
        personalPronouns.put("he", "him");
        personalPronouns.put("we", "us");
        personalPronouns.put("they", "them");

        months.put(1, "january");
        months.put(2, "february");
        months.put(3, "march");
        months.put(4, "april");
        months.put(5, "may");
        months.put(6, "june");
        months.put(7, "july");
        months.put(8, "august");
        months.put(9, "september");
        months.put(10, "october");
        months.put(11, "november");
        months.put(12, "december");
    }
}
