package pl.edu.icm.yadda.analysis.bibref.parsing.tools;

import pl.edu.icm.yadda.analysis.bibref.parsing.model.CitationTokenLabel;
import pl.edu.icm.yadda.analysis.bibref.parsing.model.CitationToken;
import pl.edu.icm.yadda.analysis.bibref.parsing.model.Citation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pl.edu.icm.yadda.analysis.bibref.BibEntry;

/**
 * Citation utility class.
 *
 * @author Dominika Tkaczyk (dtkaczyk@icm.edu.pl)
 */
public class CitationUtils {

    private static final Map<CitationTokenLabel, String> TO_BIBENTRY =
            new EnumMap<CitationTokenLabel, String>(CitationTokenLabel.class);

    static {
        TO_BIBENTRY.put(CitationTokenLabel.ARTICLE_TITLE,   BibEntry.FIELD_TITLE);
        TO_BIBENTRY.put(CitationTokenLabel.CONTENT,         BibEntry.FIELD_CONTENTS);
        TO_BIBENTRY.put(CitationTokenLabel.EDITION,         BibEntry.FIELD_EDITION);
        TO_BIBENTRY.put(CitationTokenLabel.PUBLISHER_NAME,  BibEntry.FIELD_PUBLISHER);
        TO_BIBENTRY.put(CitationTokenLabel.PUBLISHER_LOC,   BibEntry.FIELD_LOCATION);
        TO_BIBENTRY.put(CitationTokenLabel.SERIES,          BibEntry.FIELD_SERIES);
        TO_BIBENTRY.put(CitationTokenLabel.SOURCE,          BibEntry.FIELD_JOURNAL);
        TO_BIBENTRY.put(CitationTokenLabel.URI,             BibEntry.FIELD_URL);
        TO_BIBENTRY.put(CitationTokenLabel.VOLUME,          BibEntry.FIELD_VOLUME);
        TO_BIBENTRY.put(CitationTokenLabel.YEAR,            BibEntry.FIELD_YEAR);
        TO_BIBENTRY.put(CitationTokenLabel.ISSUE,           BibEntry.FIELD_NUMBER);
        /*
        TO_BIBENTRY.put(CitationTokenLabel.CONF,            BibEntry.FIELD_ABSTRACT);
        TO_BIBENTRY.put(CitationTokenLabel.SC,              BibEntry.FIELD_ABSTRACT);
        TO_BIBENTRY.put(CitationTokenLabel.VOLUME_SERIES,   BibEntry.FIELD_ABSTRACT);
         */
    }

    public static List<CitationToken> tokenizeCitation(String citation) {
        List<CitationToken> tokenList = new ArrayList<CitationToken>();

        String text = citation;
        int actIndex = 0;
        while (text.length() > 0) {
            int end = 1;
            if (Character.isLetterOrDigit(text.charAt(0))) {
                end = 0;
                while (text.length() > end && Character.isLetterOrDigit(text.charAt(end))) {
                    end++;
                }
            }
            String token = text.substring(0, end);
            if (!token.matches("\\s+")) {
                tokenList.add(new CitationToken(token, actIndex, actIndex + end));
            }
            text = text.substring(end);
            actIndex += end;
        }

        return tokenList;
    }

    public static void addHMMLabels(Citation citation) {
        List<CitationToken> tokens = citation.getTokens();

        for (int i = 0; i < tokens.size(); i++) {
            CitationTokenLabel label = tokens.get(i).getLabel();
            CitationTokenLabel prevLabel = null;
            if (i > 0) {
                prevLabel = tokens.get(i - 1).getLabel();
            }

            if (prevLabel != null && prevLabel.equals(CitationTokenLabel.TEXT)
                    && !label.equals(CitationTokenLabel.TEXT)) {
                CitationTokenLabel textBeforeLabel = CitationTokenLabel.getTextBeforeLabel(label);
                if (textBeforeLabel != null) {
                    int j = i - 1;
                    while (j >= 0 && j >= i - 2 && tokens.get(j).getLabel().equals(CitationTokenLabel.TEXT)) {
                        tokens.get(j).setLabel(textBeforeLabel);
                        j--;
                    }
                }
            }
        }

        for (int i = tokens.size() - 1; i >= 0; i--) {
            CitationTokenLabel label = tokens.get(i).getLabel();
            CitationTokenLabel prevLabel = null;
            if (i > 0) {
                prevLabel = tokens.get(i - 1).getLabel();
            }

            if (prevLabel == null || !label.equals(prevLabel)) {
                CitationTokenLabel firstLabel = CitationTokenLabel.getFirstLabel(label);
                if (firstLabel != null) {
                    tokens.get(i).setLabel(firstLabel);
                }
            }
        }
    }

    public static void removeHMMLabels(Citation citation) {
        for (CitationToken token : citation.getTokens()) {
            CitationTokenLabel normalizedLabel = CitationTokenLabel.getNormalizedLabel(token.getLabel());
            if (normalizedLabel != null) {
                token.setLabel(normalizedLabel);
            }
        }
    }

    public static BibEntry convertToBibref(Citation citation) {
        List<CitationToken> tokens = new ArrayList<CitationToken>();
        CitationToken token = null;
        for (CitationToken actToken : citation.getTokens()) {
            CitationTokenLabel actLabel = actToken.getLabel();

            if (TO_BIBENTRY.containsKey(actLabel) || actLabel.equals(CitationTokenLabel.PAGEF)
                    || actLabel.equals(CitationTokenLabel.PAGEL) || actLabel.equals(CitationTokenLabel.GIVENNAME)
                    || actLabel.equals(CitationTokenLabel.SURNAME)) {
                if (token != null && actLabel.equals(token.getLabel())) {
                    token.setEndIndex(actToken.getEndIndex());
                    token.setText(citation.getText().substring(token.getStartIndex(), token.getEndIndex()));
                } else {
                    if (token != null) {
                        tokens.add(token);
                        token = null;
                    }
                    token = new CitationToken(actToken.getText(), actToken.getStartIndex(), actToken.getEndIndex(),
                            actToken.getLabel());
                }
            }
        }

        if (token != null) {
            tokens.add(token);
        }

        String text = citation.getText();
        BibEntry bibEntry = new BibEntry(BibEntry.TYPE_ARTICLE);
        bibEntry.setText(text);

        int i = 0;
        while (i < tokens.size()) {
            CitationToken actToken = tokens.get(i);
            CitationTokenLabel actLabel = actToken.getLabel();

            CitationToken nextToken = null;
            if (i < tokens.size() - 1) {
                nextToken = tokens.get(i + 1);
            }

            if (TO_BIBENTRY.containsKey(actLabel)) {
                String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                bibEntry.addField(TO_BIBENTRY.get(actLabel), value, actToken.getStartIndex(), actToken.getEndIndex());
                i++;
            } else if (actLabel.equals(CitationTokenLabel.PAGEF)) {
                if (nextToken != null && nextToken.getLabel().equals(CitationTokenLabel.PAGEL)) {
                    String pagef = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                    String pagel = text.substring(nextToken.getStartIndex(), nextToken.getEndIndex());
                    String value = pagef + "--" + pagel;

                    bibEntry.addField(BibEntry.FIELD_PAGES, value, actToken.getStartIndex(), nextToken.getEndIndex());
                    i += 2;
                } else {
                    String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                    bibEntry.addField(BibEntry.FIELD_PAGES, value, actToken.getStartIndex(), actToken.getEndIndex());
                    i++;
                }
            } else if (actLabel.equals(CitationTokenLabel.PAGEL)) {
                String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                bibEntry.addField(BibEntry.FIELD_PAGES, value, actToken.getStartIndex(), actToken.getEndIndex());
                i++;
            } else if (actLabel.equals(CitationTokenLabel.GIVENNAME)) {
                if (nextToken != null && nextToken.getLabel().equals(CitationTokenLabel.SURNAME)) {
                    String value = text.substring(nextToken.getStartIndex(), nextToken.getEndIndex()) + ", "
                            + text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                    bibEntry.addField(BibEntry.FIELD_AUTHOR, value, actToken.getStartIndex(), nextToken.getEndIndex());
                    i += 2;
                } else {
                    String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                    bibEntry.addField(BibEntry.FIELD_AUTHOR, value, actToken.getStartIndex(), actToken.getEndIndex());
                    i++;
                }
            } else if (actLabel.equals(CitationTokenLabel.SURNAME)) {
                if (nextToken != null && nextToken.getLabel().equals(CitationTokenLabel.GIVENNAME)) {
                    String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex()) + ", "
                            + text.substring(nextToken.getStartIndex(), nextToken.getEndIndex());
                    bibEntry.addField(BibEntry.FIELD_AUTHOR, value, actToken.getStartIndex(), nextToken.getEndIndex());
                    i += 2;
                } else {
                    String value = text.substring(actToken.getStartIndex(), actToken.getEndIndex());
                    bibEntry.addField(BibEntry.FIELD_AUTHOR, value, actToken.getStartIndex(), actToken.getEndIndex());
                    i++;
                }
            }
        }

        return bibEntry;
    }
}