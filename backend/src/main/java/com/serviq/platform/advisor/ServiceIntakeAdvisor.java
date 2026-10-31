package com.serviq.platform.advisor;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic, explainable baseline. Does not use or claim to use an LLM. */
@Service
public class ServiceIntakeAdvisor {
    public record Suggestion(String category, String priority, String issueSummary,
                             String issueDescription, List<String> followUpQuestions,
                             List<String> signals, boolean needsReview, String engine) {}

    private static final List<MatchRule> CATEGORIES = List.of(
        new MatchRule("REFRIGERATOR", "refrigerator|fridge|freezer|chiller|cooling|defrost|compressor"),
        new MatchRule("AIR_CONDITIONER", "air conditioner|aircon|air con|a/c|ac unit|split type|air conditioning"),
        new MatchRule("WASHING_MACHINE", "washing machine|washer|laundry|spin cycle|drum"),
        new MatchRule("DISHWASHER", "dishwasher|dish washer"),
        new MatchRule("OVEN", "oven|stove|cooktop|burner|range hood"),
        new MatchRule("ELECTRICAL", "circuit breaker|wiring|electrician|power socket|outlet|electrical panel")
    );

    private record MatchRule(String category, Pattern pattern) {
        MatchRule(String category, String alternatives) {
            this(category, Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])"));
        }
    }

    public Suggestion analyze(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Description is required");
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < 12 || cleaned.length() > 4000)
            throw new IllegalArgumentException("Description must contain 12–4000 characters");
        String lower = cleaned.toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();
        String category = "UNSPECIFIED";
        for (MatchRule rule : CATEGORIES) {
            if (rule.pattern().matcher(lower).find()) {
                category = rule.category();
                signals.add("Category clue: " + rule.category());
                break;
            }
        }
        // Safety-oriented urgency detection: no automatic dispatch or booking.
        boolean highRisk = containsAny(lower, "sparks", "sparking", "smoke", "burning smell", "electrical fire", "gas leak", "carbon monoxide", "shocked", "electrocution");
        boolean urgent = highRisk || containsAny(lower, "leaking", "water leak", "flooding", "no power", "stopped working", "not working", "completely broken");
        String priority = highRisk ? "URGENT" : urgent ? "HIGH" : "NORMAL";
        if (highRisk) signals.add("Potential safety hazard mentioned — do not automatically dispatch");
        else if (urgent) signals.add("Possible service urgency mentioned");
        if (category.equals("UNSPECIFIED")) signals.add("Equipment category could not be determined");
        String summary = cleaned.length() <= 200 ? cleaned : cleaned.substring(0, 197).trim() + "...";
        List<String> questions = new ArrayList<>();
        if (category.equals("UNSPECIFIED")) questions.add("What equipment needs servicing (type and model)?");
        questions.add("When did the problem first occur?");
        questions.add("Is the equipment currently safe to use?");
        if (!lower.contains("model")) questions.add("What is the equipment make and model?");
        return new Suggestion(category, priority, summary, cleaned,
            List.copyOf(questions), List.copyOf(signals), true, "RULES_V1");
    }

    private static boolean containsAny(String text, String... clues) {
        for (String clue : clues) if (text.contains(clue)) return true;
        return false;
    }
}
