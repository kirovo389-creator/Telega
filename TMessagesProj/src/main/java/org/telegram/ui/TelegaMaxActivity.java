package org.telegram.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import android.content.Context;

/**
 * MAX workspace backed by the official MAX Web client.
 *
 * This deliberately delegates sign-in, sessions, chat history, media and sending
 * to MAX itself. Telega does not collect or persist MAX passwords or login codes.
 * The official bot API cannot sign a user into a personal account; using the
 * first-party web client avoids an unofficial protocol dependency.
 */
public class TelegaMaxActivity extends BaseFragment {

    private static final String MAX_HOME = "https://web.max.ru/";
    private static final int FILE_CHOOSER_REQUEST_CODE = 19754;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView errorView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("MAX");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        // MAX uses the system file picker for attachments; leave content://
        // support enabled but do not expose file:// access to web content.
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        webView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    return handleNavigation(Uri.parse(url));
                } catch (Exception ignore) {
                    return true;
                }
            }

            private boolean handleNavigation(Uri uri) {
                if (uri == null) {
                    return true;
                }
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && isMaxHost(host)) {
                    return false;
                }
                if ("about".equalsIgnoreCase(scheme) && "blank".equalsIgnoreCase(uri.getLastPathSegment())) {
                    return false;
                }
                // Keep account sign-in and chats on MAX's own origin. User-tapped
                // external https links may still be opened in the browser.
                if ("https".equalsIgnoreCase(scheme)) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        if (getParentActivity() != null && intent.resolveActivity(getParentActivity().getPackageManager()) != null) {
                            getParentActivity().startActivity(intent);
                        }
                    } catch (Exception ignore) {
                    }
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                }
                if (errorView != null) {
                    errorView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setProgress(100);
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showLoadError();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Never bypass certificate errors for a page that may collect credentials.
                handler.cancel();
                showLoadError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (ActivityNotFoundException | SecurityException e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                Uri uri = Uri.parse(url);
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    if (getParentActivity() != null && intent.resolveActivity(getParentActivity().getPackageManager()) != null) {
                        getParentActivity().startActivity(intent);
                    }
                }
            } catch (Exception ignore) {
            }
        });

        root.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP));

        errorView = new TextView(context);
        errorView.setText("Не удалось загрузить MAX. Проверь интернет и нажми, чтобы повторить.");
        errorView.setTextSize(16);
        errorView.setGravity(Gravity.CENTER);
        errorView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        errorView.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(24));
        errorView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        errorView.setVisibility(View.GONE);
        errorView.setOnClickListener(v -> {
            if (webView != null) {
                errorView.setVisibility(View.GONE);
                webView.loadUrl(MAX_HOME);
            }
        });
        root.addView(errorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        webView.loadUrl(MAX_HOME);
        return fragmentView;
    }

    private boolean isMaxHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return value.equals("max.ru") || value.endsWith(".max.ru");
    }

    private void showLoadError() {
        AndroidUtilities.runOnUIThread(() -> {
            if (errorView != null && webView != null) {
                errorView.setVisibility(View.VISIBLE);
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (webView != null && webView.canGoBack()) {
            if (invoked) {
                webView.goBack();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onFragmentDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("about:blank");
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        progressBar = null;
        errorView = null;
        super.onFragmentDestroy();
    }
}
