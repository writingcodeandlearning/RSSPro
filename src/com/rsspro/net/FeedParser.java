package com.rsspro.net;

import com.rsspro.data.Article;
import com.rsspro.data.ParsedFeed;
import com.rsspro.util.DateUtil;
import com.rsspro.util.HtmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class FeedParser {
    private static final String NS_CONTENT = "http://purl.org/rss/1.0/modules/content/";
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
    private static final String NS_RSS1 = "http://purl.org/rss/1.0/";
    private static final Pattern IMG_SRC = Pattern.compile(
            "(?is)<img\\b[^>]*?\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    private FeedParser() {
    }

    public static ParsedFeed parse(byte[] data, String feedUrl) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("La respuesta del feed está vacía.");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setCoalescing(true);
            factory.setExpandEntityReferences(false);
            setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(stripBom(data)));
            Element root = document.getDocumentElement();
            if (root == null) {
                throw new IOException("El documento XML no tiene elemento raíz.");
            }
            String rootName = localName(root).toLowerCase(Locale.US);
            ParsedFeed parsed = new ParsedFeed();
            List<Element> items = new ArrayList<Element>();

            if ("rss".equals(rootName)) {
                Element channel = firstChild(root, "channel");
                if (channel == null) {
                    throw new IOException("El RSS no contiene un canal.");
                }
                parsed.title = HtmlUtil.toPlainText(text(firstChild(channel, "title")));
                parsed.siteUrl = resolve(feedUrl, text(firstChild(channel, "link")));
                parsed.imageUrl = rssChannelImage(channel, feedUrl);
                items.addAll(children(channel, "item"));
            } else if ("feed".equals(rootName)) {
                parsed.title = HtmlUtil.toPlainText(text(firstChild(root, "title")));
                parsed.siteUrl = atomAlternateLink(root, feedUrl);
                parsed.imageUrl = atomFeedImage(root, feedUrl);
                items.addAll(children(root, "entry"));
            } else if ("rdf".equals(rootName)) {
                Element channel = firstChild(root, "channel");
                if (channel != null) {
                    parsed.title = HtmlUtil.toPlainText(text(firstChild(channel, "title")));
                    parsed.siteUrl = resolve(feedUrl, text(firstChild(channel, "link")));
                    parsed.imageUrl = rssChannelImage(channel, feedUrl);
                }
                items.addAll(children(root, "item"));
            } else {
                throw new IOException("La URL no contiene un feed RSS o Atom compatible.");
            }

            boolean atom = "feed".equals(rootName);
            Map<String, Article> unique = new LinkedHashMap<String, Article>();
            for (Element item : items) {
                Article article = parseItem(item, atom, feedUrl, parsed.title);
                if (article != null && !unique.containsKey(article.id)) {
                    unique.put(article.id, article);
                }
                if (unique.size() >= 500) {
                    break;
                }
            }

            parsed.articles.addAll(unique.values());
            Collections.sort(parsed.articles, new Comparator<Article>() {
                @Override
                public int compare(Article left, Article right) {
                    long a = left.publishedAt;
                    long b = right.publishedAt;
                    if (a == b) {
                        return left.displayTitle().compareToIgnoreCase(right.displayTitle());
                    }
                    return b > a ? 1 : -1;
                }
            });
            return parsed;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No se pudo interpretar el feed: " + safeMessage(ex), ex);
        }
    }

    private static Article parseItem(Element item, boolean atom, String feedUrl, String feedTitle) {
        String title = HtmlUtil.toPlainText(text(firstChild(item, "title")));
        String rawDate = firstText(item, "pubDate", "published", "updated", "date", "modified");
        String link = articleLink(item, atom, feedUrl);
        String guid = firstText(item, "guid", "id");
        String author = firstText(item, "author", "creator");
        Element dcCreator = firstDescendantNs(item, NS_DC, "creator");
        if (dcCreator != null && text(dcCreator).length() > 0) {
            author = text(dcCreator);
        }
        if (atom) {
            Element authorElement = firstChild(item, "author");
            String atomAuthor = text(firstChild(authorElement, "name"));
            if (atomAuthor.length() > 0) {
                author = atomAuthor;
            }
        }
        Element contentElement = firstDescendantNs(item, NS_CONTENT, "encoded");
        if (contentElement == null) {
            contentElement = firstChildNs(item, NS_ATOM, "content");
        }
        if (contentElement == null) {
            contentElement = firstChildNoNamespace(item, "content");
        }
        if (contentElement == null) {
            contentElement = firstChildNs(item, NS_ATOM, "summary");
        }
        if (contentElement == null) {
            contentElement = firstChildNoNamespace(item, "summary");
        }
        if (contentElement == null) {
            contentElement = firstChildNoNamespace(item, "description");
        }
        String rawContent = text(contentElement);
        String content = HtmlUtil.toPlainText(rawContent);
        if (content.length() > 150000) {
            content = content.substring(0, 150000);
        }
        String summary = HtmlUtil.snippet(content, 360);
        String description = text(firstChild(item, "description"));
        String summaryElement = text(firstChild(item, "summary"));
        String imageUrl = extractArticleImage(item, atom, link.length() > 0 ? link : feedUrl,
                rawContent, summaryElement, description);

        if (title.length() == 0) {
            title = "Sin título";
        }
        String key = guid;
        if (key.length() == 0) {
            key = link;
        }
        if (key.length() == 0) {
            key = title + "|" + rawDate;
        }

        Article article = new Article();
        article.id = hash(feedUrl + "\n" + key);
        article.feedId = feedUrl;
        article.feedTitle = feedTitle == null ? "" : feedTitle;
        article.title = title;
        article.link = link;
        article.imageUrl = imageUrl;
        article.summary = summary;
        article.content = content;
        article.author = HtmlUtil.toPlainText(author);
        article.category = categories(item);
        article.publishedAt = DateUtil.parse(rawDate);
        return article;
    }

    private static String extractArticleImage(Element item, boolean atom, String baseUrl,
                                              String... htmlFragments) {
        String media = mediaImage(item, baseUrl);
        if (media.length() > 0) {
            return media;
        }
        String enclosure = enclosureImage(item, atom, baseUrl);
        if (enclosure.length() > 0) {
            return enclosure;
        }
        for (String fragment : htmlFragments) {
            String image = firstHtmlImage(fragment, baseUrl);
            if (image.length() > 0) {
                return image;
            }
        }
        return "";
    }

    private static String mediaImage(Element item, String baseUrl) {
        NodeList thumbnails = item.getElementsByTagNameNS("*", "thumbnail");
        for (int i = 0; i < thumbnails.getLength(); i++) {
            Node node = thumbnails.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String value = element.getAttribute("url");
            if (value.length() == 0) {
                value = element.getAttribute("href");
            }
            if (value.length() == 0) {
                value = text(element);
            }
            String image = cleanImageUrl(value, baseUrl);
            if (image.length() > 0) {
                return image;
            }
        }

        NodeList contents = item.getElementsByTagNameNS("*", "content");
        for (int i = 0; i < contents.getLength(); i++) {
            Node node = contents.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String medium = element.getAttribute("medium");
            String type = element.getAttribute("type");
            boolean image = "image".equalsIgnoreCase(medium)
                    || (type != null && type.toLowerCase(Locale.US).startsWith("image/"));
            if (!image) {
                continue;
            }
            String value = element.getAttribute("url");
            if (value.length() == 0) {
                value = element.getAttribute("href");
            }
            String cleaned = cleanImageUrl(value, baseUrl);
            if (cleaned.length() > 0) {
                return cleaned;
            }
        }
        return "";
    }

    private static String enclosureImage(Element item, boolean atom, String baseUrl) {
        List<Element> enclosures = children(item, "enclosure");
        for (Element enclosure : enclosures) {
            String type = enclosure.getAttribute("type").toLowerCase(Locale.US);
            String value = enclosure.getAttribute("url");
            if (type.length() == 0 || type.startsWith("image/")) {
                String image = cleanImageUrl(value, baseUrl);
                if (image.length() > 0) {
                    return image;
                }
            }
        }
        if (atom) {
            List<Element> links = children(item, "link");
            for (Element link : links) {
                String rel = link.getAttribute("rel");
                String type = link.getAttribute("type").toLowerCase(Locale.US);
                if ((!"enclosure".equalsIgnoreCase(rel) && !type.startsWith("image/"))
                        || type.length() == 0) {
                    continue;
                }
                String image = cleanImageUrl(link.getAttribute("href"), baseUrl);
                if (image.length() > 0) {
                    return image;
                }
            }
        }
        return "";
    }

    private static String firstHtmlImage(String html, String baseUrl) {
        if (html == null || html.length() == 0) {
            return "";
        }
        Matcher matcher = IMG_SRC.matcher(html);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value == null) {
                value = matcher.group(2);
            }
            if (value == null) {
                value = matcher.group(3);
            }
            String image = cleanImageUrl(value, baseUrl);
            if (image.length() > 0) {
                return image;
            }
        }
        return "";
    }

    private static String cleanImageUrl(String raw, String baseUrl) {
        String value = HtmlUtil.decodeEntities(raw == null ? "" : raw.trim());
        if (value.length() == 0 || value.startsWith("data:")
                || value.startsWith("javascript:")) {
            return "";
        }
        String resolved = resolve(baseUrl, value);
        try {
            URL url = new URL(resolved);
            String protocol = url.getProtocol().toLowerCase(Locale.US);
            if (("http".equals(protocol) || "https".equals(protocol))
                    && url.getHost() != null && url.getHost().trim().length() > 0) {
                return url.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String rssChannelImage(Element channel, String feedUrl) {
        if (channel == null) {
            return "";
        }
        Element image = firstChild(channel, "image");
        if (image == null) {
            return "";
        }
        String value = text(firstChild(image, "url"));
        if (value.length() == 0) {
            value = image.getAttribute("resource");
        }
        if (value.length() == 0) {
            value = image.getAttribute("rdf:resource");
        }
        if (value.length() == 0) {
            String raw = text(image);
            if (raw.indexOf('<') < 0 && raw.indexOf("://") >= 0) {
                value = raw;
            }
        }
        return cleanImageUrl(value, feedUrl);
    }

    private static String atomFeedImage(Element feed, String feedUrl) {
        String value = text(firstChild(feed, "logo"));
        if (value.length() == 0) {
            value = text(firstChild(feed, "icon"));
        }
        return cleanImageUrl(value, feedUrl);
    }

    private static String articleLink(Element item, boolean atom, String baseUrl) {
        if (atom) {
            String alternate = atomAlternateLink(item, baseUrl);
            if (alternate.length() > 0) {
                return alternate;
            }
        }
        String plain = text(firstChild(item, "link")).trim();
        if (plain.length() > 0 && !plain.startsWith("<")) {
            return resolve(baseUrl, plain);
        }
        Element guid = firstChild(item, "guid");
        if (guid != null) {
            String value = text(guid).trim();
            String permaLink = guid.getAttribute("isPermaLink");
            if (value.length() > 0 && !"false".equalsIgnoreCase(permaLink)) {
                return resolve(baseUrl, value);
            }
        }
        return "";
    }

    private static String atomAlternateLink(Element parent, String baseUrl) {
        if (parent == null) {
            return "";
        }
        List<Element> links = children(parent, "link");
        String fallback = "";
        for (Element link : links) {
            String href = link.getAttribute("href").trim();
            if (href.length() == 0) {
                continue;
            }
            String rel = link.getAttribute("rel").trim();
            String type = link.getAttribute("type").trim();
            String resolved = resolve(baseUrl, href);
            if (fallback.length() == 0) {
                fallback = resolved;
            }
            boolean alternate = rel.length() == 0 || "alternate".equalsIgnoreCase(rel);
            boolean html = type.length() == 0 || type.contains("html");
            if (alternate && html) {
                return resolved;
            }
        }
        return fallback;
    }

    private static String resolve(String base, String target) {
        String value = target == null ? "" : target.trim();
        if (value.length() == 0) {
            return "";
        }
        try {
            URL resolved = new URL(new URL(base), value);
            return resolved.toString();
        } catch (Exception ex) {
            return value;
        }
    }

    private static String firstText(Element parent, String... names) {
        for (String name : names) {
            Element element = firstChild(parent, name);
            if (element != null) {
                String value = text(element).trim();
                if (value.length() > 0) {
                    return value;
                }
            }
        }
        return "";
    }

    private static Element firstDescendantNs(Element parent, String namespace, String name) {
        if (parent == null) {
            return null;
        }
        NodeList list = parent.getElementsByTagNameNS(namespace, name);
        if (list.getLength() == 0) {
            return null;
        }
        return (Element) list.item(0);
    }

    private static Element firstChildNs(Element parent, String namespace, String... names) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nodeNamespace = node.getNamespaceURI();
            if (nodeNamespace == null || !nodeNamespace.equals(namespace)) {
                continue;
            }
            String local = localName(node);
            for (String name : names) {
                if (name.equalsIgnoreCase(local)) {
                    return (Element) node;
                }
            }
        }
        return null;
    }

    private static Element firstChildNoNamespace(Element parent, String... names) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nodeNamespace = node.getNamespaceURI();
            if (nodeNamespace != null && nodeNamespace.length() > 0) {
                continue;
            }
            String local = localName(node);
            for (String name : names) {
                if (name.equalsIgnoreCase(local)) {
                    return (Element) node;
                }
            }
        }
        return null;
    }

    private static String categories(Element item) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (Element category : children(item, "category")) {
            String namespace = category.getNamespaceURI();
            if (namespace != null && namespace.length() > 0
                    && !NS_ATOM.equals(namespace) && !NS_RSS1.equals(namespace)) {
                continue;
            }
            String value = category.getAttribute("term");
            if (value.length() == 0) {
                value = category.getAttribute("label");
            }
            if (value.length() == 0) {
                value = text(category);
            }
            addCategory(values, value);
        }
        NodeList subjects = item.getElementsByTagNameNS(NS_DC, "subject");
        for (int i = 0; i < subjects.getLength(); i++) {
            Node node = subjects.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                addCategory(values, text((Element) node));
            }
        }
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String value : values) {
            if (count >= 8) {
                break;
            }
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(value);
            count++;
        }
        return out.toString();
    }

    private static void addCategory(LinkedHashSet<String> values, String raw) {
        String value = HtmlUtil.toPlainText(raw);
        if (value.length() > 0) {
            values.add(value);
        }
    }

    private static Element firstChild(Element parent, String... names) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String local = localName(node);
            for (String name : names) {
                if (name.equalsIgnoreCase(local)) {
                    return (Element) node;
                }
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<Element>();
        if (parent == null) {
            return result;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equalsIgnoreCase(localName(node))) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String text(Element element) {
        if (element == null) {
            return "";
        }
        String value = element.getTextContent();
        return value == null ? "" : value.trim();
    }

    private static byte[] stripBom(byte[] data) {
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            byte[] result = new byte[data.length - 3];
            System.arraycopy(data, 3, result, 0, result.length);
            return result;
        }
        return data;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0x0F, 16));
                out.append(Character.forDigit(b & 0x0F, 16));
            }
            return out.substring(0, 40);
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.length() == 0
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
