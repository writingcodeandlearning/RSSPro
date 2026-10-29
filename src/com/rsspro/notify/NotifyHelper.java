package com.rsspro.notify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.rsspro.MainActivity;
import com.rsspro.R;
import com.rsspro.core.FilterEngine;
import com.rsspro.data.Article;
import com.rsspro.store.Store;
import com.rsspro.util.HtmlUtil;

import java.util.ArrayList;
import java.util.List;

public final class NotifyHelper {
    public static final String CHANNEL_ID = "rsspro_new_articles";
    private static final String GROUP_KEY = "rsspro_news";
    private static final int MAX_INDIVIDUAL = 8;
    private static final int SUMMARY_ID = 1;
    private static final int NOTIFICATION_COLOR = 0xFF1565C0;

    private NotifyHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nuevos artículos",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Avisos de artículos nuevos que coinciden con tus filtros.");
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    public static void postNew(Context context, List<Article> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        Store store = new Store(context);
        if (!store.notificationsEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            return;
        }

        FilterEngine engine = new FilterEngine(store.getRules());
        List<Article> matched = new ArrayList<Article>();
        for (Article article : candidates) {
            if (engine.shouldNotify(article)) {
                matched.add(article);
            }
        }
        if (matched.isEmpty()) {
            return;
        }

        createChannel(context);
        int count = Math.min(matched.size(), MAX_INDIVIDUAL);
        for (int i = 0; i < count; i++) {
            Article article = matched.get(i);
            manager.notify(notificationId(article.id), buildArticleNotification(context, article));
        }
        if (matched.size() > 1) {
            manager.notify(SUMMARY_ID, buildSummary(context, matched));
        }
    }

    private static Notification buildArticleNotification(Context context, Article article) {
        String feed = article.feedTitle == null ? "RSS" : article.feedTitle;
        String text = HtmlUtil.snippet(article.summary, 160);
        if (text.length() == 0) {
            text = feed;
        } else {
            text = feed + " · " + text;
        }
        String big = article.bodyText();
        if (big.length() > 1200) {
            big = big.substring(0, 1200) + "...";
        }
        if (big.length() == 0) {
            big = text;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_ARTICLE_ID, article.id);
        int requestCode = notificationId(article.id);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = newBuilder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(NOTIFICATION_COLOR)
                .setContentTitle(article.displayTitle())
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big).setSummaryText(feed))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(article.publishedAt > 0 ? article.publishedAt : System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setGroup(GROUP_KEY);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }
        return builder.build();
    }

    private static Notification buildSummary(Context context, List<Article> matched) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.InboxStyle style = new Notification.InboxStyle()
                .setBigContentTitle(matched.size() + " artículos nuevos");
        int lines = Math.min(matched.size(), 5);
        for (int i = 0; i < lines; i++) {
            style.addLine(matched.get(i).displayTitle());
        }
        if (matched.size() > lines) {
            style.setSummaryText("y " + (matched.size() - lines) + " más");
        }

        Notification.Builder builder = newBuilder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(NOTIFICATION_COLOR)
                .setContentTitle(matched.size() + " artículos nuevos")
                .setContentText("Coinciden con tus filtros de notificación")
                .setStyle(style)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }
        return builder.build();
    }

    private static Notification.Builder newBuilder(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(context, CHANNEL_ID);
        }
        return new Notification.Builder(context);
    }

    private static int notificationId(String key) {
        if (key == null) {
            return 1000;
        }
        return 1000 + ((key.hashCode() & 0x7FFFFFFF) % 1000000);
    }
}
