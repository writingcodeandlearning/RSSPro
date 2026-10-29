package com.rsspro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.rsspro.core.ArticleSearch;
import com.rsspro.core.FilterEngine;
import com.rsspro.core.FilterSimulator;
import com.rsspro.data.Article;
import com.rsspro.data.Feed;
import com.rsspro.data.FilterRule;
import com.rsspro.net.FeedFetcher;
import com.rsspro.notify.NotifyHelper;
import com.rsspro.notify.Scheduler;
import com.rsspro.store.Store;
import com.rsspro.sync.SyncManager;
import com.rsspro.ui.ImageLoader;
import com.rsspro.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    public static final String EXTRA_ARTICLE_ID = "com.rsspro.extra.ARTICLE_ID";

    private static final int TAB_ARTICLES = 0;
    private static final int TAB_FEEDS = 1;
    private static final int TAB_FILTERS = 2;
    private static final int REQUEST_NOTIFICATIONS = 5001;
    private static final String STATE_TAB = "current_tab";
    private static final String STATE_FEED = "selected_feed";
    private static final String STATE_MODE = "article_mode";
    private static final String STATE_SEARCH = "article_search";
    private static final int SWIPE_COMMIT_DP = 80;
    private static final int SWIPE_MAX_DP = 120;
    private static final int SWIPE_EXIT_MS = 90;
    private static final int SWIPE_RESET_MS = 70;
    private static final int MODE_ALL = 0;
    private static final int MODE_UNREAD = 1;
    private static final int MODE_READ = 2;
    private static final int MODE_SEARCH = 3;
    private static final String[] MODE_LABELS = {"Todos", "Sin leer", "Leídos", "Buscar"};
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    private static final String[] ACTION_LABELS = {
            "Mostrar", "Ocultar", "Notificar", "Silenciar"
    };
    private static final String[] MATCH_LABELS = {
            "Texto", "Palabra completa", "Regex"
    };
    private static final String[] FIELD_LABELS = {
            "Cualquier campo", "Título", "Resumen", "Contenido", "Enlace", "Autor", "Fuente"
    };
    private static final String[] THEME_LABELS = {
            "Sistema", "Claro", "Oscuro"
    };
    private static final String[] INTERVAL_LABELS = {
            "15 minutos", "30 minutos", "60 minutos", "2 horas", "6 horas"
    };
    private static final int[] INTERVAL_VALUES = {15, 30, 60, 120, 360};
    private static final String[] RETENTION_LABELS = {
            "Nunca", "7 días", "30 días", "90 días", "180 días"
    };
    private static final int[] RETENTION_VALUES = {0, 7, 30, 90, 180};

    private Store store;
    private FrameLayout content;
    private ListView articleList;
    private boolean restoreArticleScroll;
    private int articleScrollPosition;
    private int articleScrollOffset;
    private TextView refreshButton;
    private TextView settingsButton;
    private final TextView[] tabs = new TextView[3];
    private int currentTab = TAB_ARTICLES;
    private String selectedFeedId;
    private boolean syncing;
    private LinearLayout articlesBar;
    private final TextView[] modeChips = new TextView[4];
    private LinearLayout searchRow;
    private EditText searchInput;
    private int articleMode = MODE_ALL;
    private String searchQuery = "";
    private boolean updatingSearch;
    private final android.os.Handler searchHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        store = new Store(this);
        setContentView(buildRoot());
        Ui.applySystemBars(this);
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(STATE_TAB, TAB_ARTICLES);
            selectedFeedId = savedInstanceState.getString(STATE_FEED);
            articleMode = savedInstanceState.getInt(STATE_MODE, MODE_ALL);
            searchQuery = savedInstanceState.getString(STATE_SEARCH, "");
        }
        selectTab(currentTab);
        handleIntent(getIntent());
        requestNotificationPermission(false);
        if (savedInstanceState == null) {
            pruneInBackground();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            render();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_ARTICLES) {
            selectTab(TAB_ARTICLES);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_TAB, currentTab);
        outState.putString(STATE_FEED, selectedFeedId);
        outState.putInt(STATE_MODE, articleMode);
        outState.putString(STATE_SEARCH, searchQuery);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            NotifyHelper.createChannel(this);
            Scheduler.schedule(this);
            Toast.makeText(this, "Notificaciones activadas.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Sin permiso de notificaciones. Puedes activarlo en Ajustes.",
                    Toast.LENGTH_LONG).show();
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
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 9));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, "RSSPro", 24, Ui.textColor(this), true);
        TextView tagline = Ui.text(this, "LECTOR RSS CON FILTROS", 9.5f,
                Ui.secondaryColor(this), true);
        tagline.setLetterSpacing(0.09f);
        tagline.setPadding(0, Ui.dp(this, 1), 0, 0);
        titles.addView(title, Ui.matchWrap());
        titles.addView(tagline, Ui.matchWrap());
        header.addView(titles, Ui.weightWrap(1));

        refreshButton = Ui.action(this, "Actualizar", false);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncAll();
            }
        });
        LinearLayout.LayoutParams refreshParams = Ui.wrapWrap();
        refreshParams.leftMargin = Ui.dp(this, 4);
        header.addView(refreshButton, refreshParams);

        settingsButton = Ui.action(this, "Ajustes", false);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });
        LinearLayout.LayoutParams settingsParams = Ui.wrapWrap();
        settingsParams.leftMargin = Ui.dp(this, 2);
        header.addView(settingsButton, settingsParams);

        root.addView(header, Ui.matchWrap());
        root.addView(Ui.divider(this));
        articlesBar = buildArticlesBar();
        root.addView(articlesBar, Ui.matchWrap());

        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.bg(this));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(Ui.divider(this));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setBackgroundColor(Ui.surface(this));
        navigation.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        String[] tabLabels = {"Artículos", "Fuentes", "Filtros"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView tab = Ui.text(this, tabLabels[i], 13, Ui.secondaryColor(this), true);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(Ui.dp(this, 46));
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            navigation.addView(tab, params);
            tabs[i] = tab;
        }
        root.addView(navigation, Ui.matchWrap());
        return root;
    }

    private void selectTab(int tab) {
        currentTab = tab;
        updateTabStyles();
        refreshArticlesBar();
        render();
    }

    private void hideSearchKeyboard() {
        if (searchInput == null) {
            return;
        }
        searchInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private void updateTabStyles() {
        if (tabs[0] == null) {
            return;
        }
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == currentTab;
            tabs[i].setTextColor(selected ? Ui.accent(this) : Ui.secondaryColor(this));
            tabs[i].setTypeface(Typeface.create(
                    selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            if (selected) {
                tabs[i].setBackground(Ui.rounded(
                        Ui.blend(Ui.surfaceAlt(this), Ui.accent(this), 0.16f),
                        Ui.dp(this, 13)));
            } else {
                tabs[i].setBackground(Ui.ripple(this,
                        Ui.rounded(0x00000000, Ui.dp(this, 13)), Ui.accent(this)));
            }
        }
    }

    private void render() {
        if (content == null || store == null) {
            return;
        }
        updateRefreshButton();
        if (currentTab == TAB_ARTICLES) {
            renderArticles();
        } else if (currentTab == TAB_FEEDS) {
            renderFeeds();
        } else {
            renderFilters();
        }
    }


    private LinearLayout buildArticlesBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.surface(this));
        bar.setVisibility(View.GONE);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 3));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < modeChips.length; i++) {
            final int mode = i;
            TextView chip = feedFilterChip(MODE_LABELS[i], false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (articleMode == mode) {
                        if (mode == MODE_SEARCH && searchInput != null) {
                            searchInput.requestFocus();
                        }
                        return;
                    }
                    articleMode = mode;
                    refreshArticlesBar();
                    renderArticles();
                    if (mode == MODE_SEARCH && searchInput != null) {
                        searchInput.requestFocus();
                    } else if (mode != MODE_SEARCH) {
                        hideSearchKeyboard();
                    }
                }
            });
            LinearLayout.LayoutParams params = Ui.wrapWrap();
            if (i > 0) {
                params.leftMargin = Ui.dp(this, 6);
            }
            row.addView(chip, params);
            modeChips[i] = chip;
        }
        bar.addView(scroll, Ui.matchWrap());

        searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 9));
        searchRow.setVisibility(View.GONE);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Buscar título, texto, autor, fuente, categoría o enlace");
        searchInput.setHintTextColor(Ui.secondaryColor(this));
        searchInput.setTextColor(Ui.textColor(this));
        searchInput.setTextSize(14);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setBackground(Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 12)));
        searchInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 9),
                Ui.dp(this, 12), Ui.dp(this, 9));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingSearch) {
                    return;
                }
                searchQuery = s == null ? "" : s.toString();
                scheduleSearch();
            }
        });
        searchRow.addView(searchInput, Ui.weightWrap(1));
        bar.addView(searchRow, Ui.matchWrap());
        bar.addView(Ui.divider(this));
        return bar;
    }

    private void refreshArticlesBar() {
        if (articlesBar == null) {
            return;
        }
        articlesBar.setVisibility(currentTab == TAB_ARTICLES ? View.VISIBLE : View.GONE);
        if (currentTab != TAB_ARTICLES) {
            hideSearchKeyboard();
            return;
        }
        for (int i = 0; i < modeChips.length; i++) {
            applyModeChipStyle(modeChips[i], i == articleMode);
        }
        if (searchRow != null) {
            searchRow.setVisibility(articleMode == MODE_SEARCH ? View.VISIBLE : View.GONE);
            if (articleMode != MODE_SEARCH) {
                hideSearchKeyboard();
            }
        }
        if (searchInput != null && !searchInput.getText().toString().equals(searchQuery)) {
            updatingSearch = true;
            searchInput.setText(searchQuery);
            searchInput.setSelection(searchInput.getText().length());
            updatingSearch = false;
        }
    }

    private void applyModeChipStyle(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(selected ? Ui.onAccent(this) : Ui.secondaryColor(this));
        if (selected) {
            chip.setBackground(Ui.rounded(Ui.accent(this), Ui.dp(this, 20)));
        } else {
            chip.setBackground(Ui.ripple(this,
                    Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 20)), Ui.accent(this)));
        }
    }

    private void scheduleSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = new Runnable() {
            @Override
            public void run() {
                pendingSearch = null;
                if (currentTab == TAB_ARTICLES) {
                    renderArticles();
                }
            }
        };
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void renderArticles() {
        List<Feed> feeds = store.getFeeds();
        if (feeds.isEmpty()) {
            showEmpty("Sin fuentes todavía",
                    "Añade la URL de un feed RSS o Atom para empezar a leer.",
                    "Añadir fuente",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showFeedDialog();
                        }
                    });
            return;
        }

        List<Article> all = store.getArticles();
        Collections.sort(all, new Comparator<Article>() {
            @Override
            public int compare(Article left, Article right) {
                if (left.publishedAt == right.publishedAt) {
                    return 0;
                }
                return right.publishedAt > left.publishedAt ? 1 : -1;
            }
        });
        if (all.isEmpty()) {
            showEmpty("Sin artículos",
                    "Pulsa Actualizar para descargar el contenido de tus fuentes.",
                    "Actualizar",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            syncAll();
                        }
                    });
            return;
        }

        List<FilterRule> rules = store.getRules();
        FilterEngine engine = new FilterEngine(rules);
        List<Article> visible = new ArrayList<Article>();
        for (Article article : all) {
            if (engine.evaluate(article).visible) {
                visible.add(article);
            }
        }
        if (visible.isEmpty()) {
            Set<String> readIds = store.getReadIds();
            int unreadAll = 0;
            for (Article article : all) {
                if (!readIds.contains(article.id)) {
                    unreadAll++;
                }
            }
            LinearLayout root = newScreen();
            addArticleSummary(root, "0 de " + all.size()
                    + " artículos visibles · todo filtrado", all, unreadAll);
            addInlineEmpty(root, "Todo está filtrado",
                    "Ningún artículo coincide con las reglas de visibilidad actuales.",
                    "Revisar filtros", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectTab(TAB_FILTERS);
                        }
                    });
            return;
        }

        Feed selectedFeed = null;
        if (selectedFeedId != null) {
            for (Feed feed : feeds) {
                if (selectedFeedId.equals(feed.url)) {
                    selectedFeed = feed;
                    break;
                }
            }
            if (selectedFeed == null) {
                selectedFeedId = null;
            }
        }

        Set<String> readIds = store.getReadIds();
        List<Article> sourceArticles = new ArrayList<Article>();
        for (Article article : visible) {
            if (selectedFeedId == null || selectedFeedId.equals(article.feedId)) {
                sourceArticles.add(article);
            }
        }
        String normalizedQuery = ArticleSearch.normalize(searchQuery);
        List<Article> displayed = new ArrayList<Article>();
        for (Article article : sourceArticles) {
            boolean read = readIds.contains(article.id);
            if (articleMode == MODE_UNREAD && read) {
                continue;
            }
            if (articleMode == MODE_READ && !read) {
                continue;
            }
            if (articleMode == MODE_SEARCH && normalizedQuery.length() > 0
                    && !ArticleSearch.matches(article, searchQuery)) {
                continue;
            }
            displayed.add(article);
        }
        int unreadAll = 0;
        for (Article article : all) {
            if (!readIds.contains(article.id)) {
                unreadAll++;
            }
        }

        LinearLayout root = newScreen();
        int active = 0;
        for (FilterRule rule : rules) {
            if (rule.enabled) {
                active++;
            }
        }
        String summaryText;
        if (articleMode == MODE_UNREAD) {
            summaryText = displayed.size() + " sin leer de " + all.size() + " artículos";
        } else if (articleMode == MODE_READ) {
            summaryText = displayed.size() + " leídos de " + all.size() + " artículos";
        } else if (articleMode == MODE_SEARCH && normalizedQuery.length() > 0) {
            String query = searchQuery.trim();
            if (query.length() > 26) {
                query = query.substring(0, 26) + "…";
            }
            summaryText = displayed.size() + " resultados de " + all.size()
                    + " artículos · “" + query + "”";
        } else {
            summaryText = displayed.size() + " de " + all.size() + " artículos";
        }
        if (selectedFeed != null) {
            summaryText += " · " + selectedFeed.displayTitle();
        }
        if (active > 0) {
            summaryText += " · " + active + " filtros activos";
        }
        addArticleSummary(root, summaryText, all, unreadAll);
        if (feeds.size() >= 2) {
            addFeedSelector(root, feeds, visible, readIds);
        }
        if (displayed.isEmpty()) {
            if (sourceArticles.isEmpty()) {
                addInlineEmpty(root, "Sin artículos en esta fuente",
                        "Esta fuente no tiene artículos visibles con los filtros actuales.",
                        "Ver todas", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                selectedFeedId = null;
                                renderArticles();
                            }
                        });
            } else if (articleMode == MODE_SEARCH && normalizedQuery.length() > 0) {
                addInlineEmpty(root, "Sin resultados",
                        "Ningún artículo coincide con todos los términos de la búsqueda.",
                        "Limpiar búsqueda", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                clearSearch();
                            }
                        });
            } else if (articleMode == MODE_UNREAD) {
                addInlineEmpty(root, "No hay artículos sin leer",
                        "Todos los artículos de esta selección ya están leídos.",
                        "Ver todos", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                setArticleMode(MODE_ALL);
                            }
                        });
            } else if (articleMode == MODE_READ) {
                addInlineEmpty(root, "No hay artículos leídos",
                        "Todavía no has abierto ningún artículo de esta selección.",
                        "Ver todos", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                setArticleMode(MODE_ALL);
                            }
                        });
            } else {
                addInlineEmpty(root, "Sin artículos",
                        "No hay artículos que coincidan con la selección actual.",
                        "Ver todos", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                setArticleMode(MODE_ALL);
                            }
                        });
            }
            return;
        }
        addList(root, new ArticleAdapter(displayed));
    }

    private void clearSearch() {
        searchQuery = "";
        if (searchInput != null) {
            updatingSearch = true;
            searchInput.setText("");
            updatingSearch = false;
        }
        renderArticles();
    }

    private void setArticleMode(int mode) {
        articleMode = mode;
        refreshArticlesBar();
        renderArticles();
    }

    private void addArticleSummary(LinearLayout root, String text, final List<Article> all,
                                   int unreadAll) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 2));
        TextView summary = Ui.text(this, text, 12, Ui.secondaryColor(this), false);
        row.addView(summary, Ui.weightWrap(1));
        if (unreadAll > 0) {
            TextView action = Ui.action(this, "Todo leído", true);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmMarkAllRead(all);
                }
            });
            LinearLayout.LayoutParams params = Ui.wrapWrap();
            params.leftMargin = Ui.dp(this, 8);
            row.addView(action, params);
        }
        root.addView(row, Ui.matchWrap());
    }

    private void confirmMarkAllRead(final List<Article> all) {
        Set<String> readIds = store.getReadIds();
        final List<String> ids = new ArrayList<String>();
        for (Article article : all) {
            if (article.id != null && !readIds.contains(article.id)) {
                ids.add(article.id);
            }
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "No hay artículos sin leer.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Marcar todo como leído")
                .setMessage("Se marcarán " + ids.size()
                        + " artículos de todas las fuentes, incluidos los ocultos por filtros.")
                .setPositiveButton("Marcar todo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.markAllRead(ids);
                        render();
                        Toast.makeText(MainActivity.this,
                                "Todos los artículos están leídos.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addFeedSelector(LinearLayout root, List<Feed> feeds,
                                 List<Article> visible, Set<String> readIds) {
        Map<String, Integer> unread = new HashMap<String, Integer>();
        int unreadAll = 0;
        for (Article article : visible) {
            if (readIds.contains(article.id)) {
                continue;
            }
            unreadAll++;
            Integer current = unread.get(article.feedId);
            unread.put(article.feedId, current == null ? 1 : current + 1);
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 3), Ui.dp(this, 16), Ui.dp(this, 3));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView allChip = feedFilterChip("Todos (" + unreadAll + ")", selectedFeedId == null);
        allChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFeedId != null) {
                    selectedFeedId = null;
                    renderArticles();
                }
            }
        });
        row.addView(allChip, Ui.wrapWrap());

        for (final Feed feed : feeds) {
            Integer count = unread.get(feed.url);
            String label = feed.displayTitle() + " (" + (count == null ? 0 : count) + ")";
            TextView chip = feedFilterChip(label, feed.url.equals(selectedFeedId));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!feed.url.equals(selectedFeedId)) {
                        selectedFeedId = feed.url;
                        renderArticles();
                    }
                }
            });
            LinearLayout.LayoutParams params = Ui.wrapWrap();
            params.leftMargin = Ui.dp(this, 6);
            row.addView(chip, params);
        }
        root.addView(scroll, Ui.matchWrap());
    }

    private TextView feedFilterChip(String label, boolean selected) {
        TextView chip = Ui.text(this, label, 12,
                selected ? Ui.onAccent(this) : Ui.secondaryColor(this), true);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setMaxWidth(Ui.dp(this, 220));
        chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 7),
                Ui.dp(this, 12), Ui.dp(this, 7));
        chip.setClickable(true);
        chip.setFocusable(true);
        if (selected) {
            chip.setBackground(Ui.rounded(Ui.accent(this), Ui.dp(this, 20)));
        } else {
            chip.setBackground(Ui.ripple(this,
                    Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 20)), Ui.accent(this)));
        }
        return chip;
    }

    private void addInlineEmpty(LinearLayout root, String title, String message,
                                String actionLabel, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 32), Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24));

        TextView heading = Ui.text(this, title, 18, Ui.textColor(this), true);
        heading.setGravity(Gravity.CENTER);
        TextView body = Ui.text(this, message, 14, Ui.secondaryColor(this), false);
        body.setGravity(Gravity.CENTER);
        body.setMaxWidth(Ui.dp(this, 360));
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        box.addView(heading, Ui.wrapWrap());
        box.addView(body, Ui.wrapWrap());
        if (actionLabel != null && listener != null) {
            TextView action = Ui.action(this, actionLabel, true);
            action.setOnClickListener(listener);
            box.addView(action, Ui.wrapWrap());
        }
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void renderFeeds() {
        final List<Feed> feeds = store.getFeeds();
        if (feeds.isEmpty()) {
            showEmpty("Sin fuentes todavía",
                    "Añade la URL de un feed RSS o Atom. También puedes pegar la dirección del sitio si contiene un feed.",
                    "Añadir fuente",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showFeedDialog();
                        }
                    });
            return;
        }

        LinearLayout root = newScreen();
        int articleCount = store.getArticles().size();
        String updated = store.getLastSync() > 0
                ? " · última sync " + relativeTime(store.getLastSync())
                : "";
        String summaryText = feeds.size() + " fuentes · " + articleCount + " artículos" + updated;
        addSummary(root, summaryText, "Añadir", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFeedDialog();
            }
        });
        addList(root, new FeedAdapter(feeds));
    }

    private void renderFilters() {
        final List<FilterRule> rules = store.getRules();
        if (rules.isEmpty()) {
            showEmpty("Sin filtros",
                    "Mostrar y Ocultar deciden qué artículos aparecen en la lista. "
                            + "Notificar y Silenciar deciden qué artículos generan avisos.\n\n"
                            + "Puedes usar texto, palabra completa o expresiones regulares.",
                    "Nuevo filtro",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showFilterDialog(null);
                        }
                    });
            return;
        }

        LinearLayout root = newScreen();
        int active = 0;
        for (FilterRule rule : rules) {
            if (rule.enabled) {
                active++;
            }
        }
        String summaryText = rules.size() + " filtros · " + active + " activos";
        addSummary(root, summaryText, "Nuevo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog(null);
            }
        });
        addHelpCard(root,
                "Mostrar/Ocultar afecta a la lista. Notificar/Silenciar afecta a los avisos. "
                        + "Si hay reglas Mostrar, solo se ven las coincidencias. "
                        + "Si hay reglas Notificar, solo se avisa de las coincidencias.");
        addList(root, new FilterAdapter(rules));
    }

    private LinearLayout newScreen() {
        articleList = null;
        content.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void addSummary(LinearLayout root, String text, String actionLabel,
                            View.OnClickListener actionListener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 2));
        TextView summary = Ui.text(this, text, 12, Ui.secondaryColor(this), false);
        row.addView(summary, Ui.weightWrap(1));
        if (actionLabel != null && actionListener != null) {
            TextView action = Ui.action(this, actionLabel, true);
            action.setOnClickListener(actionListener);
            LinearLayout.LayoutParams params = Ui.wrapWrap();
            params.leftMargin = Ui.dp(this, 8);
            row.addView(action, params);
        }
        root.addView(row, Ui.matchWrap());
    }

    private void addHelpCard(LinearLayout root, String text) {
        TextView help = Ui.text(this, text, 12, Ui.secondaryColor(this), false);
        help.setBackground(Ui.card(this, false));
        int pad = Ui.dp(this, 13);
        help.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.setMargins(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 3));
        root.addView(help, params);
    }

    private void addList(LinearLayout root, BaseAdapter adapter) {
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.dp(this, 9));
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 5), Ui.dp(this, 16), Ui.dp(this, 24));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (adapter instanceof ArticleAdapter) {
            articleList = list;
            if (restoreArticleScroll) {
                final int position = articleScrollPosition;
                final int offset = articleScrollOffset;
                restoreArticleScroll = false;
                list.post(new Runnable() {
                    @Override
                    public void run() {
                        articleList.setSelectionFromTop(position, offset);
                    }
                });
            }
        }
    }

    private void prepareArticleScrollRestore() {
        if (articleList == null) {
            return;
        }
        articleScrollPosition = articleList.getFirstVisiblePosition();
        View first = articleList.getChildAt(0);
        articleScrollOffset = first == null
                ? 0 : first.getTop() - articleList.getPaddingTop();
        restoreArticleScroll = true;
    }

    private void markArticleReadFromSwipe(final Article article, final View card) {
        if (article == null || article.id == null || store.isRead(article.id)) {
            card.setTranslationX(0);
            card.setAlpha(1.0f);
            return;
        }
        store.markRead(article.id);
        if (currentTab != TAB_ARTICLES) {
            return;
        }
        prepareArticleScrollRestore();
        final int target = Math.max(card.getWidth(), Ui.dp(this, 240));
        card.animate()
                .translationX(target)
                .alpha(0.15f)
                .setDuration(SWIPE_EXIT_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed()
                                && currentTab == TAB_ARTICLES) {
                            renderArticles();
                        }
                    }
                })
                .start();
    }

    private void showEmpty(String title, String message, String actionLabel,
                           View.OnClickListener listener) {
        content.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Ui.bg(this));
        box.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));

        TextView heading = Ui.text(this, title, 19, Ui.textColor(this), true);
        heading.setGravity(Gravity.CENTER);
        TextView body = Ui.text(this, message, 14, Ui.secondaryColor(this), false);
        body.setGravity(Gravity.CENTER);
        body.setMaxWidth(Ui.dp(this, 360));
        body.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 20));
        box.addView(heading, Ui.wrapWrap());
        box.addView(body, Ui.wrapWrap());
        if (actionLabel != null && listener != null) {
            TextView action = Ui.action(this, actionLabel, true);
            action.setOnClickListener(listener);
            box.addView(action, Ui.wrapWrap());
        }
        content.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void syncAll() {
        if (syncing) {
            return;
        }
        if (store.getFeeds().isEmpty()) {
            Toast.makeText(this, "Añade una fuente primero.", Toast.LENGTH_SHORT).show();
            selectTab(TAB_FEEDS);
            return;
        }
        syncing = true;
        updateRefreshButton();
        new Thread(new Runnable() {
            @Override
            public void run() {
                SyncManager.Result result = null;
                String fatal = null;
                try {
                    result = SyncManager.refreshAll(getApplicationContext());
                } catch (Throwable throwable) {
                    fatal = throwable.getMessage();
                    if (fatal == null) {
                        fatal = throwable.getClass().getSimpleName();
                    }
                }
                final SyncManager.Result finalResult = result;
                final String finalFatal = fatal;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        syncing = false;
                        updateRefreshButton();
                        if (isFinishing()) {
                            return;
                        }
                        render();
                        if (finalFatal != null) {
                            Toast.makeText(MainActivity.this,
                                    "Error al sincronizar: " + finalFatal,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (finalResult == null) {
                            return;
                        }
                        String message = "Fuentes actualizadas: " + finalResult.successfulFeeds
                                + "/" + finalResult.attemptedFeeds
                                + " · nuevos: " + finalResult.newArticles.size();
                        if (finalResult.errorCount() > 0) {
                            message += " · errores: " + finalResult.errorCount();
                        }
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "rsspro-manual-sync").start();
    }

    private void updateRefreshButton() {
        if (refreshButton == null) {
            return;
        }
        refreshButton.setText(syncing ? "Espera..." : "Actualizar");
        refreshButton.setEnabled(!syncing);
        refreshButton.setAlpha(syncing ? 0.5f : 1.0f);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String articleId = intent.getStringExtra(EXTRA_ARTICLE_ID);
        if (articleId != null) {
            intent.removeExtra(EXTRA_ARTICLE_ID);
            openArticle(articleId);
        }
    }

    private void openArticle(String articleId) {
        Intent intent = new Intent(this, ArticleActivity.class);
        intent.putExtra(EXTRA_ARTICLE_ID, articleId);
        startActivity(intent);
    }

    private void showFeedDialog() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 4), pad, 0);

        wrapper.addView(dialogLabel("URL del feed"), Ui.matchWrap());
        final EditText url = new EditText(this);
        url.setHint("https://ejemplo.com/feed.xml");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        wrapper.addView(url, Ui.matchWrap());

        wrapper.addView(dialogLabel("Nombre opcional"), Ui.matchWrap());
        final EditText name = new EditText(this);
        name.setHint("Mi fuente favorita");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        wrapper.addView(name, Ui.matchWrap());

        TextView hint = Ui.text(this,
                "Se admiten feeds RSS, Atom y RSS 1.0. Si dejas el nombre vacío, se usará el título del feed.",
                12, Ui.secondaryColor(this), false);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        wrapper.addView(hint, Ui.matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Añadir fuente")
                .setView(wrapper)
                .setPositiveButton("Añadir", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String normalized;
                                try {
                                    normalized = FeedFetcher.normalizeUrl(url.getText().toString());
                                } catch (Exception ex) {
                                    Toast.makeText(MainActivity.this,
                                            ex.getMessage(), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                for (Feed existing : store.getFeeds()) {
                                    if (normalized.equals(existing.url)) {
                                        Toast.makeText(MainActivity.this,
                                                "Esa fuente ya está añadida.",
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                }
                                Feed feed = new Feed(normalized, name.getText().toString().trim());
                                List<Feed> feeds = store.getFeeds();
                                feeds.add(feed);
                                store.saveFeeds(feeds);
                                dialog.dismiss();
                                selectTab(TAB_FEEDS);
                                Toast.makeText(MainActivity.this,
                                        "Fuente añadida. Sincronizando...",
                                        Toast.LENGTH_SHORT).show();
                                syncAll();
                            }
                        });
            }
        });
        dialog.show();
    }

    private void confirmDeleteFeed(final Feed feed) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar fuente")
                .setMessage("Se eliminará \"" + feed.displayTitle()
                        + "\", sus artículos en caché y los filtros asociados.")
                .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.removeFeed(feed.url);
                        Toast.makeText(MainActivity.this, "Fuente eliminada.",
                                Toast.LENGTH_SHORT).show();
                        render();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private FilterRule readFilterForm(FilterRule base, EditText name, EditText pattern,
                                      Spinner match, Spinner field, Spinner action,
                                      CheckBox caseSensitive, CheckBox enabled,
                                      Spinner scope, List<Feed> feeds) {
        FilterRule rule = new FilterRule();
        rule.id = base == null ? null : base.id;
        String ruleName = name.getText().toString().trim();
        rule.name = ruleName.length() == 0 ? "Filtro" : ruleName;
        rule.pattern = pattern.getText().toString().trim();
        rule.matchType = FilterRule.MatchType.values()[match.getSelectedItemPosition()];
        rule.field = FilterRule.Field.values()[field.getSelectedItemPosition()];
        rule.action = FilterRule.Action.values()[action.getSelectedItemPosition()];
        rule.caseSensitive = caseSensitive.isChecked();
        rule.enabled = enabled.isChecked();
        rule.feedId = scope.getSelectedItemPosition() == 0
                ? null
                : feeds.get(scope.getSelectedItemPosition() - 1).url;
        return rule;
    }

    private void showFilterDialog(final FilterRule existing) {
        final List<Feed> feeds = store.getFeeds();
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 4), pad, 0);

        wrapper.addView(dialogLabel("Nombre del filtro"), Ui.matchWrap());
        final EditText name = new EditText(this);
        name.setHint("Política, deportes, IA...");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) {
            name.setText(existing.name);
        }
        wrapper.addView(name, Ui.matchWrap());

        wrapper.addView(dialogLabel("Patrón"), Ui.matchWrap());
        final EditText pattern = new EditText(this);
        pattern.setHint("Ejemplo: \\b(android|kotlin)\\b");
        pattern.setSingleLine(true);
        pattern.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (existing != null) {
            pattern.setText(existing.pattern);
        }
        wrapper.addView(pattern, Ui.matchWrap());

        wrapper.addView(dialogLabel("Tipo de coincidencia"), Ui.matchWrap());
        final Spinner match = spinner(MATCH_LABELS,
                existing == null ? 0 : existing.matchType.ordinal());
        wrapper.addView(match, Ui.matchWrap());

        wrapper.addView(dialogLabel("Campo donde buscar"), Ui.matchWrap());
        final Spinner field = spinner(FIELD_LABELS,
                existing == null ? 0 : existing.field.ordinal());
        wrapper.addView(field, Ui.matchWrap());

        wrapper.addView(dialogLabel("Acción"), Ui.matchWrap());
        final Spinner action = spinner(ACTION_LABELS,
                existing == null ? FilterRule.Action.HIDE.ordinal() : existing.action.ordinal());
        wrapper.addView(action, Ui.matchWrap());

        final List<String> scopeLabels = new ArrayList<String>();
        scopeLabels.add("Todas las fuentes");
        for (Feed feed : feeds) {
            scopeLabels.add(feed.displayTitle());
        }
        wrapper.addView(dialogLabel("Fuente"), Ui.matchWrap());
        final Spinner scope = new Spinner(this);
        ArrayAdapter<String> scopeAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, scopeLabels);
        scopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scope.setAdapter(scopeAdapter);
        int scopeIndex = 0;
        if (existing != null && existing.feedId != null) {
            for (int i = 0; i < feeds.size(); i++) {
                if (feeds.get(i).url.equals(existing.feedId)) {
                    scopeIndex = i + 1;
                    break;
                }
            }
        }
        scope.setSelection(scopeIndex);
        wrapper.addView(scope, Ui.matchWrap());

        final CheckBox caseSensitive = new CheckBox(this);
        caseSensitive.setText("Distinguir mayúsculas y minúsculas");
        caseSensitive.setTextColor(Ui.textColor(this));
        caseSensitive.setChecked(existing != null && existing.caseSensitive);
        caseSensitive.setPadding(0, Ui.dp(this, 8), 0, 0);
        wrapper.addView(caseSensitive, Ui.matchWrap());

        final CheckBox enabled = new CheckBox(this);
        enabled.setText("Filtro activo");
        enabled.setTextColor(Ui.textColor(this));
        enabled.setChecked(existing == null || existing.enabled);
        wrapper.addView(enabled, Ui.matchWrap());

        TextView tip = Ui.text(this,
                "Mostrar/Ocultar controla la lista. Notificar/Silenciar controla los avisos. "
                        + "Las reglas Ocultar, Silenciar y las que no coinciden no afectan.",
                12, Ui.secondaryColor(this), false);
        tip.setPadding(0, Ui.dp(this, 10), 0, 0);
        wrapper.addView(tip, Ui.matchWrap());

        final TextView testAction = Ui.action(this,
                "Probar con contenido de notificación", true);
        testAction.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        testAction.setVisibility(isNotificationAction(action.getSelectedItemPosition())
                ? View.VISIBLE : View.GONE);
        testAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterRule draft = readFilterForm(existing, name, pattern, match,
                        field, action, caseSensitive, enabled, scope, feeds);
                String error = FilterEngine.validationError(draft);
                if (error != null) {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    return;
                }
                showFilterTestDialog(draft);
            }
        });
        wrapper.addView(testAction, Ui.matchWrap());
        action.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                testAction.setVisibility(isNotificationAction(position)
                        ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(wrapper);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nuevo filtro" : "Editar filtro")
                .setView(scroll)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                FilterRule rule = readFilterForm(existing, name, pattern,
                                        match, field, action, caseSensitive, enabled,
                                        scope, feeds);
                                if (rule.id == null || rule.id.length() == 0) {
                                    rule.id = UUID.randomUUID().toString();
                                }
                                String error = FilterEngine.validationError(rule);
                                if (error != null) {
                                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                List<FilterRule> rules = store.getRules();
                                boolean replaced = false;
                                for (int i = 0; i < rules.size(); i++) {
                                    if (rules.get(i).id != null
                                            && rules.get(i).id.equals(rule.id)) {
                                        rules.set(i, rule);
                                        replaced = true;
                                        break;
                                    }
                                }
                                if (!replaced) {
                                    rules.add(rule);
                                }
                                store.saveRules(rules);
                                dialog.dismiss();
                                render();
                                Toast.makeText(MainActivity.this,
                                        "Filtro guardado y aplicado.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
        dialog.show();
    }

    private void confirmDeleteRule(final FilterRule rule) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar filtro")
                .setMessage("¿Eliminar \"" + rule.name + "\"?")
                .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<FilterRule> rules = store.getRules();
                        for (int i = rules.size() - 1; i >= 0; i--) {
                            if (rules.get(i).id != null && rules.get(i).id.equals(rule.id)) {
                                rules.remove(i);
                            }
                        }
                        store.saveRules(rules);
                        render();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showSettingsDialog() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 4), pad, 0);

        wrapper.addView(dialogLabel("Tema"), Ui.matchWrap());
        final Spinner theme = spinner(THEME_LABELS, store.getThemeMode());
        wrapper.addView(theme, Ui.matchWrap());

        wrapper.addView(dialogLabel("Sincronización en segundo plano"), Ui.matchWrap());
        final Spinner interval = spinner(INTERVAL_LABELS, intervalIndex(store.getSyncIntervalMinutes()));
        wrapper.addView(interval, Ui.matchWrap());

        wrapper.addView(dialogLabel("Eliminar artículos antiguos"), Ui.matchWrap());
        final Spinner retention = spinner(RETENTION_LABELS,
                retentionIndex(store.getRetentionDays()));
        wrapper.addView(retention, Ui.matchWrap());

        final CheckBox notifications = new CheckBox(this);
        notifications.setText("Notificaciones de artículos nuevos");
        notifications.setTextColor(Ui.textColor(this));
        notifications.setChecked(store.notificationsEnabled());
        notifications.setPadding(0, Ui.dp(this, 12), 0, 0);
        wrapper.addView(notifications, Ui.matchWrap());

        TextView permission = Ui.text(this,
                notificationsGranted()
                        ? "Permiso del sistema: concedido."
                        : "Permiso del sistema: desactivado. Usa el botón inferior para abrirlo.",
                12, notificationsGranted() ? Ui.accent(this) : Ui.danger(this), false);
        permission.setPadding(0, Ui.dp(this, 6), 0, 0);
        wrapper.addView(permission, Ui.matchWrap());

        TextView note = Ui.text(this,
                "La sincronización usa JobScheduler. Android puede retrasarla por ahorro de energía. "
                        + "Las notificaciones solo se generan para artículos nuevos que superen los filtros. "
                        + "Los artículos antiguos se eliminan al iniciar y al sincronizar.",
                12, Ui.secondaryColor(this), false);
        note.setPadding(0, Ui.dp(this, 12), 0, 0);
        wrapper.addView(note, Ui.matchWrap());

        TextView about = Ui.text(this,
                "RSSPro 1.1 · Los datos y las fuentes se guardan solo en este dispositivo.",
                11, Ui.secondaryColor(this), false);
        about.setPadding(0, Ui.dp(this, 14), 0, 0);
        wrapper.addView(about, Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(wrapper);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Ajustes")
                .setView(scroll)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null);
        if (!notificationsGranted()) {
            builder.setNeutralButton("Sistema", null);
        }
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                if (!notificationsGranted()) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    openSystemNotificationSettings();
                                }
                            });
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int oldTheme = store.getThemeMode();
                                int oldInterval = store.getSyncIntervalMinutes();
                                int oldRetention = store.getRetentionDays();
                                boolean oldNotifications = store.notificationsEnabled();
                                int newTheme = theme.getSelectedItemPosition();
                                int newInterval = INTERVAL_VALUES[interval.getSelectedItemPosition()];
                                int newRetention = RETENTION_VALUES[retention.getSelectedItemPosition()];
                                boolean newNotifications = notifications.isChecked();

                                store.setThemeMode(newTheme);
                                store.setSyncIntervalMinutes(newInterval);
                                store.setRetentionDays(newRetention);
                                store.setNotificationsEnabled(newNotifications);
                                Scheduler.schedule(MainActivity.this);
                                dialog.dismiss();

                                if (oldRetention != newRetention) {
                                    pruneInBackground();
                                }
                                if (oldTheme != newTheme) {
                                    recreate();
                                    return;
                                }
                                render();
                                if (newNotifications && !notificationsGranted()) {
                                    requestNotificationPermission(true);
                                } else if (oldInterval != newInterval) {
                                    Toast.makeText(MainActivity.this,
                                            "Intervalo de sincronización actualizado.",
                                            Toast.LENGTH_SHORT).show();
                                } else if (oldRetention != newRetention) {
                                    Toast.makeText(MainActivity.this,
                                            "Retención de artículos actualizada.",
                                            Toast.LENGTH_SHORT).show();
                                } else if (oldNotifications != newNotifications) {
                                    Toast.makeText(MainActivity.this,
                                            newNotifications
                                                    ? "Notificaciones activadas."
                                                    : "Notificaciones desactivadas.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
        dialog.show();
    }

    private Spinner spinner(String[] labels, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected >= 0 && selected < labels.length) {
            spinner.setSelection(selected);
        }
        return spinner;
    }

    private TextView dialogLabel(String text) {
        TextView label = Ui.text(this, text, 12, Ui.secondaryColor(this), true);
        label.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        return label;
    }

    private int intervalIndex(int minutes) {
        for (int i = 0; i < INTERVAL_VALUES.length; i++) {
            if (INTERVAL_VALUES[i] == minutes) {
                return i;
            }
        }
        return 1;
    }

    private int retentionIndex(int days) {
        for (int i = 0; i < RETENTION_VALUES.length; i++) {
            if (RETENTION_VALUES[i] == days) {
                return i;
            }
        }
        return 2;
    }

    private void pruneInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int removed = SyncManager.pruneCached(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        render();
                        if (removed > 0) {
                            Toast.makeText(MainActivity.this,
                                    "Artículos antiguos eliminados: " + removed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }, "rsspro-prune").start();
    }

    private boolean notificationsGranted() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return Build.VERSION.SDK_INT < 24 || (manager != null && manager.areNotificationsEnabled());
    }

    private void requestNotificationPermission(boolean force) {
        if (Build.VERSION.SDK_INT < 33 || notificationsGranted()) {
            return;
        }
        if (!store.notificationsEnabled()) {
            return;
        }
        if (!force && store.notificationPermissionAsked()) {
            return;
        }
        store.markNotificationPermissionAsked();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void openSystemNotificationSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 26) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudieron abrir los ajustes del sistema.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private int actionColor(FilterRule.Action action) {
        if (action == FilterRule.Action.HIDE || action == FilterRule.Action.MUTE) {
            return action == FilterRule.Action.HIDE ? Ui.danger(this) : Ui.secondaryColor(this);
        }
        return Ui.accent(this);
    }

    private String actionLabel(FilterRule.Action action) {
        return ACTION_LABELS[action.ordinal()];
    }

    private String matchLabel(FilterRule.MatchType type) {
        return MATCH_LABELS[type.ordinal()];
    }

    private String fieldLabel(FilterRule.Field field) {
        return FIELD_LABELS[field.ordinal()];
    }

    private boolean isNotificationAction(int position) {
        return position == FilterRule.Action.NOTIFY.ordinal()
                || position == FilterRule.Action.MUTE.ordinal();
    }

    private void showFilterTestDialog(final FilterRule draft) {
        final List<Feed> feeds = store.getFeeds();
        final List<Article> cached = new ArrayList<Article>(store.getArticles());
        Collections.sort(cached, new Comparator<Article>() {
            @Override
            public int compare(Article left, Article right) {
                return Long.compare(right.publishedAt, left.publishedAt);
            }
        });
        while (cached.size() > 50) {
            cached.remove(cached.size() - 1);
        }

        final Random random = new Random();
        final FilterSimulator.Sample[] generated = {
                FilterSimulator.generate(draft, feeds, random)
        };
        final Article[] selectedCache = {cached.isEmpty() ? null : cached.get(0)};
        final int[] mode = {0};

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 4));

        TextView intro = Ui.text(this,
                "Comprueba el filtro sin esperar a una notificación real. "
                        + "Los cambios del formulario se usan solo para esta prueba.",
                12, Ui.secondaryColor(this), false);
        wrapper.addView(intro, Ui.matchWrap());

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        badges.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView actionBadge = Ui.chip(this, actionLabel(draft.action), actionColor(draft.action));
        badges.addView(actionBadge, Ui.wrapWrap());
        TextView fieldBadge = Ui.chip(this, fieldLabel(draft.field), Ui.accent(this));
        LinearLayout.LayoutParams fieldBadgeParams = Ui.wrapWrap();
        fieldBadgeParams.leftMargin = Ui.dp(this, 6);
        badges.addView(fieldBadge, fieldBadgeParams);
        wrapper.addView(badges, Ui.matchWrap());

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, Ui.dp(this, 12), 0, 0);
        final TextView generatedChip = feedFilterChip("Contenido generado", true);
        final TextView cacheChip = feedFilterChip("Artículo de la caché", false);
        modeRow.addView(generatedChip, Ui.wrapWrap());
        LinearLayout.LayoutParams cacheChipParams = Ui.wrapWrap();
        cacheChipParams.leftMargin = Ui.dp(this, 6);
        modeRow.addView(cacheChip, cacheChipParams);
        wrapper.addView(modeRow, Ui.matchWrap());

        LinearLayout generatedBox = new LinearLayout(this);
        generatedBox.setOrientation(LinearLayout.VERTICAL);
        generatedBox.setPadding(0, Ui.dp(this, 12), 0, 0);
        TextView inputLabel = dialogLabel("Texto de prueba · " + fieldLabel(draft.field));
        inputLabel.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        generatedBox.addView(inputLabel, Ui.matchWrap());
        final EditText input = new EditText(this);
        input.setText(generated[0].editableText(draft));
        input.setTextSize(13);
        input.setTextColor(Ui.textColor(this));
        input.setHintTextColor(Ui.secondaryColor(this));
        input.setBackground(Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 10)));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        generatedBox.addView(input, Ui.matchWrap());
        TextView another = Ui.action(this, "Generar otro contenido", true);
        another.setPadding(0, Ui.dp(this, 8), 0, 0);
        generatedBox.addView(another, Ui.matchWrap());
        wrapper.addView(generatedBox, Ui.matchWrap());

        LinearLayout cacheBox = new LinearLayout(this);
        cacheBox.setOrientation(LinearLayout.VERTICAL);
        cacheBox.setPadding(0, Ui.dp(this, 12), 0, 0);
        cacheBox.setVisibility(View.GONE);
        TextView cacheLabel = dialogLabel("Artículo real");
        cacheLabel.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        cacheBox.addView(cacheLabel, Ui.matchWrap());
        final Spinner cacheSpinner = new Spinner(this);
        String[] cacheLabels = new String[Math.max(1, cached.size())];
        if (cached.isEmpty()) {
            cacheLabels[0] = "No hay artículos en la caché";
            cacheSpinner.setEnabled(false);
        } else {
            for (int i = 0; i < cached.size(); i++) {
                Article item = cached.get(i);
                String title = item.title == null || item.title.length() == 0
                        ? "(Sin título)" : item.title;
                if (title.length() > 70) {
                    title = title.substring(0, 67) + "...";
                }
                cacheLabels[i] = title;
            }
        }
        ArrayAdapter<String> cacheAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, cacheLabels);
        cacheAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cacheSpinner.setAdapter(cacheAdapter);
        cacheBox.addView(cacheSpinner, Ui.matchWrap());
        final TextView cacheMeta = Ui.text(this, "", 11, Ui.secondaryColor(this), false);
        cacheMeta.setPadding(0, Ui.dp(this, 5), 0, 0);
        cacheBox.addView(cacheMeta, Ui.matchWrap());
        wrapper.addView(cacheBox, Ui.matchWrap());

        TextView resultLabel = dialogLabel("Resultado");
        wrapper.addView(resultLabel, Ui.matchWrap());
        final TextView result = Ui.text(this, "", 12, Ui.textColor(this), false);
        result.setBackground(Ui.rounded(Ui.surfaceAlt(this), Ui.dp(this, 10)));
        result.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        result.setTextIsSelectable(true);
        wrapper.addView(result, Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(wrapper);

        final Runnable evaluate = new Runnable() {
            @Override
            public void run() {
                Article article;
                if (mode[0] == 0) {
                    article = FilterSimulator.withText(generated[0].article, draft,
                            input.getText().toString()).article;
                } else {
                    article = selectedCache[0];
                }
                if (article == null) {
                    result.setText("Añade alguna fuente y sincroniza para probar con un "
                            + "artículo real, o usa el contenido generado.");
                    cacheMeta.setText("");
                    return;
                }
                if (article.id != null && article.feedId != null) {
                    cacheMeta.setText((mode[0] == 0 ? "Generado · " : "")
                            + (article.feedTitle == null ? "" : article.feedTitle)
                            + (article.category == null || article.category.length() == 0
                            ? "" : " · " + article.category));
                } else {
                    cacheMeta.setText("");
                }

                FilterSimulator.Outcome outcome = FilterSimulator.evaluate(article,
                        store.getRules(), draft);
                StringBuilder text = new StringBuilder();
                text.append(outcome.ruleMatches ? "La regla coincide" : "La regla no coincide");
                if (!outcome.inScope) {
                    text.append("\nLa fuente del artículo queda fuera del ámbito del filtro.");
                }
                if (!draft.enabled) {
                    text.append("\nEl filtro está desactivado, así que no interviene.");
                }
                text.append("\n\nResultado global");
                text.append(outcome.visible
                        ? "\nVisible en la lista: sí"
                        : "\nVisible en la lista: no");
                text.append(outcome.notify
                        ? "\nNotificaría: sí"
                        : "\nNotificaría: no");
                if (outcome.matches.isEmpty()) {
                    text.append("\n\nNo coincide ninguna regla activa.");
                } else {
                    text.append("\n\nReglas activas que coinciden:");
                    for (FilterRule rule : outcome.matches) {
                        text.append("\n• ").append(actionLabel(rule.action))
                                .append(" · ").append(rule.name);
                        if (rule == draft || (rule.id != null && draft.id != null
                                && rule.id.equals(draft.id))) {
                            text.append(" (en prueba)");
                        }
                    }
                }
                if (!outcome.visible) {
                    text.append("\n\nUna regla Ocultar coincide o no hay ninguna regla "
                            + "Mostrar que coincida.");
                } else if (!outcome.notify) {
                    text.append("\n\nUna regla Silenciar coincide o hay reglas Notificar "
                            + "y ninguna coincide.");
                }
                result.setText(text.toString());
            }
        };

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mode[0] = v == cacheChip ? 1 : 0;
                generatedBox.setVisibility(mode[0] == 0 ? View.VISIBLE : View.GONE);
                cacheBox.setVisibility(mode[0] == 1 ? View.VISIBLE : View.GONE);
                applyModeChipStyle(generatedChip, mode[0] == 0);
                applyModeChipStyle(cacheChip, mode[0] == 1);
                evaluate.run();
            }
        };
        generatedChip.setOnClickListener(modeListener);
        cacheChip.setOnClickListener(modeListener);
        another.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generated[0] = FilterSimulator.generate(draft, feeds, random);
                input.setText(generated[0].editableText(draft));
                evaluate.run();
            }
        });
        cacheSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < cached.size()) {
                    selectedCache[0] = cached.get(position);
                }
                evaluate.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Probar filtro")
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Probar", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                evaluate.run();
                            }
                        });
            }
        });
        dialog.show();
        evaluate.run();
    }

    private String feedName(String feedId) {
        if (feedId == null || feedId.length() == 0) {
            return "Todas las fuentes";
        }
        for (Feed feed : store.getFeeds()) {
            if (feed.url.equals(feedId)) {
                return feed.displayTitle();
            }
        }
        return "Fuente eliminada";
    }

    private String relativeTime(long timestamp) {
        if (timestamp <= 0) {
            return "nunca";
        }
        long now = System.currentTimeMillis();
        if (timestamp <= now && now - timestamp < 7L * DateUtils.DAY_IN_MILLIS) {
            CharSequence value = DateUtils.getRelativeTimeSpanString(
                    timestamp, now, DateUtils.MINUTE_IN_MILLIS);
            return value.toString();
        }
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private TextView dangerAction(String label) {
        TextView view = Ui.text(this, label, 13, Ui.danger(this), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(Ui.ripple(this,
                Ui.rounded(0x00000000, Ui.dp(this, 10)), Ui.danger(this)));
        return view;
    }

    private class ArticleAdapter extends BaseAdapter {
        private final List<Article> articles;

        ArticleAdapter(List<Article> articles) {
            this.articles = articles;
        }

        @Override
        public int getCount() {
            return articles.size();
        }

        @Override
        public Article getItem(int position) {
            return articles.get(position);
        }

        @Override
        public long getItemId(int position) {
            String id = articles.get(position).id;
            return id == null ? position : id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ArticleHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.card(MainActivity.this, true));
                card.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 13),
                        Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 13));

                LinearLayout top = new LinearLayout(MainActivity.this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                View dot = new View(MainActivity.this);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8));
                dotParams.rightMargin = Ui.dp(MainActivity.this, 8);
                dot.setBackground(Ui.rounded(Ui.accent(MainActivity.this),
                        Ui.dp(MainActivity.this, 4)));
                top.addView(dot, dotParams);

                TextView feed = Ui.text(MainActivity.this, "", 11,
                        Ui.secondaryColor(MainActivity.this), true);
                top.addView(feed, Ui.weightWrap(1));

                TextView date = Ui.text(MainActivity.this, "", 11,
                        Ui.secondaryColor(MainActivity.this), false);
                top.addView(date, Ui.wrapWrap());

                TextView title = Ui.text(MainActivity.this, "", 17,
                        Ui.textColor(MainActivity.this), true);
                title.setMaxLines(3);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setPadding(0, Ui.dp(MainActivity.this, 6), 0, 0);

                TextView summary = Ui.text(MainActivity.this, "", 14,
                        Ui.secondaryColor(MainActivity.this), false);
                summary.setMaxLines(2);
                summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
                summary.setPadding(0, Ui.dp(MainActivity.this, 4), 0, 0);

                LinearLayout textColumn = new LinearLayout(MainActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.addView(title, Ui.matchWrap());
                textColumn.addView(summary, Ui.matchWrap());

                ImageView image = new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(Ui.rounded(Ui.surfaceAlt(MainActivity.this),
                        Ui.dp(MainActivity.this, 10)));
                image.setContentDescription("Imagen del artículo");
                Ui.clipRounded(image, Ui.dp(MainActivity.this, 10));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 88), Ui.dp(MainActivity.this, 88));
                imageParams.leftMargin = Ui.dp(MainActivity.this, 12);

                LinearLayout contentRow = new LinearLayout(MainActivity.this);
                contentRow.setOrientation(LinearLayout.HORIZONTAL);
                contentRow.setGravity(Gravity.TOP);
                contentRow.addView(textColumn, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                contentRow.addView(image, imageParams);

                card.addView(top, Ui.matchWrap());
                card.addView(contentRow, Ui.matchWrap());

                holder = new ArticleHolder();
                holder.card = card;
                holder.dot = dot;
                holder.feed = feed;
                holder.date = date;
                holder.title = title;
                holder.summary = summary;
                holder.image = image;
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (ArticleHolder) convertView.getTag();
            }

            final Article article = getItem(position);
            boolean read = store.isRead(article.id);
            holder.card.setTranslationX(0);
            holder.card.setAlpha(1.0f);
            holder.dot.setVisibility(read ? View.INVISIBLE : View.VISIBLE);
            holder.feed.setText(article.feedTitle);
            holder.date.setText(relativeTime(article.publishedAt));
            holder.title.setText(article.displayTitle());
            holder.title.setTypeface(Typeface.create(
                    read ? "sans-serif" : "sans-serif-medium", Typeface.NORMAL));
            if (article.summary == null || article.summary.trim().length() == 0) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(article.summary);
            }
            if (article.imageUrl == null || article.imageUrl.trim().length() == 0) {
                holder.image.setVisibility(View.GONE);
                ImageLoader.clear(holder.image);
            } else {
                holder.image.setVisibility(View.VISIBLE);
                ImageLoader.load(MainActivity.this, holder.image, article.imageUrl,
                        Ui.dp(MainActivity.this, 88), Ui.dp(MainActivity.this, 88));
            }
            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openArticle(article.id);
                }
            });
            holder.card.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                private float startY;
                private boolean moved;
                private boolean swiping;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getX();
                            startY = event.getY();
                            moved = false;
                            swiping = false;
                            if (!store.isRead(article.id)) {
                                view.animate().cancel();
                                view.setTranslationX(0);
                                view.setAlpha(1.0f);
                            }
                            view.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getX() - startX;
                            float dy = event.getY() - startY;
                            int slop = ViewConfiguration.get(MainActivity.this)
                                    .getScaledTouchSlop();
                            if (!swiping) {
                                if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                                    moved = true;
                                    view.setPressed(false);
                                }
                                if (dx > slop && Math.abs(dx) > Math.abs(dy) * 1.1f
                                        && !store.isRead(article.id)) {
                                    swiping = true;
                                    view.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                            }
                            if (swiping) {
                                float distance = Math.min(dx,
                                        Ui.dp(MainActivity.this, SWIPE_MAX_DP));
                                view.setTranslationX(Math.max(0, distance));
                                view.setAlpha(1.0f - Math.min(0.22f,
                                        distance / (float) Ui.dp(MainActivity.this, SWIPE_MAX_DP)
                                                * 0.22f));
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            view.setPressed(false);
                            view.getParent().requestDisallowInterceptTouchEvent(false);
                            if (swiping) {
                                if (view.getTranslationX()
                                        >= Ui.dp(MainActivity.this, SWIPE_COMMIT_DP)) {
                                    markArticleReadFromSwipe(article, view);
                                } else {
                                    view.animate()
                                            .translationX(0)
                                            .alpha(1.0f)
                                            .setDuration(SWIPE_RESET_MS)
                                            .start();
                                }
                            } else {
                                view.setTranslationX(0);
                                view.setAlpha(1.0f);
                                if (!moved) {
                                    view.performClick();
                                }
                            }
                            swiping = false;
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            view.setPressed(false);
                            view.getParent().requestDisallowInterceptTouchEvent(false);
                            view.setTranslationX(0);
                            view.setAlpha(1.0f);
                            swiping = false;
                            return true;
                        default:
                            return false;
                    }
                }
            });
            return convertView;
        }
    }

    private static class ArticleHolder {
        LinearLayout card;
        View dot;
        TextView feed;
        TextView date;
        TextView title;
        TextView summary;
        ImageView image;
    }

    private class FeedAdapter extends BaseAdapter {
        private final List<Feed> feeds;

        FeedAdapter(List<Feed> feeds) {
            this.feeds = feeds;
        }

        @Override
        public int getCount() {
            return feeds.size();
        }

        @Override
        public Feed getItem(int position) {
            return feeds.get(position);
        }

        @Override
        public long getItemId(int position) {
            return feeds.get(position).url.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FeedHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.card(MainActivity.this, true));
                card.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 13),
                        Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 13));

                LinearLayout top = new LinearLayout(MainActivity.this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                ImageView logo = new ImageView(MainActivity.this);
                logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                logo.setBackground(Ui.rounded(Ui.surfaceAlt(MainActivity.this),
                        Ui.dp(MainActivity.this, 8)));
                logo.setContentDescription("Logo de la fuente");
                Ui.clipRounded(logo, Ui.dp(MainActivity.this, 8));
                LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 40), Ui.dp(MainActivity.this, 40));
                logoParams.rightMargin = Ui.dp(MainActivity.this, 9);
                top.addView(logo, logoParams);
                TextView title = Ui.text(MainActivity.this, "", 16,
                        Ui.textColor(MainActivity.this), true);
                top.addView(title, Ui.weightWrap(1));
                TextView delete = dangerAction("Eliminar");
                LinearLayout.LayoutParams deleteParams = Ui.wrapWrap();
                deleteParams.leftMargin = Ui.dp(MainActivity.this, 8);
                top.addView(delete, deleteParams);

                TextView url = Ui.text(MainActivity.this, "", 12,
                        Ui.secondaryColor(MainActivity.this), false);
                url.setMaxLines(1);
                url.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                url.setPadding(0, Ui.dp(MainActivity.this, 4), 0, 0);

                TextView status = Ui.text(MainActivity.this, "", 11,
                        Ui.secondaryColor(MainActivity.this), false);
                status.setMaxLines(3);
                status.setPadding(0, Ui.dp(MainActivity.this, 7), 0, 0);

                card.addView(top, Ui.matchWrap());
                card.addView(url, Ui.matchWrap());
                card.addView(status, Ui.matchWrap());

                holder = new FeedHolder();
                holder.card = card;
                holder.logo = logo;
                holder.title = title;
                holder.url = url;
                holder.status = status;
                holder.delete = delete;
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (FeedHolder) convertView.getTag();
            }

            final Feed feed = getItem(position);
            holder.title.setText(feed.displayTitle());
            holder.url.setText(feed.url);
            if (feed.imageUrl == null || feed.imageUrl.trim().length() == 0) {
                holder.logo.setVisibility(View.GONE);
                ImageLoader.clear(holder.logo);
            } else {
                holder.logo.setVisibility(View.VISIBLE);
                ImageLoader.load(MainActivity.this, holder.logo, feed.imageUrl,
                        Ui.dp(MainActivity.this, 40), Ui.dp(MainActivity.this, 40));
            }
            if (feed.lastError != null && feed.lastError.trim().length() > 0) {
                holder.status.setTextColor(Ui.danger(MainActivity.this));
                holder.status.setText("Error: " + feed.lastError);
            } else if (feed.lastFetch > 0) {
                holder.status.setTextColor(Ui.secondaryColor(MainActivity.this));
                holder.status.setText("Actualizado " + relativeTime(feed.lastFetch));
            } else {
                holder.status.setTextColor(Ui.secondaryColor(MainActivity.this));
                holder.status.setText("Pendiente de sincronizar");
            }
            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFeedId = feed.url;
                    selectTab(TAB_ARTICLES);
                }
            });
            holder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteFeed(feed);
                }
            });
            return convertView;
        }
    }

    private static class FeedHolder {
        LinearLayout card;
        ImageView logo;
        TextView title;
        TextView url;
        TextView status;
        TextView delete;
    }

    private class FilterAdapter extends BaseAdapter {
        private final List<FilterRule> rules;

        FilterAdapter(List<FilterRule> rules) {
            this.rules = rules;
        }

        @Override
        public int getCount() {
            return rules.size();
        }

        @Override
        public FilterRule getItem(int position) {
            return rules.get(position);
        }

        @Override
        public long getItemId(int position) {
            String id = rules.get(position).id;
            return id == null ? position : id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FilterHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.card(MainActivity.this, false));
                card.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 12),
                        Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 10));

                LinearLayout top = new LinearLayout(MainActivity.this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView chip = Ui.chip(MainActivity.this, "", Ui.accent(MainActivity.this));
                top.addView(chip, Ui.wrapWrap());
                TextView name = Ui.text(MainActivity.this, "", 15,
                        Ui.textColor(MainActivity.this), true);
                name.setPadding(Ui.dp(MainActivity.this, 9), 0, Ui.dp(MainActivity.this, 6), 0);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                top.addView(name, Ui.weightWrap(1));
                Switch toggle = new Switch(MainActivity.this);
                toggle.setShowText(false);
                top.addView(toggle, Ui.wrapWrap());

                TextView pattern = Ui.text(MainActivity.this, "", 13,
                        Ui.textColor(MainActivity.this), false);
                pattern.setTypeface(Typeface.MONOSPACE);
                pattern.setPadding(0, Ui.dp(MainActivity.this, 9), 0, 0);
                pattern.setMaxLines(2);
                pattern.setEllipsize(android.text.TextUtils.TruncateAt.END);

                TextView meta = Ui.text(MainActivity.this, "", 11,
                        Ui.secondaryColor(MainActivity.this), false);
                meta.setPadding(0, Ui.dp(MainActivity.this, 5), 0, 0);

                LinearLayout actions = new LinearLayout(MainActivity.this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setPadding(0, Ui.dp(MainActivity.this, 5), 0, 0);
                TextView edit = Ui.action(MainActivity.this, "Editar", false);
                actions.addView(edit, Ui.wrapWrap());
                TextView test = Ui.action(MainActivity.this, "Probar", false);
                LinearLayout.LayoutParams testParams = Ui.wrapWrap();
                testParams.leftMargin = Ui.dp(MainActivity.this, 4);
                actions.addView(test, testParams);
                View spacer = new View(MainActivity.this);
                actions.addView(spacer, Ui.weightWrap(1));
                TextView delete = dangerAction("Eliminar");
                actions.addView(delete, Ui.wrapWrap());

                card.addView(top, Ui.matchWrap());
                card.addView(pattern, Ui.matchWrap());
                card.addView(meta, Ui.matchWrap());
                card.addView(actions, Ui.matchWrap());

                holder = new FilterHolder();
                holder.card = card;
                holder.chip = chip;
                holder.name = name;
                holder.pattern = pattern;
                holder.meta = meta;
                holder.toggle = toggle;
                holder.edit = edit;
                holder.test = test;
                holder.delete = delete;
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (FilterHolder) convertView.getTag();
            }

            final FilterRule rule = getItem(position);
            int color = actionColor(rule.action);
            holder.chip.setText(actionLabel(rule.action));
            holder.chip.setTextColor(color);
            holder.chip.setBackground(Ui.rounded(
                    Ui.blend(Ui.surfaceAlt(MainActivity.this), color, 0.18f),
                    Ui.dp(MainActivity.this, 20)));
            holder.name.setText(rule.name);
            holder.pattern.setText(rule.pattern);
            String scope = rule.feedId == null ? "Todas las fuentes" : feedName(rule.feedId);
            holder.meta.setText(matchLabel(rule.matchType) + " · " + fieldLabel(rule.field)
                    + " · " + scope);
            holder.card.setAlpha(rule.enabled ? 1.0f : 0.55f);
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(rule.enabled);
            holder.toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (rule.enabled == isChecked) {
                        return;
                    }
                    rule.enabled = isChecked;
                    List<FilterRule> current = store.getRules();
                    for (FilterRule item : current) {
                        if (item.id != null && item.id.equals(rule.id)) {
                            item.enabled = isChecked;
                        }
                    }
                    store.saveRules(current);
                    content.post(new Runnable() {
                        @Override
                        public void run() {
                            if (currentTab == TAB_FILTERS) {
                                renderFilters();
                            }
                        }
                    });
                }
            });
            holder.edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFilterDialog(rule);
                }
            });
            holder.test.setVisibility(isNotificationAction(rule.action.ordinal())
                    ? View.VISIBLE : View.GONE);
            holder.test.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFilterTestDialog(rule);
                }
            });
            holder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteRule(rule);
                }
            });
            return convertView;
        }
    }

    private static class FilterHolder {
        LinearLayout card;
        TextView chip;
        TextView name;
        TextView pattern;
        TextView meta;
        Switch toggle;
        TextView edit;
        TextView test;
        TextView delete;
    }
}
