package com.rsspro.core;

import com.rsspro.data.Article;
import com.rsspro.data.Feed;
import com.rsspro.data.FilterRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FilterSimulator {
    public static final class Sample {
        public final Article article;

        Sample(Article article) {
            this.article = article;
        }

        public String editableText(FilterRule rule) {
            return fieldText(article, rule == null ? FilterRule.Field.ANY : rule.field);
        }
    }

    public static final class Outcome {
        public final boolean inScope;
        public final boolean ruleMatches;
        public final boolean visible;
        public final boolean notify;
        public final List<FilterRule> matches;

        Outcome(boolean inScope, boolean ruleMatches, boolean visible, boolean notify,
                List<FilterRule> matches) {
            this.inScope = inScope;
            this.ruleMatches = ruleMatches;
            this.visible = visible;
            this.notify = notify;
            this.matches = matches;
        }
    }

    private static final class Scenario {
        final String title;
        final String summary;
        final String body;
        final String category;
        final String author;
        final String path;

        Scenario(String title, String summary, String body, String category,
                 String author, String path) {
            this.title = title;
            this.summary = summary;
            this.body = body;
            this.category = category;
            this.author = author;
            this.path = path;
        }
    }

    private static final Scenario[] SCENARIOS = {
            new Scenario("Oferta flash en auriculares inalámbricos",
                    "Descuento del 35 % durante las próximas horas.",
                    "Los auriculares incluyen cancelación de ruido y estuche de carga. "
                            + "El precio rebajado estará disponible hasta agotar existencias.",
                    "Ofertas", "Lucía Gómez", "ofertas/auriculares"),
            new Scenario("Nueva actualización de Android mejora la privacidad",
                    "La actualización incorpora controles adicionales por aplicación.",
                    "El sistema permite revisar los permisos concedidos y revocar el acceso "
                            + "a la ubicación o al micrófono desde un único panel.",
                    "Tecnología", "Daniel Ruiz", "tecnologia/android-privacidad"),
            new Scenario("Estudio sobre el consumo de energía en ciudades",
                    "La investigación compara datos de varios países durante diez años.",
                    "Los resultados muestran cambios en el consumo residencial y proponen "
                            + "medidas para reducir el gasto energético en edificios antiguos.",
                    "Ciencia", "Marta Sánchez", "ciencia/energia-ciudades"),
            new Scenario("El equipo local remonta en el último cuarto",
                    "Un parcial de 14-2 decidió el partido en los minutos finales.",
                    "El encuentro se mantuvo igualado hasta el último cuarto, cuando el equipo "
                            + "local encontró mejores tiros y aseguró la victoria.",
                    "Deportes", "Javier Moreno", "deportes/remontada"),
            new Scenario("Estreno de una serie documental sobre el océano",
                    "La producción recorre ecosistemas de aguas profundas.",
                    "La serie combina imágenes submarinas con entrevistas a investigadores y "
                            + "dedica un episodio a las corrientes que regulan el clima.",
                    "Cultura", "Elena Vidal", "cultura/serie-oceano"),
            new Scenario("Guía práctica para viajar con poco equipaje",
                    "Consejos para preparar una maleta ligera y útil.",
                    "La guía recomienda elegir ropa por capas, revisar el clima del destino y "
                            + "reservar espacio para los objetos imprescindibles.",
                    "Viajes", "Pablo Navarro", "viajes/equipaje-ligero"),
            new Scenario("Actualización de seguridad para servidores web",
                    "El fabricante corrige varias vulnerabilidades críticas.",
                    "La actualización sustituye componentes afectados y recomienda revisar las "
                            + "configuraciones expuestas a internet después de instalarla.",
                    "Seguridad", "Sara Molina", "seguridad/servidores"),
            new Scenario("Nuevo modelo de inteligencia artificial para programación",
                    "El modelo promete mejorar la revisión automática de código.",
                    "La herramienta analiza cambios, señala posibles errores y propone pruebas "
                            + "adicionales para los proyectos que la integran.",
                    "Tecnología", "Iván Torres", "tecnologia/asistente-codigo"),
            new Scenario("Mercado de agricultura urbana crece en las grandes ciudades",
                    "Cada vez más proyectos cultivan alimentos en azoteas y solares.",
                    "Los huertos urbanos reducen el transporte de alimentos y crean espacios "
                            + "comunitarios, aunque enfrentan dificultades para conseguir suelo.",
                    "Sociedad", "Carmen Flores", "sociedad/agricultura-urbana"),
            new Scenario("Concierto solidario reúne a varias bandas locales",
                    "La recaudación se destinará a un banco de alimentos.",
                    "El cartel incluye grupos de estilos distintos y las entradas se pueden "
                            + "comprar en línea o en la taquilla del recinto.",
                    "Música", "Alberto Gil", "musica/concierto-solidario")
    };

    private FilterSimulator() {
    }

    public static Sample generate(FilterRule rule, List<Feed> feeds, Random random) {
        Random source = random == null ? new Random() : random;
        Scenario scenario = SCENARIOS[source.nextInt(SCENARIOS.length)];
        Article article = new Article();
        article.id = "simulacion-" + source.nextInt(1000000);
        article.title = scenario.title;
        article.summary = scenario.summary;
        article.content = scenario.body;
        article.category = scenario.category;
        article.author = scenario.author;
        article.link = "https://ejemplo.test/" + scenario.path;
        article.publishedAt = System.currentTimeMillis();

        Feed feed = chooseFeed(rule, feeds, source);
        article.feedId = feed.url;
        article.feedTitle = feed.displayTitle();

        FilterRule fieldRule = rule == null ? new FilterRule() : rule;
        if (fieldRule.matchType != FilterRule.MatchType.REGEX
                && fieldRule.pattern != null && fieldRule.pattern.trim().length() > 0) {
            injectPattern(article, fieldRule);
        }
        return new Sample(article);
    }

    public static Sample withText(Article base, FilterRule rule, String text) {
        Article article = copy(base);
        FilterRule fieldRule = rule == null ? new FilterRule() : rule;
        String value = text == null ? "" : text;
        switch (fieldRule.field == null ? FilterRule.Field.ANY : fieldRule.field) {
            case TITLE:
                article.title = value;
                break;
            case SUMMARY:
                article.summary = value;
                break;
            case CONTENT:
            case ANY:
                article.content = value;
                break;
            case LINK:
                article.link = value;
                break;
            case AUTHOR:
                article.author = value;
                break;
            case FEED:
                article.feedTitle = value;
                break;
            default:
                article.content = value;
                break;
        }
        return new Sample(article);
    }

    public static Outcome evaluate(Article article, List<FilterRule> savedRules,
                                   FilterRule draft) {
        List<FilterRule> effective = effectiveRules(savedRules, draft);
        boolean inScope = draft == null || draft.appliesToFeed(article.feedId);
        boolean matches = inScope && FilterEngine.ruleMatches(article, draft);

        List<FilterRule> matched = new ArrayList<FilterRule>();
        if (article != null) {
            for (FilterRule rule : effective) {
                if (rule != null && rule.enabled && FilterEngine.ruleMatches(article, rule)) {
                    matched.add(rule);
                }
            }
        }
        FilterEngine.Result result = new FilterEngine(effective).evaluate(article);
        return new Outcome(inScope, matches, result.visible, result.notify, matched);
    }

    public static List<FilterRule> effectiveRules(List<FilterRule> savedRules, FilterRule draft) {
        List<FilterRule> effective = new ArrayList<FilterRule>();
        boolean replaced = false;
        if (savedRules != null) {
            for (FilterRule rule : savedRules) {
                if (rule == null) {
                    continue;
                }
                if (draft != null && draft.id != null && draft.id.equals(rule.id)) {
                    effective.add(draft);
                    replaced = true;
                } else {
                    effective.add(rule);
                }
            }
        }
        if (draft != null && !replaced) {
            effective.add(draft);
        }
        return effective;
    }

    private static Feed chooseFeed(FilterRule rule, List<Feed> feeds, Random random) {
        if (rule != null && rule.feedId != null && rule.feedId.length() > 0) {
            if (feeds != null) {
                for (Feed feed : feeds) {
                    if (rule.feedId.equals(feed.url)) {
                        return feed;
                    }
                }
            }
            return new Feed(rule.feedId, "Fuente de prueba");
        }
        if (feeds != null && !feeds.isEmpty()) {
            Feed selected = feeds.get(random.nextInt(feeds.size()));
            if (selected.url != null) {
                return selected;
            }
        }
        return new Feed("https://ejemplo.test/feed", "Fuente de prueba");
    }

    private static void injectPattern(Article article, FilterRule rule) {
        String pattern = rule.pattern;
        String decorated = " " + pattern + " ";
        FilterRule.Field field = rule.field == null ? FilterRule.Field.ANY : rule.field;
        switch (field) {
            case TITLE:
                article.title = article.title + " · " + pattern;
                break;
            case SUMMARY:
                article.summary = article.summary + " " + pattern;
                break;
            case CONTENT:
                article.content = article.content + "\n\n" + decorated;
                break;
            case LINK:
                article.link = "https://ejemplo.test/prueba?texto=" + pattern;
                break;
            case AUTHOR:
                article.author = article.author + " " + pattern;
                break;
            case FEED:
                article.feedTitle = article.feedTitle + " " + pattern;
                break;
            case ANY:
            default:
                article.content = article.content + "\n\n" + decorated;
                break;
        }
    }

    private static String fieldText(Article article, FilterRule.Field field) {
        switch (field == null ? FilterRule.Field.ANY : field) {
            case TITLE:
                return safe(article.title);
            case SUMMARY:
                return safe(article.summary);
            case CONTENT:
            case ANY:
                return safe(article.content);
            case LINK:
                return safe(article.link);
            case AUTHOR:
                return safe(article.author);
            case FEED:
                return safe(article.feedTitle);
            default:
                return safe(article.content);
        }
    }

    private static Article copy(Article base) {
        Article article = new Article();
        if (base == null) {
            return article;
        }
        article.id = base.id;
        article.feedId = base.feedId;
        article.feedTitle = base.feedTitle;
        article.title = base.title;
        article.link = base.link;
        article.imageUrl = base.imageUrl;
        article.summary = base.summary;
        article.content = base.content;
        article.fullContent = base.fullContent;
        article.author = base.author;
        article.category = base.category;
        article.publishedAt = base.publishedAt;
        article.fullFetchAt = base.fullFetchAt;
        return article;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
