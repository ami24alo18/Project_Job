package com.amit.jobagent.application;

import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes verified facts that are unsafe or unnecessary to disclose to a content generator. */
@Component
class VerifiedFactInputPolicy {
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?iu)(ignore|disregard|override|forget|bypass).{0,60}"
                    + "(previous|prior|system|developer|instructions?|prompt|rules?)"
                    + "|(?:reveal|print|show|expose).{0,40}(system|developer).{0,30}(prompt|instructions?|message)"
                    + "|(?:system|developer)\\s*(?:message|prompt|instructions?)\\s*[:=]"
                    + "|\\b(?:jailbreak|prompt injection|exfiltrat(?:e|ion))\\b");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?iu)\\b(?:race|racial identity|ethnic(?:ity)?|religion|religious belief|gender identity|"
                    + "sexual orientation|disabilit(?:y|ies)|veteran status|criminal (?:history|record)|caste|"
                    + "marital status|national origin|nationality|date of birth|birth date|d\\.?o\\.?b\\.?|"
                    + "genetic (?:data|information|history)|pregnan(?:t|cy)|medical (?:condition|history|information)|"
                    + "health information|salary|compensation|work authori[sz]ation|sponsorship|notice period|"
                    + "relocation preference)\\b");
    private static final Pattern LEGAL_OR_CONSENT = Pattern.compile(
            "(?iu)\\b(?:consent|attest|attestation|swear|under penalty|legally binding|"
                    + "electronic signature|e-signature|sign this|declare(?:d|s|ation)?|"
                    + "acknowledge(?:ment)?|agree to (?:the )?(?:terms|conditions|policy)|"
                    + "accept (?:the )?(?:terms|conditions|policy)|"
                    + "certif(?:y|ies|ied)\\s+(?:that\\s+)?(?:this|the|my|all|these)?\\s*"
                    + "(?:application|information|answers?|statements?))\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]+@"
                    + "[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}(?![\\p{L}\\p{N}.-])");
    private static final Pattern URL = Pattern.compile("(?iu)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern LABELED_CONTACT = Pattern.compile(
            "(?iu)\\b(?:phone|mobile|telephone|whatsapp|contact number|e-?mail|linkedin|github|portfolio)\\b"
                    + ".{0,40}(?:[+()\\d]|@|https?://|www\\.)");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])\\+\\d(?:[ .()\\-]*\\d){6,14}(?!\\d)");

    List<VerifiedFactSnapshot> safeForGeneration(
            PublishedProfileSnapshot profile, List<VerifiedFactSnapshot> verifiedFacts) {
        Objects.requireNonNull(profile, "profile is required");
        if (verifiedFacts == null || verifiedFacts.isEmpty()) {
            return List.of();
        }
        List<String> contactValues = contactValues(profile);
        return verifiedFacts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> safe(fact, contactValues))
                .toList();
    }

    private static boolean safe(VerifiedFactSnapshot fact, List<String> contactValues) {
        String material = String.join(" ",
                Objects.toString(fact.statement(), ""),
                Objects.toString(fact.company(), ""),
                Objects.toString(fact.startDate(), ""),
                Objects.toString(fact.endDate(), ""),
                String.join(" ", fact.skillTags() == null ? java.util.Set.of() : fact.skillTags()),
                String.join(" ", fact.domainTags() == null ? java.util.Set.of() : fact.domainTags()));
        if (PROMPT_INJECTION.matcher(material).find()
                || SENSITIVE.matcher(material).find()
                || LEGAL_OR_CONSENT.matcher(material).find()
                || EMAIL.matcher(material).find()
                || URL.matcher(material).find()
                || LABELED_CONTACT.matcher(material).find()
                || INTERNATIONAL_PHONE.matcher(material).find()) {
            return false;
        }
        return contactValues.stream().noneMatch(value -> containsContactValue(material, value));
    }

    private static List<String> contactValues(PublishedProfileSnapshot profile) {
        var core = profile.snapshot().path("profile");
        var values = new ArrayList<String>();
        for (String field : List.of(
                "fullName", "email", "phone", "currentLocation", "linkedinUrl", "githubUrl", "leetcodeUrl", "portfolioUrl")) {
            var value = core.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                values.add(value.asText().trim());
            }
        }
        return List.copyOf(values);
    }

    private static boolean containsContactValue(String material, String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            String materialDigits = material.replaceAll("\\D", "");
            if (materialDigits.contains(digits)) {
                return true;
            }
        }
        String normalizedValue = normalize(value);
        if (normalizedValue.length() < 4) {
            return false;
        }
        return Pattern.compile(
                        "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedValue) + "(?![\\p{L}\\p{N}])")
                .matcher(normalize(material))
                .find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
