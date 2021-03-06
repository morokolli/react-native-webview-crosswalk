package com.jordansexton.react.crosswalk.webview;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.webkit.ValueCallback;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import org.xwalk.core.XWalkNavigationHistory;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkCookieManager;

import javax.annotation.Nullable;

class CrosswalkWebView extends XWalkView implements LifecycleEventListener {

    private final Activity activity;

    private final EventDispatcher eventDispatcher;

    private final ResourceClient resourceClient;
    private final UIClient uiClient;
    private final XWalkCookieManager cookieManager;
	
    private @Nullable String injectedJavaScript;

    private boolean isJavaScriptInjected;
    private boolean isChoosingFile;

    public CrosswalkWebView (ReactContext reactContext, Activity _activity) {
        super(reactContext, _activity);

        activity = _activity;
        eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
        resourceClient = new ResourceClient(this);
        uiClient = new UIClient(this);
        cookieManager = new XWalkCookieManager();

        this.setResourceClient(resourceClient);
        this.setUIClient(uiClient);
    }

    public Boolean getLocalhost () {
        return resourceClient.getLocalhost();
    }

    public void setLocalhost (Boolean localhost) {
        resourceClient.setLocalhost(localhost);
    }

    @Override
    public void onHostResume() {
        resumeTimers();
        onShow();
    }

    @Override
    public void onHostPause() {
        pauseTimers();
        if (!isChoosingFile) {
            onHide();
        }
    }

    @Override
    public void onHostDestroy() {
        onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (isChoosingFile) {
            isChoosingFile = false;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void load (String url, String content) {
        isJavaScriptInjected = false;
        isChoosingFile = false;
        super.load(url, content);
    }

    public void setCookie(String url, String cookie) {
        cookieManager.setCookie(url, cookie);
    }
        
    public void setInjectedJavaScript(@Nullable String js) {
        injectedJavaScript = js;
    }

    public void callInjectedJavaScript() {
        if (!isJavaScriptInjected) {
            isJavaScriptInjected = true;
        }

        if (injectedJavaScript != null && !TextUtils.isEmpty(injectedJavaScript)) {
            this.evaluateJavascript(injectedJavaScript, null);
        }
    }

    protected class ResourceClient extends XWalkResourceClient {

        private Boolean localhost = false;

        ResourceClient (XWalkView view) {
            super(view);
        }

        public Boolean getLocalhost () {
            return localhost;
        }

        public void setLocalhost (Boolean _localhost) {
            localhost = _localhost;
        }

        @Override
        public void onLoadFinished (XWalkView view, String url) {
            ((CrosswalkWebView) view).callInjectedJavaScript();

            XWalkNavigationHistory navigationHistory = view.getNavigationHistory();
            eventDispatcher.dispatchEvent(
                new NavigationStateChangeEvent(
                    getId(),
                    SystemClock.uptimeMillis(),
                    view.getTitle(),
                    false,
                    url,
                    navigationHistory.canGoBack(),
                    navigationHistory.canGoForward()
                )
            );

        }

        @Override
        public void onLoadStarted (XWalkView view, String url) {
            XWalkNavigationHistory navigationHistory = view.getNavigationHistory();
            eventDispatcher.dispatchEvent(
                new NavigationStateChangeEvent(
                    getId(),
                    SystemClock.uptimeMillis(),
                    view.getTitle(),
                    true,
                    url,
                    navigationHistory != null ? navigationHistory.canGoBack() : false,
                    navigationHistory != null ? navigationHistory.canGoForward() :false
                )
            );
        }

        @Override
        public void onReceivedLoadError (XWalkView view, int errorCode, String description, String failingUrl) {
            eventDispatcher.dispatchEvent(
                new ErrorEvent(
                    getId(),
                    SystemClock.uptimeMillis(),
                    errorCode,
                    description,
                    failingUrl
                )
            );
        }

        @Override
        public boolean shouldOverrideUrlLoading (XWalkView view, String url) {
            Uri uri = Uri.parse(url);
            if (uri.getScheme().equals(CrosswalkWebViewManager.JSNavigationScheme)) {
                onLoadFinished(view, url);
                return true;
            }
            else if (getLocalhost()) {
                if (uri.getHost().equals("localhost")) {
                    return false;
                }
                else {
                    overrideUri(uri);
                    return true;
                }
            }
            else if (uri.getScheme().equals("http") || uri.getScheme().equals("https") || uri.getScheme().equals("file")) {
                return false;
            }
            else {
                overrideUri(uri);
                return true;
            }
        }

        private void overrideUri (Uri uri) {
            Intent action = new Intent(Intent.ACTION_VIEW, uri);
            activity.startActivity(action);
        }
    }

    protected class UIClient extends XWalkUIClient {
        public UIClient(XWalkView view) {
            super(view);
        }

        @Override
        public void openFileChooser (XWalkView view, ValueCallback<Uri> uploadFile, String acceptType, String capture) {
            isChoosingFile = true;
            super.openFileChooser(view, uploadFile, acceptType, capture);
        }
    }
}
