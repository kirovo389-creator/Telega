package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

public class MaxWebFragment extends BaseFragment {
    private static final String MAX_URL = "https://web.max.ru";
    private WebView webView;

    public MaxWebFragment() {
        super();
    }

    public MaxWebFragment(Bundle args) {
        super(args);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("MAX");
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, Uri.parse(url));
            }

            private boolean handleUrl(WebView view, Uri uri) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme == null || host == null) {
                    return true;
                }
                if (!"https".equalsIgnoreCase(scheme)) {
                    return true;
                }
                if (isMaxHost(host)) {
                    return false;
                }
                try {
                    Context activity = getParentActivity();
                    if (activity != null) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    Toast.makeText(getParentActivity(), "Не удалось открыть ссылку.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            private boolean isMaxHost(String host) {
                String normalized = host.toLowerCase(java.util.Locale.ROOT);
                return normalized.equals("max.ru")
                        || normalized.endsWith(".max.ru")
                        || normalized.equals("oneme.ru")
                        || normalized.endsWith(".oneme.ru");
            }
        });

        root.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = root;
        webView.loadUrl(MAX_URL);
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onFragmentDestroy();
    }
}
