package com.rsspro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rsspro.data.Article;
import com.rsspro.net.ArticlePageLoader;
import com.rsspro.store.Store;
import com.rsspro.ui.ImageLoader;
import com.rsspro.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ArticleActivity extends Activity {
    private static final long FULL_CONTENT_RETRY_MS = 24L * 60L * 60L * 1000L;
    private static final int FULL_CONTENT_MIN_LENGTH = 300;

    private Store store;
    private Article article;
    private TextView bodyView;
    private TextView loadStatus;
    private boolean loadingFullContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        store = new Store(this);
        article = store.getArticle(getIntent().getStringExtra(MainActivity.EXTRA_ARTICLE_ID));
        setContentView(buildRoot());
        Ui.applySystemBars(this);
        if (article != null) {
            store.markRead(article.id);
            maybeLoadFullContent(false);
        }
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Ui.surface(this));
        header.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 9));

        TextView back = Ui.action(this, "Volver", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back, Ui.wrapWrap());

        TextView label = Ui.text(this, "LECTOR", 10, Ui.secondaryColor(this), true);
        label.setLetterSpacing(0.12f);
        label.setGravity(Gravity.CENTER);
        header.addView(label, Ui.weightWrap(1));

        TextView share = Ui.action(this, "Compartir", false);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareArticle();
            }
        });
        header.addView(share, Ui.wrapWrap());

        root.addView(header, Ui.matchWrap());
        root.addView(Ui.divider(this));

        if (article == null) {
            root.addView(buildMissing(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            return root;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.bg(this));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 32));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String feed = article.feedTitle == null || article.feedTitle.trim().length() == 0
                ? "RSS" : article.feedTitle.trim();
        TextView chip = Ui.chip(this, feed, Ui.accent(this));
        body.addView(chip, Ui.wrapWrap());

        if (article.imageUrl != null && article.imageUrl.trim().length() > 0) {
            ImageView hero = new ImageView(this);
            hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.setBackground(Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 14)));
            hero.setContentDescription("Imagen del artículo");
            Ui.clipRounded(hero, Ui.dp(this, 14));
            hero.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openInBrowser();
                }
            });
            LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210));
            heroParams.topMargin = Ui.dp(this, 13);
            body.addView(hero, heroParams);
            ImageLoader.load(this, hero, article.imageUrl,
                    Ui.dp(this, 320), Ui.dp(this, 180));
        }

        TextView title = Ui.text(this, article.displayTitle(), 25,
                hasWebLink() ? Ui.accent(this) : Ui.textColor(this), true);
        title.setTextIsSelectable(!hasWebLink());
        title.setPadding(0, Ui.dp(this, 13), 0, 0);
        if (hasWebLink()) {
            title.setClickable(true);
            title.setFocusable(true);
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openInBrowser();
                }
            });
        }
        body.addView(title, Ui.matchWrap());

        addMetaRow(body, "Fecha", dateLabel(article.publishedAt));
        if (article.author != null && article.author.trim().length() > 0) {
            addMetaRow(body, "Autor", article.author.trim());
        }
        if (article.category != null && article.category.trim().length() > 0) {
            addMetaRow(body, "Categoría", article.category.trim());
        }
        if (hasWebLink()) {
            TextView hint = Ui.text(this, "Toca el título para abrir el artículo original.",
                    11, Ui.accent(this), false);
            hint.setPadding(0, Ui.dp(this, 6), 0, 0);
            body.addView(hint, Ui.matchWrap());
        }

        View rule = Ui.divider(this);
        LinearLayout.LayoutParams ruleParams = Ui.matchWrap();
        ruleParams.topMargin = Ui.dp(this, 16);
        body.addView(rule, ruleParams);

        String content = article.bodyText();
        bodyView = Ui.text(this,
                content.length() == 0
                        ? "Este artículo no incluye contenido. Abre el enlace original para leerlo."
                        : content,
                16, content.length() == 0 ? Ui.secondaryColor(this) : Ui.textColor(this), false);
        bodyView.setTextIsSelectable(true);
        bodyView.setLineSpacing(Ui.dp(this, 4), 1.05f);
        if (content.length() == 0) {
            bodyView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        Linkify.addLinks(bodyView, Linkify.WEB_URLS);
        bodyView.setPadding(0, Ui.dp(this, 17), 0, Ui.dp(this, 8));
        body.addView(bodyView, Ui.matchWrap());

        loadStatus = Ui.text(this, "", 12, Ui.secondaryColor(this), false);
        loadStatus.setVisibility(View.GONE);
        loadStatus.setPadding(0, 0, 0, Ui.dp(this, 12));
        body.addView(loadStatus, Ui.matchWrap());

        if (hasWebLink()) {
            TextView open = Ui.action(this, "Abrir en el navegador", true);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openInBrowser();
                }
            });
            body.addView(open, Ui.matchWrap());
        }

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.setGravity(Gravity.CENTER_VERTICAL);
        secondary.setPadding(0, Ui.dp(this, 10), 0, 0);

        TextView copy = Ui.action(this, "Copiar enlace", false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLink();
            }
        });
        secondary.addView(copy, Ui.weightWrap(1));

        TextView unread = Ui.action(this, "Marcar como no leído", false);
        unread.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.markUnread(article.id);
                Toast.makeText(ArticleActivity.this, "Marcado como no leído.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        LinearLayout.LayoutParams unreadParams = Ui.weightWrap(1);
        unreadParams.leftMargin = Ui.dp(this, 8);
        secondary.addView(unread, unreadParams);

        body.addView(secondary, Ui.matchWrap());

        if (article.link != null && article.link.trim().length() > 0) {
            TextView linkLabel = Ui.text(this, "Enlace", 12, Ui.secondaryColor(this), true);
            linkLabel.setPadding(0, Ui.dp(this, 18), 0, 0);
            body.addView(linkLabel, Ui.matchWrap());
            TextView link = Ui.text(this, article.link.trim(), 11,
                    Ui.accent(this), false);
            link.setTextIsSelectable(true);
            link.setClickable(true);
            link.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openInBrowser();
                }
            });
            link.setPadding(0, Ui.dp(this, 4), 0, 0);
            body.addView(link, Ui.matchWrap());
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private View buildMissing() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Ui.bg(this));
        box.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));

        TextView heading = Ui.text(this, "Artículo no disponible", 19,
                Ui.textColor(this), true);
        heading.setGravity(Gravity.CENTER);
        TextView message = Ui.text(this,
                "Puede que la caché se haya actualizado o que la fuente se haya eliminado.",
                14, Ui.secondaryColor(this), false);
        message.setGravity(Gravity.CENTER);
        message.setMaxWidth(Ui.dp(this, 360));
        message.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 20));

        TextView back = Ui.action(this, "Volver a los artículos", true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        box.addView(heading, Ui.wrapWrap());
        box.addView(message, Ui.wrapWrap());
        box.addView(back, Ui.wrapWrap());
        return box;
    }

    private void addMetaRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(this, 7), 0, 0);
        TextView key = Ui.text(this, label, 12, Ui.secondaryColor(this), true);
        key.setMinWidth(Ui.dp(this, 78));
        TextView content = Ui.text(this, value, 12, Ui.textColor(this), false);
        content.setTextIsSelectable(true);
        row.addView(key, Ui.wrapWrap());
        row.addView(content, Ui.weightWrap(1));
        parent.addView(row, Ui.matchWrap());
    }

    private void maybeLoadFullContent(boolean force) {
        if (article == null || loadingFullContent || !hasWebLink()) {
            return;
        }
        String current = article.bodyText();
        if (article.fullContent != null && article.fullContent.trim().length() > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && (current.length() >= FULL_CONTENT_MIN_LENGTH
                || now - article.fullFetchAt < FULL_CONTENT_RETRY_MS)) {
            return;
        }
        loadingFullContent = true;
        if (loadStatus != null) {
            loadStatus.setVisibility(View.VISIBLE);
            loadStatus.setTextColor(Ui.secondaryColor(this));
            loadStatus.setText("Cargando artículo completo…");
            loadStatus.setClickable(false);
            loadStatus.setOnClickListener(null);
        }
        final int currentLength = current.length();
        final String link = article.link.trim();
        final String articleId = article.id;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String extracted = null;
                String error = null;
                try {
                    ArticlePageLoader.Result result = ArticlePageLoader.load(link);
                    extracted = result.text == null ? "" : result.text.trim();
                    if (extracted.length() == 0) {
                        error = "No se encontró texto en el artículo original.";
                    }
                } catch (Throwable throwable) {
                    error = throwable.getMessage();
                    if (error == null || error.trim().length() == 0) {
                        error = "No se pudo descargar el artículo.";
                    }
                }
                final String finalText = extracted;
                final String finalError = error;
                final long fetchedAt = System.currentTimeMillis();
                final boolean accepted = finalText != null
                        && finalText.length() > currentLength + 40;
                store.recordFullContentFetch(articleId,
                        accepted ? finalText : null, fetchedAt);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingFullContent = false;
                        if (isFinishing() || isDestroyed() || article == null) {
                            return;
                        }
                        article.fullFetchAt = fetchedAt;
                        if (accepted) {
                            article.fullContent = finalText;
                            applyFullContent(finalText);
                            if (loadStatus != null) {
                                loadStatus.setVisibility(View.GONE);
                            }
                            return;
                        }
                        if (loadStatus == null) {
                            return;
                        }
                        if (currentLength == 0) {
                            loadStatus.setVisibility(View.VISIBLE);
                            loadStatus.setTextColor(Ui.accent(ArticleActivity.this));
                            loadStatus.setText("No se pudo cargar el detalle completo. Toca para reintentar.");
                            loadStatus.setClickable(true);
                            loadStatus.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    maybeLoadFullContent(true);
                                }
                            });
                        } else {
                            loadStatus.setVisibility(View.GONE);
                            loadStatus.setClickable(false);
                            loadStatus.setOnClickListener(null);
                        }
                    }
                });
            }
        }, "rsspro-full-article").start();
    }

    private void applyFullContent(String text) {
        if (bodyView == null || text == null) {
            return;
        }
        bodyView.setText(text);
        bodyView.setTextColor(Ui.textColor(this));
        bodyView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        bodyView.setLineSpacing(Ui.dp(this, 4), 1.05f);
        bodyView.setTextIsSelectable(true);
        Linkify.addLinks(bodyView, Linkify.WEB_URLS);
    }

    private boolean hasWebLink() {
        if (article == null || article.link == null) {
            return false;
        }
        String value = article.link.trim();
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private void openInBrowser() {
        if (!hasWebLink()) {
            Toast.makeText(this, "Este artículo no tiene un enlace web.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(article.link.trim())));
        } catch (Exception ex) {
            Toast.makeText(this, "No se encontró un navegador disponible.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void shareArticle() {
        if (article == null) {
            return;
        }
        String text = article.displayTitle();
        if (article.link != null && article.link.trim().length() > 0) {
            text = text + "\n" + article.link.trim();
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, article.displayTitle());
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "Compartir artículo"));
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudo compartir el artículo.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLink() {
        if (article == null || article.link == null || article.link.trim().length() == 0) {
            Toast.makeText(this, "Este artículo no tiene enlace.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                "Enlace del artículo", article.link.trim()));
        Toast.makeText(this, "Enlace copiado.", Toast.LENGTH_SHORT).show();
    }

    private String dateLabel(long timestamp) {
        if (timestamp <= 0) {
            return "Sin fecha";
        }
        return new SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}
