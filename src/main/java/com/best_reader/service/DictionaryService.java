package com.best_reader.service;

import com.best_reader.model.DictionaryWord;
import com.best_reader.repository.DictionaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final DictionaryRepository dictionaryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, DictionaryWord> OFFLINE_FR = createOfflineDictionary();

    public DictionaryWord findWord(String word, String language) {
        String cleanWord = clean(word);
        String cleanLanguage = cleanLanguage(language);
        if (cleanWord.isBlank()) return fallback(cleanWord, cleanLanguage);

        DictionaryWord local = findFirstLocal(cleanWord, cleanLanguage);
        if (hasRealDefinition(local)) return normalizeResult(local, cleanWord, cleanLanguage);

        DictionaryWord offline = findOffline(cleanWord, cleanLanguage);
        if (offline != null) return saveOfflineSafe(offline);

        DictionaryWord online = fetchFromFreeDictionary(cleanWord, cleanLanguage);
        if (hasRealDefinition(online)) return saveOfflineSafe(online);

        if ("fr".equals(cleanLanguage)) {
            DictionaryWord wiktionary = fetchFromWiktionaryFr(cleanWord);
            if (hasRealDefinition(wiktionary)) return saveOfflineSafe(wiktionary);
        }

        if ("en".equals(cleanLanguage)) {
            DictionaryWord english = fetchFromFreeDictionary(cleanWord, "en");
            if (hasRealDefinition(english)) return saveOfflineSafe(english);
        }

        return fallback(cleanWord, cleanLanguage);
    }

    public List<DictionaryWord> searchWords(String word, String language) {
        return dictionaryRepository.findByWordContainingIgnoreCaseAndLanguage(clean(word), cleanLanguage(language));
    }

    public boolean wordExists(String word, String language) {
        return dictionaryRepository.existsByWordAndLanguage(clean(word), cleanLanguage(language));
    }

    @Transactional
    public DictionaryWord saveOffline(DictionaryWord input) {
        if (input == null) return fallback("mot", "fr");
        String cleanWord = clean(input.getWord());
        String lang = cleanLanguage(input.getLanguage());
        if (cleanWord.isBlank()) return fallback("mot", lang);

        DictionaryWord d = findFirstLocal(cleanWord, lang);
        if (d == null) d = new DictionaryWord();
        d.setWord(cleanWord);
        d.setLanguage(lang);
        d.setShortDefinition(safeText(input.getShortDefinition(), 5000));
        d.setDefinition(safeText(input.getDefinition(), 20000));
        d.setSynonyms(limitSynonyms(input.getSynonyms()));
        return dictionaryRepository.save(d);
    }

    public DictionaryWord saveOfflineSafe(DictionaryWord input) {
        try { return saveOffline(input); }
        catch (DataAccessException ex) { return normalizeResult(input, input == null ? "mot" : input.getWord(), input == null ? "fr" : input.getLanguage()); }
        catch (Exception ex) { return normalizeResult(input, input == null ? "mot" : input.getWord(), input == null ? "fr" : input.getLanguage()); }
    }

    private DictionaryWord findFirstLocal(String word, String language) {
        List<DictionaryWord> rows = dictionaryRepository.findByWordIgnoreCaseAndLanguageIgnoreCaseOrderByIdAsc(word, language);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private DictionaryWord findOffline(String word, String language) {
        if (!"fr".equals(language)) return null;
        for (String candidate : candidates(word)) {
            DictionaryWord d = OFFLINE_FR.get(candidate);
            if (d != null) return copy(d);
        }
        return null;
    }

    private DictionaryWord fetchFromFreeDictionary(String word, String lang) {
        try {
            RestTemplate restTemplate = restTemplate();
            String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8);
            String url = "https://api.dictionaryapi.dev/api/v2/entries/" + lang + "/" + encoded;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) return null;
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray() || root.isEmpty()) return null;
            JsonNode entry = root.get(0);
            JsonNode meaning = first(entry.path("meanings"));
            JsonNode definitionNode = meaning == null ? null : first(meaning.path("definitions"));
            String definition = definitionNode == null ? "" : definitionNode.path("definition").asText("");
            if (definition.isBlank()) return null;
            List<String> synonyms = new ArrayList<>();
            if (meaning != null) meaning.path("synonyms").forEach(s -> synonyms.add(s.asText()));
            if (definitionNode != null) definitionNode.path("synonyms").forEach(s -> synonyms.add(s.asText()));
            if ("en".equals(lang) && synonyms.size() < 2) synonyms.addAll(fetchEnglishSynonyms(word));
            DictionaryWord d = new DictionaryWord();
            d.setWord(clean(entry.path("word").asText(word)));
            d.setLanguage(lang);
            d.setShortDefinition(meaning == null ? "Definition" : meaning.path("partOfSpeech").asText("Definition"));
            d.setDefinition(definition);
            d.setSynonyms(limitSynonyms(String.join(",", synonyms)));
            return d;
        } catch (RestClientException e) { return null; }
        catch (Exception e) { return null; }
    }

    private DictionaryWord fetchFromWiktionaryFr(String word) {
        try {
            RestTemplate restTemplate = restTemplate();
            String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8);
            String url = "https://fr.wiktionary.org/api/rest_v1/page/summary/" + encoded;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) return null;
            JsonNode root = objectMapper.readTree(response);
            String extract = root.path("extract").asText("").replaceAll("\\s+", " ").trim();
            if (extract.isBlank() || extract.toLowerCase(Locale.ROOT).contains("peut désigner")) return null;
            DictionaryWord d = new DictionaryWord();
            d.setWord(word);
            d.setLanguage("fr");
            d.setShortDefinition("Wiktionnaire");
            d.setDefinition(extract.length() > 700 ? extract.substring(0, 700) + "…" : extract);
            d.setSynonyms(limitSynonyms(synonymesFrancaisProbables(word)));
            return d;
        } catch (Exception e) { return null; }
    }

    private List<String> fetchEnglishSynonyms(String word) {
        List<String> out = new ArrayList<>();
        try {
            RestTemplate restTemplate = restTemplate();
            String url = "https://api.datamuse.com/words?max=2&rel_syn=" + URLEncoder.encode(word, StandardCharsets.UTF_8);
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) return out;
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray()) root.forEach(n -> out.add(n.path("word").asText("")));
        } catch (Exception ignored) {}
        return out;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(3500);
        return new RestTemplate(factory);
    }

    private DictionaryWord fallback(String word, String language) {
        DictionaryWord fallback = new DictionaryWord();
        fallback.setWord(word == null || word.isBlank() ? "mot" : clean(word));
        fallback.setLanguage(cleanLanguage(language));
        fallback.setShortDefinition("Mot non trouvé");
        fallback.setDefinition("Aucune définition précise n’a été trouvée dans la base locale. Connecte-toi à Internet une fois ou ajoute ce mot pour l’utiliser hors connexion ensuite.");
        fallback.setSynonyms("");
        return fallback;
    }

    private boolean hasRealDefinition(DictionaryWord d) {
        if (d == null || d.getDefinition() == null || d.getDefinition().isBlank()) return false;
        String txt = (nullToEmpty(d.getShortDefinition()) + " " + d.getDefinition()).toLowerCase(Locale.ROOT);
        return !txt.contains("définition introuvable") && !txt.contains("aucune définition trouvée");
    }

    private DictionaryWord normalizeResult(DictionaryWord d, String word, String language) {
        if (d == null) return fallback(word, language);
        d.setWord(clean(d.getWord() == null || d.getWord().isBlank() ? word : d.getWord()));
        d.setLanguage(cleanLanguage(d.getLanguage() == null || d.getLanguage().isBlank() ? language : d.getLanguage()));
        d.setShortDefinition(safeText(d.getShortDefinition(), 5000));
        d.setDefinition(safeText(d.getDefinition(), 20000));
        d.setSynonyms(limitSynonyms(d.getSynonyms()));
        return d;
    }

    private String limitSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) return "";
        String[] parts = synonyms.split(",");
        List<String> two = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isBlank() && two.stream().noneMatch(x -> x.equalsIgnoreCase(s))) two.add(s);
            if (two.size() == 2) break;
        }
        return String.join(",", two);
    }

    private static JsonNode first(JsonNode array) {
        return array != null && array.isArray() && !array.isEmpty() ? array.get(0) : null;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("^[^a-zA-ZÀ-ÿ]+|[^a-zA-ZÀ-ÿ]+$", "").trim();
    }

    private static String cleanLanguage(String language) {
        if (language == null || language.isBlank()) return "fr";
        String l = language.toLowerCase(Locale.ROOT).trim();
        return l.startsWith("en") ? "en" : "fr";
    }

    private static String safeText(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static DictionaryWord copy(DictionaryWord source) {
        DictionaryWord d = new DictionaryWord();
        d.setWord(source.getWord()); d.setLanguage(source.getLanguage()); d.setShortDefinition(source.getShortDefinition());
        d.setDefinition(source.getDefinition()); d.setSynonyms(source.getSynonyms()); return d;
    }

    private static List<String> candidates(String word) {
        List<String> list = new ArrayList<>();
        list.add(word);
        String noAccent = Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        list.add(noAccent);
        for (String w : new ArrayList<>(list)) {
            if (w.endsWith("s") && w.length() > 3) list.add(w.substring(0, w.length() - 1));
            if (w.endsWith("es") && w.length() > 4) list.add(w.substring(0, w.length() - 2));
            if (w.endsWith("ant") && w.length() > 5) { list.add(w.substring(0, w.length() - 3) + "er"); list.add(w.substring(0, w.length() - 3) + "re"); }
        }
        return list.stream().distinct().toList();
    }

    private static String synonymesFrancaisProbables(String word) {
        return switch (word) {
            case "maintenant" -> "actuellement,désormais";
            case "bouche" -> "gueule,orifice";
            case "dans" -> "dedans,parmi";
            case "riant", "rire" -> "souriant,s’amusant";
            case "crachant", "cracher" -> "rejetant,expulsant";
            case "livre" -> "ouvrage,volume";
            case "document" -> "fichier,texte";
            default -> "";
        };
    }

    private static Map<String, DictionaryWord> createOfflineDictionary() {
        Map<String, DictionaryWord> map = new LinkedHashMap<>();
        add(map, "maintenant", "Adverbe", "Au moment présent, à l’instant actuel.", "actuellement,désormais");
        add(map, "dans", "Préposition", "Indique l’intérieur, le lieu, le temps ou la situation où se trouve quelque chose.", "dedans,parmi");
        add(map, "bouche", "Nom féminin", "Ouverture du visage qui sert à parler, respirer et manger.", "gueule,orifice");
        add(map, "crachant", "Participe présent", "Action de projeter hors de la bouche de la salive, un liquide ou une matière.", "rejetant,expulsant");
        add(map, "cracher", "Verbe", "Projeter hors de la bouche de la salive, un liquide ou une matière.", "rejeter,expulser");
        add(map, "riant", "Participe présent", "Action d’exprimer la gaieté par le rire.", "souriant,s’amusant");
        add(map, "rire", "Verbe", "Exprimer la joie, l’amusement ou parfois la moquerie par des sons et une expression du visage.", "sourire,s’amuser");
        add(map, "livre", "Nom masculin", "Ensemble de pages imprimées ou numériques contenant un texte à lire.", "ouvrage,volume");
        add(map, "document", "Nom masculin", "Support écrit, numérique ou imprimé contenant une information.", "fichier,texte");
        add(map, "texte", "Nom masculin", "Suite de mots formant un écrit ou un passage destiné à être lu.", "écrit,contenu");
        add(map, "page", "Nom féminin", "Face d’une feuille ou partie affichée d’un document.", "feuille,écran");
        add(map, "mot", "Nom masculin", "Élément de la langue composé de sons ou de lettres et porteur d’un sens.", "terme,vocable");
        add(map, "auteur", "Nom", "Personne qui a écrit ou créé une œuvre.", "écrivain,créateur");
        add(map, "chercher", "Verbe", "Essayer de trouver quelque chose ou quelqu’un.", "rechercher,trouver");
        add(map, "trouver", "Verbe", "Découvrir ou rencontrer ce que l’on cherchait.", "découvrir,repérer");
        return map;
    }

    private static void add(Map<String, DictionaryWord> map, String word, String type, String definition, String synonyms) {
        DictionaryWord d = new DictionaryWord();
        d.setWord(word); d.setLanguage("fr"); d.setShortDefinition(type); d.setDefinition(definition); d.setSynonyms(synonyms); map.put(word, d);
    }
}
