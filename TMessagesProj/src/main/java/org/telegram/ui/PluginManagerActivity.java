package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local plugin manager. Source files are imported and inspected as text only.
 * Arbitrary Python code is deliberately not executed in the app process.
 */
public class PluginManagerActivity extends BaseFragment {
    private static final int PICK_PLUGIN = 7401;
    private static final int MAX_PLUGIN_BYTES = 1024 * 1024;
    private LinearLayout pluginList;
    private TextView status;
    private File pluginDir;

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle("Плагины");
        }

        pluginDir = new File(context.getFilesDir(), "telega_plugins");
        if (!pluginDir.exists()) pluginDir.mkdirs();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(page, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout intro = new LinearLayout(context);
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(16), dp(16), dp(16), dp(16));
        intro.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
        page.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("Управление плагинами");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        intro.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText("Импортируйте файлы .plugin, просматривайте метаданные и управляйте локальной библиотекой. Неизвестный Python-код хранится как текст и не запускается автоматически.");
        description.setTextSize(14);
        description.setLineSpacing(dp(2), 1f);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams descParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descParams.topMargin = dp(8);
        intro.addView(description, descParams);

        Button importButton = new Button(context);
        importButton.setText("Импортировать .plugin");
        importButton.setOnClickListener(v -> openPluginPicker());
        LinearLayout.LayoutParams importParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(48));
        importParams.topMargin = dp(12);
        intro.addView(importButton, importParams);

        status = new TextView(context);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setTextSize(13);
        status.setPadding(dp(4), dp(10), dp(4), dp(8));
        page.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        page.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        pluginList = new LinearLayout(context);
        pluginList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pluginList, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        refreshPlugins();
        fragmentView = root;
        return root;
    }

    private void openPluginPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "text/plain"});
            startActivityForResult(intent, PICK_PLUGIN);
        } catch (Exception e) {
            Toast.makeText(getParentActivity(), "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != PICK_PLUGIN || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = queryDisplayName(uri);
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".plugin")) {
            Toast.makeText(getParentActivity(), "Выберите файл с расширением .plugin", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            byte[] bytes = readLimited(uri);
            String source = new String(bytes, StandardCharsets.UTF_8);
            String id = metadata(source, "__id__");
            String pluginName = metadata(source, "__name__");
            if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,31}") || pluginName == null || pluginName.trim().isEmpty()) {
                Toast.makeText(getParentActivity(), "Не найдены корректные __id__ и __name__ в метаданных", Toast.LENGTH_LONG).show();
                return;
            }
            File destination = new File(pluginDir, id + ".plugin");
            try (FileOutputStream output = new FileOutputStream(destination)) {
                output.write(bytes);
            }
            Toast.makeText(getParentActivity(), "Импортирован: " + pluginName + ". Код сохранён без запуска.", Toast.LENGTH_LONG).show();
            refreshPlugins();
        } catch (Exception e) {
            Toast.makeText(getParentActivity(), "Ошибка импорта: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readLimited(Uri uri) throws Exception {
        try (InputStream input = getParentActivity().getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Файл недоступен");
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0, read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PLUGIN_BYTES) throw new IllegalArgumentException("Максимальный размер файла — 1 МБ");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String queryDisplayName(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        try {
            android.database.Cursor cursor = getParentActivity().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (cursor.moveToFirst() && index >= 0) path = cursor.getString(index);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignore) {}
        return path;
    }

    private String metadata(String source, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(['\"])(.*?)\\1");
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(2).trim() : null;
    }

    private void refreshPlugins() {
        if (pluginList == null || pluginDir == null) return;
        pluginList.removeAllViews();
        File[] files = pluginDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".plugin"));
        if (files == null || files.length == 0) {
            status.setText("Плагины не установлены");
            TextView empty = new TextView(getContext());
            empty.setText("Импортированные плагины появятся здесь.");
            empty.setTextSize(14);
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            pluginList.addView(empty);
            return;
        }
        status.setText("Файлов: " + files.length + " · запуск Python-кода отключён");
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            String source = readText(file);
            String id = metadata(source, "__id__");
            String name = metadata(source, "__name__");
            String version = metadata(source, "__version__");
            String author = metadata(source, "__author__");
            String description = metadata(source, "__description__");
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));
            LinearLayout.LayoutParams cardParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            pluginList.addView(card, cardParams);

            TextView pluginTitle = new TextView(getContext());
            pluginTitle.setText(name == null ? file.getName() : name);
            pluginTitle.setTextSize(16);
            pluginTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            pluginTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            card.addView(pluginTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView details = new TextView(getContext());
            StringBuilder info = new StringBuilder();
            info.append("ID: ").append(id == null ? "не определён" : id);
            info.append(" · версия ").append(version == null ? "1.0" : version);
            if (author != null) info.append("\nАвтор: ").append(author);
            if (description != null) info.append("\n").append(description);
            info.append("\nPython-код не исполняется");
            details.setText(info.toString());
            details.setTextSize(13);
            details.setLineSpacing(dp(2), 1f);
            details.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            LinearLayout.LayoutParams detailParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            detailParams.topMargin = dp(6);
            card.addView(details, detailParams);

            Button delete = new Button(getContext());
            delete.setText("Удалить файл");
            delete.setOnClickListener(v -> new android.app.AlertDialog.Builder(getParentActivity())
                    .setTitle("Удалить плагин?")
                    .setMessage("Файл " + file.getName() + " будет удалён из локального хранилища.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        if (file.delete()) {
                            Toast.makeText(getParentActivity(), "Файл удалён", Toast.LENGTH_SHORT).show();
                            refreshPlugins();
                        } else {
                            Toast.makeText(getParentActivity(), "Не удалось удалить файл", Toast.LENGTH_LONG).show();
                        }
                    }).show());
            LinearLayout.LayoutParams deleteParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(44));
            deleteParams.topMargin = dp(6);
            card.addView(delete, deleteParams);
        }
    }

    private String readText(File file) {
        try (InputStream input = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
