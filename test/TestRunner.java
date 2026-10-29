import com.rsspro.core.ArticleSearch;
import com.rsspro.core.FilterEngine;
import com.rsspro.core.FilterSimulator;
import com.rsspro.core.RetentionPolicy;
import com.rsspro.data.Article;
import com.rsspro.data.FilterRule;
import com.rsspro.data.ParsedFeed;
import com.rsspro.net.ArticlePageLoader;
import com.rsspro.net.FeedFetcher;
import com.rsspro.net.FeedParser;
import com.rsspro.util.DateUtil;
import com.rsspro.util.HtmlUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TestRunner {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testRss();
        testAtom();
        testRdf();
        testImages();
        testContentAndCategories();
        testArticleSearch();
        testArticlePageLoader();
        testMalformed();
        testHtml();
        testDates();
        testUrls();
        testRetention();
        testFilters();
        testWordAndRegex();
        testFilterSimulator();
        testMsrcRegression();
        System.out.println();
        System.out.println(passed + " pruebas correctas, " + failed + " fallidas");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRss() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rss version=\"2.0\" "
                + "xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "<channel><title>Mi &amp; Feed</title>\n"
                + "<link>https://example.com/blog/</link>\n"
                + "<item>\n"
                + "<title><![CDATA[Título <b>uno</b>]]></title>\n"
                + "<link>/post/1</link>\n"
                + "<description><![CDATA[<p>Hola &amp; adiós</p>]]></description>\n"
                + "<content:encoded><![CDATA[<p>Contenido <strong>completo</strong></p>"
                + "<p>Segunda línea</p>]]></content:encoded>\n"
                + "<pubDate>Wed, 02 Oct 2024 13:00:00 GMT</pubDate>\n"
                + "<guid>post-1</guid>\n"
                + "<dc:creator>Ana</dc:creator>\n"
                + "</item></channel></rss>";
        ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                "https://example.com/feed.xml");
        check("RSS título del canal", "Mi & Feed", parsed.title);
        check("RSS URL del sitio", "https://example.com/blog/", parsed.siteUrl);
        check("RSS número de artículos", 1, parsed.articles.size());
        Article article = parsed.articles.get(0);
        check("RSS título limpio", "Título uno", article.title);
        check("RSS enlace relativo", "https://example.com/post/1", article.link);
        check("RSS contenido preferente", "Contenido completo\nSegunda línea", article.content);
        check("RSS autor Dublin Core", "Ana", article.author);
        check("RSS fecha", true, article.publishedAt > 0L);
        check("RSS id determinista", article.id,
                FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                        "https://example.com/feed.xml").articles.get(0).id);
        check("RSS id de 40 caracteres", 40, article.id.length());

        byte[] withBom = new byte[xml.getBytes(StandardCharsets.UTF_8).length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(xml.getBytes(StandardCharsets.UTF_8), 0, withBom, 3,
                withBom.length - 3);
        check("RSS con BOM", 1, FeedParser.parse(withBom,
                "https://example.com/feed.xml").articles.size());
    }

    private static void testAtom() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
                + "<title>Atom Feed</title>\n"
                + "<link href=\"https://example.org/\" rel=\"alternate\"/>\n"
                + "<entry>\n"
                + "<title>Entrada</title>\n"
                + "<link href=\"/a/1\" rel=\"alternate\" type=\"text/html\"/>\n"
                + "<updated>2025-01-02T03:04:05Z</updated>\n"
                + "<id>tag:example.org,2025:1</id>\n"
                + "<author><name>Luis</name></author>\n"
                + "<content type=\"html\">&lt;p&gt;Cuerpo&lt;/p&gt;</content>\n"
                + "</entry></feed>";
        ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                "https://example.org/atom.xml");
        check("Atom título del canal", "Atom Feed", parsed.title);
        check("Atom URL del sitio", "https://example.org/", parsed.siteUrl);
        Article article = parsed.articles.get(0);
        check("Atom título", "Entrada", article.title);
        check("Atom enlace", "https://example.org/a/1", article.link);
        check("Atom autor", "Luis", article.author);
        check("Atom contenido", "Cuerpo", article.content);
        check("Atom fecha", true, article.publishedAt > 0L);
    }

    private static void testRdf() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" "
                + "xmlns=\"http://purl.org/rss/1.0/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "<channel rdf:about=\"https://rdf.example/\">\n"
                + "<title>RDF Feed</title><link>https://rdf.example/</link>\n"
                + "</channel>\n"
                + "<item rdf:about=\"https://rdf.example/1\">\n"
                + "<title>Item RDF</title><link>https://rdf.example/1</link>\n"
                + "<description>Texto</description>\n"
                + "<dc:date>2024-05-06T07:08:09Z</dc:date>\n"
                + "</item></rdf:RDF>";
        ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                "https://rdf.example/feed.rdf");
        check("RDF título del canal", "RDF Feed", parsed.title);
        check("RDF número de artículos", 1, parsed.articles.size());
        check("RDF título", "Item RDF", parsed.articles.get(0).title);
        check("RDF fecha", true, parsed.articles.get(0).publishedAt > 0L);
    }

    private static void testImages() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<rss version=\"2.0\" "
                + "xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" "
                + "xmlns:media=\"http://search.yahoo.com/mrss/\">\n"
                + "<channel><title>Con imágenes</title>\n"
                + "<link>https://example.com/</link>\n"
                + "<image><url>/logo.png</url></image>\n"
                + "<item><title>Miniatura</title>\n"
                + "<link>https://example.com/post/1</link><guid>1</guid>\n"
                + "<description><![CDATA[<p>Texto</p><img src=\"/contenido.jpg\">]]></description>\n"
                + "<media:thumbnail url=\"/thumb.jpg\"/>\n"
                + "</item>\n"
                + "<item><title>Enclosure</title>\n"
                + "<link>https://example.com/post/2</link><guid>2</guid>\n"
                + "<enclosure url=\"/foto.png\" type=\"image/png\"/>\n"
                + "</item>\n"
                + "<item><title>Relativa</title>\n"
                + "<link>https://example.com/blog/post-3</link><guid>3</guid>\n"
                + "<content:encoded><![CDATA[<p>Texto</p><img src=\"../img/foto.jpg\">]]>"
                + "</content:encoded>\n"
                + "</item>\n"
                + "<item><title>Data URI</title>\n"
                + "<link>https://example.com/post/4</link><guid>4</guid>\n"
                + "<description><![CDATA[<img src=\"data:image/png;base64,AAAA\">]]>"
                + "</description>\n"
                + "</item>\n"
                + "</channel></rss>";
        ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                "https://example.com/feed.xml");
        check("Imagen del canal", "https://example.com/logo.png", parsed.imageUrl);
        check("Imagen media thumbnail", "https://example.com/thumb.jpg",
                findArticle(parsed, "Miniatura").imageUrl);
        check("Imagen enclosure", "https://example.com/foto.png",
                findArticle(parsed, "Enclosure").imageUrl);
        check("Imagen relativa del contenido", "https://example.com/img/foto.jpg",
                findArticle(parsed, "Relativa").imageUrl);
        check("Data URI ignorada", "", findArticle(parsed, "Data URI").imageUrl);

        String atom = "<?xml version=\"1.0\"?>\n"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
                + "<title>Atom imágenes</title>\n"
                + "<logo>https://example.org/logo.png</logo>\n"
                + "<entry><title>Foto</title>\n"
                + "<link href=\"https://example.org/a\" rel=\"alternate\"/>\n"
                + "<link href=\"/foto.jpg\" rel=\"enclosure\" type=\"image/jpeg\"/>\n"
                + "<id>foto</id><updated>2025-01-02T03:04:05Z</updated>\n"
                + "</entry></feed>";
        ParsedFeed atomParsed = FeedParser.parse(atom.getBytes(StandardCharsets.UTF_8),
                "https://example.org/feed.xml");
        check("Logo Atom", "https://example.org/logo.png", atomParsed.imageUrl);
        check("Imagen enclosure Atom", "https://example.org/foto.jpg",
                atomParsed.articles.get(0).imageUrl);
    }


    private static void testContentAndCategories() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<rss version=\"2.0\" "
                + "xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" "
                + "xmlns:media=\"http://search.yahoo.com/mrss/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "<channel><title>Ofertas</title><link>https://example.com/</link>\n"
                + "<item><title>Con media</title>\n"
                + "<link>https://example.com/1</link><guid>1</guid>\n"
                + "<media:content medium=\"image\" url=\"https://example.com/foto.jpg\"/>\n"
                + "<description><![CDATA[<p>Texto breve de la oferta</p>]]></description>\n"
                + "<category>Ofertas</category>\n"
                + "<category>Hogar</category>\n"
                + "<dc:subject>Descuentos</dc:subject>\n"
                + "</item>\n"
                + "<item><title>Con contenido</title>\n"
                + "<link>https://example.com/2</link><guid>2</guid>\n"
                + "<media:content medium=\"image\" url=\"https://example.com/otra.jpg\"/>\n"
                + "<description>Resumen corto</description>\n"
                + "<content:encoded><![CDATA[<p>Cuerpo ampliado</p>]]></content:encoded>\n"
                + "</item>\n"
                + "</channel></rss>";
        ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                "https://example.com/feed.xml");
        Article first = findArticle(parsed, "Con media");
        check("media:content no reemplaza description", "Texto breve de la oferta",
                first.content);
        check("categorías RSS y DC", "Ofertas · Hogar · Descuentos", first.category);
        check("contenido ampliado preferente", "Cuerpo ampliado",
                findArticle(parsed, "Con contenido").content);

        String atom = "<?xml version=\"1.0\"?>\n"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
                + "<title>Atom</title><link href=\"https://example.org/\"/>\n"
                + "<entry><title>Entrada</title>\n"
                + "<link href=\"https://example.org/1\" rel=\"alternate\"/>\n"
                + "<id>a1</id><updated>2025-01-02T03:04:05Z</updated>\n"
                + "<category term=\"Tecnología\"/>\n"
                + "<category label=\"Ciencia\"/>\n"
                + "</entry></feed>";
        Article atomArticle = FeedParser.parse(atom.getBytes(StandardCharsets.UTF_8),
                "https://example.org/feed.xml").articles.get(0);
        check("categorías Atom", "Tecnología · Ciencia", atomArticle.category);
    }

    private static void testArticleSearch() {
        Article article = article("Canción de verano", "Un resumen", "Mi fuente");
        article.category = "Tecnología";
        article.link = "https://example.com/nota-especial";
        article.author = "Jóse Ángel";
        article.fullContent = "Detalle ampliado del artículo";
        check("Búsqueda sin tildes", true,
                ArticleSearch.matches(article, "cancion"));
        check("Búsqueda en categoría", true,
                ArticleSearch.matches(article, "tecnologia"));
        check("Búsqueda en enlace", true,
                ArticleSearch.matches(article, "nota especial"));
        check("Búsqueda en autor", true,
                ArticleSearch.matches(article, "jose angel"));
        check("Búsqueda en contenido completo", true,
                ArticleSearch.matches(article, "detalle ampliado"));
        check("Búsqueda requiere todos los términos", false,
                ArticleSearch.matches(article, "cancion deporte"));
        check("Búsqueda vacía coincide", true, ArticleSearch.matches(article, "  "));
        check("Búsqueda normaliza tildes", "cancion", ArticleSearch.normalize("Canción"));
        check("Búsqueda normaliza mayúsculas", "texto",
                ArticleSearch.normalize("TEXTO"));
    }

    private static void testArticlePageLoader() {
        String html = "<html><body><nav>Menú inicio</nav>"
                + "<article><h1>Título</h1><p>Hola &amp; adiós.</p>"
                + "<script>alert('x')</script><p>Segundo párrafo.</p></article>"
                + "<footer>Pie</footer></body></html>";
        String articleText = ArticlePageLoader.extract(html);
        check("Página extrae artículo", "Título\nHola & adiós.\nSegundo párrafo.",
                articleText);
        check("Página elimina navegación", false, articleText.contains("Menú"));
        check("Página elimina pie", false, articleText.contains("Pie"));

        String state = "<html><body><div id=\"app\">Cargando</div>"
                + "<script>window.__INITIAL_STATE__={\"preparedHtmlDescription\":"
                + "\"<p>Detalle <b>completo</b> del producto.</p>\"};</script>"
                + "</body></html>";
        check("Página lee JSON embebido", "Detalle completo del producto.",
                ArticlePageLoader.extract(state));

        String jsonLd = "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"Article\",\"articleBody\":\"Cuerpo estructurado\"}"
                + "</script></head><body><div id=\"app\"></div></body></html>";
        check("Página lee JSON-LD", "Cuerpo estructurado",
                ArticlePageLoader.extract(jsonLd));

        String skeleton = "<html><body><main><nav>Promodescuentos Publicar</nav>"
                + "<h1>Oferta</h1></main><script>window.__STATE__={"
                + "\"descriptionPurified\":\"Texto breve de la oferta\"};"
                + "</script></body></html>";
        check("Página prefiere JSON a plantilla", "Texto breve de la oferta",
                ArticlePageLoader.extract(skeleton));

        String rich = "<html><body><article><p>"
                + "Contenido real suficientemente largo para conservarse en la extracción "
                + "principal del artículo y no ser sustituido por un resumen corto."
                + "</p></article><script>window.__STATE__={\"articleBody\":"
                + "\"Resumen corto\"};</script></body></html>";
        check("Página conserva artículo largo", true,
                ArticlePageLoader.extract(rich).contains("Contenido real"));

        String meta = "<html><head><meta name=\"description\" "
                + "content=\"Resumen desde meta &amp; más\"></head>"
                + "<body><div id=\"app\"></div></body></html>";
        check("Página lee meta description", "Resumen desde meta & más",
                ArticlePageLoader.extract(meta));
        check("Página vacía", "", ArticlePageLoader.extract(""));
    }

    private static Article findArticle(ParsedFeed parsed, String title) {
        for (Article article : parsed.articles) {
            if (title.equals(article.title)) {
                return article;
            }
        }
        throw new IllegalStateException("No se encontró el artículo: " + title);
    }

    private static void testRetention() {
        long now = 1_800_000_000_000L;
        long day = 24L * 60L * 60L * 1000L;
        Article old = article("Antiguo", "Resumen", "Fuente");
        old.id = "old";
        old.publishedAt = now - 40L * day;
        Article recent = article("Reciente", "Resumen", "Fuente");
        recent.id = "recent";
        recent.publishedAt = now - 2L * day;
        Article undated = article("Sin fecha", "Resumen", "Fuente");
        undated.id = "undated";
        undated.publishedAt = 0L;

        List<Article> original = list(old, recent, undated);
        check("Retención elimina antiguos", 2,
                RetentionPolicy.retain(original, now, 30).size());
        check("Retención conserva sin fecha", true,
                !RetentionPolicy.isExpired(undated, now, 30));
        check("Retención desactivada", 3,
                RetentionPolicy.retain(original, now, 0).size());
        check("Retención detecta antiguo", true,
                RetentionPolicy.isExpired(old, now, 30));
        check("Retención conserva reciente", false,
                RetentionPolicy.isExpired(recent, now, 30));
    }

    private static void testMalformed() {
        expectFailure("XML malformado", "<rss><channel>");
        expectFailure("DOCTYPE rechazado", "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE rss [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<rss><channel><title>&xxe;</title></channel></rss>");
    }

    private static void expectFailure(String label, String xml) {
        try {
            FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                    "https://example.com/feed.xml");
            check(label, "excepción", "sin excepción");
        } catch (IOException expected) {
            check(label, true, true);
        }
    }

    private static void testHtml() {
        check("HTML limpia etiquetas y bloques",
                "Uno\nDos tres",
                HtmlUtil.toPlainText("<p>Uno</p><div>Dos <b>tres</b></div>"));
        check("HTML elimina script", "Antes Después",
                HtmlUtil.toPlainText("Antes<script>alert(1)</script>Después"));
        check("HTML entidades", "A & B < C > D \"E\" 'F'",
                HtmlUtil.decodeEntities("A &amp; B &lt; C &gt; D &quot;E&quot; &apos;F&apos;"));
        check("HTML entidad numérica", "Año",
                HtmlUtil.decodeEntities("A&#241;o"));
        check("HTML snippet", "uno dos...", HtmlUtil.snippet("uno dos tres cuatro", 10));
    }

    private static void testDates() {
        check("Fecha RFC con GMT", true,
                DateUtil.parse("Wed, 02 Oct 2024 13:00:00 GMT") > 0L);
        check("Fecha RFC con offset", true,
                DateUtil.parse("Wed, 02 Oct 2024 13:00:00 +0200") > 0L);
        check("Fecha ISO Z", true,
                DateUtil.parse("2025-01-02T03:04:05Z") > 0L);
        check("Fecha ISO con milisegundos", true,
                DateUtil.parse("2025-01-02T03:04:05.123+01:00") > 0L);
        check("Fecha solo día", true, DateUtil.parse("2024-05-06") > 0L);
        check("Fecha inválida", 0L, DateUtil.parse("no es una fecha"));
    }

    private static void testUrls() throws Exception {
        check("URL sin esquema", "https://example.com/feed",
                FeedFetcher.normalizeUrl("example.com/feed"));
        check("URL con espacios", "https://example.com/feed",
                FeedFetcher.normalizeUrl("  https://example.com/feed  "));
        check("URL HTTP permitida", "http://example.com/feed",
                FeedFetcher.normalizeUrl("http://example.com/feed"));
        expectUrlFailure("URL FTP rechazada", "ftp://example.com/feed");
        expectUrlFailure("URL de archivo rechazada", "file:///etc/passwd");
        expectUrlFailure("URL vacía rechazada", "   ");
    }

    private static void expectUrlFailure(String label, String url) {
        try {
            FeedFetcher.normalizeUrl(url);
            check(label, "excepción", "sin excepción");
        } catch (IOException expected) {
            check(label, true, true);
        }
    }

    private static void testFilters() {
        Article article = article("Oferta especial", "Descuento en TODO", "Tienda");

        FilterRule hide = rule(FilterRule.Action.HIDE, FilterRule.MatchType.CONTAINS,
                "spam", FilterRule.Field.ANY);
        check("Filtro ocultar sin coincidencia", true,
                new FilterEngine(list(hide)).isVisible(article));

        FilterRule showMatch = rule(FilterRule.Action.SHOW, FilterRule.MatchType.CONTAINS,
                "oferta", FilterRule.Field.TITLE);
        check("Filtro mostrar coincidente", true,
                new FilterEngine(list(showMatch)).isVisible(article));

        FilterRule showMiss = rule(FilterRule.Action.SHOW, FilterRule.MatchType.CONTAINS,
                "deporte", FilterRule.Field.TITLE);
        check("Filtro mostrar no coincidente", false,
                new FilterEngine(list(showMiss)).isVisible(article));

        FilterRule hideWins = rule(FilterRule.Action.HIDE, FilterRule.MatchType.CONTAINS,
                "descuento", FilterRule.Field.SUMMARY);
        check("Ocultar gana a mostrar", false,
                new FilterEngine(list(showMatch, hideWins)).isVisible(article));

        FilterRule notify = rule(FilterRule.Action.NOTIFY, FilterRule.MatchType.CONTAINS,
                "oferta", FilterRule.Field.TITLE);
        check("Notificar coincidente", true,
                new FilterEngine(list(notify)).shouldNotify(article));
        check("Notificar no coincidente", false,
                new FilterEngine(list(rule(FilterRule.Action.NOTIFY,
                        FilterRule.MatchType.CONTAINS, "deporte",
                        FilterRule.Field.TITLE))).shouldNotify(article));
        check("Silenciar gana a notificar", false,
                new FilterEngine(list(notify, rule(FilterRule.Action.MUTE,
                        FilterRule.MatchType.CONTAINS, "oferta",
                        FilterRule.Field.TITLE))).shouldNotify(article));
        check("Oculto no notifica", false,
                new FilterEngine(list(notify, hideWins)).shouldNotify(article));

        FilterRule scoped = rule(FilterRule.Action.HIDE, FilterRule.MatchType.CONTAINS,
                "oferta", FilterRule.Field.TITLE);
        scoped.feedId = "https://otra.example/feed";
        check("Filtro limitado a otra fuente", true,
                new FilterEngine(list(scoped)).isVisible(article));

        FilterRule disabled = rule(FilterRule.Action.HIDE, FilterRule.MatchType.CONTAINS,
                "oferta", FilterRule.Field.TITLE);
        disabled.enabled = false;
        check("Filtro desactivado", true,
                new FilterEngine(list(disabled)).isVisible(article));
    }

    private static void testWordAndRegex() {
        Article article = article("TODOS los días", "Texto normal", "Fuente");

        FilterRule word = rule(FilterRule.Action.SHOW, FilterRule.MatchType.WORD,
                "TODO", FilterRule.Field.TITLE);
        check("Palabra completa no coincide parcial", false,
                new FilterEngine(list(word)).isVisible(article));

        FilterRule wordOk = rule(FilterRule.Action.SHOW, FilterRule.MatchType.WORD,
                "días", FilterRule.Field.TITLE);
        check("Palabra completa coincide", true,
                new FilterEngine(list(wordOk)).isVisible(article));

        FilterRule regex = rule(FilterRule.Action.SHOW, FilterRule.MatchType.REGEX,
                "TODOS\\s+los", FilterRule.Field.TITLE);
        check("Regex coincide", true, new FilterEngine(list(regex)).isVisible(article));
        check("Regex válida", null, FilterEngine.validationError(regex));
        check("Regex inválida", true,
                FilterEngine.validationError(rule(FilterRule.Action.SHOW,
                        FilterRule.MatchType.REGEX, "(", FilterRule.Field.TITLE)) != null);

        FilterRule caseSensitive = rule(FilterRule.Action.SHOW,
                FilterRule.MatchType.CONTAINS, "todos", FilterRule.Field.TITLE);
        caseSensitive.caseSensitive = true;
        check("Mayúsculas y minúsculas", false,
                new FilterEngine(list(caseSensitive)).isVisible(article));
        caseSensitive.caseSensitive = false;
        check("Sin distinguir mayúsculas", true,
                new FilterEngine(list(caseSensitive)).isVisible(article));
    }

    private static void testFilterSimulator() {
        FilterRule notify = rule(FilterRule.Action.NOTIFY, FilterRule.MatchType.CONTAINS,
                "privacidad", FilterRule.Field.TITLE);
        FilterSimulator.Sample sample = FilterSimulator.generate(notify,
                new ArrayList<com.rsspro.data.Feed>(), new java.util.Random(7L));
        check("Simulador inserta el patrón", true,
                sample.article.title.contains("privacidad"));
        check("Simulador conserva el ámbito", true,
                FilterEngine.ruleMatches(sample.article, notify));

        FilterSimulator.Outcome outcome = FilterSimulator.evaluate(sample.article,
                list(notify), notify);
        check("Simulador regla coincidente", true, outcome.ruleMatches);
        check("Simulador visible", true, outcome.visible);
        check("Simulador notificaría", true, outcome.notify);
        check("Simulador reglas coincidentes", 1, outcome.matches.size());

        FilterRule hide = rule(FilterRule.Action.HIDE, FilterRule.MatchType.CONTAINS,
                "privacidad", FilterRule.Field.TITLE);
        hide.id = "hide-privacidad";
        FilterSimulator.Outcome hidden = FilterSimulator.evaluate(sample.article,
                list(hide), notify);
        check("Simulador ocultar gana", false, hidden.visible);
        check("Simulador oculto no notifica", false, hidden.notify);
        check("Simulador muestra ambas reglas", 2, hidden.matches.size());

        FilterRule scoped = rule(FilterRule.Action.NOTIFY, FilterRule.MatchType.CONTAINS,
                "privacidad", FilterRule.Field.TITLE);
        scoped.feedId = "https://otra.example/feed";
        check("Simulador detecta fuente fuera de ámbito", false,
                FilterSimulator.evaluate(sample.article, list(scoped), scoped).inScope);
        check("Simulador no aplica regla de otra fuente", false,
                FilterEngine.ruleMatches(sample.article, scoped));

        FilterRule regex = rule(FilterRule.Action.SHOW, FilterRule.MatchType.REGEX,
                "privacidad|seguridad", FilterRule.Field.ANY);
        FilterSimulator.Sample edited = FilterSimulator.withText(sample.article, regex,
                "Nueva privacidad y seguridad");
        check("Simulador usa el texto editado", "Nueva privacidad y seguridad",
                edited.article.content);
        check("Simulador evalúa regex", true,
                FilterEngine.ruleMatches(edited.article, regex));
    }

    private static void testMsrcRegression() {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">\n"
                + "<channel><title>MSRC</title><link>https://api.msrc.microsoft.com/</link>\n"
                + "<item><title>Actualización de seguridad</title>\n"
                + "<link>https://msrc.microsoft.com/update-guide/vulnerability/CVE-2026-1</link>\n"
                + "<guid>CVE-2026-1</guid>\n"
                + "<media:content url=\"https://img.example/msrc.png\" "
                + "type=\"image/png\"/>\n"
                + "<description><![CDATA[<p>Descripción válida del feed de MSRC.</p>]]></description>\n"
                + "</item></channel></rss>";
        try {
            ParsedFeed parsed = FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8),
                    "https://api.msrc.microsoft.com/update-guide/rss");
            Article item = parsed.articles.get(0);
            check("MSRC conserva descripción tras media:content", true,
                    item.summary.contains("Descripción válida"));
            check("MSRC conserva imagen", "https://img.example/msrc.png", item.imageUrl);
        } catch (IOException ex) {
            check("MSRC parsea el feed", true, false);
        }

        String spa = "<!doctype html><html><head><title>MSRC</title></head>"
                + "<body><div id=\"root\"></div>"
                + "<script>window.__INITIAL_STATE__={};</script></body></html>";
        check("MSRC SPA sin texto extraíble", "", ArticlePageLoader.extract(spa));
    }

    private static Article article(String title, String summary, String feedTitle) {
        Article article = new Article();
        article.id = "id";
        article.feedId = "https://example.com/feed";
        article.feedTitle = feedTitle;
        article.title = title;
        article.summary = summary;
        article.content = summary;
        article.link = "https://example.com/a";
        article.author = "Ana";
        article.publishedAt = 1000L;
        return article;
    }

    private static FilterRule rule(FilterRule.Action action, FilterRule.MatchType type,
                                   String pattern, FilterRule.Field field) {
        FilterRule rule = new FilterRule();
        rule.id = pattern;
        rule.name = pattern;
        rule.action = action;
        rule.matchType = type;
        rule.pattern = pattern;
        rule.field = field;
        rule.enabled = true;
        return rule;
    }

    private static List<FilterRule> list(FilterRule... rules) {
        List<FilterRule> result = new ArrayList<FilterRule>();
        for (FilterRule rule : rules) {
            result.add(rule);
        }
        return result;
    }

    private static List<Article> list(Article... articles) {
        List<Article> result = new ArrayList<Article>();
        for (Article article : articles) {
            result.add(article);
        }
        return result;
    }

    private static void check(String label, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
            System.out.println("OK   " + label);
        } else {
            failed++;
            System.out.println("FAIL " + label + " -> esperado: " + expected
                    + ", obtenido: " + actual);
        }
    }
}
